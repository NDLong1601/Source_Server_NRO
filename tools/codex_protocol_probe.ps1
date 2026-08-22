param(
    [string]$HostName = '127.0.0.1',
    [int]$Port = 14445,
    [int]$Zoom = 2,
    [switch]$SkipClientType,
    [int]$IconId = -1,
    [int]$MobId = -1,
    [switch]$MapData,
    [int]$MapId = -1,
    [int]$BgImageId = -1
)

$ErrorActionPreference = 'Stop'
$key = [byte[]][char[]]'NRO'
$writeIndex = 0
$readIndex = 0

function Get-Sha256Hex([byte[]]$Data) {
    $sha256 = [Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString($sha256.ComputeHash($Data))).Replace('-', '')
    } finally {
        $sha256.Dispose()
    }
}

function Read-BigEndianInt32([byte[]]$Data, [int]$Offset) {
    return (([int]$Data[$Offset] -shl 24) -bor
        ([int]$Data[($Offset + 1)] -shl 16) -bor
        ([int]$Data[($Offset + 2)] -shl 8) -bor
        [int]$Data[($Offset + 3)])
}

function Read-Utf8([byte[]]$Data, [ref]$Offset) {
    if ($Offset.Value + 2 -gt $Data.Length) {
        throw 'Truncated UTF length in map-data packet.'
    }
    $length = ([int]$Data[$Offset.Value] -shl 8) -bor [int]$Data[$Offset.Value + 1]
    $Offset.Value += 2
    if ($Offset.Value + $length -gt $Data.Length) {
        throw 'Truncated UTF value in map-data packet.'
    }
    $value = [Text.Encoding]::UTF8.GetString($Data, $Offset.Value, $length)
    $Offset.Value += $length
    return $value
}

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
$client.ReceiveTimeout = 15000
$client.SendTimeout = 15000
$stream = $null
try {
    $client.Connect($HostName, $Port)
    $stream = $client.GetStream()
    Write-RawMessage $stream -27 ([byte[]]::new(0))
    $handshake = Read-RawMessage $stream
    if ($handshake.Command -ne -27) {
        throw "Unexpected handshake command $($handshake.Command)."
    }

    if (-not $SkipClientType) {
        Write-KeyedMessage $stream -29 (New-ClientTypePayload $Zoom)
        # Wait for the server to apply zoom/client metadata before requesting an
        # asset. Sending both commands back-to-back can race the controller on a
        # fresh local connection and intermittently drop the resource request.
        $clientTypeAck = Read-KeyedMessage $stream
        "clientTypeAck=$($clientTypeAck.Command) length=$($clientTypeAck.Data.Length)"
        if ($clientTypeAck.Command -ne -29) {
            throw "Unexpected setClientType response $($clientTypeAck.Command)."
        }
    }
    if ($MobId -ge 0) {
        if ($MobId -gt 127) {
            throw 'MobId must fit the signed-byte protocol range 0..127.'
        }
        Write-KeyedMessage $stream 11 ([byte[]]@($MobId))
    } elseif ($IconId -ge 0) {
        $iconRequest = [byte[]]@(
            (($IconId -shr 24) -band 0xFF),
            (($IconId -shr 16) -band 0xFF),
            (($IconId -shr 8) -band 0xFF),
            ($IconId -band 0xFF)
        )
        Write-KeyedMessage $stream -67 $iconRequest
    } elseif ($MapData) {
        Write-KeyedMessage $stream -28 ([byte[]]@(6))
    } elseif ($MapId -ge 0) {
        if ($MapId -gt 255) {
            throw 'MapId must fit the unsigned-byte protocol range 0..255.'
        }
        Write-KeyedMessage $stream -28 ([byte[]]@(10, $MapId))
    } elseif ($BgImageId -ge 0) {
        if ($BgImageId -gt 32767) {
            throw 'BgImageId must fit the signed-short protocol range 0..32767.'
        }
        $bgRequest = [byte[]]@(
            (($BgImageId -shr 8) -band 0xFF),
            ($BgImageId -band 0xFF)
        )
        Write-KeyedMessage $stream -32 $bgRequest
    } else {
        Write-KeyedMessage $stream -28 ([byte[]]@(8))
    }

    $deadline = [DateTime]::UtcNow.AddSeconds(20)
    $packets = New-Object System.Collections.Generic.List[object]
    while ([DateTime]::UtcNow -lt $deadline) {
        try {
            $packet = Read-KeyedMessage $stream
            $packets.Add($packet)
            $subType = if ($packet.Command -eq -28 -and $packet.Data.Length -ge 3) { $packet.Data[2] } else { -1 }
            "command=$($packet.Command) length=$($packet.Data.Length) itemSubType=$subType"
            if (($MobId -ge 0 -and $packet.Command -eq 11) -or
                ($MobId -lt 0 -and $IconId -ge 0 -and $packet.Command -eq -67) -or
                ($MobId -lt 0 -and $IconId -lt 0 -and $MapData -and
                    $packet.Command -eq -28 -and $packet.Data.Length -ge 3 -and $packet.Data[0] -eq 6) -or
                ($MobId -lt 0 -and $IconId -lt 0 -and -not $MapData -and $MapId -ge 0 -and
                    $packet.Command -eq -28 -and $packet.Data.Length -ge 3 -and $packet.Data[0] -eq 10) -or
                ($MobId -lt 0 -and $IconId -lt 0 -and -not $MapData -and $MapId -lt 0 -and $BgImageId -ge 0 -and
                    $packet.Command -eq -32) -or
                ($MobId -lt 0 -and $IconId -lt 0 -and -not $MapData -and $MapId -lt 0 -and $BgImageId -lt 0 -and
                    ($packets | Where-Object { $_.Command -eq -28 -and $_.Data.Length -ge 3 -and $_.Data[2] -eq 2 }).Count -ge 1)) {
                break
            }
        } catch [System.IO.IOException] {
            break
        }
    }

    if ($MobId -ge 0) {
        $mobPackets = @($packets | Where-Object { $_.Command -eq 11 })
        "mobPackets=$($mobPackets.Count)"
        if ($mobPackets.Count -ne 1 -or $mobPackets[0].Data.Length -le 9) {
            throw 'The mob request did not return asset data.'
        }
        if ($mobPackets[0].Data[0] -ne $MobId) {
            throw "The mob response returned ID $($mobPackets[0].Data[0]) instead of $MobId."
        }
        $expectedPath = Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')) "data\mob\x$Zoom\$MobId"
        $expected = [IO.File]::ReadAllBytes($expectedPath)
        $actual = $mobPackets[0].Data[1..($mobPackets[0].Data.Length - 1)]
        if ($actual.Length -ne $expected.Length -or (Get-Sha256Hex $actual) -ne (Get-Sha256Hex $expected)) {
            throw 'The mob response payload does not match the deployed asset file.'
        }
        if ($actual[0] -ne 0) {
            throw "Unsupported mob EffectData read type $($actual[0])."
        }
        $effectDataLength = Read-BigEndianInt32 $actual 1
        $pngLengthOffset = 5 + $effectDataLength
        if ($effectDataLength -le 0 -or $pngLengthOffset + 5 -gt $actual.Length) {
            throw 'The mob EffectData length is invalid.'
        }
        $pngLength = Read-BigEndianInt32 $actual $pngLengthOffset
        $pngOffset = $pngLengthOffset + 4
        if ($pngLength -le 8 -or $pngOffset + $pngLength + 1 -ne $actual.Length) {
            throw 'The mob PNG byte-array length or final typeData byte is invalid.'
        }
        $pngSignature = [byte[]]@(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        for ($i = 0; $i -lt $pngSignature.Length; $i++) {
            if ($actual[($pngOffset + $i)] -ne $pngSignature[$i]) {
                throw 'The mob payload does not contain a valid PNG signature.'
            }
        }
        if ($actual[$actual.Length - 1] -ne 0) {
            throw "Unsupported mob typeData $($actual[$actual.Length - 1])."
        }
        "mobId=$MobId zoom=$Zoom payloadBytes=$($actual.Length) sha256=$(Get-Sha256Hex $actual)"
        "mobReadType=0 effectDataBytes=$effectDataLength pngBytes=$pngLength typeData=0"
    } elseif ($IconId -ge 0) {
        $iconPackets = @($packets | Where-Object { $_.Command -eq -67 })
        "iconPackets=$($iconPackets.Count)"
        if ($iconPackets.Count -ne 1 -or $iconPackets[0].Data.Length -le 8) {
            throw 'The icon request did not return image data.'
        }
    } elseif ($MapData) {
        $mapPackets = @($packets | Where-Object {
            $_.Command -eq -28 -and $_.Data.Length -ge 3 -and $_.Data[0] -eq 6
        })
        if ($mapPackets.Count -ne 1) {
            throw 'The map-data request did not return exactly one subtype-6 packet.'
        }
        $data = $mapPackets[0].Data
        $offset = 1
        $mapVersion = [int]$data[$offset++]
        $mapCount = [int]$data[$offset++]
        for ($i = 0; $i -lt $mapCount; $i++) {
            $null = Read-Utf8 $data ([ref]$offset)
        }
        if ($offset -ge $data.Length) {
            throw 'Map-data packet ended before the NPC template count.'
        }
        $npcCount = [int]$data[$offset++]
        for ($i = 0; $i -lt $npcCount; $i++) {
            $null = Read-Utf8 $data ([ref]$offset)
            if ($offset + 7 -gt $data.Length) {
                throw 'Truncated NPC template in map-data packet.'
            }
            $offset += 7 # head/body/leg int16 plus one trailing byte
        }
        if ($offset -ge $data.Length) {
            throw 'Map-data packet ended before the mob template count.'
        }
        $mobCount = [int]$data[$offset++]
        if ($mobCount -gt 127) {
            throw "Map-data contains $mobCount mob templates; this client supports at most 127 because the count is a signed byte."
        }
        $lastMobName = ''
        for ($i = 0; $i -lt $mobCount; $i++) {
            if ($offset -ge $data.Length) {
                throw 'Truncated mob type in map-data packet.'
            }
            $offset++ # type
            $lastMobName = Read-Utf8 $data ([ref]$offset)
            if ($offset + 7 -gt $data.Length) {
                throw 'Truncated mob template in map-data packet.'
            }
            $offset += 7 # hp int32 plus range/speed/dart bytes
        }
        if ($offset -ne $data.Length) {
            throw "Map-data parser left $($data.Length - $offset) trailing bytes."
        }
        "mapDataVersion=$mapVersion mapTemplates=$mapCount npcTemplates=$npcCount mobTemplates=$mobCount lastMob=$lastMobName payloadBytes=$($data.Length)"
    } elseif ($MapId -ge 0) {
        $mapPackets = @($packets | Where-Object {
            $_.Command -eq -28 -and $_.Data.Length -ge 3 -and $_.Data[0] -eq 10
        })
        if ($mapPackets.Count -ne 1) {
            throw 'The map-template request did not return exactly one subtype-10 packet.'
        }
        $expectedPath = Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')) "data\map\tile_map_data\$MapId"
        $expected = [IO.File]::ReadAllBytes($expectedPath)
        $actual = $mapPackets[0].Data[1..($mapPackets[0].Data.Length - 1)]
        if ($actual.Length -ne $expected.Length -or (Get-Sha256Hex $actual) -ne (Get-Sha256Hex $expected)) {
            throw 'The map-template response does not match the deployed tile-map file.'
        }
        $width = [int]$actual[0]
        $height = [int]$actual[1]
        if ($actual.Length -ne 2 + $width * $height) {
            throw 'The deployed tile-map payload has invalid dimensions.'
        }
        "mapId=$MapId width=$width height=$height payloadBytes=$($actual.Length) sha256=$(Get-Sha256Hex $actual)"
    } elseif ($BgImageId -ge 0) {
        $bgPackets = @($packets | Where-Object { $_.Command -eq -32 })
        if ($bgPackets.Count -ne 1 -or $bgPackets[0].Data.Length -le 6) {
            throw 'The background-image request did not return exactly one image packet.'
        }
        $data = $bgPackets[0].Data
        $returnedId = ([int]$data[0] -shl 8) -bor [int]$data[1]
        $imageLength = Read-BigEndianInt32 $data 2
        if ($returnedId -ne $BgImageId) {
            throw "The background response returned ID $returnedId instead of $BgImageId."
        }
        if ($imageLength -le 8 -or 6 + $imageLength -ne $data.Length) {
            throw 'The background response has an invalid image length.'
        }
        $actual = $data[6..($data.Length - 1)]
        $expectedPath = Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')) "data\item_bg_temp\x$Zoom\$BgImageId.png"
        $expected = [IO.File]::ReadAllBytes($expectedPath)
        if ($actual.Length -ne $expected.Length -or (Get-Sha256Hex $actual) -ne (Get-Sha256Hex $expected)) {
            throw 'The background response does not match the deployed PNG file.'
        }
        "bgImageId=$BgImageId zoom=$Zoom payloadBytes=$($actual.Length) sha256=$(Get-Sha256Hex $actual)"
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
    if ($null -ne $stream) {
        $stream.Dispose()
    }
    $client.Dispose()
}
