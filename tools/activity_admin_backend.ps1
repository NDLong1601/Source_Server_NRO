# Activity Points Admin backend. This file is dot-sourced by admin_data.ps1;
# it deliberately has no parameter block so it shares the caller's validated
# command arguments, database helpers and audit writer.

function Ensure-ActivityAdminSchema {
    Invoke-MySql @"
CREATE TABLE IF NOT EXISTS activity_admin_audit (
  id BIGINT NOT NULL AUTO_INCREMENT,
  action_name VARCHAR(64) NOT NULL,
  actor_note VARCHAR(500) NOT NULL,
  expected_revision BIGINT NULL,
  config_version_no BIGINT NULL,
  player_id BIGINT NULL,
  before_json LONGTEXT NULL,
  after_json LONGTEXT NULL,
  result_code VARCHAR(24) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY ix_activity_admin_audit_created (created_at),
  KEY ix_activity_admin_audit_player (player_id,created_at),
  KEY ix_activity_admin_audit_config (config_version_no,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
"@ | Out-Null
}

function ConvertTo-ActivityBool {
    param($Value, [string]$Label)
    $text = ([string]$Value).Trim().ToLowerInvariant()
    if ($text -in @('true','1')) { return $true }
    if ($text -in @('false','0')) { return $false }
    throw "$Label phải là true hoặc false."
}

function Get-ActivityInteger {
    param($Value, [string]$Label, [int]$Minimum, [int]$Maximum)
    $number = 0
    if (-not [int]::TryParse([string]$Value, [ref]$number) -or $number -lt $Minimum -or $number -gt $Maximum) {
        throw "$Label phải là số nguyên từ $Minimum đến $Maximum."
    }
    return $number
}

function Get-ActivityProperty {
    param($Object, [string]$Name, $Default = $null)
    if ($null -eq $Object) { return $Default }
    if ($Object -is [System.Collections.IDictionary]) {
        if ($Object.Contains($Name)) { return $Object[$Name] }
        return $Default
    }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property -or $null -eq $property.Value) { return $Default }
    return $property.Value
}

function Get-ActivitySourceDefaults {
    @(
        [ordered]@{ key='DAILY_LOGIN'; enabled=$true; category='LOGIN'; points=5; dailyCap=5; groupKey=$null; groupDailyCap=0; dedupePolicy='REQUIRED' },
        [ordered]@{ key='DAILY_CHECKIN'; enabled=$true; category='LOGIN'; points=5; dailyCap=5; groupKey=$null; groupDailyCap=0; dedupePolicy='REQUIRED' },
        [ordered]@{ key='PEA_HARVEST'; enabled=$true; category='LIFE'; points=5; dailyCap=5; groupKey=$null; groupDailyCap=0; dedupePolicy='NONE' },
        [ordered]@{ key='SIDE_TASK_EASY'; enabled=$true; category='PVE'; points=5; dailyCap=30; groupKey='SIDE_TASK'; groupDailyCap=30; dedupePolicy='NONE' },
        [ordered]@{ key='SIDE_TASK_NORMAL'; enabled=$true; category='PVE'; points=8; dailyCap=30; groupKey='SIDE_TASK'; groupDailyCap=30; dedupePolicy='NONE' },
        [ordered]@{ key='SIDE_TASK_HARD'; enabled=$true; category='PVE'; points=12; dailyCap=30; groupKey='SIDE_TASK'; groupDailyCap=30; dedupePolicy='NONE' },
        [ordered]@{ key='SIDE_TASK_VERY_HARD'; enabled=$true; category='PVE'; points=15; dailyCap=30; groupKey='SIDE_TASK'; groupDailyCap=30; dedupePolicy='NONE' },
        [ordered]@{ key='SIDE_TASK_SPECIAL'; enabled=$true; category='PVE'; points=20; dailyCap=30; groupKey='SIDE_TASK'; groupDailyCap=30; dedupePolicy='NONE' },
        [ordered]@{ key='CLAN_CHECKIN'; enabled=$true; category='SOCIAL'; points=5; dailyCap=5; groupKey=$null; groupDailyCap=0; dedupePolicy='REQUIRED' },
        [ordered]@{ key='FISH_CATCH'; enabled=$true; category='LIFE'; points=2; dailyCap=10; groupKey=$null; groupDailyCap=0; dedupePolicy='NONE' },
        [ordered]@{ key='PVP_WIN'; enabled=$true; category='PVP'; points=5; dailyCap=10; groupKey=$null; groupDailyCap=0; dedupePolicy='REQUIRED' },
        [ordered]@{ key='DUNGEON_CLEAR'; enabled=$true; category='PVE'; points=10; dailyCap=20; groupKey=$null; groupDailyCap=0; dedupePolicy='REQUIRED' },
        [ordered]@{ key='BOSS_KILL'; enabled=$true; category='PVE'; points=5; dailyCap=10; groupKey=$null; groupDailyCap=0; dedupePolicy='REQUIRED' },
        [ordered]@{ key='DIVERSITY_BONUS'; enabled=$true; category='BONUS'; points=10; dailyCap=10; groupKey=$null; groupDailyCap=0; dedupePolicy='REQUIRED' }
    )
}

function Get-ActivityRuntimeSnapshot {
    Ensure-ActivityAdminSchema
    $metaText = Invoke-MySql @"
SELECT r.revision,r.published_config_id,v.version_no,v.status,
       COALESCE(DATE_FORMAT(v.published_at,'%Y-%m-%d %H:%i:%s'),'') AS published_at,
       COALESCE(v.note,'') AS note
FROM activity_runtime_config r
LEFT JOIN activity_config_version v ON v.id=r.published_config_id
WHERE r.id=1 LIMIT 1;
"@
    $lines = @($metaText -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($lines.Count -lt 2) { throw "Không tìm thấy runtime config Năng động." }
    $parts = @($lines[-1] -split "`t", 6)
    if ($parts.Count -lt 6) { throw "Không đọc đủ metadata runtime Năng động." }
    $configJson = Get-MySqlScalar "SELECT v.config_json FROM activity_runtime_config r JOIN activity_config_version v ON v.id=r.published_config_id WHERE r.id=1 LIMIT 1;" ""
    if ([string]::IsNullOrWhiteSpace($configJson)) { throw "Runtime config Năng động trống." }
    [pscustomobject]@{
        revision=[long]$parts[0]; configId=[long]$parts[1]; versionNo=[long]$parts[2]; status=[string]$parts[3]
        publishedAt=[string]$parts[4]; note=[string]$parts[5]; configJson=[string]$configJson
    }
}

function ConvertFrom-ActivityConfigJson {
    param([string]$Json, [string]$Label = 'Cấu hình Năng động')
    try { return $Json | ConvertFrom-Json } catch { throw "$Label không phải JSON hợp lệ." }
}

function Get-ActivityTierHistory {
    $raw = Invoke-MySql "SELECT config_json FROM activity_config_version WHERE status IN ('PUBLISHED','ARCHIVED') ORDER BY version_no;"
    $history = @{}
    $bits = @{}
    foreach ($line in @($raw -split "`r?`n" | Select-Object -Skip 1)) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        try { $config = ConvertFrom-ActivityConfigJson $line 'Lịch sử cấu hình Năng động' } catch { continue }
        foreach ($period in @('dailyTiers','weeklyTiers')) {
            $periodName = if ($period -eq 'dailyTiers') { 'DAILY' } else { 'WEEKLY' }
            foreach ($tier in @((Get-ActivityProperty $config $period @()))) {
                $id = [string](Get-ActivityProperty $tier 'id' '')
                $bitText = [string](Get-ActivityProperty $tier 'claimBit' '')
                if ([string]::IsNullOrWhiteSpace($id) -or $bitText -notmatch '^\d+$') { continue }
                $idKey = "$periodName|$id"
                $bitKey = "$periodName|$bitText"
                if (-not $history.ContainsKey($idKey)) { $history[$idKey] = [int]$bitText }
                if (-not $bits.ContainsKey($bitKey)) { $bits[$bitKey] = $id }
            }
        }
    }
    [pscustomobject]@{ ids=$history; bits=$bits }
}

function Normalize-ActivityConfig {
    param($RawConfig)
    if ($null -eq $RawConfig) { throw "Thiếu config trong payload." }
    $globalRaw = Get-ActivityProperty $RawConfig 'global' $null
    if ($null -eq $globalRaw) { throw "Config thiếu global." }
    $dailyMax = Get-ActivityInteger (Get-ActivityProperty $globalRaw 'dailyMax' $null) 'dailyMax' 1 1000
    $qualified = Get-ActivityInteger (Get-ActivityProperty $globalRaw 'qualifiedDailyPoints' $null) 'qualifiedDailyPoints' 1 $dailyMax
    $resetHour = Get-ActivityInteger (Get-ActivityProperty $globalRaw 'dailyResetHour' $null) 'dailyResetHour' 0 23
    $diversityCategories = Get-ActivityInteger (Get-ActivityProperty $globalRaw 'diversityCategoryCount' $null) 'diversityCategoryCount' 1 5
    $diversityBonus = Get-ActivityInteger (Get-ActivityProperty $globalRaw 'diversityBonus' $null) 'diversityBonus' 0 $dailyMax
    $timezone = [string](Get-ActivityProperty $globalRaw 'timezone' '')
    if ($timezone -ne 'Asia/Ho_Chi_Minh') { throw "timezone hiện chỉ hỗ trợ Asia/Ho_Chi_Minh." }
    $weeklyDay = ([string](Get-ActivityProperty $globalRaw 'weeklyResetDay' '')).ToUpperInvariant()
    if ($weeklyDay -notin @('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')) { throw "weeklyResetDay không hợp lệ." }
    $global = [ordered]@{
        enabled=(ConvertTo-ActivityBool (Get-ActivityProperty $globalRaw 'enabled' $null) 'global.enabled')
        shadowMode=(ConvertTo-ActivityBool (Get-ActivityProperty $globalRaw 'shadowMode' $null) 'global.shadowMode')
        rewardEnabled=(ConvertTo-ActivityBool (Get-ActivityProperty $globalRaw 'rewardEnabled' $null) 'global.rewardEnabled')
        timezone=$timezone; dailyResetHour=$resetHour; weeklyResetDay=$weeklyDay; dailyMax=$dailyMax
        qualifiedDailyPoints=$qualified; diversityCategoryCount=$diversityCategories; diversityBonus=$diversityBonus
    }

    $defaultsByKey = @{}
    foreach ($source in @(Get-ActivitySourceDefaults)) { $defaultsByKey[$source.key] = $source }
    $rawSources = @((Get-ActivityProperty $RawConfig 'sources' @()))
    if ($rawSources.Count -eq 0) { $rawSources = @(Get-ActivitySourceDefaults) }
    if ($rawSources.Count -ne $defaultsByKey.Count) { throw "sources phải chứa đúng $($defaultsByKey.Count) source key bất biến." }
    $sources = New-Object System.Collections.Generic.List[object]
    $seenSources = @{}
    foreach ($source in $rawSources) {
        $key = ([string](Get-ActivityProperty $source 'key' '')).Trim().ToUpperInvariant()
        if (-not $defaultsByKey.ContainsKey($key) -or $seenSources.ContainsKey($key)) { throw "source key '$key' không hợp lệ hoặc trùng." }
        $default = $defaultsByKey[$key]
        $enabled = ConvertTo-ActivityBool (Get-ActivityProperty $source 'enabled' $null) "sources.$key.enabled"
        $category = ([string](Get-ActivityProperty $source 'category' $default.category)).Trim().ToUpperInvariant()
        if ($category -notin @('LOGIN','PVE','PVP','SOCIAL','LIFE','BONUS')) { throw "Category source $key không hợp lệ." }
        $points = Get-ActivityInteger (Get-ActivityProperty $source 'points' $null) "Điểm source $key" 0 $dailyMax
        $dailyCap = Get-ActivityInteger (Get-ActivityProperty $source 'dailyCap' $null) "Trần source $key" 0 $dailyMax
        if ($enabled -and ($points -le 0 -or $dailyCap -lt $points)) { throw "Source $key đang bật phải có điểm dương và cap >= điểm." }
        $groupKey = ([string](Get-ActivityProperty $source 'groupKey' $default.groupKey)).Trim().ToUpperInvariant()
        $groupCap = Get-ActivityInteger (Get-ActivityProperty $source 'groupDailyCap' $default.groupDailyCap) "Trần nhóm source $key" 0 $dailyMax
        if ([string]::IsNullOrWhiteSpace($groupKey)) { $groupKey = $null; $groupCap = 0 }
        elseif ($groupKey -notmatch '^[A-Z][A-Z0-9_]{1,31}$' -or $groupCap -lt $points) { throw "Nhóm/cap source $key không hợp lệ." }
        $dedupe = ([string](Get-ActivityProperty $source 'dedupePolicy' $default.dedupePolicy)).Trim().ToUpperInvariant()
        if ($dedupe -ne $default.dedupePolicy) { throw "dedupePolicy của source $key là bất biến ($($default.dedupePolicy))." }
        $sources.Add([ordered]@{ key=$key; enabled=$enabled; category=$category; points=$points; dailyCap=$dailyCap; groupKey=$groupKey; groupDailyCap=$groupCap; dedupePolicy=$dedupe })
        $seenSources[$key] = $true
    }

    $itemIds = New-Object System.Collections.Generic.HashSet[int]
    $optionIds = New-Object System.Collections.Generic.HashSet[int]
    $history = Get-ActivityTierHistory
    $tiersByPeriod = @{}
    foreach ($periodSpec in @(@{ property='dailyTiers'; name='DAILY'; max=$dailyMax }, @{ property='weeklyTiers'; name='WEEKLY'; max=($dailyMax * 7) })) {
        $rawTiers = @((Get-ActivityProperty $RawConfig $periodSpec.property $null))
        if ($null -eq (Get-ActivityProperty $RawConfig $periodSpec.property $null) -or $rawTiers.Count -gt 50) { throw "$($periodSpec.property) phải có tối đa 50 mốc." }
        $tiers = New-Object System.Collections.Generic.List[object]
        $seenIds = @{}; $seenBits = @{}; $seenThresholds = @{}; $previousThreshold = 0
        foreach ($tier in $rawTiers) {
            $tierId = ([string](Get-ActivityProperty $tier 'id' '')).Trim().ToUpperInvariant()
            if ($tierId -notmatch '^[A-Z][A-Z0-9_]{2,63}$' -or $seenIds.ContainsKey($tierId)) { throw "Tier ID '$tierId' không hợp lệ hoặc trùng." }
            $claimBit = Get-ActivityInteger (Get-ActivityProperty $tier 'claimBit' $null) "claimBit $tierId" 0 62
            $threshold = Get-ActivityInteger (Get-ActivityProperty $tier 'threshold' $null) "Ngưỡng $tierId" 1 $periodSpec.max
            $qualifiedDays = Get-ActivityInteger (Get-ActivityProperty $tier 'minQualifiedDays' 0) "Ngày đạt chuẩn $tierId" 0 7
            if ($seenBits.ContainsKey($claimBit) -or $seenThresholds.ContainsKey($threshold)) { throw "claimBit hoặc threshold tier $tierId bị trùng." }
            if ($threshold -le $previousThreshold) { throw "threshold tier $tierId phải tăng dần trong $($periodSpec.property)." }
            $historyKey = "$($periodSpec.name)|$tierId"; $bitHistoryKey = "$($periodSpec.name)|$claimBit"
            if ($history.ids.ContainsKey($historyKey) -and [int]$history.ids[$historyKey] -ne $claimBit) { throw "Tier $tierId đã publish với claimBit khác và không thể đổi." }
            if ($history.bits.ContainsKey($bitHistoryKey) -and [string]$history.bits[$bitHistoryKey] -ne $tierId) { throw "claimBit $claimBit đã thuộc tier $($history.bits[$bitHistoryKey]) và không thể tái sử dụng." }
            $rewards = New-Object System.Collections.Generic.List[object]
            foreach ($reward in @((Get-ActivityProperty $tier 'rewards' @()))) {
                $kind = ([string](Get-ActivityProperty $reward 'kind' '')).Trim().ToUpperInvariant()
                if ($kind -notin @('ITEM','GOLD','GEM','RUBY')) { throw "Reward của $tierId có kind không hợp lệ." }
                $minimum = Get-ActivityInteger (Get-ActivityProperty $reward 'quantityMin' $null) "quantityMin $tierId" 1 100000000
                $maximum = Get-ActivityInteger (Get-ActivityProperty $reward 'quantityMax' $minimum) "quantityMax $tierId" $minimum 100000000
                $itemId = Get-ActivityInteger (Get-ActivityProperty $reward 'itemId' 0) "itemId $tierId" 0 32767
                if ($kind -eq 'ITEM' -and $itemId -le 0) { throw "Reward ITEM của $tierId cần itemId hợp lệ." }
                if ($kind -eq 'ITEM') { [void]$itemIds.Add($itemId) }
                $gender = Get-ActivityInteger (Get-ActivityProperty $reward 'gender' 3) "gender reward $tierId" -1 3
                $bindMode = ([string](Get-ActivityProperty $reward 'bindMode' 'BOUND')).Trim().ToUpperInvariant()
                if ($bindMode -notin @('BOUND','UNBOUND')) { throw "bindMode $tierId không hợp lệ." }
                $expiryMin = Get-ActivityInteger (Get-ActivityProperty $reward 'expiryDaysMin' 0) "expiryDaysMin $tierId" 0 36500
                $expiryMax = Get-ActivityInteger (Get-ActivityProperty $reward 'expiryDaysMax' $expiryMin) "expiryDaysMax $tierId" $expiryMin 36500
                $options = New-Object System.Collections.Generic.List[object]; $seenOptions = @{}
                foreach ($option in @((Get-ActivityProperty $reward 'options' @()))) {
                    $optionId = Get-ActivityInteger (Get-ActivityProperty $option 'id' $null) "Option ID $tierId" 0 32767
                    if ($seenOptions.ContainsKey($optionId)) { throw "Option $optionId của $tierId bị trùng." }
                    $paramMin = Get-ActivityInteger (Get-ActivityProperty $option 'paramMin' 0) "paramMin option $optionId" -32768 32767
                    $paramMax = Get-ActivityInteger (Get-ActivityProperty $option 'paramMax' $paramMin) "paramMax option $optionId" $paramMin 32767
                    $options.Add([ordered]@{ id=$optionId; paramMin=$paramMin; paramMax=$paramMax }); $seenOptions[$optionId]=$true; [void]$optionIds.Add($optionId)
                }
                $initBaseOptions = ConvertTo-ActivityBool (Get-ActivityProperty $reward 'initBaseOptions' $false) "initBaseOptions $tierId"
                $useDefaultOptions = ConvertTo-ActivityBool (Get-ActivityProperty $reward 'useDefaultOptions' $false) "useDefaultOptions $tierId"
                if ($kind -ne 'ITEM' -and ($itemId -ne 0 -or $options.Count -gt 0 -or $initBaseOptions -or $useDefaultOptions -or $expiryMin -ne 0 -or $expiryMax -ne 0)) {
                    throw "Reward tiền tệ $tierId không được chứa item/options/HSD."
                }
                $rewards.Add([ordered]@{ kind=$kind; itemId=$itemId; quantityMin=$minimum; quantityMax=$maximum; gender=$gender; bindMode=$bindMode; initBaseOptions=$initBaseOptions; useDefaultOptions=$useDefaultOptions; options=$options.ToArray(); expiryDaysMin=$expiryMin; expiryDaysMax=$expiryMax })
            }
            $enabled = ConvertTo-ActivityBool (Get-ActivityProperty $tier 'enabled' $null) "enabled tier $tierId"
            if ($enabled -and $rewards.Count -eq 0) { throw "Tier bật $tierId phải có ít nhất một reward." }
            $tiers.Add([ordered]@{ id=$tierId; claimBit=$claimBit; enabled=$enabled; threshold=$threshold; minQualifiedDays=$qualifiedDays; rewards=$rewards.ToArray() })
            $seenIds[$tierId]=$true; $seenBits[$claimBit]=$true; $seenThresholds[$threshold]=$true; $previousThreshold=$threshold
        }
        foreach ($pastKey in $history.ids.Keys | Where-Object { $_ -like "$($periodSpec.name)|*" }) {
            $pastId = $pastKey.Substring($periodSpec.name.Length + 1)
            if (-not $seenIds.ContainsKey($pastId)) { throw "Không thể xóa tier đã publish $pastId; hãy giữ lại và disable." }
        }
        $tiersByPeriod[$periodSpec.property] = $tiers.ToArray()
    }

    if ($itemIds.Count -gt 0) {
        $itemRows = Invoke-MySql "SELECT id,`TYPE` FROM item_template WHERE id IN ($($itemIds -join ','));"
        $foundItems = @{}
        foreach ($line in @($itemRows -split "`r?`n" | Select-Object -Skip 1)) { if ($line -match '^\d+\t-?\d+$') { $parts=$line -split "`t"; $foundItems[[int]$parts[0]]=[int]$parts[1] } }
        foreach ($itemId in $itemIds) { if (-not $foundItems.ContainsKey($itemId) -or $foundItems[$itemId] -in @(9,10,34)) { throw "Reward ITEM dùng itemId $itemId không tồn tại hoặc là tiền tệ; dùng kind phù hợp." } }
    }
    if ($optionIds.Count -gt 0) {
        $foundOptionCount = [int](Get-MySqlScalar "SELECT COUNT(*) FROM item_option_template WHERE id IN ($($optionIds -join ','));" '0')
        if ($foundOptionCount -ne $optionIds.Count) { throw "Có option reward không tồn tại trong item_option_template." }
    }
    [ordered]@{ schemaVersion=1; global=$global; sources=$sources.ToArray(); dailyTiers=$tiersByPeriod['dailyTiers']; weeklyTiers=$tiersByPeriod['weeklyTiers'] }
}

function ConvertTo-ActivityCanonicalJson {
    param($Config)
    ConvertTo-Json -InputObject $Config -Compress -Depth 30
}

function Read-ActivityMutationPayload {
    try { return $PayloadJson | ConvertFrom-Json } catch { throw "Payload Năng động không phải JSON hợp lệ." }
}

function Get-ActivityReason {
    param($Payload)
    $reason = ([string](Get-ActivityProperty $Payload 'reason' '')).Trim()
    if ($reason.Length -lt 5 -or $reason.Length -gt 500) { throw "Lý do thao tác Năng động phải dài 5–500 ký tự." }
    return $reason
}

function Get-ActivityExpectedRevision {
    param($Payload)
    [long](Get-ActivityInteger (Get-ActivityProperty $Payload 'expectedRevision' $null) 'expectedRevision' 0 2147483647)
}

function Assert-ActivityExpectedRevision {
    param([long]$Expected, $Runtime)
    if ($Expected -ne [long]$Runtime.revision) { throw "Runtime revision đã đổi (hiện $($Runtime.revision), request $Expected). Hãy tải lại trước khi lưu." }
}

function Write-ActivityAdminAudit {
    param([string]$ActionName, [string]$Reason, [long]$ExpectedRevision, $ConfigVersionNo = $null, $PlayerId = $null, [string]$BeforeJson, [string]$AfterJson, [string]$ResultCode)
    Ensure-ActivityAdminSchema
    $configSql = if ($null -eq $ConfigVersionNo) { 'NULL' } else { [long]$ConfigVersionNo }
    $playerSql = if ($null -eq $PlayerId) { 'NULL' } else { [long]$PlayerId }
    Invoke-MySql "INSERT INTO activity_admin_audit (action_name,actor_note,expected_revision,config_version_no,player_id,before_json,after_json,result_code) VALUES ($(SqlString $ActionName),$(SqlString $Reason),$(if ($ExpectedRevision -gt 0) {$ExpectedRevision} else {'NULL'}),$configSql,$playerSql,$(if ([string]::IsNullOrWhiteSpace($BeforeJson)) {'NULL'} else {SqlString $BeforeJson}),$(if ([string]::IsNullOrWhiteSpace($AfterJson)) {'NULL'} else {SqlString $AfterJson}),$(SqlString $ResultCode));" | Out-Null
}

function Get-ActivityOverview {
    $runtime = Get-ActivityRuntimeSnapshot
    $normalized = Normalize-ActivityConfig (ConvertFrom-ActivityConfigJson $runtime.configJson)
    $activityProperties = Get-PropertyMap (Join-Path $Root 'activity.properties')
    [ordered]@{
        status='ok'; revision=[long]$runtime.revision; publishedConfigId=[long]$runtime.configId; versionNo=[long]$runtime.versionNo
        publishedAt=$runtime.publishedAt; note=$runtime.note
        emergencyDisabled=([string]$activityProperties['activity.emergency.disable']).Trim().ToLowerInvariant() -in @('true','1')
        config=$normalized
        summary=[ordered]@{ sourceCount=@($normalized.sources).Count; dailyTierCount=@($normalized.dailyTiers).Count; weeklyTierCount=@($normalized.weeklyTiers).Count; rewardEnabled=[bool]$normalized.global.rewardEnabled; shadowMode=[bool]$normalized.global.shadowMode }
    } | ConvertTo-Json -Depth 35
}

function Get-ActivityVersionByNumber {
    param([long]$VersionNo)
    if ($VersionNo -le 0) { throw "versionNo không hợp lệ." }
    $raw = Invoke-MySql "SELECT id,version_no,status,schema_version,config_json,COALESCE(note,''),DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s'),COALESCE(DATE_FORMAT(published_at,'%Y-%m-%d %H:%i:%s'),'') FROM activity_config_version WHERE version_no=$VersionNo LIMIT 1;"
    $lines = @($raw -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($lines.Count -lt 2) { throw "Không tìm thấy version Năng động $VersionNo." }
    $parts = @($lines[-1] -split "`t", 8)
    if ($parts.Count -lt 8) { throw "Dữ liệu version Năng động không đầy đủ." }
    [ordered]@{
        id=[long]$parts[0]; versionNo=[long]$parts[1]; status=[string]$parts[2]; schemaVersion=[int]$parts[3]
        config=(Normalize-ActivityConfig (ConvertFrom-ActivityConfigJson $parts[4] "Version $VersionNo"))
        note=[string]$parts[5]; createdAt=[string]$parts[6]; publishedAt=[string]$parts[7]
    }
}

function Get-ActivityDraft {
    Ensure-ActivityAdminSchema
    $versionNo = if ($Id -match '^\d+$') { [long]$Id } else { 0L }
    if ($versionNo -gt 0) {
        $draft = Get-ActivityVersionByNumber $versionNo
        if ($draft.status -ne 'DRAFT') { throw "Version $versionNo không phải bản nháp." }
        return $draft | ConvertTo-Json -Depth 35
    }
    $draftNo = [long](Get-MySqlScalar "SELECT COALESCE(MAX(version_no),0) FROM activity_config_version WHERE status='DRAFT';" '0')
    if ($draftNo -le 0) { return '{"status":"empty","draft":null}' }
    (Get-ActivityVersionByNumber $draftNo) | ConvertTo-Json -Depth 35
}

function List-ActivityVersions {
    Ensure-ActivityAdminSchema
    $raw = Invoke-MySql "SELECT v.version_no,v.status,v.schema_version,COALESCE(v.note,''),DATE_FORMAT(v.created_at,'%Y-%m-%d %H:%i:%s'),COALESCE(DATE_FORMAT(v.published_at,'%Y-%m-%d %H:%i:%s'),''),CASE WHEN v.id=(SELECT published_config_id FROM activity_runtime_config WHERE id=1) THEN 1 ELSE 0 END FROM activity_config_version v ORDER BY v.version_no DESC LIMIT 200;"
    $rows = New-Object System.Collections.Generic.List[object]
    foreach ($line in @($raw -split "`r?`n" | Select-Object -Skip 1)) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $parts = @($line -split "`t", 7); if ($parts.Count -lt 7) { continue }
        $rows.Add([ordered]@{ versionNo=[long]$parts[0]; status=[string]$parts[1]; schemaVersion=[int]$parts[2]; note=[string]$parts[3]; createdAt=[string]$parts[4]; publishedAt=[string]$parts[5]; runtime=([int]$parts[6] -eq 1) })
    }
    [ordered]@{ status='ok'; runtimeRevision=(Get-ActivityRuntimeSnapshot).revision; versions=$rows.ToArray() } | ConvertTo-Json -Depth 10
}

function Validate-ActivityConfigRequest {
    $payload = Read-ActivityMutationPayload
    $candidate = Get-ActivityProperty $payload 'config' $payload
    $normalized = Normalize-ActivityConfig $candidate
    if ([bool]$normalized.global.rewardEnabled -and [bool]$normalized.global.shadowMode) {
        throw "Không thể mở phát quà khi shadowMode=true. Tắt shadowMode trong cùng bản phát hành."
    }
    [ordered]@{ status='valid'; config=$normalized; warnings=@($(if ([bool]$normalized.global.rewardEnabled) { 'Mở quà cần xác nhận ENABLE_ACTIVITY_REWARDS khi publish.' })) } | ConvertTo-Json -Depth 35
}

function Save-ActivityDraft {
    $payload = Read-ActivityMutationPayload
    $runtime = Get-ActivityRuntimeSnapshot
    $expected = Get-ActivityExpectedRevision $payload
    Assert-ActivityExpectedRevision $expected $runtime
    $reason = Get-ActivityReason $payload
    $normalized = Normalize-ActivityConfig (Get-ActivityProperty $payload 'config' $null)
    $configJson = ConvertTo-ActivityCanonicalJson $normalized
    $nextVersion = [long](Get-MySqlScalar "SELECT COALESCE(MAX(version_no),0)+1 FROM activity_config_version;" '1')
    Invoke-MySql "INSERT INTO activity_config_version (version_no,status,schema_version,config_json,note) VALUES ($nextVersion,'DRAFT',1,$(SqlString $configJson),$(SqlString $reason));" | Out-Null
    Write-ActivityAdminAudit 'saveactivitydraft' $reason $expected $nextVersion $null $runtime.configJson $configJson 'SUCCESS'
    [ordered]@{ status='ok'; versionNo=$nextVersion; revision=$runtime.revision; message='Đã lưu bản nháp; runtime chưa thay đổi.' } | ConvertTo-Json
}

function Publish-ActivityConfig {
    $payload = Read-ActivityMutationPayload
    $runtime = Get-ActivityRuntimeSnapshot
    $expected = Get-ActivityExpectedRevision $payload
    Assert-ActivityExpectedRevision $expected $runtime
    $reason = Get-ActivityReason $payload
    $versionNo = [long](Get-ActivityInteger (Get-ActivityProperty $payload 'versionNo' $null) 'versionNo' 1 2147483647)
    $draft = Get-ActivityVersionByNumber $versionNo
    if ($draft.status -ne 'DRAFT') { throw "Chỉ có thể publish version DRAFT." }
    $normalized = Normalize-ActivityConfig $draft.config
    if ([bool]$normalized.global.rewardEnabled) {
        if ([bool]$normalized.global.shadowMode) {
            throw "Không thể mở phát quà khi shadowMode=true. Tắt shadowMode trong cùng bản phát hành."
        }
        if ([string](Get-ActivityProperty $payload 'releaseToken' '') -cne 'ENABLE_ACTIVITY_REWARDS') {
            throw "Mở phát quà cần releaseToken ENABLE_ACTIVITY_REWARDS." 
        }
        $current = Normalize-ActivityConfig (ConvertFrom-ActivityConfigJson $runtime.configJson 'Runtime Năng động')
        if (-not [bool]$current.global.enabled -or -not [bool]$current.global.shadowMode -or [bool]$current.global.rewardEnabled) {
            throw "Chỉ được mở quà từ runtime đang bật, shadowMode=true và rewardEnabled=false."
        }
        $activityProperties = Get-PropertyMap (Join-Path $Root 'activity.properties')
        if (([string]$activityProperties['activity.emergency.disable']).Trim().ToLowerInvariant() -in @('true','1')) {
            throw "Emergency disable đang bật; không thể mở phát quà."
        }
    }
    $configJson = ConvertTo-ActivityCanonicalJson $normalized
    Invoke-MySql "UPDATE activity_config_version SET config_json=$(SqlString $configJson),note=$(SqlString $reason) WHERE version_no=$versionNo AND status='DRAFT';" | Out-Null
    $publishSql = @"
START TRANSACTION;
UPDATE activity_runtime_config r
JOIN activity_config_version v ON v.version_no=$versionNo AND v.status='DRAFT'
SET r.published_config_id=v.id,r.revision=r.revision+1
WHERE r.id=1 AND r.revision=$expected;
SET @activity_publish_ok=ROW_COUNT();
UPDATE activity_config_version SET status='ARCHIVED' WHERE @activity_publish_ok=1 AND status='PUBLISHED' AND version_no<>$versionNo;
UPDATE activity_config_version SET status='PUBLISHED',published_at=NOW(),note=$(SqlString $reason) WHERE @activity_publish_ok=1 AND version_no=$versionNo AND status='DRAFT';
COMMIT;
SELECT @activity_publish_ok;
"@
    $published = [int](Get-MySqlScalar $publishSql '0')
    if ($published -ne 1) { throw "Publish bị từ chối vì runtime revision hoặc trạng thái draft đã đổi. Hãy tải lại." }
    $after = Get-ActivityRuntimeSnapshot
    Write-ActivityAdminAudit 'publishactivityconfig' $reason $expected $versionNo $null $runtime.configJson $after.configJson 'SUCCESS'
    [ordered]@{ status='ok'; versionNo=$versionNo; revision=$after.revision; message='Đã publish config; server sẽ poll revision mới trong tối đa 5 giây.' } | ConvertTo-Json
}

function Rollback-ActivityConfig {
    $payload = Read-ActivityMutationPayload
    $runtime = Get-ActivityRuntimeSnapshot
    $expected = Get-ActivityExpectedRevision $payload
    Assert-ActivityExpectedRevision $expected $runtime
    $reason = Get-ActivityReason $payload
    $versionNo = [long](Get-ActivityInteger (Get-ActivityProperty $payload 'versionNo' $null) 'versionNo' 1 2147483647)
    $target = Get-ActivityVersionByNumber $versionNo
    if ($target.status -eq 'DRAFT') { throw "Không thể rollback trực tiếp về DRAFT; hãy publish bản nháp trước." }
    $normalized = Normalize-ActivityConfig $target.config
    if ([bool]$normalized.global.rewardEnabled) { throw "Giai đoạn 4 không cho rollback tới config rewardEnabled=true." }
    $rollbackSql = @"
START TRANSACTION;
UPDATE activity_runtime_config r JOIN activity_config_version v ON v.version_no=$versionNo
SET r.published_config_id=v.id,r.revision=r.revision+1
WHERE r.id=1 AND r.revision=$expected;
SET @activity_rollback_ok=ROW_COUNT();
UPDATE activity_config_version SET status='ARCHIVED' WHERE @activity_rollback_ok=1 AND status='PUBLISHED' AND version_no<>$versionNo;
UPDATE activity_config_version SET status='PUBLISHED',published_at=NOW(),note=$(SqlString $reason) WHERE @activity_rollback_ok=1 AND version_no=$versionNo;
COMMIT;
SELECT @activity_rollback_ok;
"@
    $rolledBack = [int](Get-MySqlScalar $rollbackSql '0')
    if ($rolledBack -ne 1) { throw "Rollback bị từ chối vì runtime revision đã đổi." }
    $after = Get-ActivityRuntimeSnapshot
    Write-ActivityAdminAudit 'rollbackactivityconfig' $reason $expected $versionNo $null $runtime.configJson $after.configJson 'SUCCESS'
    [ordered]@{ status='ok'; versionNo=$versionNo; revision=$after.revision; message='Đã rollback config; server sẽ poll revision mới trong tối đa 5 giây.' } | ConvertTo-Json
}

function Set-ActivityEmergencyDisable {
    $payload = Read-ActivityMutationPayload
    $runtime = Get-ActivityRuntimeSnapshot
    $expected = Get-ActivityExpectedRevision $payload
    Assert-ActivityExpectedRevision $expected $runtime
    $reason = Get-ActivityReason $payload
    $disabled = ConvertTo-ActivityBool (Get-ActivityProperty $payload 'disabled' $true) 'disabled'
    $path = Join-Path $Root 'activity.properties'
    $before = if (Test-Path -LiteralPath $path) { [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8) } else { '' }
    $lines = New-Object System.Collections.Generic.List[string]
    foreach ($line in @($before -split "`r?`n")) { if ($line -ne '') { $lines.Add($line) } }
    $updated = $false
    for ($i=0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '^\s*activity\.emergency\.disable\s*=') { $lines[$i] = "activity.emergency.disable=$($disabled.ToString().ToLowerInvariant())"; $updated=$true; break }
    }
    if (-not $updated) { $lines.Add("activity.emergency.disable=$($disabled.ToString().ToLowerInvariant())") }
    [IO.File]::WriteAllText($path, ($lines -join [Environment]::NewLine) + [Environment]::NewLine, $Utf8NoBom)
    $after = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)
    Write-ActivityAdminAudit 'disableactivityemergency' $reason $expected $null $null $before $after 'SUCCESS'
    [ordered]@{ status='ok'; disabled=$disabled; revision=$runtime.revision; message='Đã đổi emergency flag; server poll activity.properties trong tối đa 5 giây.' } | ConvertTo-Json
}

