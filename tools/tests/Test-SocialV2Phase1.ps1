param(
    [ValidateSet('Contract', 'Red')]
    [string]$Mode = 'Contract'
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$tempRoot = Join-Path $repoRoot ('target\social-v2-phase1-' + [Guid]::NewGuid().ToString('N'))
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

    $testSource = if ($Mode -eq 'Contract') {
        @(
            (Join-Path $PSScriptRoot 'SocialV2PhaseOneContractTest.java'),
            (Join-Path $PSScriptRoot 'SocialV2ProtocolRegressionTest.java')
        )
    } else {
        @((Join-Path $PSScriptRoot 'SocialV2BehaviorRegressionTest.java'))
    }
    & javac --release 17 -encoding UTF-8 -cp ($classes + ';' + $classpath) -d $classes $testSource
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    if ($Mode -eq 'Contract') {
        & java -cp ($classes + ';' + $classpath) nro.models.social.SocialV2PhaseOneContractTest
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        & java -cp ($classes + ';' + $classpath) nro.models.social.SocialV2ProtocolRegressionTest
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        Write-Output 'SOCIAL_V2_PHASE1_CONTRACT_OK'
    } else {
        $savedPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        $redOutput = & java -cp ($classes + ';' + $classpath) nro.models.social.SocialV2BehaviorRegressionTest 2>&1
        $redExit = $LASTEXITCODE
        $ErrorActionPreference = $savedPreference
        $redOutput | Write-Output
        if ($redExit -eq 0 -or ($redOutput -join "`n") -notmatch 'Social V2 RED specifications still failing') {
            throw 'Expected phase-1 RED specifications did not fail for the reserved behavior boundary.'
        }
        Write-Output 'SOCIAL_V2_PHASE1_RED_CONFIRMED'
    }
} finally {
    if (Test-Path -LiteralPath $tempRoot) {
        $resolvedTemp = (Resolve-Path -LiteralPath $tempRoot).Path
        $allowedRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'target')) + [IO.Path]::DirectorySeparatorChar
        if (-not $resolvedTemp.StartsWith($allowedRoot, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Refusing Social V2 test cleanup outside repository target'
        }
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}
