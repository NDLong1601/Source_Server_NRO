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
$tempRoot = Join-Path $repoRoot ('target\social-v2-phase2-db-' + [Guid]::NewGuid().ToString('N'))
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

    $testSource = Join-Path $PSScriptRoot 'SocialPersistenceDatabaseIntegrationTest.java'
    & javac --release 17 -encoding UTF-8 -cp ($classes + ';' + $classpath) -d $classes $testSource
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    & java "-Dsocial.v2.test.jdbcUrl=$JdbcUrl" "-Dsocial.v2.test.dbUser=$DbUser" `
        -cp ($classes + ';' + $classpath) nro.models.social.SocialPersistenceDatabaseIntegrationTest
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Host 'SOCIAL_V2_PHASE2_DATABASE_OK'
} finally {
    if (Test-Path -LiteralPath $tempRoot) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}
