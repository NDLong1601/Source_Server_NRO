param(
    [string]$Action = "status",
    [string]$Value = ""
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$LogDir = Join-Path $Root "logs"
$StatusPath = Join-Path $LogDir "menu_status.txt"
$ServerLog = Join-Path $LogDir "server.log"
$ServerErrorLog = Join-Path $LogDir "server-error.log"
$ControlLog = Join-Path $LogDir "server-control.log"
$PidPath = Join-Path $LogDir "server.pid"

$ControlMutex = New-Object System.Threading.Mutex($false, "Global\NRO_SERVER_DASHBOARD_CONTROL")
$HasControlMutex = $false

try {
    $HasControlMutex = $ControlMutex.WaitOne(15000)
    if (-not $HasControlMutex) {
        throw "Không lấy được khóa điều khiển dashboard sau 15 giây."
    }

    if (-not (Test-Path $LogDir)) {
        New-Item -ItemType Directory -Path $LogDir | Out-Null
    }

function Invoke-WithRetry {
    param(
        [scriptblock]$Script,
        [int]$Retries = 8,
        [int]$DelayMs = 120
    )

    for ($i = 0; $i -lt $Retries; $i++) {
        try {
            return & $Script
        } catch {
            if ($i -eq ($Retries - 1)) {
                throw
            }
            Start-Sleep -Milliseconds $DelayMs
        }
    }
}

function Read-FileShared {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        return ""
    }

    return Invoke-WithRetry -Script {
        $stream = [System.IO.File]::Open($Path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
        try {
            $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8, $true)
            try {
                return $reader.ReadToEnd()
            } finally {
                $reader.Dispose()
            }
        } finally {
            $stream.Dispose()
        }
    }
}

function Write-FileAtomic {
    param(
        [string]$Path,
        [string]$Text
    )

    $tempPath = "$Path.tmp.$PID"
    Invoke-WithRetry -Script {
        [System.IO.File]::WriteAllText($tempPath, $Text, [System.Text.Encoding]::UTF8)
        if (Test-Path $Path) {
            Remove-Item -LiteralPath $Path -Force -ErrorAction SilentlyContinue
        }
        Move-Item -LiteralPath $tempPath -Destination $Path -Force
    }
}

function Write-ControlLog {
    param([string]$Message)

    $line = "[{0}] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $Message
    try {
        [System.IO.File]::AppendAllText(
            $ControlLog,
            $line + [Environment]::NewLine,
            [System.Text.Encoding]::UTF8
        )
    } catch {
        # Control logging must never block server actions.
    }
    Write-Output $line
}

function Get-ServerProcessIds {
    $ids = New-Object System.Collections.Generic.List[int]

    if (Test-Path $PidPath) {
        (Read-FileShared $PidPath) -split "\r?\n" |
            Where-Object { $_ -match "^\d+$" } |
            ForEach-Object {
                $process = Get-Process -Id ([int]$_) -ErrorAction SilentlyContinue
                if ($process -and ($process.ProcessName -eq "java" -or $process.ProcessName -eq "javaw")) {
                    if (-not $ids.Contains($process.Id)) {
                        $ids.Add($process.Id)
                    }
                }
            }
    }

    try {
        Get-CimInstance Win32_Process -ErrorAction Stop |
            Where-Object {
                ($_.Name -eq "java.exe" -or $_.Name -eq "javaw.exe") -and
                $_.CommandLine -like "*20.jar*"
            } |
            ForEach-Object {
                if (-not $ids.Contains([int]$_.ProcessId)) {
                    $ids.Add([int]$_.ProcessId)
                }
            }
    } catch {
        Write-ControlLog "Không thể quét tiến trình Java bằng WMI: $($_.Exception.Message)"
    }

    if ($ids.Count -gt 0) {
        Write-FileAtomic -Path $PidPath -Text ($ids -join [Environment]::NewLine)
    } else {
        Invoke-WithRetry -Script {
            Remove-Item -Path $PidPath -Force -ErrorAction SilentlyContinue
        }
    }

    return $ids.ToArray()
}

function Get-ConfigValue {
    param(
        [string]$Key,
        [string]$Default = ""
    )

    $configPath = Join-Path $Root "Config.properties"
    if (-not (Test-Path $configPath)) {
        return $Default
    }

    $line = (Read-FileShared $configPath) -split "\r?\n" |
        Where-Object { $_ -match "^\s*$([regex]::Escape($Key))\s*=" } |
        Select-Object -First 1

    if (-not $line) {
        return $Default
    }

    return ($line -replace "^\s*$([regex]::Escape($Key))\s*=\s*", "").Trim()
}

function Get-ListeningProcessIds {
    param(
        [int[]]$CandidateIds,
        [int]$Port
    )

    $listeningIds = New-Object System.Collections.Generic.List[int]
    try {
        Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop |
            ForEach-Object {
                $processId = [int]$_.OwningProcess
                if ($CandidateIds -contains $processId -and -not $listeningIds.Contains($processId)) {
                    $listeningIds.Add($processId)
                }
            }
    } catch {
        return @()
    }

    return $listeningIds.ToArray()
}

function Set-ConfigValue {
    param(
        [string]$Key,
        [string]$NewValue
    )

    $configPath = Join-Path $Root "Config.properties"
    $lines = New-Object System.Collections.Generic.List[string]
    if (Test-Path $configPath) {
        foreach ($line in ((Read-FileShared $configPath) -split "\r?\n")) {
            $lines.Add($line)
        }
    }

    $updated = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match "^\s*$([regex]::Escape($Key))\s*=") {
            $lines[$i] = "$Key=$NewValue"
            $updated = $true
            break
        }
    }

    if (-not $updated) {
        $insertAt = 0
        for ($i = 0; $i -lt $lines.Count; $i++) {
            if ($lines[$i] -match "^\s*#SERVER\s*$") {
                $insertAt = $i + 1
                break
            }
        }
        $lines.Insert($insertAt, "$Key=$NewValue")
    }

    Write-FileAtomic -Path $configPath -Text ($lines -join [Environment]::NewLine)
}