function Get-ActivityPlayerRecord {
    param([long]$PlayerId)
    if ($PlayerId -le 0) { throw "Player ID không hợp lệ." }
    Ensure-PlayerOnlineSchema
    $raw = Invoke-MySql @"
SELECT p.id,p.name,p.gender,p.data_point,COALESCE(p.data_activity,''),
       CASE WHEN apo.last_seen>=DATE_SUB(NOW(),INTERVAL 20 SECOND) THEN 'ONLINE'
            WHEN a.last_time_login>a.last_time_logout THEN 'ONLINE?' ELSE 'OFFLINE' END,
       COALESCE(DATE_FORMAT(a.last_time_login,'%Y-%m-%d %H:%i:%s'),''),
       COALESCE(DATE_FORMAT(a.last_time_logout,'%Y-%m-%d %H:%i:%s'),'')
FROM player p LEFT JOIN account a ON a.id=p.account_id
LEFT JOIN admin_player_online apo ON apo.player_id=p.id WHERE p.id=$PlayerId LIMIT 1;
"@
    $lines = @($raw -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($lines.Count -lt 2) { throw "Không tìm thấy player ID $PlayerId." }
    $parts = @($lines[-1] -split "`t", 8)
    if ($parts.Count -lt 8) { throw "Không đọc đủ trạng thái Năng động player ID $PlayerId." }
    [pscustomobject]@{ id=[long]$parts[0]; name=[string]$parts[1]; gender=[int]$parts[2]; dataPoint=[string]$parts[3]; dataActivity=[string]$parts[4]; onlineState=[string]$parts[5]; lastLogin=[string]$parts[6]; lastLogout=[string]$parts[7] }
}

function Get-ActivityPlayerVersion {
    param($Record)
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [Text.Encoding]::UTF8.GetBytes(([string]$Record.dataPoint) + '|' + ([string]$Record.dataActivity))
        (($sha.ComputeHash($bytes) | ForEach-Object { $_.ToString('x2') }) -join '')
    } finally { $sha.Dispose() }
}

