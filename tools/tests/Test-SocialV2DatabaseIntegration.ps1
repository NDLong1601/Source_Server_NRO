param(
    [string] $JdbcUrl = $env:SOCIAL_V2_TEST_JDBC_URL,
    [string] $DbUser = $env:SOCIAL_V2_TEST_DB_USER
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($JdbcUrl) -or [string]::IsNullOrWhiteSpace($DbUser)) {
    throw 'Set SOCIAL_V2_TEST_JDBC_URL and SOCIAL_V2_TEST_DB_USER before running this disposable local database test.'
}
if ($JdbcUrl -notmatch '^jdbc:mysql://(127\.0\.0\.1|localhost):[0-9]{2,5}/$') {
    throw 'SOCIAL_V2_TEST_JDBC_URL must be a local MySQL/MariaDB URL ending in /. Remote and application database URLs are refused.'
}

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$targetRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'target'))
$tempRoot = Join-Path $targetRoot ('social-v2-database-' + [Guid]::NewGuid().ToString('N'))
$classes = Join-Path $tempRoot 'classes'
$sourceList = Join-Path $tempRoot 'sources.txt'
$classpath = (Join-Path $repoRoot '20.jar') + ';' + (Join-Path $repoRoot 'lib\*')
$processorPath = Join-Path $repoRoot 'lib\lombok.jar'
$databaseTests = @(
    'SocialPersistenceDatabaseIntegrationTest',
    'SocialDirectoryDatabaseIntegrationTest',
    'SocialProfileDatabaseIntegrationTest'
)

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
        throw "Social V2 database test source compile failed with exit code $compileExit"
    }

    $testSources = $databaseTests | ForEach-Object { Join-Path $PSScriptRoot ($_.ToString() + '.java') }
    & javac --release 17 -encoding UTF-8 -cp ($classes + ';' + $classpath) -d $classes $testSources
    if ($LASTEXITCODE -ne 0) {
        throw "Social V2 database test compile failed with exit code $LASTEXITCODE"
    }

    foreach ($testClass in $databaseTests) {
        & java "-Dsocial.v2.test.jdbcUrl=$JdbcUrl" "-Dsocial.v2.test.dbUser=$DbUser" `
            -cp ($classes + ';' + $classpath) ('nro.models.social.' + $testClass)
        if ($LASTEXITCODE -ne 0) {
            throw "$testClass failed with exit code $LASTEXITCODE"
        }
    }
    Write-Output 'SOCIAL_V2_DATABASE_INTEGRATION_OK'
} finally {
    if (Test-Path -LiteralPath $tempRoot) {
        $resolvedTemp = (Resolve-Path -LiteralPath $tempRoot).Path
        $allowedPrefix = $targetRoot + [IO.Path]::DirectorySeparatorChar
        if (-not $resolvedTemp.StartsWith($allowedPrefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Refusing Social V2 database test cleanup outside repository target'
        }
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}