function Write-Status {
    $processIds = @(Get-ServerProcessIds)
    $port = [int](Get-ConfigValue -Key "server.port" -Default "14445")
    $listeningIds = @(Get-ListeningProcessIds -CandidateIds $processIds -Port $port)
    $displayIds = if ($listeningIds.Count -gt 0) { $listeningIds } else { $processIds }
    $processStartTimes = @($processIds | ForEach-Object {
        $process = Get-Process -Id $_ -ErrorAction SilentlyContinue
        if ($process) { $process.StartTime }
    })
    $startupAgeSeconds = if ($processStartTimes.Count -gt 0) {
        ((Get-Date) - ($processStartTimes | Sort-Object | Select-Object -First 1)).TotalSeconds
    } else {
        [double]::PositiveInfinity
    }
    $status = if ($listeningIds.Count -gt 0) {
        "Đang chạy"
    } elseif ($processIds.Count -gt 0 -and $startupAgeSeconds -lt 90) {
        "Đang khởi động (chưa mở cổng $port)"
    } elseif ($processIds.Count -gt 0) {
        "Lỗi khởi động (chưa mở cổng $port)"
    } else {
        "Đang dừng"
    }
    $pidText = if ($displayIds.Count -gt 0) { ($displayIds -join ", ") } else { "-" }
    $lastLog = if (Test-Path $ServerLog) { (Get-Item $ServerLog).LastWriteTime.ToString("yyyy-MM-dd HH:mm:ss") } else { "-" }

    $text = @(
        "Trạng thái: $status",
        "PID: $pidText",
        "Log cập nhật: $lastLog"
    ) -join [Environment]::NewLine

    Write-FileAtomic -Path $StatusPath -Text $text
}