function ConvertTo-ActivityMap {
    param($Raw)
    $map = [ordered]@{}
    if ($null -eq $Raw) { return $map }
    if ($Raw -is [System.Collections.IDictionary]) {
        foreach ($key in $Raw.Keys) { $map[[string]$key] = $Raw[$key] }
        return $map
    }
    foreach ($property in @($Raw.PSObject.Properties)) { $map[[string]$property.Name] = $property.Value }
    $map
}

function ConvertTo-ActivityState {
    param([string]$Raw, [int]$LegacyDailyPoints)
    $parsed = $null
    if (-not [string]::IsNullOrWhiteSpace($Raw)) {
        try { $parsed = $Raw | ConvertFrom-Json } catch { throw "data_activity player bị lỗi JSON; không tự ghi đè dữ liệu hỏng." }
    }
    $integer = {
        param($Value, [int]$Default = 0)
        $n = 0
        if ([int]::TryParse([string]$Value, [ref]$n)) { return [Math]::Max(0,$n) }
        return $Default
    }
    $longValue = {
        param($Value)
        $n = 0L
        if ([long]::TryParse([string]$Value, [ref]$n)) { return [Math]::Max(0L,$n) }
        return 0L
    }
    $state = [ordered]@{
        schemaVersion=1; dayKey=[string](Get-ActivityProperty $parsed 'dayKey' ''); dailyPoints=(& $integer (Get-ActivityProperty $parsed 'dailyPoints' $LegacyDailyPoints) $LegacyDailyPoints); dailyClaimMask=(& $longValue (Get-ActivityProperty $parsed 'dailyClaimMask' 0));
        dailyCounters=[ordered]@{}; dailyCategories=@(); dailyUnique=[ordered]@{}; weekKey=[string](Get-ActivityProperty $parsed 'weekKey' ''); weeklyPoints=(& $integer (Get-ActivityProperty $parsed 'weeklyPoints' 0) 0); qualifiedDays=(& $integer (Get-ActivityProperty $parsed 'qualifiedDays' 0) 0); qualifiedDayKeys=@(); weeklyClaimMask=(& $longValue (Get-ActivityProperty $parsed 'weeklyClaimMask' 0)); lastAwardAt=(& $longValue (Get-ActivityProperty $parsed 'lastAwardAt' 0))
    }
    foreach ($property in (ConvertTo-ActivityMap (Get-ActivityProperty $parsed 'dailyCounters' $null)).GetEnumerator()) {
        if ($property.Key -match '^[A-Z0-9_:.-]{1,64}$') { $state.dailyCounters[$property.Key] = (& $integer $property.Value 0) }
    }
    $categories = New-Object System.Collections.Generic.List[string]
    foreach ($category in @((Get-ActivityProperty $parsed 'dailyCategories' @()))) {
        $name = ([string]$category).Trim().ToUpperInvariant(); if ($name -in @('LOGIN','PVE','PVP','SOCIAL','LIFE','BONUS') -and -not $categories.Contains($name)) { $categories.Add($name) }
    }
    $state.dailyCategories = $categories.ToArray()
    foreach ($source in (ConvertTo-ActivityMap (Get-ActivityProperty $parsed 'dailyUnique' $null)).GetEnumerator()) {
        $keys = New-Object System.Collections.Generic.List[string]
        foreach ($key in @($source.Value)) { $text=([string]$key).Trim(); if ($text.Length -gt 0 -and $text.Length -le 128 -and -not $keys.Contains($text) -and $keys.Count -lt 128) { $keys.Add($text) } }
        if ($source.Key -match '^[A-Z0-9_:.-]{1,64}$') { $state.dailyUnique[$source.Key] = $keys.ToArray() }
    }
    $qualified = New-Object System.Collections.Generic.List[string]
    foreach ($day in @((Get-ActivityProperty $parsed 'qualifiedDayKeys' @()))) { $text=([string]$day).Trim(); if ($text -match '^\d{4}-\d{2}-\d{2}$' -and -not $qualified.Contains($text) -and $qualified.Count -lt 7) { $qualified.Add($text) } }
    $state.qualifiedDayKeys = $qualified.ToArray(); $state.qualifiedDays=[Math]::Max([int]$state.qualifiedDays,$qualified.Count)
    $state
}

