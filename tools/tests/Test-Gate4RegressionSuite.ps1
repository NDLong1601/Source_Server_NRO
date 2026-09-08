param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$tempRoot = Join-Path $repoRoot ('target\gate4-' + [Guid]::NewGuid().ToString('N'))
$classes = Join-Path $tempRoot 'classes'
$sourceList = Join-Path $tempRoot 'sources.txt'
New-Item -ItemType Directory -Path $classes -Force | Out-Null

try {
    Get-ChildItem -LiteralPath (Join-Path $repoRoot 'src') -Recurse -Filter '*.java' -File |
        ForEach-Object { $_.FullName } |
        Set-Content -LiteralPath $sourceList -Encoding ASCII

    $classpath = (Join-Path $repoRoot '20.jar') + ';' + (Join-Path $repoRoot 'lib\*')
    $processorPath = Join-Path $repoRoot 'lib\lombok.jar'
    $savedPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & javac --release 17 -encoding UTF-8 -cp $classpath -processorpath $processorPath `
        -d $classes ("@" + $sourceList) 2>&1
    $compileExit = $LASTEXITCODE
    $ErrorActionPreference = $savedPreference
    if ($compileExit -ne 0) { exit $compileExit }

    $testSource = Join-Path $PSScriptRoot 'Gate4RuntimeOwnershipTest.java'
    & javac --release 17 -encoding UTF-8 -cp ($classes + ';' + $classpath) -d $classes $testSource
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    & java -cp ($classes + ';' + $classpath) nro.models.server.Gate4RuntimeOwnershipTest
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    $productionManagerCallers = Get-ChildItem -LiteralPath (Join-Path $repoRoot 'src') -Recurse -Filter '*.java' -File |
        Where-Object { $_.Name -ne 'Manager.java' } |
        Where-Object { [IO.File]::ReadAllText($_.FullName) -cmatch '\bManager\.' }
    if ($productionManagerCallers) {
        $relative = $productionManagerCallers | ForEach-Object { $_.FullName.Substring($repoRoot.Length + 1) }
        throw ('Manager direct callers remain: ' + ($relative -join ', '))
    }

    $managerSource = [IO.File]::ReadAllText((Join-Path $repoRoot 'src\nro\models\server\Manager.java'))
    if ($managerSource -match 'public\s+static\s+(?:final\s+)?(?:List|Map|Set|Collection|[A-Za-z0-9_<>?, ]+\[\])') {
        throw 'Manager compatibility facade exposes a public mutable registry'
    }
    if (($managerSource -split "`n").Count -gt 100) {
        throw 'Manager compatibility facade grew beyond the Gate 4 boundary'
    }
    Write-Output 'GATE4_MANAGER_DEPENDENCY_GATE_OK'
} finally {
    if (Test-Path -LiteralPath $tempRoot) {
        $resolvedTemp = (Resolve-Path -LiteralPath $tempRoot).Path
        $allowedRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'target')) + [IO.Path]::DirectorySeparatorChar
        if (-not $resolvedTemp.StartsWith($allowedRoot, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Refusing Gate 4 test cleanup outside repository target'
        }
        Remove-Item -LiteralPath $resolvedTemp -Recurse -Force
    }
}
