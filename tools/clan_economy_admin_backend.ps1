# Read-only phase 5E backend. Dot-sourced by admin_data.ps1 and by pure tests.

function Get-ClanEconomyPropertyMap {
    param([string]$Path)
    $result = @{}
    if (-not (Test-Path -LiteralPath $Path)) { return $result }
    foreach ($line in [System.IO.File]::ReadAllLines($Path, [System.Text.Encoding]::UTF8)) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#") -or $trimmed.IndexOf("=") -lt 1) { continue }
        $separator = $trimmed.IndexOf("=")
        $key = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()
        $result[$key] = $value
    }
    $result
}

function Get-ClanEconomyConfigInteger {
    param(
        [hashtable]$Values,
        [string]$Key,
        [int]$Default,
        [int]$Minimum,
        [int]$Maximum
    )
    $parsed = 0
    if ($Values.ContainsKey($Key) -and [int]::TryParse([string]$Values[$Key], [ref]$parsed)) {
        return [Math]::Max($Minimum, [Math]::Min($Maximum, $parsed))
    }
    [Math]::Max($Minimum, [Math]::Min($Maximum, $Default))
}

function Assert-ClanEconomyLookbackDays {
    param([string]$Value, [int]$Maximum = 90)
    if ($Maximum -lt 1 -or $Maximum -gt 365) { $Maximum = 90 }
    if ($Value -notmatch '^[0-9]{1,3}$') {
        throw "Lookback must be an integer number of days."
    }
    $days = [int]$Value
    if ($days -lt 1 -or $days -gt $Maximum) {
        throw "Lookback must be between 1 and $Maximum days."
    }
    $days
}

function Get-ClanEconomyBalanceAssessment {
    param(
        [long]$Inflow,
        [long]$Outflow,
        [int]$MinimumPercent,
        [int]$MaximumPercent
    )
    $safeInflow = [Math]::Max(0L, $Inflow)
    $safeOutflow = [Math]::Max(0L, $Outflow)
    if ($safeInflow -eq 0L -and $safeOutflow -eq 0L) {
        return [pscustomobject]@{ Status = "NO_FLOW"; RatioPercent = 0; Message = "No transactions" }
    }
    if ($safeInflow -eq 0L) {
        return [pscustomobject]@{ Status = "DEFICIT_RISK"; RatioPercent = 9999; Message = "Outflow without inflow" }
    }
    $ratio = [Math]::Min(9999, [int][Math]::Round(($safeOutflow * 100.0) / $safeInflow))
    if ($ratio -lt $MinimumPercent) {
        return [pscustomobject]@{ Status = "SURPLUS_RISK"; RatioPercent = $ratio; Message = "Inflow exceeds sink threshold" }
    }
    if ($ratio -gt $MaximumPercent) {
        return [pscustomobject]@{ Status = "DEFICIT_RISK"; RatioPercent = $ratio; Message = "Sink exceeds inflow threshold" }
    }
    [pscustomobject]@{ Status = "BALANCED"; RatioPercent = $ratio; Message = "Within configured threshold" }
}