function Get-ActivityAdminPeriod {
    param($Config)
    $zone = [TimeZoneInfo]::FindSystemTimeZoneById('SE Asia Standard Time')
    $local = [TimeZoneInfo]::ConvertTimeFromUtc([DateTime]::UtcNow,$zone)
    if ($local.Hour -lt [int]$Config.global.dailyResetHour) { $local = $local.AddDays(-1) }
    $day = $local.Date
    $wanted = [System.DayOfWeek]::$(([string]$Config.global.weeklyResetDay).Substring(0,1) + ([string]$Config.global.weeklyResetDay).Substring(1).ToLowerInvariant())
    while ($day.DayOfWeek -ne $wanted) { $day = $day.AddDays(-1) }
    [ordered]@{ dayKey=$local.ToString('yyyy-MM-dd'); weekKey=$day.ToString('yyyy-MM-dd') }
}

function Reset-ActivityDailyState {
    param($State, [string]$DayKey)
    $State.dayKey=$DayKey; $State.dailyPoints=0; $State.dailyClaimMask=0L; $State.dailyCounters=[ordered]@{}; $State.dailyCategories=@(); $State.dailyUnique=[ordered]@{}
}

function Reset-ActivityWeeklyState {
    param($State, [string]$WeekKey)
    $State.weekKey=$WeekKey; $State.weeklyPoints=0; $State.qualifiedDays=0; $State.qualifiedDayKeys=@(); $State.weeklyClaimMask=0L
}

