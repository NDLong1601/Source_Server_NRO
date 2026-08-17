param(
    [string]$HostName = '127.0.0.1',
    [int]$Port = 14445,
    [int]$Zoom = 2,
    [switch]$SkipClientType,
    [int]$IconId = -1
)

$key = [byte[]][char[]]'NRO'
$writeIndex = 0
$readIndex = 0

function Convert-WithKey([int]$value, [ref]$index) {
    $result = (($value -band 0xFF) -bxor $key[$index.Value % $key.Length]) -band 0xFF
    $index.Value++
    return $result
}

function Write-RawMessage($stream, [int]$command, [byte[]]$data) {
    $stream.WriteByte([byte]($command -band 0xFF))
    $stream.WriteByte([byte](($data.Length -shr 8) -band 0xFF))
    $stream.WriteByte([byte]($data.Length -band 0xFF))
    if ($data.Length -gt 0) {
        $stream.Write($data, 0, $data.Length)
    }
    $stream.Flush()
}

function Write-KeyedMessage($stream, [int]$command, [byte[]]$data) {
    $stream.WriteByte([byte](Convert-WithKey $command ([ref]$script:writeIndex)))
    $stream.WriteByte([byte](Convert-WithKey (($data.Length -shr 8) -band 0xFF) ([ref]$script:writeIndex)))
    $stream.WriteByte([byte](Convert-WithKey ($data.Length -band 0xFF) ([ref]$script:writeIndex)))
    foreach ($value in $data) {
        $stream.WriteByte([byte](Convert-WithKey $value ([ref]$script:writeIndex)))
    }
    $stream.Flush()
}

function Read-Exact($stream, [int]$length) {
    $data = [byte[]]::new($length)
    $offset = 0
    while ($offset -lt $length) {
        $count = $stream.Read($data, $offset, $length - $offset)
        if ($count -le 0) {
            throw 'Connection closed while reading a packet.'
        }
        $offset += $count
    }
    return $data
}

function Read-RawMessage($stream) {
    $command = $stream.ReadByte()
    $high = $stream.ReadByte()
    $low = $stream.ReadByte()
    if ($command -lt 0 -or $high -lt 0 -or $low -lt 0) {
        throw 'Connection closed during handshake.'
    }
    $size = (($high -band 0xFF) -shl 8) -bor ($low -band 0xFF)
    $signedCommand = if ($command -gt 127) { $command - 256 } else { $command }
    return [pscustomobject]@{ Command = $signedCommand; Data = (Read-Exact $stream $size) }
}

function Read-KeyedMessage($stream) {
    $wireCommand = $stream.ReadByte()
    if ($wireCommand -lt 0) {
        throw 'Connection closed while waiting for a packet.'
    }
    $commandByte = Convert-WithKey $wireCommand ([ref]$script:readIndex)
    $command = if ($commandByte -gt 127) { $commandByte - 256 } else { $commandByte }
    $largeCommands = @(-32, -66, -74, 11, -67, -87, 66)
    if ($largeCommands -contains [int]$command) {
        $b0 = Convert-WithKey ((($stream.ReadByte() + 128) -band 0xFF)) ([ref]$script:readIndex)
        $b1 = Convert-WithKey ((($stream.ReadByte() + 128) -band 0xFF)) ([ref]$script:readIndex)
        $b2 = Convert-WithKey ((($stream.ReadByte() + 128) -band 0xFF)) ([ref]$script:readIndex)
        $size = $b0 -bor ($b1 -shl 8) -bor ($b2 -shl 16)
    } else {
        $high = Convert-WithKey $stream.ReadByte() ([ref]$script:readIndex)
        $low = Convert-WithKey $stream.ReadByte() ([ref]$script:readIndex)
        $size = ($high -shl 8) -bor $low
    }
    $wireData = Read-Exact $stream $size
    $data = [byte[]]::new($size)
    for ($i = 0; $i -lt $size; $i++) {
        $data[$i] = [byte](Convert-WithKey $wireData[$i] ([ref]$script:readIndex))
    }
    return [pscustomobject]@{ Command = $command; Data = $data }
}

