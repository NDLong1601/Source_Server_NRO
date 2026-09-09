param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$tempRoot = Join-Path $repoRoot ('target\gate5-' + [Guid]::NewGuid().ToString('N'))
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

    $testSource = Join-Path $PSScriptRoot 'Gate5PlayerArchitectureTest.java'
    & javac --release 17 -encoding UTF-8 -cp ($classes + ';' + $classpath) -d $classes $testSource
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $lifecycleTestSource = Join-Path $PSScriptRoot 'ClientRemovalLifecycleTest.java'
    & javac --release 17 -encoding UTF-8 -cp ($classes + ';' + $classpath) -d $classes $lifecycleTestSource
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    & java -cp ($classes + ';' + $classpath) nro.models.player.Gate5PlayerArchitectureTest
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & java -cp ($classes + ';' + $classpath) nro.models.server.ClientRemovalLifecycleTest
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    $playerSource = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'src\nro\models\player\Player.java')
    if ($playerSource -match 'class\s+Player\s+implements\s+Runnable') {
        throw 'Gate 5 dependency gate failed: Player still implements Runnable.'
    }
    if ($playerSource -match 'void\s+start\s*\(\s*\)') {
        throw 'Gate 5 dependency gate failed: Player still exposes the legacy start method.'
    }

    $daoSource = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'src\nro\models\database\PlayerDAO.java')
    if ($daoSource -match 'player\.(inventory|nPoint|event|location|zone|superRank|playerTask)') {
        throw 'Gate 5 dependency gate failed: PlayerDAO reads live mutable player state.'
    }

    $eventLegacyFields = Select-String -Path (Join-Path $repoRoot 'src\nro\models\player\Player.java') `
        -Pattern 'eventPointType[1-6]|checkDailyReward|checkTopReward[1-3]|lastCheckIn'
    if ($eventLegacyFields) {
        throw 'Gate 5 dependency gate failed: persisted event fields remain on Player.'
    }

    $publicMutableFields = Select-String -Path (Join-Path $repoRoot 'src\nro\models\player\Player.java') `
        -Pattern '^\s*public\s+(?!static\s+final\b)(?:volatile\s+)?[\w<>\[\], ?]+\s+\w+(?:\s*=.*)?;\s*$'
    if ($publicMutableFields.Count -gt 235) {
        throw "Gate 5 dependency gate failed: public mutable Player field budget grew to $($publicMutableFields.Count)."
    }

    $repositorySource = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'src\nro\models\database\PlayerRepository.java')
    if ($repositorySource -notmatch 'setAutoCommit\(false\)' -or
        $repositorySource -notmatch 'player_wallet' -or
        $repositorySource -notmatch 'connection\.commit\(\)') {
        throw 'Gate 5 dependency gate failed: atomic normalized persistence contract is missing.'
    }

    $schemaSource = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'src\nro\models\database\PlayerPersistenceSchema.java')
    if ($schemaSource -match 'persistence_schema_version') {
        throw 'Gate 5 schema gate failed: the legacy player row has no budget for a redundant JSON schema column.'
    }

    Write-Host 'GATE5_REGRESSION_SUITE_OK'
} finally {
    if (Test-Path -LiteralPath $tempRoot) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}
