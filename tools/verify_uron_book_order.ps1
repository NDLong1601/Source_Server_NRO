$ErrorActionPreference='Stop'
# Regression check for the actual shared query + the gender filter used by
# TabShopUron / TabShopHocKynang. In particular shield books are gender=3.
$rows=@(& mysql --protocol=tcp -h 127.0.0.1 -P 3307 -u root -N -B team2026 -e 'SELECT s.tab_id,s.temp_id,t.gender FROM item_shop s JOIN item_template t ON t.id=s.temp_id WHERE s.tab_id IN(10,11,12) AND s.is_sell=1 ORDER BY s.tab_id,s.create_time DESC,s.id ASC;')
if($LASTEXITCODE -ne 0){throw 'Cannot read the shop order.'}
$items=@($rows | ForEach-Object { $f=$_ -split "`t"; [pscustomobject]@{Tab=[int]$f[0];Id=[int]$f[1];Gender=[int]$f[2]} })
$expected=@{
    '10/0'=@(67..72)+@(300..306)+@(488..494)
    '10/1'=@(79..84)+@(86)+@(481..487)
    '10/2'=@(87..93)
    '11/0'=@(94..100)+@(495..501)+@(2219..2225)
    '11/1'=@(101..107)+@(328..334)+@(474..480)+@(2226..2232)
    '11/2'=@(108..114)+@(321..327)+@(502..508)
    '12/0'=@(115..121)+@(307..313)+@(2233..2239)+@(434..440)
    '12/1'=@(122..128)+@(335..341)+@(434..440)
    '12/2'=@(129..135)+@(314..320)+@(509..515)+@(2240..2246)+@(434..440)
}
foreach($tab in 10..12){
    foreach($gender in 0..2){
        $actual=@($items | Where-Object { $_.Tab -eq $tab -and ($_.Gender -eq $gender -or $_.Gender -eq 3) } | ForEach-Object Id)
        $key="$tab/$gender"
        if(($actual -join ',') -ne ($expected[$key] -join ',')){throw "Wrong order for tab/gender ${key}: $($actual -join ',')"}
        "PASS tab=$tab gender=$gender count=$($actual.Count) last=$($actual[-1])"
    }
}