function Ensure-ActivityStateCurrentPeriodAdmin {
    param($State, $Config)
    $period = Get-ActivityAdminPeriod $Config
    $weekChanged = -not [string]::IsNullOrWhiteSpace([string]$State.weekKey) -and $State.weekKey -ne $period.weekKey
    if ([string]::IsNullOrWhiteSpace([string]$State.weekKey) -or $weekChanged) { Reset-ActivityWeeklyState $State $period.weekKey }
    if ([string]::IsNullOrWhiteSpace([string]$State.dayKey)) { $State.dayKey=$period.dayKey }
    elseif ($State.dayKey -ne $period.dayKey) {
        if (-not $weekChanged -and [int]$State.dailyPoints -ge [int]$Config.global.qualifiedDailyPoints -and -not (@($State.qualifiedDayKeys) -contains $State.dayKey)) {
            $State.qualifiedDayKeys = @($State.qualifiedDayKeys + @($State.dayKey) | Select-Object -Unique | Select-Object -First 7); $State.qualifiedDays=@($State.qualifiedDayKeys).Count
        }
        Reset-ActivityDailyState $State $period.dayKey
    }
    if ([int]$State.dailyPoints -gt [int]$Config.global.dailyMax) { $State.dailyPoints=[int]$Config.global.dailyMax }
    return $period
}