function Start-Server {
    $existingIds = @(Get-ServerProcessIds)
    if ($existingIds.Count -gt 0) {
        $port = [int](Get-ConfigValue -Key "server.port" -Default "14445")
        $listeningIds = @(Get-ListeningProcessIds -CandidateIds $existingIds -Port $port)
        if ($listeningIds.Count -gt 0) {
            Write-ControlLog "Server đang chạy, bỏ qua lệnh khởi chạy."
        } else {
            Write-ControlLog "Có tiến trình Java nhưng cổng $port chưa mở. Hãy dùng Restart để dọn tiến trình lỗi và khởi động lại."
        }
        Write-Status
        return
    }

    $pendingJar = Join-Path $Root "20.jar.pending"
    if (Test-Path -LiteralPath $pendingJar) {
        $targetJar = Join-Path $Root "20.jar"
        $backupJar = Join-Path $Root ("20.jar.bak_pending_{0}" -f (Get-Date -Format "yyyyMMdd_HHmmss"))
        if (Test-Path -LiteralPath $targetJar) {
            Copy-Item -LiteralPath $targetJar -Destination $backupJar
        }
        try {
            Copy-Item -LiteralPath $pendingJar -Destination $targetJar -Force
            Remove-Item -LiteralPath $pendingJar -Force
            Write-ControlLog "Đã áp dụng 20.jar.pending trước khi khởi động; backup: $([System.IO.Path]::GetFileName($backupJar))."
        } catch {
            if (Test-Path -LiteralPath $backupJar) {
                Copy-Item -LiteralPath $backupJar -Destination $targetJar -Force
            }
            throw "Không thể áp dụng 20.jar.pending: $($_.Exception.Message)"
        }
    }

    $process = Start-Process -FilePath "java.exe" `
        -WorkingDirectory $Root `
        -ArgumentList @("-server", "-Dfile.encoding=UTF-8", "-jar", "20.jar") `
        -RedirectStandardOutput $ServerLog `
        -RedirectStandardError $ServerErrorLog `
        -WindowStyle Hidden `
        -PassThru

    Write-FileAtomic -Path $PidPath -Text ([string]$process.Id)

    Write-ControlLog "Đã khởi chạy server từ 20.jar với PID $($process.Id)."
    $port = [int](Get-ConfigValue -Key "server.port" -Default "14445")
    $listeningIds = @()
    for ($attempt = 0; $attempt -lt 12; $attempt++) {
        Start-Sleep -Seconds 1
        $currentIds = @(Get-ServerProcessIds)
        if ($currentIds.Count -eq 0) { break }
        $listeningIds = @(Get-ListeningProcessIds -CandidateIds $currentIds -Port $port)
        if ($listeningIds.Count -gt 0) { break }
    }
    if ($listeningIds.Count -gt 0) {
        Write-ControlLog "Server đã mở cổng $port với PID $($listeningIds -join ', ')."
    } else {
        Write-ControlLog "Server không mở được cổng $port sau khi khởi động. Kiểm tra logs\server-error.log."
    }
    Write-Status
}

