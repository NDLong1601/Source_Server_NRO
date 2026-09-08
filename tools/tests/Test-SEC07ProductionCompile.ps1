param()

# Diagnostic full-source compile only. It never writes build/classes or 20.jar.
$ErrorActionPreference = 'Continue'
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$tempRoot = Join-Path $repoRoot ('target\sec07-full-' + [guid]::NewGuid().ToString('N'))
$tempClasses = Join-Path $tempRoot 'classes'
$sourceList = Join-Path $tempRoot 'sources.txt'
New-Item -ItemType Directory -Path $tempClasses -Force | Out-Null

try {
    Get-ChildItem -LiteralPath (Join-Path $repoRoot 'src') -Recurse -Filter '*.java' -File |
        ForEach-Object { $_.FullName } |
        Set-Content -LiteralPath $sourceList -Encoding ASCII

    $classpath = (Join-Path $repoRoot '20.jar') + ';' + (Join-Path $repoRoot 'lib\*')
    $processorPath = Join-Path $repoRoot 'lib\lombok.jar'
    $savedNativePreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & javac --release 17 -encoding UTF-8 -cp $classpath -processorpath $processorPath -d $tempClasses ("@" + $sourceList) 2>&1
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $savedNativePreference
    Write-Output ("SEC-07 FULL SOURCE COMPILE EXIT CODE=" + $exitCode)
    if ($exitCode -ne 0) { exit $exitCode }

    $helpOutput = & java -cp ($tempClasses + ';' + $classpath) nro.models.ledger.VndReconciliationCli --help 2>&1
    if ($LASTEXITCODE -ne 0 -or ($helpOutput -notmatch 'Usage: java nro.models.ledger.VndReconciliationCli')) {
        throw "SEC-07 CLI --help failed or did not emit usage without bootstrap"
    }
    Write-Output 'SEC-07 CLI HELP EXIT CODE=0; bootstrap not required'

    $invalidOutput = & java -cp ($tempClasses + ';' + $classpath) nro.models.ledger.VndReconciliationCli --format xml 2>&1
    $invalidExitCode = $LASTEXITCODE
    $invalidText = ($invalidOutput | ForEach-Object { $_.ToString() }) -join [Environment]::NewLine
    Write-Output ("SEC-07 CLI INVALID-ARGS EXIT CODE=" + $invalidExitCode)
    if ($invalidExitCode -ne 64 -or $invalidText -notmatch 'SEC-07 invalid arguments') {
        throw "SEC-07 CLI invalid-argument contract failed"
    }

    $cliTest = Join-Path $repoRoot 'tools\tests\Sec07CliRegressionTest.java'
    & javac --release 17 -encoding UTF-8 -cp ($tempClasses + ';' + $classpath) -d $tempClasses $cliTest
    if ($LASTEXITCODE -ne 0) { throw "SEC-07 CLI seam test compile failed with exit code $LASTEXITCODE" }
    & java -cp ($tempClasses + ';' + $classpath) tools.tests.Sec07CliRegressionTest
    if ($LASTEXITCODE -ne 0) { throw "SEC-07 CLI seam test failed with exit code $LASTEXITCODE" }
    $pagingTest = Join-Path $repoRoot 'tools\tests\Sec07JdbcPagingRegressionTest.java'
    $pureTest = Join-Path $repoRoot 'tools\tests\Sec07ReconciliationRegressionTest.java'
    & javac --release 17 -encoding UTF-8 -cp ($tempClasses + ';' + $classpath) -d $tempClasses $pagingTest $pureTest
    if ($LASTEXITCODE -ne 0) { throw 'SEC-07 additional test compile failed' }
    & java -Xmx128m -cp ($tempClasses + ';' + $classpath) tools.tests.Sec07JdbcPagingRegressionTest
    if ($LASTEXITCODE -ne 0) { throw 'SEC-07 JDBC paging regression failed' }
    & java -cp ($tempClasses + ';' + $classpath) tools.tests.Sec07ReconciliationRegressionTest
    if ($LASTEXITCODE -ne 0) { throw 'SEC-07 pure regression failed' }
    & (Join-Path $PSScriptRoot 'Test-SEC07RegressionSuite.ps1') -ProductionClasses $tempClasses
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