function ConvertTo-ActivityDataPointJson {
    param([string]$RawDataPoint, [int]$DailyPoints)
    $values = @((Convert-PlayerJsonArray $RawDataPoint 'data_point' 15))
    while ($values.Count -lt 15) { $values += 0 }
    $values[11] = $DailyPoints
    ConvertTo-Json -InputObject $values -Compress
}

function Assert-ActivityPlayerOffline {
    param($Record)
    if ($Record.onlineState -in @('ONLINE','ONLINE?')) { throw "Player $($Record.name) đang online hoặc không chắc offline; Admin Năng động chỉ ghi player OFFLINE." }
}

function Save-ActivityPlayerState {
    param($Record, $State, [string]$ExpectedVersion)
    if ((Get-ActivityPlayerVersion $Record) -ne $ExpectedVersion) { throw "Dữ liệu Năng động player đã đổi; hãy tải lại trước khi lưu." }
    $stateJson = ConvertTo-Json -InputObject $State -Compress -Depth 20
    $pointJson = ConvertTo-ActivityDataPointJson $Record.dataPoint ([int]$State.dailyPoints)
    $sql = @"
START TRANSACTION;
UPDATE player p LEFT JOIN account a ON a.id=p.account_id
LEFT JOIN admin_player_online apo ON apo.player_id=p.id
SET p.data_activity=$(SqlString $stateJson),p.data_point=$(SqlString $pointJson)
WHERE p.id=$($Record.id) AND COALESCE(p.data_activity,'')=$(SqlString ([string]$Record.dataActivity)) AND p.data_point=$(SqlString ([string]$Record.dataPoint))
  AND (apo.last_seen IS NULL OR apo.last_seen<DATE_SUB(NOW(),INTERVAL 20 SECOND))
  AND (a.id IS NULL OR a.last_time_login<=a.last_time_logout);
SET @activity_player_saved=ROW_COUNT();
COMMIT;
SELECT @activity_player_saved;
"@
    if ([int](Get-MySqlScalar $sql '0') -ne 1) { throw "Player đã thay đổi hoặc không còn offline; không ghi đè dữ liệu." }
    [pscustomobject]@{ stateJson=$stateJson; pointJson=$pointJson }
}

