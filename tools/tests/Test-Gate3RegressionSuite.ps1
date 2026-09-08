param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$tempRoot = Join-Path $repoRoot ('target\gate3-' + [Guid]::NewGuid().ToString('N'))
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

    $testSource = Join-Path $PSScriptRoot 'Gate3CommandDispatcherTest.java'
    & javac --release 17 -encoding UTF-8 -cp ($classes + ';' + $classpath) -d $classes $testSource
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    & java -cp ($classes + ';' + $classpath) nro.models.server.dispatch.Gate3CommandDispatcherTest
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    $controllerSource = [IO.File]::ReadAllText((Join-Path $repoRoot 'src\nro\models\server\Controller.java'))
    if ($controllerSource -match 'switch\s*\(') {
        throw 'Controller compatibility facade must not retain command business switches'
    }
    if (($controllerSource -split "`n").Count -gt 160) {
        throw 'Controller compatibility facade grew beyond the Gate 3 boundary'
    }
    Write-Output 'GATE3_CONTROLLER_FACADE_OK'
} finally {
    if (Test-Path -LiteralPath $tempRoot) {
        $resolvedTemp = (Resolve-Path -LiteralPath $tempRoot).Path
        $allowedRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'target')) + [IO.Path]::DirectorySeparatorChar
        if (-not $resolvedTemp.StartsWith($allowedRoot, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Refusing Gate 3 test cleanup outside repository target'
        }
        Remove-Item -LiteralPath $resolvedTemp -Recurse -Force
    }
}
