param(
    [switch]$FailOnIssue,
    [string]$ClientRoot = ""
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$LogDir = Join-Path $Root "logs"

if ([string]::IsNullOrWhiteSpace($ClientRoot)) {
    $musicRoot = Split-Path (Split-Path $Root -Parent) -Parent
    $ClientRoot = Join-Path $musicRoot "PRJ_2Tab_550K"
}

function Get-PropertyMap([string]$Path) {
    $map = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ($line -match "^\s*([^#!][^=]+?)\s*=\s*(.*)\s*$") {
            $map[$matches[1].Trim()] = $matches[2].Trim()
        }
    }
    return $map
}

function Add-Issue([System.Collections.Generic.List[string]]$Issues, [string]$Message) {
    $Issues.Add($Message)
}

function Test-Contains([string]$Path, [string]$Needle) {
    return (Test-Path -LiteralPath $Path) -and ((Get-Content -LiteralPath $Path -Raw -Encoding UTF8).IndexOf($Needle, [StringComparison]::Ordinal) -ge 0)
}

$issues = New-Object System.Collections.Generic.List[string]
$warnings = New-Object System.Collections.Generic.List[string]

# Reuse the prior phase checks so Phase 7 cannot hide a regression in schema,
# runtime config, reward safety, audit tables, or the deployed JAR.
$phase3 = $null
$phase4 = $null
try {
    $phase3 = ((@(& (Join-Path $PSScriptRoot "check_activity_phase3.ps1")) -join [Environment]::NewLine) | ConvertFrom-Json)
    foreach ($issue in @($phase3.issues)) { Add-Issue $issues "Phase 3: $issue" }
} catch {
    Add-Issue $issues "Không chạy được kiểm tra Phase 3: $($_.Exception.Message)"
}
try {
    $phase4 = ((@(& (Join-Path $PSScriptRoot "check_activity_phase4.ps1")) -join [Environment]::NewLine) | ConvertFrom-Json)
    foreach ($issue in @($phase4.issues)) { Add-Issue $issues "Phase 4: $issue" }
} catch {
    Add-Issue $issues "Không chạy được kiểm tra Phase 4: $($_.Exception.Message)"
}

$configPath = Join-Path $Root "Config.properties"
$activityPropertiesPath = Join-Path $Root "activity.properties"
$port = 14445
try {
    $serverConfig = Get-PropertyMap $configPath
    if ($serverConfig.ContainsKey("server.port")) { $port = [int]$serverConfig["server.port"] }
} catch {
    Add-Issue $issues "Không đọc được server.port: $($_.Exception.Message)"
}

try {
    $activityProperties = Get-PropertyMap $activityPropertiesPath
    if ($activityProperties["activity.emergency.disable"] -ne "false") {
        Add-Issue $issues "activity.emergency.disable phải là false khi QA shadow."
    }
} catch {
    Add-Issue $issues "Không đọc được activity.properties: $($_.Exception.Message)"
}

try {
    $listeners = @(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction Stop)
    if ($listeners.Count -eq 0) {
        Add-Issue $issues "Server không lắng nghe cổng $port."
    } elseif (-not (@($listeners | ForEach-Object { (Get-Process -Id $_.OwningProcess -ErrorAction SilentlyContinue).ProcessName }) -contains "java")) {
        Add-Issue $issues "Cổng $port không do tiến trình Java server lắng nghe."
    }
} catch {
    Add-Issue $issues "Không xác nhận được cổng server ${port}: $($_.Exception.Message)"
}