function Get-ActivityLegacyDailyPoints {
    param([string]$RawDataPoint)
    $values = @((Convert-PlayerJsonArray $RawDataPoint 'data_point' 15))
    [int]$values[11]
}

function Get-ActivityPlayerView {
    param($Record, $Config)
    $state = ConvertTo-ActivityState $Record.dataActivity (Get-ActivityLegacyDailyPoints $Record.dataPoint)
    $period = Ensure-ActivityStateCurrentPeriodAdmin $state $Config
    [ordered]@{
        id=[long]$Record.id; name=$Record.name; gender=[int]$Record.gender; onlineState=$Record.onlineState
        lastLogin=$Record.lastLogin; lastLogout=$Record.lastLogout; version=(Get-ActivityPlayerVersion $Record)
        period=$period; state=$state
        legacyDailyMirror=(Get-ActivityLegacyDailyPoints $Record.dataPoint)
    }
}

function Read-ActivityPlayerMutationPayload {
    $payload = Read-ActivityMutationPayload
    $playerId = [long](Get-ActivityInteger (Get-ActivityProperty $payload 'playerId' $null) 'playerId' 1 2147483647)
    $version = ([string](Get-ActivityProperty $payload 'expectedVersion' '')).Trim().ToLowerInvariant()
    if ($version -notmatch '^[a-f0-9]{64}$') { throw "expectedVersion player không hợp lệ; hãy tải lại player trước khi ghi." }
    [pscustomobject]@{ payload=$payload; playerId=$playerId; expectedVersion=$version; reason=(Get-ActivityReason $payload) }
}

function Get-ActivityConfigTier {
    param($Config, [string]$Period, [string]$TierId)
    $tiers = if ($Period -eq 'DAILY') { @($Config.dailyTiers) } else { @($Config.weeklyTiers) }
    $matches = @($tiers | Where-Object { ([string]$_.id).ToUpperInvariant() -eq $TierId })
    if ($matches.Count -ne 1) { throw "Không tìm thấy tier $TierId của $Period trong config runtime." }
    $matches[0]
}

function Test-ActivityConfirmation {
    param($Payload, [string]$Expected)
    if (([string](Get-ActivityProperty $Payload 'confirm' '')).Trim() -ne $Expected) { throw "Xác nhận an toàn không hợp lệ. Nhập chính xác $Expected." }
}

function Get-ActivityPlayer {
    $playerId = [long](Get-ActivityInteger $Id 'Id' 1 2147483647)
    $runtime = Get-ActivityRuntimeSnapshot
    $config = Normalize-ActivityConfig (ConvertFrom-ActivityConfigJson $runtime.configJson)
    [ordered]@{ status='ok'; revision=$runtime.revision; player=(Get-ActivityPlayerView (Get-ActivityPlayerRecord $playerId) $config) } | ConvertTo-Json -Depth 30
}

function List-ActivityPlayers {
    $page = Get-ActivityInteger $(if ([string]::IsNullOrWhiteSpace($Page)) { 1 } else { $Page }) 'Page' 1 100000
    $pageSize = Get-ActivityInteger $(if ([string]::IsNullOrWhiteSpace($PageSize)) { 30 } else { $PageSize }) 'PageSize' 1 100
    $offset = ($page - 1) * $pageSize
    Ensure-PlayerOnlineSchema
    $where = ''
    if (-not [string]::IsNullOrWhiteSpace($Search)) {
        $needle = $Search.Replace("\\", "\\\\").Replace("'", "''")
        $where = if ($Search -match '^\d+$') { "WHERE p.id=$(SqlInt $Search) OR p.name LIKE '%$needle%'" } else { "WHERE p.name LIKE '%$needle%'" }
    }
    $idsRaw = Invoke-MySql "SELECT p.id FROM player p $where ORDER BY p.id DESC LIMIT $offset,$pageSize;"
    $ids = @($idsRaw -split "`r?`n" | Select-Object -Skip 1 | Where-Object { $_ -match '^\d+$' } | ForEach-Object { [long]$_ })
    $runtime = Get-ActivityRuntimeSnapshot
    $config = Normalize-ActivityConfig (ConvertFrom-ActivityConfigJson $runtime.configJson)
    $players = New-Object System.Collections.Generic.List[object]
    foreach ($playerId in $ids) {
        $view = Get-ActivityPlayerView (Get-ActivityPlayerRecord $playerId) $config
        $players.Add([ordered]@{ id=$view.id; name=$view.name; gender=$view.gender; onlineState=$view.onlineState; dailyPoints=$view.state.dailyPoints; weeklyPoints=$view.state.weeklyPoints; dayKey=$view.state.dayKey; weekKey=$view.state.weekKey; lastAwardAt=$view.state.lastAwardAt })
    }
    $total = [long](Get-MySqlScalar "SELECT COUNT(*) FROM player p $where;" '0')
    [ordered]@{ status='ok'; revision=$runtime.revision; page=$page; pageSize=$pageSize; total=$total; players=$players.ToArray() } | ConvertTo-Json -Depth 12
}

function Adjust-ActivityPlayer {
    $request = Read-ActivityPlayerMutationPayload
    $scope = ([string](Get-ActivityProperty $request.payload 'scope' '')).Trim().ToUpperInvariant()
    if ($scope -notin @('DAILY','WEEKLY')) { throw "scope chỉ nhận DAILY hoặc WEEKLY." }
    $delta = Get-ActivityInteger (Get-ActivityProperty $request.payload 'pointsDelta' $null) 'pointsDelta' -1000 1000
    if ($delta -eq 0) { throw "pointsDelta phải khác 0." }
    $runtime = Get-ActivityRuntimeSnapshot
    $config = Normalize-ActivityConfig (ConvertFrom-ActivityConfigJson $runtime.configJson)
    $record = Get-ActivityPlayerRecord $request.playerId
    Assert-ActivityPlayerOffline $record
    $before = Get-ActivityPlayerView $record $config
    if ($before.version -ne $request.expectedVersion) { throw "Dữ liệu player đã đổi; hãy tải lại trước khi ghi." }
    $beforeJson = $before | ConvertTo-Json -Compress -Depth 25
    $state = $before.state
    if ($scope -eq 'DAILY') {
        $newDaily = [int]$state.dailyPoints + $delta
        if ($newDaily -lt 0 -or $newDaily -gt [int]$config.global.dailyMax) { throw "Điểm DAILY sau điều chỉnh phải trong 0–$($config.global.dailyMax)." }
        $state.dailyPoints=$newDaily
        $state.weeklyPoints=[Math]::Max(0, [int]$state.weeklyPoints + $delta)
    } else {
        $newWeekly = [int]$state.weeklyPoints + $delta
        if ($newWeekly -lt [int]$state.dailyPoints -or $newWeekly -gt ([int]$config.global.dailyMax * 7)) { throw "Điểm WEEKLY sau điều chỉnh phải từ dailyPoints đến $([int]$config.global.dailyMax * 7)." }
        $state.weeklyPoints=$newWeekly
    }
    $state.lastAwardAt=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $saved = Save-ActivityPlayerState $record $state $request.expectedVersion
    Write-ActivityAdminAudit 'adjustactivityplayer' $request.reason $runtime.revision $runtime.versionNo $record.id $beforeJson $saved.stateJson 'SUCCESS'
    [ordered]@{ status='ok'; playerId=$record.id; scope=$scope; pointsDelta=$delta; version=(Get-ActivityPlayerVersion ([pscustomobject]@{dataPoint=$saved.pointJson;dataActivity=$saved.stateJson})); state=$state } | ConvertTo-Json -Depth 25
}