function ConvertFrom-ClanEconomyTsv {
    param([string]$Text)
    $lines = @($Text -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($lines.Count -lt 2) { return @() }
    @($lines | ConvertFrom-Csv -Delimiter "`t")
}

function ConvertTo-ClanEconomyTsvCell {
    param($Value)
    if ($null -eq $Value) { return "" }
    ([string]$Value) -replace '[\x00-\x1F\x7F]', ' '
}

function Add-ClanEconomyReportRow {
    param(
        [System.Text.StringBuilder]$Builder,
        [string]$Section,
        [string]$Key,
        [string]$Label,
        $Value,
        $Secondary,
        [string]$Status,
        [string]$Note
    )
    $cells = @($Section, $Key, $Label, $Value, $Secondary, $Status, $Note)
    for ($i = 0; $i -lt $cells.Count; $i++) { $cells[$i] = ConvertTo-ClanEconomyTsvCell $cells[$i] }
    [void]$Builder.AppendLine(($cells -join "`t"))
}

function Get-ClanEconomyCurrencyLabel {
    param([string]$Currency)
    switch ($Currency) {
        "0" { "Clan gold" }
        "1" { "Clan gem" }
        "2" { "Capsule bang" }
        default { "Currency $Currency" }
    }
}

function Get-ClanEconomySignalLabel {
    param([string]$Signal)
    $labels = @{
        TREE_WATER = "Tree water"; TREE_FERTILIZE = "Tree fertilizer";
        TREE_VITALITY_REACHED = "Daily vitality reached"; TREE_HARVEST = "Tree harvest";
        TREE_LEVEL_UP = "Tree level up"; CLAN_LEVEL_UP = "Clan level up";
        GIFT_SENT = "Gift sent"; GIFT_BLOCKED_POLICY = "Gift blocked by policy";
        PENDING_GIFT_DELIVERED = "Pending gift delivered"; DUPLICATE_REQUEST = "Duplicate request";
        TRANSACTION_ROLLBACK = "Transaction rollback"; TERRITORY_CREATED = "Territory created";
        TERRITORY_DISPOSED = "Territory disposed"
    }
    if ($labels.ContainsKey($Signal)) { return $labels[$Signal] }
    $Signal
}

function Get-ClanEconomyReport {
    param([string]$LookbackDays, [string]$ConfigPath)
    $config = Get-ClanEconomyPropertyMap -Path $ConfigPath
    $maximum = Get-ClanEconomyConfigInteger $config "lookback_max_days" 90 7 365
    $defaultDays = Get-ClanEconomyConfigInteger $config "lookback_default_days" 14 1 $maximum
    $daysText = if ([string]::IsNullOrWhiteSpace($LookbackDays)) { [string]$defaultDays } else { $LookbackDays }
    $days = Assert-ClanEconomyLookbackDays -Value $daysText -Maximum $maximum
    $offsetDays = $days - 1

    $builder = New-Object System.Text.StringBuilder
    [void]$builder.AppendLine("section`tkey`tlabel`tvalue`tsecondary`tstatus`tnote")
    Add-ClanEconomyReportRow $builder "META" "generated_at" "Generated at" (Get-Date -Format "yyyy-MM-dd HH:mm:ss") "" "OK" ""
    Add-ClanEconomyReportRow $builder "META" "lookback_days" "Window" $days "days" "OK" "Vietnam day boundary"

    $summarySql = @"
SELECT COUNT(*) AS total_clans,
COALESCE(SUM(clan_gold),0) AS current_gold,
COALESCE(SUM(clan_gem),0) AS current_gem,
COALESCE(SUM(clan_point),0) AS current_capsule,
COALESCE(ROUND(AVG(clan_value)),0) AS average_value,
COALESCE(MAX(clan_value),0) AS maximum_value,
COALESCE(ROUND(AVG(level),2),0) AS average_level
FROM clan;
"@
    $summary = @(ConvertFrom-ClanEconomyTsv (Invoke-MySql $summarySql))
    if ($summary.Count -gt 0) {
        $row = $summary[0]
        Add-ClanEconomyReportRow $builder "SUMMARY" "total_clans" "Total clans" $row.total_clans "clans" "OK" ""
        Add-ClanEconomyReportRow $builder "SUMMARY" "current_gold" "Current gold" $row.current_gold "gold" "OK" ""
        Add-ClanEconomyReportRow $builder "SUMMARY" "current_gem" "Current gem" $row.current_gem "gem" "OK" ""
        Add-ClanEconomyReportRow $builder "SUMMARY" "current_capsule" "Current capsule" $row.current_capsule "capsule" "OK" ""
        Add-ClanEconomyReportRow $builder "SUMMARY" "average_value" "Average Clan Value" $row.average_value "points" "OK" "Maximum: $($row.maximum_value)"
        Add-ClanEconomyReportRow $builder "SUMMARY" "average_level" "Average clan level" $row.average_level "level" "OK" ""
    }

    $activeSql = "SELECT COUNT(DISTINCT clan_id) AS active_clans, COUNT(*) AS active_clan_days FROM clan_economy_active_clan WHERE metric_day>=DATE_SUB(CURRENT_DATE, INTERVAL $offsetDays DAY);"
    $active = @(ConvertFrom-ClanEconomyTsv (Invoke-MySql $activeSql))
    if ($active.Count -gt 0) {
        Add-ClanEconomyReportRow $builder "SUMMARY" "active_clans" "Active clans" $active[0].active_clans "clans" "OK" "Within $days days"
        Add-ClanEconomyReportRow $builder "SUMMARY" "active_clan_days" "Active clan-days" $active[0].active_clan_days "clan-days" "OK" "Denominator for tree care"
    }

    $flowSql = "SELECT currency_type,COALESCE(SUM(CASE WHEN amount>0 THEN amount ELSE 0 END),0) AS inflow,COALESCE(SUM(CASE WHEN amount<0 THEN -amount ELSE 0 END),0) AS outflow,COUNT(*) AS transactions FROM clan_ledger WHERE created_at>=DATE_SUB(CURRENT_DATE, INTERVAL $offsetDays DAY) GROUP BY currency_type ORDER BY currency_type;"
    $flowRows = @(ConvertFrom-ClanEconomyTsv (Invoke-MySql $flowSql))
    foreach ($row in $flowRows) {
        $currency = [string]$row.currency_type
        $minimum = switch ($currency) {
            "0" { Get-ClanEconomyConfigInteger $config "gold_sink_ratio_min_percent" 70 0 1000 }
            "1" { Get-ClanEconomyConfigInteger $config "gem_sink_ratio_min_percent" 50 0 1000 }
            default { Get-ClanEconomyConfigInteger $config "capsule_sink_ratio_min_percent" 70 0 1000 }
        }
        $maximumRatio = switch ($currency) {
            "0" { Get-ClanEconomyConfigInteger $config "gold_sink_ratio_max_percent" 110 $minimum 1000 }
            "1" { Get-ClanEconomyConfigInteger $config "gem_sink_ratio_max_percent" 150 $minimum 1000 }
            default { Get-ClanEconomyConfigInteger $config "capsule_sink_ratio_max_percent" 130 $minimum 1000 }
        }
        $assessment = Get-ClanEconomyBalanceAssessment -Inflow ([long]$row.inflow) -Outflow ([long]$row.outflow) -MinimumPercent $minimum -MaximumPercent $maximumRatio
        Add-ClanEconomyReportRow $builder "FLOW" $currency (Get-ClanEconomyCurrencyLabel $currency) $row.inflow $row.outflow $assessment.Status ("Sink/source {0}% | {1} | {2} transactions" -f $assessment.RatioPercent, $assessment.Message, $row.transactions)
    }
    foreach ($currency in @("0", "1", "2")) {
        if (-not ($flowRows | Where-Object { [string]$_.currency_type -eq $currency })) {
            Add-ClanEconomyReportRow $builder "FLOW" $currency (Get-ClanEconomyCurrencyLabel $currency) 0 0 "NO_FLOW" "No transactions in window"
        }
    }

    $metricSql = "SELECT signal_key,SUM(event_count) AS event_count,SUM(amount_total) AS amount_total FROM clan_economy_metric WHERE metric_day>=DATE_SUB(CURRENT_DATE, INTERVAL $offsetDays DAY) GROUP BY signal_key ORDER BY signal_key;"
    foreach ($row in @(ConvertFrom-ClanEconomyTsv (Invoke-MySql $metricSql))) {
        $note = if ($row.signal_key -eq "TREE_LEVEL_UP") { "amount = total tree upgrade milliseconds" } elseif ($row.signal_key -eq "TERRITORY_DISPOSED") { "amount = total territory lifetime milliseconds" } else { "" }
        Add-ClanEconomyReportRow $builder "SIGNAL" $row.signal_key (Get-ClanEconomySignalLabel $row.signal_key) $row.event_count $row.amount_total "OK" $note
    }

    $actionSql = "SELECT currency_type,action_type,SUM(CASE WHEN amount>0 THEN amount ELSE 0 END) AS inflow,SUM(CASE WHEN amount<0 THEN -amount ELSE 0 END) AS outflow,COUNT(*) AS transactions FROM clan_ledger WHERE created_at>=DATE_SUB(CURRENT_DATE, INTERVAL $offsetDays DAY) GROUP BY currency_type,action_type ORDER BY currency_type,SUM(CASE WHEN amount>=0 THEN amount ELSE -amount END) DESC,action_type LIMIT 60;"
    foreach ($row in @(ConvertFrom-ClanEconomyTsv (Invoke-MySql $actionSql))) {
        Add-ClanEconomyReportRow $builder "ACTION" ("$($row.currency_type):$($row.action_type)") $row.action_type $row.inflow $row.outflow "OK" ((Get-ClanEconomyCurrencyLabel $row.currency_type) + " | $($row.transactions) transactions")
    }

    $potentialSql = "SELECT branch_key,COUNT(*) AS clans_with_rank,SUM(rank_value) AS total_rank,ROUND(AVG(rank_value),2) AS average_rank,MAX(rank_value) AS maximum_rank FROM clan_potential WHERE rank_value>0 GROUP BY branch_key ORDER BY branch_key;"
    foreach ($row in @(ConvertFrom-ClanEconomyTsv (Invoke-MySql $potentialSql))) {
        Add-ClanEconomyReportRow $builder "POTENTIAL" $row.branch_key $row.branch_key $row.total_rank $row.clans_with_rank "OK" ("Average rank $($row.average_rank) | maximum $($row.maximum_rank)")
    }

    $pendingSql = "SELECT COUNT(*) AS pending_count,COALESCE(SUM(gold_amount),0) AS pending_gold,COALESCE(SUM(ruby_amount),0) AS pending_ruby FROM clan_pending_reward WHERE status=0;"
    $pending = @(ConvertFrom-ClanEconomyTsv (Invoke-MySql $pendingSql))
    if ($pending.Count -gt 0) {
        $pendingStatus = if ([long]$pending[0].pending_count -gt 0) { "WARNING" } else { "OK" }
        Add-ClanEconomyReportRow $builder "SUMMARY" "pending_rewards" "Pending rewards" $pending[0].pending_count "rewards" $pendingStatus ("Gold $($pending[0].pending_gold) | bound gem $($pending[0].pending_ruby)")
    }
    $builder.ToString().TrimEnd()
}