$adminOverview = $null
try {
    if (-not (Test-Path -LiteralPath $LogDir)) { New-Item -ItemType Directory -Path $LogDir | Out-Null }
    $adminOutput = Join-Path $LogDir "activity_phase7_admin_overview.json"
    & (Get-Command powershell.exe -ErrorAction Stop).Source -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "admin_data.ps1") -Action getactivityoverview -Output $adminOutput
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $adminOutput)) {
        Add-Issue $issues "Admin không trả được getactivityoverview."
    } else {
        $adminOverview = (Get-Content -LiteralPath $adminOutput -Raw -Encoding UTF8) | ConvertFrom-Json
        if ($adminOverview.status -ne "ok") { Add-Issue $issues "Admin overview trả trạng thái '$($adminOverview.status)'." }
        if ($adminOverview.revision -lt 4) { Add-Issue $issues "Admin overview có revision $($adminOverview.revision), cần tối thiểu 4." }
        if (-not [bool]$adminOverview.config.global.enabled) { Add-Issue $issues "Runtime Năng động đang tắt, không thể QA trong Unity." }
        if (-not [bool]$adminOverview.config.global.shadowMode) { Add-Issue $issues "Runtime không còn shadowMode=true." }
        if ([bool]$adminOverview.config.global.rewardEnabled) { Add-Issue $issues "rewardEnabled=true; phải giữ tắt trong QA." }
        if ([bool]$adminOverview.emergencyDisabled) { Add-Issue $issues "Emergency disable đang bật." }
        if (@($adminOverview.config.sources).Count -ne 14) { Add-Issue $issues "Admin overview không có đúng 14 source." }
        if (@($adminOverview.config.dailyTiers).Count -ne 5 -or @($adminOverview.config.weeklyTiers).Count -ne 2) {
            Add-Issue $issues "Admin overview không có đúng 5 mốc ngày và 2 mốc tuần."
        }
    }
} catch {
    Add-Issue $issues "Admin smoke test thất bại: $($_.Exception.Message)"
}

$serverLogPath = Join-Path $LogDir "server.log"
if (-not (Test-Contains $serverLogPath "Loaded Activity Points config revision")) {
    Add-Issue $issues "server.log không có xác nhận nạp Activity config."
}
if (-not (Test-Contains $serverLogPath "Activity Points shadow metrics are enabled")) {
    Add-Issue $issues "server.log không xác nhận shadow metrics đang bật."
}

