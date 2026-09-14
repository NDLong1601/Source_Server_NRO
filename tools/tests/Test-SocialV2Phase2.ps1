param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$tempRoot = Join-Path $repoRoot ('target\social-v2-phase2-' + [Guid]::NewGuid().ToString('N'))
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

    $testSources = @(
        (Join-Path $PSScriptRoot 'SocialPersistenceRegressionTest.java'),
        (Join-Path $PSScriptRoot 'SocialPersistenceSchemaContractTest.java'),
        (Join-Path $PSScriptRoot 'SocialV2ProtocolRegressionTest.java'),
        (Join-Path $PSScriptRoot 'SocialV2BehaviorRegressionTest.java')
    )
    & javac --release 17 -encoding UTF-8 -cp ($classes + ';' + $classpath) -d $classes $testSources
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    & java -cp ($classes + ';' + $classpath) nro.models.social.SocialPersistenceRegressionTest
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & java -cp ($classes + ';' + $classpath) nro.models.social.SocialPersistenceSchemaContractTest
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & java -cp ($classes + ';' + $classpath) nro.models.social.SocialV2ProtocolRegressionTest
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    $savedPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $redOutput = & java -cp ($classes + ';' + $classpath) nro.models.social.SocialV2BehaviorRegressionTest 2>&1
    $redExit = $LASTEXITCODE
    $ErrorActionPreference = $savedPreference
    $redOutput | Write-Output
    if ($redExit -eq 0 -or ($redOutput -join "`n") -notmatch 'Social V2 RED specifications still failing: 3') {
        throw 'Phase-2 rules must turn five specifications green; search/chat/location must remain RED.'
    }
    Write-Output 'SOCIAL_V2_PHASE2_OK'
} finally {
    if (Test-Path -LiteralPath $tempRoot) {
        $resolvedTemp = (Resolve-Path -LiteralPath $tempRoot).Path
        $allowedRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'target')) + [IO.Path]::DirectorySeparatorChar
        if (-not $resolvedTemp.StartsWith($allowedRoot, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Refusing Social V2 phase-2 test cleanup outside repository target'
        }
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}