function Stop-Server {
    $processIds = @(Get-ServerProcessIds)
    if ($processIds.Count -eq 0) {
        Write-ControlLog "Yêu cầu dừng server, nhưng server hiện không chạy."
        Invoke-WithRetry -Script {
            Remove-Item -Path $PidPath -Force -ErrorAction SilentlyContinue
        }
        Write-Status
        return
    }

    foreach ($processId in $processIds) {
        Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
        Write-ControlLog "Đã dừng tiến trình server PID $processId."
    }

    Invoke-WithRetry -Script {
        Remove-Item -Path $PidPath -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 1
    Write-Status
}

function Restart-Server {
    Stop-Server
    Start-Sleep -Seconds 2
    Start-Server
}

function Set-ServerEvent {
    param([string]$EventValue)

    if ([string]::IsNullOrWhiteSpace($EventValue)) {
        $EventValue = "none"
    }

    Set-ConfigValue -Key "server.event" -NewValue $EventValue
    Write-ControlLog "Đã cập nhật server.event=$EventValue. Restart server để áp dụng."
    Write-Status
}

function Set-ServerEventAndRestart {
    param([string]$EventValue)

    Set-ServerEvent -EventValue $EventValue
    Write-ControlLog "Đang khởi động lại server để áp dụng sự kiện."
    Restart-Server
    Write-ControlLog "Hoàn tất áp dụng sự kiện: $EventValue."
}

function Set-ExpRate {
    param([string]$Rate)

    if ($Rate -notmatch "^[1-9][0-9]*$") {
        $Rate = "1"
    }

    Set-ConfigValue -Key "server.expserver" -NewValue $Rate
    Write-ControlLog "Đã cập nhật server.expserver=$Rate. Restart server để áp dụng."
    Write-Status
}

function Set-ExpRateAndRestart {
    param([string]$Rate)

    Set-ExpRate -Rate $Rate
    Write-ControlLog "Đang khởi động lại server để áp dụng x$Rate tiềm năng/sức mạnh."
    Restart-Server
    Write-ControlLog "Hoàn tất áp dụng x$Rate tiềm năng/sức mạnh."
}

function Update-RuntimeJarClasses {
    param(
        [string]$TargetJar,
        [string]$ClassesDirectory
    )

    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::Open(
        $TargetJar,
        [System.IO.Compression.ZipArchiveMode]::Update
    )
    try {
        $classesRoot = (Resolve-Path -LiteralPath $ClassesDirectory).Path
        $classFiles = @(Get-ChildItem -LiteralPath $classesRoot -Recurse -File -Filter "*.class")
        if ($classFiles.Count -eq 0) {
            throw "Thư mục build không có class: $classesRoot"
        }
        foreach ($classFile in $classFiles) {
            $entryName = $classFile.FullName.Substring($classesRoot.Length + 1).Replace("\", "/")
            $oldEntry = $archive.GetEntry($entryName)
            if ($oldEntry) {
                $oldEntry.Delete()
            }
            $newEntry = $archive.CreateEntry(
                $entryName,
                [System.IO.Compression.CompressionLevel]::Optimal
            )
            $input = [System.IO.File]::OpenRead($classFile.FullName)
            $output = $newEntry.Open()
            try {
                $input.CopyTo($output)
            } finally {
                $output.Dispose()
                $input.Dispose()
            }
        }
        return $classFiles.Count
    } finally {
        $archive.Dispose()
    }
}

function Test-RuntimeJar {
    param([string]$TargetJar)

    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($TargetJar)
    try {
        return $null -ne $archive.GetEntry("com/zaxxer/hikari/HikariConfig.class") -and
            $null -ne $archive.GetEntry("nro/models/server/ServerManager.class")
    } finally {
        $archive.Dispose()
    }
}

function Build-Server {
    $ant = Get-Command ant -ErrorAction SilentlyContinue
    $javac = Get-Command javac -ErrorAction SilentlyContinue
    $tempBuildRoot = $null

    if (@(Get-ServerProcessIds).Count -gt 0) {
        Write-ControlLog "Không thể build khi server đang chạy. Hãy dừng server trước rồi build lại."
        Write-Status
        return
    }

    Write-ControlLog "Bắt đầu build."
    Push-Location $Root
    try {
        if ($ant) {
            $previousErrorAction = $ErrorActionPreference
            $ErrorActionPreference = "Continue"
            try {
                $antOutput = @(& ant clean jar 2>&1)
                $antExitCode = $LASTEXITCODE
            } finally {
                $ErrorActionPreference = $previousErrorAction
            }
            if ($antExitCode -ne 0) {
                $detail = ($antOutput | Select-Object -Last 5) -join " | "
                Write-ControlLog "Build bằng Ant thất bại với mã lỗi $antExitCode. $detail"
                return
            }

            $builtJar = Join-Path $Root "dist\NgocRongOnline.jar"
            $builtClasses = Join-Path $Root "build\classes"
            $targetJar = Join-Path $Root "20.jar"
            if (-not (Test-Path $builtJar) -or -not (Test-Path $builtClasses)) {
                Write-ControlLog "Build xong nhưng thiếu dist\NgocRongOnline.jar hoặc build\classes."
                return
            }
            if (-not (Test-Path $targetJar)) {
                Write-ControlLog "Không tìm thấy 20.jar nền chứa thư viện runtime."
                return
            }

            $backup = Join-Path $Root ("20.jar.bak_{0}" -f (Get-Date -Format "yyyyMMdd_HHmmss"))
            Copy-Item -Path $targetJar -Destination $backup -Force
            try {
                $updatedCount = Update-RuntimeJarClasses -TargetJar $targetJar -ClassesDirectory $builtClasses
                if (-not (Test-RuntimeJar -TargetJar $targetJar)) {
                    throw "JAR sau build thiếu class runtime bắt buộc."
                }
            } catch {
                Copy-Item -Path $backup -Destination $targetJar -Force
                Write-ControlLog "Cập nhật class vào 20.jar thất bại; đã khôi phục backup: $($_.Exception.Message)"
                return
            }
            Write-ControlLog "Build hoàn tất. Đã cập nhật $updatedCount class vào JAR runtime và tạo backup $([System.IO.Path]::GetFileName($backup))."
            return
        }

        if (-not $javac) {
            Write-ControlLog "Không thể build: máy chưa có Ant hoặc JDK javac trong PATH."
            return
        }

        $tempBuildRoot = Join-Path $Root ("build\server-build-{0}" -f $PID)
        $tempClasses = Join-Path $tempBuildRoot "classes"
        New-Item -ItemType Directory -Path $tempClasses | Out-Null

        $sourceList = Join-Path $tempBuildRoot "sources.txt"
        Get-ChildItem -Path (Join-Path $Root "src") -Recurse -Filter "*.java" |
            ForEach-Object { $_.FullName } |
            Set-Content -Path $sourceList -Encoding ASCII

        $classpath = @(
            (Join-Path $Root "20.jar"),
            (Join-Path $Root "lib\*")
        ) -join ";"

        $processorPath = Join-Path $Root "lib\lombok.jar"
        $previousErrorAction = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            $javacOutput = @(& javac --release 17 -encoding UTF-8 -cp $classpath -processorpath $processorPath -d $tempClasses "@$sourceList" 2>&1)
            $javacExitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousErrorAction
        }
        if ($javacExitCode -ne 0) {
            $detail = ($javacOutput | Select-Object -Last 5) -join " | "
            Write-ControlLog "Build bằng javac thất bại với mã lỗi $javacExitCode. $detail"
            return
        }

        $targetJar = Join-Path $Root "20.jar"
        $backup = Join-Path $Root ("20.jar.bak_{0}" -f (Get-Date -Format "yyyyMMdd_HHmmss"))
        Copy-Item -Path $targetJar -Destination $backup -Force
        try {
            $updatedCount = Update-RuntimeJarClasses -TargetJar $targetJar -ClassesDirectory $tempClasses
            if (-not (Test-RuntimeJar -TargetJar $targetJar)) {
                throw "JAR sau build thiếu class runtime bắt buộc."
            }
        } catch {
            Copy-Item -Path $backup -Destination $targetJar -Force
            Write-ControlLog "Cập nhật 20.jar thất bại; đã khôi phục backup: $($_.Exception.Message)"
            return
        }
        Write-ControlLog "Build javac hoàn tất. Đã cập nhật $updatedCount class vào 20.jar và tạo backup $([System.IO.Path]::GetFileName($backup))."
    }
    finally {
        if ($tempBuildRoot -and (Test-Path -LiteralPath $tempBuildRoot)) {
            Remove-Item -LiteralPath $tempBuildRoot -Recurse -Force -ErrorAction SilentlyContinue
        }
        Pop-Location
        Write-Status
    }
}

try {
    switch ($Action.ToLowerInvariant()) {
        "start" { Start-Server }
        "stop" { Stop-Server }
        "restart" { Restart-Server }
        "status" { Write-Status }
        "event" { Set-ServerEvent -EventValue $Value }
        "eventrestart" { Set-ServerEventAndRestart -EventValue $Value }
        "exp" { Set-ExpRate -Rate $Value }
        "exprestart" { Set-ExpRateAndRestart -Rate $Value }
        "build" { Build-Server }
        "openlogs" {
            Start-Process -FilePath "explorer.exe" -ArgumentList $LogDir | Out-Null
            Write-ControlLog "Đã mở thư mục log."
            Write-Status
        }
        default {
            Write-ControlLog "Lệnh không hợp lệ: $Action"
            Write-Status
            exit 1
        }
    }
}
catch {
    Write-ControlLog "Lỗi: $($_.Exception.Message)"
    Write-Status
    exit 1
}
}
finally {
    if ($HasControlMutex) {
        $ControlMutex.ReleaseMutex()
    }
    $ControlMutex.Dispose()
}