$clientSummary = [ordered]@{ root = $ClientRoot; mirror = $false; activityScreenErrorsAfterLatestImport = $null }
if (-not (Test-Path -LiteralPath $ClientRoot)) {
    Add-Issue $issues "Không tìm thấy Unity client: $ClientRoot"
} else {
    $game1 = Join-Path $ClientRoot "Assets\Scripts\Assembly-CSharp\Game1"
    $game2 = Join-Path $ClientRoot "Assets\Scripts\Assembly-CSharp\Game2"
    $screen1 = Join-Path $game1 "ActivityScreen.cs"
    $screen2 = Join-Path $game2 "ActivityScreen.cs"
    $clientFiles = @($screen1, $screen2, "$screen1.meta", "$screen2.meta")
    foreach ($path in $clientFiles) {
        if (-not (Test-Path -LiteralPath $path)) { Add-Issue $issues "Thiếu client file: $path" }
    }

    if ((Test-Path -LiteralPath $screen1) -and (Test-Path -LiteralPath $screen2)) {
        $screen1Text = Get-Content -LiteralPath $screen1 -Raw -Encoding UTF8
        $screen2Text = Get-Content -LiteralPath $screen2 -Raw -Encoding UTF8
        $normalized1 = $screen1Text -replace "namespace Game1", "namespace GameX"
        $normalized2 = $screen2Text -replace "namespace Game2", "namespace GameX"
        $clientSummary.mirror = ($normalized1 -ceq $normalized2)
        if (-not $clientSummary.mirror) { Add-Issue $issues "ActivityScreen Game1/Game2 không còn mirror." }
        if ([regex]::IsMatch($screen1Text + $screen2Text, "(?<!System\.)Math\.(?:Max|Min)")) {
            Add-Issue $issues "ActivityScreen có Math.Max/Min chưa được định danh System.Math."
        }
        if ([regex]::IsMatch($screen1Text + $screen2Text, "mFont\s+statusFont\s*=\s*statusFont")) {
            Add-Issue $issues "ActivityScreen có biến statusFont che hàm cùng tên."
        }
    }

    foreach ($namespace in @("Game1", "Game2")) {
        $folder = Join-Path $ClientRoot "Assets\Scripts\Assembly-CSharp\$namespace"
        $requirements = @(
            @{ file = "Controller.cs"; needle = "case -58:" },
            @{ file = "Service.cs"; needle = "new Message(-58)" },
            @{ file = "Panel.cs"; needle = "ACTIVITY_TOOL" },
            @{ file = "Panel.cs"; needle = "openActivityDashboard();" },
            @{ file = "Panel.cs"; needle = "paintActivityPanel(g);" },
            @{ file = "SoundMn.cs"; needle = "InsertActivityTool();" },
            @{ file = "SoundMn.cs"; needle = "tools[insertAt] = Panel.ACTIVITY_TOOL" }
        )
        foreach ($requirement in $requirements) {
            $path = Join-Path $folder $requirement.file
            if (-not (Test-Contains $path $requirement.needle)) {
                Add-Issue $issues "$namespace/$($requirement.file) thiếu protocol/UI marker '$($requirement.needle)'."
            }
        }
        $legacyShortcutPath = Join-Path $folder "ModFunc.cs"
        $legacyShortcutText = if (Test-Path -LiteralPath $legacyShortcutPath) { Get-Content -LiteralPath $legacyShortcutPath -Raw -Encoding UTF8 } else { "" }
        $hasLegacyShortcut = [regex]::IsMatch($legacyShortcutText, 'new Command\("[^"]+", 61\)')
        if ($hasLegacyShortcut) {
            Add-Issue $issues "$namespace/ModFunc.cs vẫn có lối tắt Năng động cũ; mục phải chỉ nằm trong panel Chức Năng."
        }
    }

    $unityLogPath = Join-Path $env:LOCALAPPDATA "Unity\Editor\Editor.log"
    if (Test-Path -LiteralPath $unityLogPath) {
        $unityLog = Get-Content -LiteralPath $unityLogPath -Raw -Encoding UTF8
        $latestImport = [Math]::Max(
            $unityLog.LastIndexOf("Start importing Assets/Scripts/Assembly-CSharp/Game1/ActivityScreen.cs", [StringComparison]::Ordinal),
            $unityLog.LastIndexOf("Start importing Assets/Scripts/Assembly-CSharp/Game2/ActivityScreen.cs", [StringComparison]::Ordinal)
        )
        if ($latestImport -ge 0) {
            $afterImport = $unityLog.Substring($latestImport)
            $activityErrors = @([regex]::Matches($afterImport, "ActivityScreen\.cs\([^\r\n]*error CS", [Text.RegularExpressions.RegexOptions]::IgnoreCase)).Count
            $clientSummary.activityScreenErrorsAfterLatestImport = $activityErrors
            if ($activityErrors -gt 0) { Add-Issue $issues "Unity log vẫn có $activityErrors lỗi ActivityScreen sau lần import mới nhất." }
        } else {
            $warnings.Add("Không tìm thấy lần import ActivityScreen trong Unity Editor.log để đối chiếu compile.")
        }
    } else {
        $warnings.Add("Không tìm thấy Unity Editor.log; hãy để Unity compile một lần trước khi test.")
    }
}

$summary = [ordered]@{
    phase = "activity-phase7"
    status = if ($issues.Count -eq 0) { "ready-for-unity-editor-qa" } else { "blocked" }
    buildClient = "skipped-by-request"
    serverPort = $port
    runtime = [ordered]@{
        revision = if ($null -ne $adminOverview) { $adminOverview.revision } else { $null }
        enabled = if ($null -ne $adminOverview) { [bool]$adminOverview.config.global.enabled } else { $null }
        shadowMode = if ($null -ne $adminOverview) { [bool]$adminOverview.config.global.shadowMode } else { $null }
        rewardsEnabled = if ($null -ne $adminOverview) { [bool]$adminOverview.config.global.rewardEnabled } else { $null }
        emergencyDisabled = if ($null -ne $adminOverview) { [bool]$adminOverview.emergencyDisabled } else { $null }
    }
    client = $clientSummary
    jarSha256 = if ($null -ne $phase4) { $phase4.jarSha256 } else { $null }
    issues = $issues.ToArray()
    warnings = $warnings.ToArray()
}

$summary | ConvertTo-Json -Depth 8
if ($FailOnIssue -and $issues.Count -gt 0) { exit 1 }
