param([string]$ServerRoot = (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)))

$ErrorActionPreference = 'Stop'
$testRoot = Join-Path $ServerRoot ('output\jar-cleanup-test-' + [Guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($testRoot) | Out-Null

# Load only the packaging function; never dispatch server-control actions in a test.
$tokens = $null
$parseErrors = $null
$controller = [Management.Automation.Language.Parser]::ParseFile(
    (Join-Path $ServerRoot 'tools\server_control.ps1'), [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count -gt 0) { throw 'Cannot parse server_control.ps1' }
$function = $controller.Find({
    param($node)
    $node -is [Management.Automation.Language.FunctionDefinitionAst] -and
        $node.Name -eq 'Update-RuntimeJarClasses'
}, $true)
if ($null -eq $function) { throw 'Packaging function not found' }
. ([ScriptBlock]::Create($function.Extent.Text))

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$fixture = Join-Path $testRoot 'fixture'
$classes = Join-Path $testRoot 'classes'
[IO.Directory]::CreateDirectory($fixture) | Out-Null
[IO.Directory]::CreateDirectory($classes) | Out-Null
$oldEntries = @('game/Skill.class', 'game/Skill$Old.class', 'game/Skill$Old$Nested.class',
    'game/Skill$Live.class', 'dependency/Library.class', 'dependency/Library$Inner.class',
    'legacy/Unrebuilt$Inner.class', 'assets/skill.png', 'META-INF/MANIFEST.MF')
$newEntries = @('game/Skill.class', 'game/Skill$Live.class')
foreach ($entry in $oldEntries) {
    $path = Join-Path $fixture $entry
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($path)) | Out-Null
    [IO.File]::WriteAllBytes($path, [byte[]]@(1, 2, 3))
}
foreach ($entry in $newEntries) {
    $path = Join-Path $classes $entry
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($path)) | Out-Null
    [IO.File]::WriteAllBytes($path, [byte[]]@(4, 5, 6, 7))
}
$jar = Join-Path $testRoot 'fixture.jar'
$archive = [IO.Compression.ZipFile]::Open($jar, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($entry in $oldEntries) {
        [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $archive, (Join-Path $fixture $entry), $entry)
    }
} finally {
    $archive.Dispose()
}
$updated = Update-RuntimeJarClasses -TargetJar $jar -ClassesDirectory $classes
if ($updated -ne 2) { throw 'Unexpected compiled class count' }
$archive = [IO.Compression.ZipFile]::OpenRead($jar)
try {
    if ($archive.GetEntry('game/Skill$Old.class') -or $archive.GetEntry('game/Skill$Old$Nested.class')) {
        throw 'Stale inner classes were retained'
    }
    foreach ($entry in $oldEntries | Where-Object { $_ -notlike 'game/Skill$Old*' }) {
        if ($null -eq $archive.GetEntry($entry)) { throw "Unrelated entry was removed: $entry" }
    }
    foreach ($entry in $newEntries) {
        if ($archive.GetEntry($entry).Length -ne 4) { throw "Class was not updated: $entry" }
    }
    if ($archive.Entries.Count -ne 7) { throw 'Unexpected archive entry count' }
} finally {
    $archive.Dispose()
}
Write-Output 'PASS JAR cleanup: stale inner classes removed; current classes, dependencies and resources preserved.'