function New-ClientTypePayload([int]$zoom) {
    $memory = [System.IO.MemoryStream]::new()
    $writer = [System.IO.BinaryWriter]::new($memory)
    try {
        $writer.Write([byte]2) # messageNotLogin: setClientType
        $writer.Write([byte]4) # desktop client
        $writer.Write([byte]$zoom)
        $writer.Write([byte]0) # is_gprs=false
        foreach ($value in @(517, 628)) {
            $writer.Write([byte](($value -shr 24) -band 0xFF))
            $writer.Write([byte](($value -shr 16) -band 0xFF))
            $writer.Write([byte](($value -shr 8) -band 0xFF))
            $writer.Write([byte]($value -band 0xFF))
        }
        $writer.Write([byte]0) # is_qwerty=false
        $writer.Write([byte]1) # is_touch=true
        $platform = [Text.Encoding]::UTF8.GetBytes('Windows|2.2.0')
        $writer.Write([byte](($platform.Length -shr 8) -band 0xFF))
        $writer.Write([byte]($platform.Length -band 0xFF))
        $writer.Write($platform)
        return $memory.ToArray()
    } finally {
        $writer.Dispose()
        $memory.Dispose()
    }
}

$client = [System.Net.Sockets.TcpClient]::new()
$client.ReceiveTimeout = 5000
$client.SendTimeout = 5000
$client.Connect($HostName, $Port)
$stream = $client.GetStream()
try {
    Write-RawMessage $stream -27 ([byte[]]::new(0))
    $handshake = Read-RawMessage $stream
    if ($handshake.Command -ne -27) {
        throw "Unexpected handshake command $($handshake.Command)."
    }

    if (-not $SkipClientType) {
        Write-KeyedMessage $stream -29 (New-ClientTypePayload $Zoom)
    }
    if ($IconId -ge 0) {
        $iconRequest = [byte[]]@(
            (($IconId -shr 24) -band 0xFF),
            (($IconId -shr 16) -band 0xFF),
            (($IconId -shr 8) -band 0xFF),
            ($IconId -band 0xFF)
        )
        Write-KeyedMessage $stream -67 $iconRequest
    } else {
        Write-KeyedMessage $stream -28 ([byte[]]@(8))
    }

    $deadline = [DateTime]::UtcNow.AddSeconds(12)
    $packets = New-Object System.Collections.Generic.List[object]
    while ([DateTime]::UtcNow -lt $deadline) {
        try {
            $packet = Read-KeyedMessage $stream
            $packets.Add($packet)
            $subType = if ($packet.Command -eq -28 -and $packet.Data.Length -ge 3) { $packet.Data[2] } else { -1 }
            "command=$($packet.Command) length=$($packet.Data.Length) itemSubType=$subType"
            if (($IconId -ge 0 -and $packet.Command -eq -67) -or
                ($IconId -lt 0 -and ($packets | Where-Object { $_.Command -eq -28 -and $_.Data.Length -ge 3 -and $_.Data[2] -eq 2 }).Count -ge 1)) {
                break
            }
        } catch [System.IO.IOException] {
            break
        }
    }

    if ($IconId -ge 0) {
        $iconPackets = @($packets | Where-Object { $_.Command -eq -67 })
        "iconPackets=$($iconPackets.Count)"
        if ($iconPackets.Count -ne 1 -or $iconPackets[0].Data.Length -le 8) {
            throw 'The icon request did not return image data.'
        }
    } else {
        $itemPackets = @($packets | Where-Object { $_.Command -eq -28 })
        $reloadPackets = @($itemPackets | Where-Object { $_.Data.Length -ge 3 -and $_.Data[2] -eq 1 })
        $appendPackets = @($itemPackets | Where-Object { $_.Data.Length -ge 3 -and $_.Data[2] -eq 2 })
        "itemPackets=$($itemPackets.Count) reloadPackets=$($reloadPackets.Count) appendPackets=$($appendPackets.Count)"
        if ($reloadPackets.Count -ne 1 -or $appendPackets.Count -ne 1) {
            throw 'Item template update did not use exactly one reload and one append packet.'
        }
        if (($itemPackets | Where-Object { $_.Data.Length -gt 65535 }).Count -gt 0) {
            throw 'An item packet exceeded the protocol limit.'
        }
    }
} finally {
    $stream.Dispose()
    $client.Dispose()
}
