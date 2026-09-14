param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$tempRoot = Join-Path $repoRoot ('target\social-v2-phase4-' + [Guid]::NewGuid().ToString('N'))
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
        (Join-Path $PSScriptRoot 'SocialV2BehaviorRegressionTest.java'),
        (Join-Path $PSScriptRoot 'SocialDirectoryRegressionTest.java'),
        (Join-Path $PSScriptRoot 'SocialActionRateLimiterRegressionTest.java'),
        (Join-Path $PSScriptRoot 'SocialV2ServerFacadeRegressionTest.java'),
        (Join-Path $PSScriptRoot 'SocialV2Phase4RegressionTest.java'),
        (Join-Path $PSScriptRoot 'SocialV2Phase4FacadeRegressionTest.java'),
        (Join-Path $PSScriptRoot 'SocialProfileDatabaseIntegrationTest.java')
    )
    & javac --release 17 -encoding UTF-8 -cp ($classes + ';' + $classpath) -d $classes $testSources
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    & java -cp ($classes + ';' + $classpath) nro.models.social.SocialPersistenceRegressionTest
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & java -cp ($classes + ';' + $classpath) nro.models.social.SocialPersistenceSchemaContractTest
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & java -cp ($classes + ';' + $classpath) nro.models.social.SocialV2ProtocolRegressionTest
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & java -cp ($classes + ';' + $classpath) nro.models.social.SocialV2BehaviorRegressionTest
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & java -cp ($classes + ';' + $classpath) nro.models.social.SocialDirectoryRegressionTest
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & java -cp ($classes + ';' + $classpath) nro.models.social.SocialActionRateLimiterRegressionTest
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & java -cp ($classes + ';' + $classpath) nro.models.social.SocialV2ServerFacadeRegressionTest
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & java -cp ($classes + ';' + $classpath) nro.models.social.SocialV2Phase4RegressionTest
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & java -cp ($classes + ';' + $classpath) nro.models.social.SocialV2Phase4FacadeRegressionTest
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Output 'SOCIAL_V2_PHASE4_OK'
} finally {
    if (Test-Path -LiteralPath $tempRoot) {
        $resolvedTemp = (Resolve-Path -LiteralPath $tempRoot).Path
        $allowedRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'target')) + [IO.Path]::DirectorySeparatorChar
        if (-not $resolvedTemp.StartsWith($allowedRoot, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Refusing Social V2 phase-4 test cleanup outside repository target'
        }
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}