function Reset-ActivityPlayer {
    $request = Read-ActivityPlayerMutationPayload
    $scope = ([string](Get-ActivityProperty $request.payload 'scope' '')).Trim().ToUpperInvariant()
    if ($scope -notin @('DAILY','WEEKLY','SOURCE')) { throw "scope chỉ nhận DAILY, WEEKLY hoặc SOURCE." }
    Test-ActivityConfirmation $request.payload 'RESET_ACTIVITY'
    $runtime = Get-ActivityRuntimeSnapshot
    $config = Normalize-ActivityConfig (ConvertFrom-ActivityConfigJson $runtime.configJson)
    $record = Get-ActivityPlayerRecord $request.playerId
    Assert-ActivityPlayerOffline $record
    $before = Get-ActivityPlayerView $record $config
    if ($before.version -ne $request.expectedVersion) { throw "Dữ liệu player đã đổi; hãy tải lại trước khi ghi." }
    $beforeJson = $before | ConvertTo-Json -Compress -Depth 25
    $state = $before.state
    if ($scope -eq 'DAILY') {
        $state.weeklyPoints=[Math]::Max(0,[int]$state.weeklyPoints-[int]$state.dailyPoints)
        Reset-ActivityDailyState $state $before.period.dayKey
    } elseif ($scope -eq 'WEEKLY') {
        Reset-ActivityDailyState $state $before.period.dayKey
        Reset-ActivityWeeklyState $state $before.period.weekKey
    } else {
        $sourceKey = ([string](Get-ActivityProperty $request.payload 'sourceKey' '')).Trim().ToUpperInvariant()
        if ($sourceKey -notmatch '^[A-Z][A-Z0-9_]{2,63}$' -or $null -eq (Get-ActivityProperty $state.dailyCounters $sourceKey $null)) { throw "sourceKey không tồn tại trong dailyCounters hiện tại." }
        $state.dailyCounters.Remove($sourceKey)
        $state.dailyUnique.Remove($sourceKey)
    }
    $state.lastAwardAt=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $saved = Save-ActivityPlayerState $record $state $request.expectedVersion
    Write-ActivityAdminAudit 'resetactivityplayer' $request.reason $runtime.revision $runtime.versionNo $record.id $beforeJson $saved.stateJson 'SUCCESS'
    [ordered]@{ status='ok'; playerId=$record.id; scope=$scope; version=(Get-ActivityPlayerVersion ([pscustomobject]@{dataPoint=$saved.pointJson;dataActivity=$saved.stateJson})); state=$state } | ConvertTo-Json -Depth 25
}

function Set-ActivityClaim {
    $request = Read-ActivityPlayerMutationPayload
    $period = ([string](Get-ActivityProperty $request.payload 'period' '')).Trim().ToUpperInvariant()
    if ($period -notin @('DAILY','WEEKLY')) { throw "period chỉ nhận DAILY hoặc WEEKLY." }
    $tierId = ([string](Get-ActivityProperty $request.payload 'tierId' '')).Trim().ToUpperInvariant()
    if ($tierId -notmatch '^[A-Z][A-Z0-9_]{2,63}$') { throw "tierId không hợp lệ." }
    $claimed = ConvertTo-ActivityBool (Get-ActivityProperty $request.payload 'claimed' $null) 'claimed'
    Test-ActivityConfirmation $request.payload 'SET_ACTIVITY_CLAIM'
    $runtime = Get-ActivityRuntimeSnapshot
    $config = Normalize-ActivityConfig (ConvertFrom-ActivityConfigJson $runtime.configJson)
    $tier = Get-ActivityConfigTier $config $period $tierId
    $record = Get-ActivityPlayerRecord $request.playerId
    Assert-ActivityPlayerOffline $record
    $before = Get-ActivityPlayerView $record $config
    if ($before.version -ne $request.expectedVersion) { throw "Dữ liệu player đã đổi; hãy tải lại trước khi ghi." }
    $beforeJson = $before | ConvertTo-Json -Compress -Depth 25
    $periodKey = if ($period -eq 'DAILY') { $before.period.dayKey } else { $before.period.weekKey }
    if (-not $claimed) {
        $auditExists = [int](Get-MySqlScalar "SELECT COUNT(*) FROM activity_claim_audit WHERE player_id=$($record.id) AND period_type=$(SqlString $period) AND period_key=$(SqlString $periodKey) AND tier_id=$(SqlString $tierId) AND result_code IN ('PENDING','SUCCESS');" '0')
        if ($auditExists -gt 0) { throw "Không thể bỏ trạng thái đã nhận: activity_claim_audit đã có lịch sử claim $period/$tierId." }
    }
    $flag = [int64]1 -shl [int]$tier.claimBit
    if ($period -eq 'DAILY') {
        $mask=[int64]$before.state.dailyClaimMask
        $before.state.dailyClaimMask = if ($claimed) { $mask -bor $flag } else { $mask -band (-bnot $flag) }
    } else {
        $mask=[int64]$before.state.weeklyClaimMask
        $before.state.weeklyClaimMask = if ($claimed) { $mask -bor $flag } else { $mask -band (-bnot $flag) }
    }
    $saved = Save-ActivityPlayerState $record $before.state $request.expectedVersion
    Write-ActivityAdminAudit 'setactivityclaim' $request.reason $runtime.revision $runtime.versionNo $record.id $beforeJson $saved.stateJson 'SUCCESS'
    [ordered]@{ status='ok'; playerId=$record.id; period=$period; tierId=$tierId; claimed=$claimed; version=(Get-ActivityPlayerVersion ([pscustomobject]@{dataPoint=$saved.pointJson;dataActivity=$saved.stateJson})); state=$before.state } | ConvertTo-Json -Depth 25
}

function List-ActivityLogs {
    Ensure-ActivityAdminSchema
    $limit = Get-ActivityInteger $(if ([string]::IsNullOrWhiteSpace($PageSize)) { 100 } else { $PageSize }) 'PageSize' 1 500
    $playerId = if ($Id -match '^\d+$') { [long]$Id } else { 0L }
    $adminWhere = if ($playerId -gt 0) { "WHERE player_id=$playerId" } else { '' }
    $claimWhere = if ($playerId -gt 0) { "WHERE player_id=$playerId" } else { '' }
    $adminRows = New-Object System.Collections.Generic.List[object]
    $rawAdmin = Invoke-MySql "SELECT id,action_name,COALESCE(player_id,0),COALESCE(config_version_no,0),actor_note,result_code,DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s') FROM activity_admin_audit $adminWhere ORDER BY id DESC LIMIT $limit;"
    foreach ($line in @($rawAdmin -split "`r?`n" | Select-Object -Skip 1)) { $p=@($line -split "`t",7); if ($p.Count -eq 7) { $adminRows.Add([ordered]@{ id=[long]$p[0]; action=$p[1]; playerId=[long]$p[2]; configVersionNo=[long]$p[3]; reason=$p[4]; result=$p[5]; createdAt=$p[6] }) } }
    $claimRows = New-Object System.Collections.Generic.List[object]
    $rawClaims = Invoke-MySql "SELECT claim_id,player_id,period_type,period_key,tier_id,result_code,COALESCE(reward_json,''),DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s') FROM activity_claim_audit $claimWhere ORDER BY claim_id DESC LIMIT $limit;"
    foreach ($line in @($rawClaims -split "`r?`n" | Select-Object -Skip 1)) { $p=@($line -split "`t",8); if ($p.Count -eq 8) { $claimRows.Add([ordered]@{ id=[long]$p[0]; playerId=[long]$p[1]; period=$p[2]; periodKey=$p[3]; tierId=$p[4]; status=$p[5]; rewardJson=$p[6]; createdAt=$p[7] }) } }
    [ordered]@{ status='ok'; admin=$adminRows.ToArray(); claims=$claimRows.ToArray() } | ConvertTo-Json -Depth 15
}
