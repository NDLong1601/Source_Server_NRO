param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$sourceRoot = Join-Path $repoRoot 'src'
$violations = New-Object System.Collections.Generic.List[string]
$restoreExactCallers = @(
    'src\nro\models\clan\ClanGiftService.java',
    'src\nro\models\clan\ClanTreasuryService.java',
    'src\nro\models\database\MrFinn.java',
    'src\nro\models\ledger\MoneyLedgerService.java',
    'src\nro\models\player\InventoryPersistenceSnapshot.java',
    'src\nro\models\player\PlayerWallet.java'
)

Get-ChildItem -LiteralPath $sourceRoot -Recurse -Filter '*.java' -File | ForEach-Object {
    $path = $_.FullName
    $relative = $path.Substring($repoRoot.Length + 1)
    $text = [IO.File]::ReadAllText($path)

    if ($relative -notlike '*\player\PlayerWallet.java') {
        $directMutation = '(?m)(?:\b(?:inventory|inv)\s*|\.inventory)\.(?:gold|gem|ruby|coupon)\s*(?:[+\-*/%]?=|\+\+|--)'
        if ([regex]::IsMatch($text, $directMutation)) {
            $violations.Add("${relative}: direct currency field mutation")
        }
    }

    if (($relative -notlike '*\player\Inventory.java') -and [regex]::IsMatch($text, '\.(?:subGold|addGold|subGem|addGem|tryCreditGemExact)\s*\(')) {
        $violations.Add("${relative}: legacy Inventory currency helper call")
    }

    # Match a complete wallet call whose return value is followed directly by a
    # semicolon. The balancing group handles multi-line/nested arguments. An
    # explicit WalletResult assignment is allowed; every other direct call must
    # chain requireSuccess() and therefore will not match this pattern.
    $discardedMutationPatterns = @(
        '\.(?:tryDebit|tryCreditExact|creditUpToCap|applyCommitted|restoreExact)\s*\((?>[^()]|\((?<depth>)|\)(?<-depth>))*(?(depth)(?!))\)\s*;',
        '(?:\.getWallet\(\)|\bPlayerWallet)\.(?:executeBatch|executePair|transfer)\s*\((?>[^()]|\((?<depth>)|\)(?<-depth>))*(?(depth)(?!))\)\s*;'
    )
    foreach ($pattern in $discardedMutationPatterns) {
        foreach ($match in [regex]::Matches($text, $pattern)) {
            $statementStart = [Math]::Max(
                [Math]::Max($text.LastIndexOf(';', $match.Index), $text.LastIndexOf('{', $match.Index)),
                $text.LastIndexOf('}', $match.Index)
            ) + 1
            $prefix = $text.Substring($statementStart, $match.Index - $statementStart)
            if ($prefix -notmatch '(?s)\b(?:WalletResult|var)\s+\w+\s*=\s*.*$') {
                $line = 1 + ([regex]::Matches($text.Substring(0, $match.Index), "`n")).Count
                $violations.Add("${relative}:${line}: wallet result is discarded")
            }
        }
    }

    if (($restoreExactCallers -notcontains $relative) -and $text -match '\.restoreExact\s*\(') {
        $violations.Add("${relative}: restoreExact is restricted to reviewed persistence/recovery boundaries")
    }
}

$walletSource = [IO.File]::ReadAllText((Join-Path $sourceRoot 'nro\models\player\PlayerWallet.java'))
if ($walletSource -match 'public\s+Inventory\s+getInventory\s*\(') {
    $violations.Add('PlayerWallet.java: backing Inventory is publicly exposed')
}
if ($walletSource -match 'public\s+void\s+hydrate\s*\(') {
    $violations.Add('PlayerWallet.java: unrestricted clamping hydration is publicly exposed')
}

if ($violations.Count -gt 0) {
    $violations | ForEach-Object { Write-Error $_ }
    exit 1
}

Write-Output 'WALLET_MUTATION_BOUNDARY_OK'
