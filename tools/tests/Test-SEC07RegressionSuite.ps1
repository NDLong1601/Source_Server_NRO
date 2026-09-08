param([Parameter(Mandatory=$true)][string]$ProductionClasses)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$tempRoot = Join-Path $repoRoot ('target\sec07-suite-' + [guid]::NewGuid().ToString('N'))
$classes = Join-Path $tempRoot 'classes'
if (-not (Test-Path -LiteralPath (Join-Path $ProductionClasses 'nro\models\ledger\VndReconciliationCli.class'))) {
    throw 'Provide freshly compiled source classes using Test-SEC07ProductionCompile.ps1'
}
$productionClasspath = $ProductionClasses + ';' + $repoRoot + '\20.jar;' + $repoRoot + '\lib\*'
New-Item -ItemType Directory -Path $classes -Force | Out-Null

try {
    $sources = @(
        (Join-Path $repoRoot 'tools\tests\WalletRegressionTest.java'),
        (Join-Path $repoRoot 'tools\tests\TradeStateRegressionTest.java'),
        (Join-Path $repoRoot 'tools\tests\ConsignAtomicPurchaseRegressionTest.java'),
        (Join-Path $repoRoot 'tools\tests\AchievementClaimRegressionTest.java'),
        (Join-Path $repoRoot 'tools\tests\ActivityCoreTest.java'),
        (Join-Path $repoRoot 'tools\tests\ActivityRewardCoreTest.java')
    )
    & javac --release 17 -encoding UTF-8 -cp $productionClasspath -d $classes $sources
    if ($LASTEXITCODE -ne 0) { throw "regression compilation failed with exit code $LASTEXITCODE" }

    $tests = @(
        'tools.tests.WalletRegressionTest',
        'nro.models.services_func.TradeStateRegressionTest',
        'nro.models.shop_ky_gui.ConsignAtomicPurchaseRegressionTest',
        'AchievementClaimRegressionTest',
        'nro.models.activity.ActivityCoreTest',
        'nro.models.activity.ActivityRewardCoreTest'
    )
    foreach ($test in $tests) {
        & java -cp ($classes + ';' + $productionClasspath) $test
        if ($LASTEXITCODE -ne 0) { throw "$test failed with exit code $LASTEXITCODE" }
    }
} finally {
    if (Test-Path -LiteralPath $tempRoot) {
        $resolvedTemp = (Resolve-Path -LiteralPath $tempRoot).Path
        $allowedTarget = [IO.Path]::GetFullPath((Join-Path $repoRoot 'target')) + [IO.Path]::DirectorySeparatorChar
        if (-not $resolvedTemp.StartsWith($allowedTarget, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Refusing cleanup outside repository target'
        }
        Remove-Item -LiteralPath $resolvedTemp -Recurse -Force
    }
}
