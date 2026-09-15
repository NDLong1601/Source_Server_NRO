param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$targetRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'target'))
$tempRoot = Join-Path $targetRoot ('social-v2-regression-' + [Guid]::NewGuid().ToString('N'))
$classes = Join-Path $tempRoot 'classes'
$sourceList = Join-Path $tempRoot 'sources.txt'
$classpath = (Join-Path $repoRoot '20.jar') + ';' + (Join-Path $repoRoot 'lib\*')
$processorPath = Join-Path $repoRoot 'lib\lombok.jar'

function Invoke-JavaTest {
    param([Parameter(Mandatory = $true)][string] $ClassName)

    & java -cp ($classes + ';' + $classpath) $ClassName
    if ($LASTEXITCODE -ne 0) {
        throw "$ClassName failed with exit code $LASTEXITCODE"
    }
}

New-Item -ItemType Directory -Path $classes -Force | Out-Null

try {
    Get-ChildItem -LiteralPath (Join-Path $repoRoot 'src') -Recurse -Filter '*.java' -File |
        ForEach-Object { $_.FullName } |
        Set-Content -LiteralPath $sourceList -Encoding ASCII

    $savedPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & javac --release 17 -encoding UTF-8 -cp $classpath -processorpath $processorPath `
        -d $classes ("@" + $sourceList) 2>&1
    $compileExit = $LASTEXITCODE
    $ErrorActionPreference = $savedPreference
    if ($compileExit -ne 0) {
        throw "Social V2 source compile failed with exit code $compileExit"
    }

    $testClasses = @(
        'SocialPersistenceRegressionTest',
        'SocialPersistenceSchemaContractTest',
        'SocialV2ProtocolRegressionTest',
        'SocialV2BehaviorRegressionTest',
        'SocialDirectoryRegressionTest',
        'SocialActionRateLimiterRegressionTest',
        'SocialV2ServerFacadeRegressionTest',
        'SocialRealtimeRegressionTest',
        'SocialRealtimeFacadeRegressionTest'
    )
    $testSources = $testClasses | ForEach-Object { Join-Path $PSScriptRoot ($_.ToString() + '.java') }
    & javac --release 17 -encoding UTF-8 -cp ($classes + ';' + $classpath) -d $classes $testSources
    if ($LASTEXITCODE -ne 0) {
        throw "Social V2 regression test compile failed with exit code $LASTEXITCODE"
    }

    foreach ($testClass in $testClasses) {
        Invoke-JavaTest ('nro.models.social.' + $testClass)
    }
    Write-Output 'SOCIAL_V2_REGRESSION_SUITE_OK'
} finally {
    if (Test-Path -LiteralPath $tempRoot) {
        $resolvedTemp = (Resolve-Path -LiteralPath $tempRoot).Path
        $allowedPrefix = $targetRoot + [IO.Path]::DirectorySeparatorChar
        if (-not $resolvedTemp.StartsWith($allowedPrefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Refusing Social V2 test cleanup outside repository target'
        }
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}
