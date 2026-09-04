# Tài liệu thiết kế Cây chung và hệ thống phát triển bang hội

> Trạng thái: Bản thiết kế trước triển khai  
> Phiên bản: 1.0  
> Phạm vi: Server Java tại C:/Users/PC/Music/Teamobi2026/SRC và client Unity tại C:/Users/PC/Music/PRJ_2Tab_550K  
> Mục tiêu: Làm cơ sở thống nhất gameplay, dữ liệu, giao thức và tiêu chí kiểm thử trước khi sửa mã nguồn.

## Mục lục nhanh

- [1–5. Định hướng, hiện trạng và vòng lặp gameplay](#1-tóm-tắt-quyết-định-thiết-kế)
- [6. Lãnh địa bang tại map 153](#6-lãnh-địa-bang-tại-map-153)
- [7. Cây chung bang hội](#7-chức-năng-cây-chung-bang-hội)
- [8. Cấp bang không giới hạn nội dung](#8-cấp-bang-không-giới-hạn-nội-dung)
- [9. Điểm tiềm năng bang](#9-điểm-tiềm-năng-bang)
- [10. Kho và tiền tệ bang](#10-kho-bang-và-tiền-tệ-bang)
- [11. Tặng quà thành viên](#11-tặng-quà-thành-viên)
- [12. Giá trị và xếp hạng bang](#12-giá-trị-và-xếp-hạng-bang)
- [13–14. Giao diện và giao thức client–server](#13-giao-diện-client)
- [15–18. Dữ liệu, transaction, reset và bảo mật](#15-mô-hình-dữ-liệu-đề-xuất)
- [19–20. Tình huống biên và cấu hình](#19-tình-huống-biên)
- [21–22. Kế hoạch mã nguồn và lộ trình triển khai](#21-kế-hoạch-mã-nguồn)
- [23–24. Kiểm thử và tiêu chí nghiệm thu](#23-kế-hoạch-kiểm-thử)
- [25–27. Theo dõi, giá trị cần chốt và kết luận](#25-chỉ-số-theo-dõi-sau-phát-hành)

## 1. Tóm tắt quyết định thiết kế

Phiên bản đầu tiên sử dụng các quyết định mặc định sau:

1. Mỗi bang chỉ có **một Cây chung của bang hội**, không tạo cây vật lý riêng cho từng thành viên.
2. Map 153 tiếp tục được dùng làm mẫu lãnh địa, nhưng mỗi bang được đưa vào **một khu động riêng**. Thành viên bang khác không thể vào hoặc nhìn thấy cây của nhau.
3. Khu bang chỉ tồn tại trong bộ nhớ khi có người sử dụng; trạng thái cây được lưu trong cơ sở dữ liệu nên không phụ thuộc khu có đang mở hay không.
4. Cây không thả vàng hoặc vật phẩm xuống mặt đất. Tất cả phần thưởng được cộng vào kho chờ và nhận bằng nút để tránh tạo nhiều vật thể và làm chậm server.
5. Tưới trực tiếp tại cây và tưới từ tab/chat bang là cùng một hành động. Mỗi lần hợp lệ đều tiêu thụ một Bình nước và dùng chung giới hạn hằng ngày.
6. Mỗi thành viên được tưới tối đa 5 lần mỗi ngày ở cấu hình mặc định.
7. Cây không chết và không tụt cấp. Thiếu chăm sóc chỉ làm giảm sản lượng ngày, tránh biến hoạt động bang thành nghĩa vụ gây khó chịu.
8. Cấp bang không còn giới hạn nội dung ở cấp 15. Hệ thống vẫn có trần kỹ thuật để phòng tràn số và có thể mở rộng bằng cấu hình.
9. Mỗi cấp bang cấp 5 Điểm tiềm năng bang. Chỉ số chiến đấu có trần; cấp cao tiếp tục có giá trị thông qua tiện ích, cửa hàng, cây bang, kho và ngoại hình.
10. Kho bang là kho tiền tệ và sổ cái giao dịch trong phiên bản đầu. Chưa triển khai gửi/rút vật phẩm dùng chung.
11. Tiền nạp vào quỹ bang là một chiều, không được rút lại thành tài sản cá nhân.
12. Quà thành viên phải tiêu thụ Phiếu quà bang và chịu giới hạn ngày. Quà ngọc nên là ngọc khóa.
13. Mọi thay đổi tài sản, điểm tiềm năng và phần thưởng đều do server quyết định, chạy trong giao dịch dữ liệu và có lịch sử kiểm tra.

## 2. Mục tiêu sản phẩm

### 2.1. Mục tiêu chính

- Tạo lý do để thành viên đăng nhập và hoạt động cùng bang mỗi ngày.
- Biến cấp bang thành tiến trình dài hạn có giá trị thay vì dừng ở cấp thấp.
- Gắn nhiệm vụ bang, săn quái, boss bang, cây bang, kho và cửa hàng vào một vòng lặp thống nhất.
- Cho bang chủ có quyền xây dựng hướng phát triển của bang nhưng không thể tùy ý lấy tài sản thành viên.
- Tạo cảm giác đóng góp cá nhân có ý nghĩa và được ghi nhận minh bạch.
- Hạn chế chênh lệch sức mạnh quá lớn giữa bang cũ và bang mới.
- Không tạo lượng lớn vật phẩm rơi vật lý trên map.

### 2.2. Ngoài phạm vi phiên bản đầu

- Cây riêng vật lý cho từng thành viên.
- Gửi trang bị hoặc vật phẩm tự do vào kho dùng chung.
- Rút vàng/ngọc từ quỹ bang về nhân vật.
- Giao dịch trực tiếp vàng/ngọc giữa hai thành viên thông qua nút Tặng quà.
- Bang chiến, chiếm lãnh thổ hoặc phá cây của bang khác.
- Cây chết, tụt cấp hoặc mất tài sản do không chăm sóc.

## 3. Thuật ngữ

| Thuật ngữ | Ý nghĩa |
|---|---|
| Cấp bang | Cấp tiến trình tổng thể của bang |
| Clan EXP | Kinh nghiệm dùng để đủ điều kiện tăng cấp bang |
| Điểm tiềm năng bang | Điểm bang chủ phân bổ vào các nhánh chỉ số hoặc tiện ích |
| Cây bang | Cây chung duy nhất thuộc về một bang |
| Sinh trưởng | Tiến độ riêng dùng để tăng cấp cây |
| Sức sống ngày | Mức chăm sóc trong ngày, ảnh hưởng hệ số sản lượng nhưng không làm tụt cấp |
| Quỹ bang | Capsule Bang, Vàng bang và Ngọc bang thuộc sở hữu bang |
| Điểm cống hiến | Chỉ số cá nhân ghi nhận hoạt động của thành viên |
| Kho chờ | Giá trị sản xuất đã hình thành nhưng chưa quyết toán vào quỹ hoặc chưa được cá nhân nhận |
| Khu bang | Zone động của map 153 chỉ cho một bang truy cập |
| Phiếu quà bang | Vật phẩm/quyền gửi quà có giới hạn, kiếm từ hoạt động bang |

## 4. Hiện trạng kỹ thuật cần tương thích

Trước khi triển khai cần lưu ý các đặc điểm hiện có:

- Cấp bang hiện bị chặn khi lớn hơn 10 trong DrDrief, nên cấp đạt được thực tế khoảng 11.
- Giá Capsule nâng cấp hiện được hard-code theo cấp 1 đến 11 trong ClanService.
- Cột LEVEL trong bảng clan là INT, nhưng server đang đọc bằng getByte và gửi sang client bằng writeByte.
- Game1 và Game2 đều đang đọc cấp bang bằng readByte.
- maxMember trong model server đang là byte và hiện được tăng một đơn vị theo mỗi lần tăng cấp.
- Bang đã có capsuleClan, điểm Capsule cá nhân của thành viên và cơ chế bang chủ/phó tiêu quỹ.
- Client đã có luồng chat xin/cho đậu; có thể tham khảo cách hiển thị nút hành động nhưng cây bang phải dùng loại thông điệp và xác thực riêng.
- Client có cờ isBoxClan cũ, nhưng phiên bản Kho bang trong tài liệu này là kho tiền tệ/sổ cái, không phụ thuộc cơ chế rương vật phẩm cũ.

Hệ quả bắt buộc:

- Không được chỉ xóa điều kiện giới hạn cấp.
- Phải mở rộng kiểu dữ liệu cấp trên server, giao thức và cả hai nhánh client.
- Phải tách tăng số thành viên khỏi tăng cấp thường.
- Phải thay bảng giá hard-code bằng cấu hình hoặc công thức có kiểm soát tràn số.

## 5. Vòng lặp gameplay tổng thể

~~~text
Thành viên làm nhiệm vụ/săn quái/boss
        |
        v
Nhận Bình nước, Phân bón, Capsule và Phiếu quà
        |
        v
Chăm Cây bang tại map 153 hoặc từ tab/chat
        |
        +--> Điểm cống hiến cá nhân
        +--> Sinh trưởng và sản lượng cây
        +--> Clan EXP và tài nguyên quỹ
        |
        v
Tăng cấp cây và cấp bang
        |
        +--> 5 Điểm tiềm năng mỗi cấp bang
        +--> Mở tầng cửa hàng/tiện ích/mốc thành viên
        +--> Tăng phần thưởng cây có giới hạn
        |
        v
Bang chủ phân bổ tiềm năng và đầu tư quỹ
        |
        v
Thành viên nhận lợi ích và tiếp tục hoạt động
~~~

Tiền nạp không được là con đường duy nhất hoặc nhanh nhất để lên cấp. Clan EXP kiếm từ hoạt động là điều kiện bắt buộc; quỹ chỉ là chi phí hoàn tất nâng cấp.

## 6. Lãnh địa bang tại map 153

### 6.1. Mô hình khu động

Map 153 là map chung về dữ liệu nền nhưng không dùng chung một Zone cho mọi bang.

Server quản lý khu theo khóa:

~~~text
(mapId = 153, clanId)
~~~

Luồng vào khu:

1. Người chơi yêu cầu vào lãnh địa bang.
2. Server kiểm tra người chơi đang có bang và tư cách thành viên còn hợp lệ.
3. Server tìm khu đang hoạt động của clanId.
4. Nếu chưa tồn tại, server tạo một Zone động từ template map 153 và nạp trạng thái Cây bang.
5. Người chơi được chuyển vào đúng Zone của bang mình.
6. Khi Zone không còn người, server giữ chờ một khoảng cấu hình, sau đó hủy Zone khỏi bộ nhớ.
7. Hủy Zone không xóa cây, tài nguyên hoặc tiến độ vì dữ liệu đã được lưu riêng.

### 6.2. Quy tắc truy cập

- Chỉ thành viên hiện tại của bang được vào.
- Không cho chọn khu của bang khác qua chức năng Đổi khu.
- Nếu người chơi rời bang, bị kick hoặc bang giải tán trong lúc đang ở map 153, server đưa người chơi về map an toàn.
- Không dùng zoneId công khai làm quyền truy cập; mọi lần vào đều kiểm tra clanId ở server.
- Khi chuyển bang, người chơi không được giữ tham chiếu tới khu bang cũ.
- Một bang chỉ có tối đa một khu map 153 hoạt động tại một thời điểm.

### 6.3. Cây vật lý trong khu

Mỗi khu có đúng một đối tượng cây:

~~~text
Cây Bang <Tên bang> - Cấp <cấp cây>
~~~

Cây có sprite theo giai đoạn phát triển. Đối tượng hiển thị chỉ là đại diện; dữ liệu thật nằm ở ClanTree phía server.

Không nên tái sử dụng trực tiếp MagicTree của cây đậu cá nhân vì:

- MagicTree gắn với trạng thái từng Player.
- Cây bang thuộc Clan và cần đồng bộ cho nhiều người.
- Dùng chung packet dễ làm sai cây đậu hiện có.

Nên tạo packet và model hiển thị Cây bang riêng, dù có thể tái sử dụng sprite hoặc cách vẽ.

## 7. Chức năng Cây chung bang hội

### 7.1. Trạng thái cây

| Trạng thái | Ý nghĩa |
|---|---|
| GROWING | Đang tích lũy sinh trưởng |
| NEED_CARE | Chưa đạt nhu cầu nước/phân của ngày hoặc cấp |
| READY_UPGRADE | Đã đủ điều kiện tăng cấp nhưng đang chờ đột phá/chi phí |
| MAX_CONTENT | Đã đạt cấp nội dung hiện tại, vẫn sản xuất và tích cống hiến |
| DISABLED | Tạm khóa bởi cấu hình/bảo trì |

Cây không có trạng thái DEAD. Cây không bị mất cấp và không mất tài nguyên đã tích lũy vì vắng người.

### 7.2. Tài nguyên liên quan

#### Bình nước

- Rơi từ quái ở các map được chỉ định trong cấu hình.
- Chỉ tính khi người chơi đủ điều kiện nhận drop theo luật chống auto/farm hiện có.
- Có giới hạn số Bình nước kiếm mỗi ngày nếu cần cân bằng.
- Là vật phẩm có số lượng, tiêu thụ một bình cho mỗi lần tưới.
- Không tạo nước miễn phí khi bấm giúp qua chat hoặc tab bang.

#### Phân bón

- Hiếm hơn Bình nước.
- Nguồn chính: nhiệm vụ bang, boss bang, cửa hàng bang hoặc mốc hoạt động tuần.
- Có thể nộp trực tiếp vào kho vật liệu cây.
- Nên là vật phẩm khóa hoặc tài nguyên bang để hạn chế giao dịch clone.

#### Điểm sinh trưởng

- Là số liệu server, không phải vật phẩm.
- Tưới nước, bón phân và hoàn thành mục tiêu bang tạo Điểm sinh trưởng.
- Điểm không bị giảm theo ngày.
- Mỗi cấp cây có ngưỡng sinh trưởng riêng.

#### Sức sống ngày

- Được làm mới theo ngày.
- Phản ánh số lượt chăm hợp lệ của các thành viên duy nhất.
- Chỉ ảnh hưởng hệ số sản lượng của ngày.
- Không làm cây tụt cấp.

### 7.3. Hành động Tưới cây

Người chơi có thể tưới theo hai cách:

1. Đến Cây bang trong lãnh địa và chọn Tưới cây.
2. Mở Bang hội > Cây bang hoặc bấm Giúp tưới trong chat.

Cả hai luồng gọi cùng một hàm server và cùng điều kiện:

- Người chơi còn trong đúng bang.
- Cây đang hoạt động.
- Còn lượt tưới trong ngày.
- Có ít nhất một Bình nước hợp lệ.
- Yêu cầu chưa được xử lý trước đó.
- Túi và dữ liệu người chơi đang ở trạng thái cho phép thay đổi vật phẩm.

Một lần tưới mặc định:

- Trừ 1 Bình nước.
- Cộng 1 đơn vị nước.
- Cộng Điểm sinh trưởng theo cấu hình.
- Cộng Điểm cống hiến cho người tưới.
- Tăng Sức sống ngày.
- Ghi log tổng hợp.
- Cập nhật giao diện cây cho thành viên đang xem.

Giới hạn mặc định:

| Hạn mức | Giá trị đề xuất |
|---|---:|
| Số lần tưới mỗi thành viên/ngày | 5 |
| Khoảng cách tối thiểu giữa hai request | 500 ms |
| Bình nước kiếm từ drop/ngày | Cấu hình sau khi đo kinh tế |
| Điểm sinh trưởng từ một người/ngày | Bị chặn theo số lượt tưới |

### 7.4. Bón phân

Bón phân có thể thực hiện tại cây hoặc tab Cây bang.

Quy tắc đề xuất:

- Mỗi lần tiêu thụ một Phân bón.
- Phân được cộng vào kho vật liệu cây hoặc dùng ngay cho cấp hiện tại.
- Có giới hạn cá nhân/ngày để một tài khoản không chiếm toàn bộ thành tích.
- Phân tạo nhiều Điểm sinh trưởng hơn nước nhưng không thay thế điều kiện nước.
- Phân thừa được giữ cho cấp sau trong giới hạn kho.

### 7.5. Xin giúp đỡ trong chat bang

Người chơi chọn Xin tưới cây để tạo một thông điệp hành động trong chat:

~~~text
<Tên người chơi> đang kêu gọi chăm Cây bang.
[Giúp tưới]
~~~

Quy tắc:

- Đây là loại ClanMessage mới, không dùng chung type xin đậu.
- Mỗi bang chỉ có một yêu cầu tưới đang hoạt động.
- Yêu cầu hết hạn sau 2 giờ theo cấu hình.
- Cooldown tạo yêu cầu là 10 phút theo cấu hình.
- Bấm Giúp tưới không yêu cầu vào map 153.
- Người bấm vẫn phải có Bình nước và còn lượt ngày.
- Nếu cây đã đủ nhu cầu hiện tại, server thông báo và không trừ vật phẩm.
- Sau khi hết hạn hoặc đủ nhu cầu, nút chuyển thành Đã hoàn thành/Đã hết hạn.

### 7.6. Điều kiện tăng cấp cây

Một cấp cây yêu cầu đồng thời:

- Đủ Điểm sinh trưởng.
- Đủ tổng lượng nước yêu cầu.
- Đủ Phân bón yêu cầu.
- Bang đạt cấp tối thiểu.
- Đạt số thành viên hoạt động tối thiểu trong chu kỳ nếu cấu hình bật.
- Hoàn tất chi phí đột phá tại các mốc đặc biệt.

Công thức khởi điểm, chỉ dùng làm mặc định cấu hình:

~~~text
growthRequired(treeLevel) = round(100 × treeLevel^1.40)
waterRequired(treeLevel)  = 20 + 10 × treeLevel
fertilizerRequired        = floor((treeLevel + 1) / 3)
minimumClanLevel          = ceil(treeLevel / 2)
~~~

Các con số phải nằm trong file cấu hình, không hard-code trong NPC.

Luật đột phá:

- Cấp thường tự tăng khi đủ điều kiện.
- Mỗi mốc cây 5, 10, 15, 20... chuyển sang READY_UPGRADE.
- Bang chủ hoặc bang phó được ủy quyền xác nhận đột phá.
- Đột phá có thể tiêu Capsule/Vàng bang và không tiêu Ngọc bang ở quá nhiều mốc.
- Mọi lần đột phá được ghi vào lịch sử và chat bang.

### 7.7. Sản lượng cây

Cây sinh sản lượng theo thời gian nhưng không chạy một timer tạo vật phẩm mỗi giờ.

Khi có thao tác xem/nhận/định kỳ lưu, server tính bù:

~~~text
eligibleHours = min(now - lastProductionAt, productionCapHours)
baseOutput    = hourlyOutput(treeLevel) × eligibleHours
finalOutput   = baseOutput × dailyVitalityMultiplier
~~~

Khuyến nghị:

- productionCapHours mặc định 24 giờ.
- Không tính bù vô hạn sau thời gian server nghỉ dài.
- Dùng timestamp server và phép tính long an toàn.
- Không ghi một dòng ledger mỗi giờ; gộp thành một giao dịch khi quyết toán.

Hệ số Sức sống ngày đề xuất:

| Mức hoàn thành nhu cầu chăm sóc | Hệ số |
|---|---:|
| Dưới 50% | 0,50 |
| 50% đến dưới 100% | 0,75 |
| Đạt 100% | 1,00 |
| Vượt mục tiêu hợp lệ | Tối đa 1,10 nếu bật thưởng vượt |

Cây không tạo thêm sức mạnh chiến đấu trực tiếp. Lợi ích chiến đấu thuộc hệ Tiềm năng bang để dễ cân bằng một nơi.

### 7.8. Phần thưởng chung

Sản lượng chung ưu tiên:

- Vàng bang.
- Capsule Bang.
- Xác suất nhỏ tạo Phân bón hoặc Phiếu quà bang.

Sản lượng được giữ trong Kho chờ của cây. Bất kỳ thành viên đủ điều kiện có thể bấm Thu hoạch cho bang vì thao tác này chỉ chuyển tài sản vào quỹ, không lấy tài sản về cá nhân.

Khi thu hoạch:

- Tính sản lượng còn thiếu tới thời điểm hiện tại.
- Khóa cây và quỹ.
- Cộng tài sản vào quỹ bang.
- Đặt kho chờ về 0.
- Cập nhật lastProductionAt.
- Tạo một dòng ledger.
- Gửi thông báo chat bang dạng tổng hợp.

### 7.9. Phần thưởng cá nhân

Mỗi thành viên có một phần thưởng chăm cây riêng mỗi ngày nếu:

- Đã vào bang ít nhất 72 giờ.
- Có tối thiểu số Điểm cống hiến ngày được cấu hình.
- Chưa nhận phần thưởng cây trong ngày.

Phần thưởng do server random lúc nhận, chủ yếu là vàng:

| Nhóm thưởng | Tỷ lệ khởi điểm |
|---|---:|
| Vàng cá nhân | 85–90% |
| Capsule/tiêu hao khóa | 5–10% |
| 3–5 ngọc khóa | Tối đa 5% |

Giá trị vàng phụ thuộc cấp cây và có trần theo kinh tế server. Không gửi vật phẩm xuống map; phần thưởng được cộng thẳng vào túi hoặc hộp thư chờ nếu túi đầy.

Nếu người chơi rời bang trước khi nhận, phần thưởng chưa nhận hết hiệu lực. Phần đóng góp trước đó vẫn thuộc bang.

## 8. Cấp bang không giới hạn nội dung

### 8.1. Mô hình tăng cấp

Bang muốn tăng từ cấp L lên L + 1 cần:

- Đủ Clan EXP.
- Đủ Capsule Bang.
- Đủ Vàng bang tại mốc yêu cầu.
- Đủ Ngọc bang tại mốc lớn nếu cấu hình dùng.
- Không có một giao dịch nâng cấp khác đang chạy.

Công thức tham khảo:

~~~text
expRequired(L)     = round(baseExp × L^1.55)
capsuleRequired(L) = round(baseCapsule × L^1.35)
~~~

Vàng bang nên xuất hiện từ cấp 5 trở lên. Ngọc bang chỉ nên xuất hiện ở mốc 10 cấp hoặc dùng cho tiện ích đặc biệt, tránh ép nạp.

### 8.2. Nguồn Clan EXP

- Hoàn thành nhiệm vụ bang.
- Tưới/bón cây với giới hạn cá nhân.
- Hoàn thành mục tiêu tuần.
- Boss/phó bản bang.
- Thành tích bang lần đầu.
- Sự kiện bang có cấu hình.

Nạp vàng hoặc ngọc không trực tiếp tạo Clan EXP. Quy tắc này ngăn bang chỉ dùng tài khoản giàu để mua toàn bộ tiến trình.

### 8.3. Trần kỹ thuật

- Cấp bang lưu bằng int.
- Trần kỹ thuật mặc định đề xuất: 1.000.
- Giao diện không quảng bá đây là trần nội dung.
- Công thức chi phí phải dùng long và kiểm tra tràn trước khi ép kiểu.
- Nếu cấp đạt trần kỹ thuật, server từ chối an toàn và ghi log quản trị.

### 8.4. Mốc mở khóa

Không tăng maxMember ở mọi cấp. Dùng mốc:

| Mốc cấp | Ví dụ mở khóa |
|---|---|
| 2 | Cây bang |
| 3 | Kho bang |
| 5 | Tiềm năng bang và +1 slot thành viên |
| 10 | Tầng 2 cửa hàng, +1 slot |
| 15 | Nhánh tiện ích cây |
| 20 | Ngoại hình cây mới, +1 slot |
| Mỗi 10 cấp sau đó | Tiện ích, shop, danh hiệu hoặc slot theo trần |

Số thành viên phải dùng short/int trong model và có giới hạn cấu hình độc lập.

## 9. Điểm tiềm năng bang

### 9.1. Cấp và hoàn điểm

~~~text
totalPotentialPoints = max(0, clanLevel - 1) × 5
unspentPoints         = totalPotentialPoints - sum(costOfAllocatedRanks)
~~~

Bang cũ được backfill theo công thức trên. Không cấp điểm hai lần khi chạy lại migration.

### 9.2. Nhánh đề xuất

| Khóa | Tên hiển thị | Hiệu quả cuối đề xuất | Ảnh hưởng PvP |
|---|---|---:|---:|
| ATTACK | Tấn công | Tối đa +4% | 50% hiệu quả |
| HP | Sinh lực | Tối đa +8% | 50% hiệu quả |
| KI | Khí lực | Tối đa +10% | 50% hiệu quả |
| PVE_REDUCTION | Phòng thủ PvE | Tối đa giảm 2% | Không áp dụng PvP |
| MOB_GOLD | Vàng từ quái thường | Tối đa +5% | Không liên quan |
| TREE_YIELD | Sản lượng cây | Tối đa +10% | Không liên quan |

Không nên đưa chí mạng, né đòn, hút HP hoặc hồi sinh vào phiên bản đầu vì dễ tạo combo mất cân bằng.

### 9.3. Giá bậc

Không dùng một điểm bằng một phần trăm. Giá tăng theo bậc:

~~~text
costNextRank = 1 + floor(currentRank / 10)
~~~

Ví dụ:

- Bậc 1–10: 1 điểm/bậc.
- Bậc 11–20: 2 điểm/bậc.
- Bậc 21–30: 3 điểm/bậc.

Mỗi nhánh có maxRank riêng trong cấu hình. Khi các nhánh chiến đấu đã tối đa, bang vẫn dùng điểm vào tiện ích hiện tại hoặc các nhánh mới trong tương lai.

### 9.4. Quyền quản lý

- Thành viên: xem bảng.
- Bang phó: xem, có thể đề xuất nếu sau này thêm cơ chế đề xuất.
- Bang chủ: cộng điểm.
- Tẩy nhánh: chỉ bang chủ, có phí quỹ và cooldown 7 ngày.
- Tẩy toàn bộ: chỉ bang chủ, phí cao hơn và yêu cầu xác nhận hai bước.

Mỗi thay đổi ghi:

- Người thao tác.
- Nhánh.
- Bậc trước/sau.
- Điểm trước/sau.
- Chi phí.
- Thời gian.

### 9.5. Áp dụng buff

Buff được tính trong luồng tính chỉ số tập trung, tương tự cách RewardBlackBall được đọc trong NPoint nhưng dùng lớp dữ liệu riêng.

Quy tắc:

- Không sửa chỉ số gốc đã lưu của Player.
- Khi vào/rời/chuyển bang phải tính lại chỉ số ngay.
- Khi bang chủ cộng/tẩy điểm, tính lại cho thành viên online.
- Thành viên offline nhận đúng buff khi đăng nhập.
- Phiên bản đầu chỉ áp dụng đầy đủ cho nhân vật chính.
- Đệ tử không nhận hoặc chỉ nhận tỷ lệ riêng sau khi cân bằng.
- PvP/giải đấu phải đi qua hàm xác định hệ số buff theo chế độ.
- Không tin giá trị phần trăm do client gửi.

## 10. Kho bang và tiền tệ bang

### 10.1. Số dư

| Tiền tệ | Nguồn | Công dụng |
|---|---|---|
| Capsule Bang | Nhiệm vụ, hoạt động tuần, cây | Nâng cấp và nhập hàng |
| Vàng bang | Thành viên đóng góp, cây, hoạt động | Nâng cấp, đột phá cây, nhập hàng |
| Ngọc bang | Thành viên đóng góp, sự kiện hạn chế | Mốc nâng cấp đặc biệt, tiện ích |
| Điểm cống hiến cá nhân | Hoạt động và đóng góp | Điều kiện mua shop/xếp hạng cá nhân |

Số dư quỹ dùng long trên server. Mọi phép cộng dùng kiểm tra giới hạn trước khi cập nhật.

### 10.2. Đóng góp

Thành viên chọn:

~~~text
Đóng góp Vàng
Đóng góp Ngọc
Đóng góp vật liệu cây
~~~

Trước khi xác nhận client hiển thị:

- Số lượng muốn đóng góp.
- Số dư cá nhân hiện tại.
- Số dư cá nhân sau đóng góp.
- Số dư quỹ dự kiến sau đóng góp.
- Cảnh báo không thể rút lại.

Server xác thực lại toàn bộ. Người chơi được nhập số lượng mong muốn trong phạm vi:

- Lớn hơn 0.
- Không lớn hơn tài sản đang có.
- Không vượt giới hạn một giao dịch/cả ngày nếu cấu hình bật.
- Không làm số dư quỹ vượt giới hạn long của hệ thống.

### 10.3. Không cho rút

Không có chức năng rút Vàng bang hoặc Ngọc bang. Khi:

- Người chơi rời bang: đóng góp vẫn thuộc bang.
- Bang chủ đổi: quỹ đi cùng bang.
- Thành viên bị kick: không hoàn tiền.
- Bang giải tán: quỹ bị đóng/xóa theo chính sách giải tán, không chia cho thành viên.

Client phải hiển thị cảnh báo rõ trước đóng góp.

### 10.4. Ai được tiêu quỹ

| Hành động | Thành viên | Bang phó | Bang chủ |
|---|---:|---:|---:|
| Xem số dư | Có | Có | Có |
| Đóng góp | Có | Có | Có |
| Xem lịch sử cơ bản | Có | Có | Có |
| Nâng cấp thường | Không | Có nếu được phép | Có |
| Đột phá lớn | Không | Không mặc định | Có |
| Nhập hàng shop | Không | Có nếu được phép | Có |
| Tẩy tiềm năng | Không | Không | Có |

Nên có quyền cấu hình cho bang phó thay vì suy luận mọi quyền chỉ từ role.

### 10.5. Cửa hàng bang

Để tránh một thành viên tiêu sạch quỹ:

- Quỹ chung dùng để mở tầng shop và nhập số lượng hàng.
- Thành viên mua hàng đã nhập bằng Capsule cá nhân/Điểm cống hiến.
- Mỗi mặt hàng có giới hạn cá nhân ngày/tuần.
- Mặt hàng mạnh yêu cầu tuổi bang, cấp bang và cống hiến tối thiểu.
- Vật phẩm nhận từ shop nên khóa nếu có nguy cơ chuyển tài sản.

Không cho mọi thành viên mua trực tiếp bằng số dư Vàng bang/Ngọc bang chung.

### 10.6. Lịch sử

Mỗi dòng lịch sử có:

- ID giao dịch.
- Clan ID.
- Người thực hiện.
- Loại hành động.
- Loại tiền.
- Số tiền tăng/giảm.
- Số dư sau giao dịch.
- Người/vật phẩm/mục tiêu liên quan.
- Thời gian.
- Request ID chống lặp.

Client mặc định tải 50 dòng gần nhất và phân trang. Chat chỉ hiện các giao dịch đáng chú ý; không spam từng lần tưới nhỏ.

## 11. Tặng quà thành viên

### 11.1. Luồng giao diện

Trong Bang hội > Thành viên:

1. Chọn một thành viên khác.
2. Chọn Tặng quà.
3. Xem điều kiện và số Phiếu quà đang có.
4. Xác nhận.
5. Server random phần thưởng.
6. Người nhận nhận ngay nếu online hoặc nhận qua hộp quà chờ.
7. Chat/lịch sử hiển thị kết quả.

### 11.2. Điều kiện

- Người gửi và người nhận đang cùng bang.
- Không phải cùng nhân vật hoặc cùng tài khoản.
- Cả hai vào bang ít nhất 72 giờ.
- Người gửi có Phiếu quà bang.
- Người gửi còn lượt ngày.
- Người nhận chưa vượt số quà được nhận trong ngày.
- Request chưa từng được xử lý.

Giới hạn đề xuất:

| Hạn mức | Giá trị |
|---|---:|
| Gửi mỗi người/ngày | 1 |
| Nhận mỗi người/ngày | 3 |
| Tặng cùng một người | 1 lần/ngày |
| Tuổi thành viên tối thiểu | 72 giờ |

### 11.3. Phần thưởng

Theo yêu cầu gameplay, kết quả là vàng hoặc 3–5 ngọc:

- Phần lớn là vàng theo cấp người nhận và cấp bang.
- Xác suất nhỏ là 3–5 ngọc khóa.
- Random tại server.
- Không cho client chọn kết quả.
- Không chuyển trực tiếp vàng/ngọc của người gửi cho người nhận.

Phiếu quà là chi phí thực. Không có lượt tặng miễn phí vô hạn.

### 11.4. Chống clone

- Chặn cùng accountId.
- Theo dõi cặp senderId/receiverId/ngày.
- Yêu cầu cống hiến tối thiểu trong tuần.
- Có thể dùng device/IP làm tín hiệu rủi ro, không nên chặn tuyệt đối chỉ bằng IP vì người chơi chung mạng.
- Hạ hoặc khóa phần thưởng với tài khoản có mẫu gửi vòng tròn đáng ngờ.
- Ghi log quản trị đầy đủ.

## 12. Giá trị và xếp hạng bang

Giá trị bang phản ánh hoạt động, không phản ánh trực tiếp số tiền nạp.

Công thức hiển thị tham khảo:

~~~text
clanValue =
    clanLevel × 1000
  + spentPotentialPoints × 100
  + treeLevel × 500
  + achievementScore
  + cappedWeeklyActivityScore
~~~

Quỹ hiện có không tính hoặc chỉ chiếm tối đa 10% điểm để tránh bơm tài sản tạo xếp hạng giả.

Giá trị bang dùng cho:

- Bảng xếp hạng.
- Khung biểu tượng/danh hiệu.
- Ngoại hình cây/lãnh địa.
- Mở nội dung tiện ích.

Không nên cấp thêm buff chiến đấu lớn theo vị trí xếp hạng, vì sẽ khiến bang mạnh ngày càng bỏ xa.

## 13. Giao diện client

### 13.1. Điều hướng đề xuất

Do màn hình client nhỏ, không nên tạo quá nhiều tab ngang. Dùng trang Bang hội hiện có và các nút cấp hai:

~~~text
Bang hội
├── Tổng quan
├── Thành viên
├── Chat bang
├── Cây bang
├── Tiềm năng
├── Kho bang
├── Cửa hàng
└── Lịch sử
~~~

### 13.2. Màn hình Cây bang

Hiển thị:

- Tên và cấp cây.
- Trạng thái.
- Sinh trưởng hiện tại/yêu cầu.
- Nước và phân hiện tại/yêu cầu.
- Sức sống ngày.
- Sản lượng mỗi giờ ước tính.
- Kho chờ chung.
- Lượt tưới cá nhân còn lại.
- Điều kiện phần thưởng cá nhân.

Nút:

- Tưới cây.
- Bón phân.
- Xin giúp.
- Thu hoạch cho bang.
- Nhận thưởng cá nhân.
- Đi tới lãnh địa.
- Nâng cấp/Đột phá, chỉ hiện đúng quyền.

### 13.3. Màn hình Tiềm năng

Mỗi nhánh hiển thị:

- Bậc hiện tại/tối đa.
- Hiệu quả hiện tại.
- Hiệu quả bậc sau.
- Giá điểm bậc sau.
- Điểm còn lại.

Nút cộng chỉ hoạt động với bang chủ. Client vẫn gửi yêu cầu nhánh/bậc; server tự tính giá và kết quả.

### 13.4. Màn hình Kho bang

Hiển thị:

- Ba số dư quỹ.
- Điểm cống hiến cá nhân.
- Nút đóng góp.
- Nút nâng cấp/nhập hàng theo quyền.
- Danh sách lịch sử có phân trang.

### 13.5. Menu thành viên

Thêm Tặng quà bên cạnh các hành động hiện có. Nút chỉ là lối vào; mọi điều kiện vẫn do server xác thực.

### 13.6. Đồng bộ Game1 và Game2

Mọi thay đổi phải được thực hiện song song trong:

- Game1/Clan.cs và Game2/Clan.cs.
- Game1/Controller.cs và Game2/Controller.cs.
- Game1/Service.cs và Game2/Service.cs.
- Game1/Panel.cs và Game2/Panel.cs.
- Chuỗi ngôn ngữ tương ứng.

Không phát hành khi một tab hoạt động khác tab còn lại.

## 14. Giao thức client–server

### 14.1. Nguyên tắc

- Không nhét dữ liệu mới tùy tiện vào cuối packet cũ nếu client cũ có thể đọc sai.
- Nên tạo nhóm subcommand CLAN_V2 hoặc packet mở rộng riêng.
- Server là nguồn sự thật.
- Mọi request thay đổi tài sản có requestId/nonce.
- Số dư dùng long.
- Cấp bang dùng int trong packet V2.
- Số lượng/rank dùng int trừ khi chứng minh byte là đủ.

### 14.2. Snapshot server gửi client

#### CLAN_PROFILE_V2

- clanId
- clanLevel int
- clanExp long
- expRequired long
- maxMember int
- totalPotential int
- unspentPotential int
- clanValue long

#### CLAN_TREE_SNAPSHOT

- clanId
- treeLevel int
- state byte
- growth long
- growthRequired long
- water int
- waterRequired int
- fertilizer int
- fertilizerRequired int
- vitality int
- pendingClanGold long
- pendingCapsule long
- personalWaterRemaining int
- personalRewardState byte
- nextProductionAt/lastProductionAt long

#### CLAN_POTENTIAL_SNAPSHOT

- pointsTotal int
- pointsUnspent int
- danh sách branchKey/rank/maxRank/bonus
- resetCooldown long

#### CLAN_TREASURY_SNAPSHOT

- capsule long
- clanGold long
- clanGem long
- memberContribution long
- treasuryVersion long

### 14.3. Request client gửi server

- VIEW_TREE
- WATER_TREE
- FERTILIZE_TREE
- REQUEST_TREE_HELP
- HELP_WATER_TREE
- CLAIM_TREE_CLAN_OUTPUT
- CLAIM_TREE_PERSONAL_REWARD
- VIEW_POTENTIAL
- ALLOCATE_POTENTIAL
- RESET_POTENTIAL_BRANCH
- VIEW_TREASURY
- DEPOSIT_CLAN_GOLD
- DEPOSIT_CLAN_GEM
- VIEW_LEDGER_PAGE
- SEND_CLAN_GIFT

Mỗi request thay đổi dữ liệu gồm requestId và tham số tối thiểu. Client không gửi giá thưởng, số điểm nhận hoặc số dư sau.

### 14.4. Tương thích client cũ

Hai lựa chọn:

1. Bắt buộc cập nhật client đồng thời và đổi packet hiện có.
2. Khuyến nghị: giữ packet clan cũ cho client cũ, kẹp level hiển thị ở 127 và gửi giá trị thật qua CLAN_PROFILE_V2 khi client khai báo hỗ trợ.

Phương án 2 an toàn hơn khi còn nhiều bản client ngoài thực tế.

## 15. Mô hình dữ liệu đề xuất

### 15.1. Mở rộng bảng clan

Giữ các trường hiện có làm nguồn tương thích và bổ sung:

| Cột | Kiểu | Ghi chú |
|---|---|---|
| clan_exp | BIGINT | Clan EXP hiện tại |
| potential_total | INT | Tổng điểm đã được cấp |
| potential_unspent | INT | Điểm chưa dùng |
| clan_gold | BIGINT | Vàng quỹ |
| clan_gem | BIGINT | Ngọc quỹ |
| treasury_version | BIGINT | Optimistic locking |
| last_potential_reset | BIGINT | Cooldown |

clan_point hiện có tiếp tục là Capsule Bang ở giai đoạn đầu. Không tạo hai nguồn Capsule khác nhau.

### 15.2. Bảng clan_potential

| Cột | Kiểu |
|---|---|
| clan_id | INT |
| branch_key | VARCHAR |
| rank | INT |
| updated_by | BIGINT |
| updated_at | TIMESTAMP |

Khóa chính: clan_id + branch_key.

### 15.3. Bảng clan_tree

| Cột | Kiểu |
|---|---|
| clan_id | INT |
| tree_level | INT |
| state | TINYINT |
| growth | BIGINT |
| water_total_for_level | BIGINT |
| fertilizer_stock | BIGINT |
| vitality_day_key | DATE |
| vitality_value | INT |
| pending_clan_gold | BIGINT |
| pending_capsule | BIGINT |
| pending_material_json hoặc bảng con | TEXT/Bảng riêng |
| last_production_at | BIGINT |
| active_help_request_id | BIGINT nullable |
| updated_at | TIMESTAMP |
| version | BIGINT |

### 15.4. Bảng clan_member_daily

| Cột | Kiểu |
|---|---|
| clan_id | INT |
| player_id | BIGINT |
| day_key | DATE |
| water_count | INT |
| fertilizer_count | INT |
| contribution_points | BIGINT |
| personal_tree_reward_claimed | BOOLEAN |
| gift_sent_count | INT |
| gift_received_count | INT |

Khóa chính: clan_id + player_id + day_key.

### 15.5. Bảng clan_member_contribution

| Cột | Kiểu |
|---|---|
| clan_id | INT |
| player_id | BIGINT |
| lifetime_score | BIGINT |
| weekly_score | BIGINT |
| donated_gold | BIGINT |
| donated_gem | BIGINT |
| last_contribution_at | TIMESTAMP |

Không nhồi các số liệu mới thay đổi thường xuyên vào JSON members.

### 15.6. Bảng clan_ledger

| Cột | Kiểu |
|---|---|
| id | BIGINT auto increment |
| clan_id | INT |
| actor_id | BIGINT |
| action_type | VARCHAR |
| currency_type | VARCHAR nullable |
| amount | BIGINT |
| balance_after | BIGINT nullable |
| target_id | BIGINT nullable |
| metadata_json | TEXT nullable |
| request_id | VARCHAR |
| created_at | TIMESTAMP |

Chỉ mục:

- clan_id + id giảm dần.
- actor_id + created_at.
- request_id unique trong phạm vi hành động hoặc toàn hệ thống.

### 15.7. Bảng clan_pending_reward

Dùng cho quà/offline/túi đầy:

| Cột | Kiểu |
|---|---|
| id | BIGINT |
| clan_id | INT |
| player_id | BIGINT |
| source_type | VARCHAR |
| reward_json | TEXT |
| status | TINYINT |
| created_at | TIMESTAMP |
| claimed_at | TIMESTAMP nullable |
| request_id | VARCHAR unique |

Phần thưởng được tạo một lần và lưu cố định; không random lại mỗi lần người chơi mở hộp.

## 16. Transaction và đồng thời

### 16.1. Đóng góp quỹ

Trong cùng một transaction:

1. Khóa bản ghi Player/inventory cần thiết.
2. Kiểm tra lại số dư.
3. Trừ tài sản cá nhân.
4. Cộng quỹ bang.
5. Cộng thống kê cống hiến.
6. Ghi ledger.
7. Commit.

Nếu bất kỳ bước nào lỗi phải rollback toàn bộ.

### 16.2. Tưới cây

Trong cùng một transaction hoặc critical section có lưu bền:

1. Kiểm tra requestId.
2. Khóa daily record và ClanTree.
3. Kiểm tra lượt.
4. Tìm/trừ Bình nước.
5. Cộng cây và cống hiến.
6. Ghi trạng thái request.
7. Commit.

Không được trừ bình trước rồi lỗi cộng cây.

### 16.3. Nâng cấp/tiềm năng

- Khóa Clan progression, treasury và potential.
- Server tự tính chi phí.
- So sánh version nếu dùng optimistic locking.
- Không cho hai leader request đồng thời tạo hai lần nâng cấp.
- Sau commit mới phát thông báo và cập nhật thành viên online.

### 16.4. Nhận thưởng

- Đặt cờ claimed trong cùng transaction với cộng phần thưởng.
- Request lặp trả lại kết quả cũ hoặc thông báo đã nhận, không cấp lần hai.
- Túi đầy thì tạo pending reward, không làm mất phần thưởng và không random lại.

## 17. Reset ngày và thời gian

- Dùng timezone server Asia/Ho_Chi_Minh.
- dayKey phải được tính tập trung, không dùng giờ client.
- Không cần job reset mọi thành viên lúc 00:00.
- Khi đọc daily record, nếu dayKey khác ngày hiện tại thì tạo record mới.
- Sản lượng cây dựa trên timestamp, không dựa vào số vòng update của Zone.
- Thay đổi giờ hệ thống bất thường phải được phát hiện/log để tránh tính sản lượng âm hoặc quá lớn.

## 18. Phân quyền và bảo mật

### 18.1. Kiểm tra bắt buộc tại server

Mọi request phải kiểm tra:

- Session còn hợp lệ.
- Player ID khớp session.
- Player còn trong Clan.
- clanId request khớp player.clan.id.
- Role và quyền chi tiết.
- Tuổi thành viên.
- Hạn mức ngày.
- Tài sản hiện tại.
- Trạng thái cây/quỹ.
- RequestId chưa xử lý.

### 18.2. Không tin client

Không nhận từ client:

- Kết quả random.
- Số vàng/ngọc được cộng.
- Phần trăm buff.
- Giá nâng cấp.
- Điểm sinh trưởng nhận.
- Role hoặc clanId mục tiêu tùy ý.

### 18.3. Chống spam

- Rate limit packet theo Player.
- Cooldown hành động UI.
- Một help request hoạt động mỗi bang.
- Gộp chat/log cho hành động nhỏ.
- Đóng session hoặc ghi cảnh báo khi gửi request sai liên tục.

## 19. Tình huống biên

### 19.1. Rời hoặc bị kick khỏi bang

- Buff bang bị gỡ ngay và tính lại chỉ số.
- Bị đưa khỏi khu bang.
- Không thể nhận thưởng cây chưa nhận.
- Đóng góp và lịch sử cũ không bị xóa.
- Quà pending đã được tạo hợp lệ trước đó vẫn có thể nhận nếu chính sách cho phép; mặc định nên cho nhận vì phần thưởng đã chốt.

### 19.2. Chuyển bang

- Daily record gắn với clanId cũ, không chuyển sang bang mới.
- Tuổi thành viên được tính lại.
- Không mang lượt tưới/phần thưởng đủ điều kiện sang bang mới.

### 19.3. Bang giải tán

- Khóa mọi request mới.
- Đưa người chơi khỏi khu.
- Gỡ buff.
- Hủy khu động.
- Đánh dấu ledger lưu trữ.
- Không hoàn quỹ cho bang chủ hay thành viên.
- Chỉ xóa dữ liệu vật lý sau thời gian lưu an toàn hoặc theo job dọn dẹp.

### 19.4. Chuyển chức bang chủ

- Điểm tiềm năng và quỹ không thay đổi.
- Quyền thao tác cập nhật ngay.
- Request đang chờ của chủ cũ phải xác thực role lại khi thực thi.

### 19.5. Server restart

- Không mất kho chờ, sinh trưởng, daily record hoặc request quà.
- Khu map 153 được tạo lại khi có thành viên vào.
- Sản lượng tính bù tối đa theo productionCapHours.

### 19.6. Cấp 127/128 và cao hơn

- Client V2 phải hiển thị đúng.
- Packet cũ không được khiến readByte đọc thành số âm.
- Tất cả phép tính cấp dùng int/long.

## 20. Cấu hình đề xuất

Nên tạo clan.properties hoặc một lớp ClanConfig có reload an toàn.

~~~properties
clan.level.technicalMax=1000
clan.level.potentialPointsPerLevel=5
clan.member.max=50
clan.member.joinAgeHoursForBenefits=72

clan.tree.enabled=true
clan.tree.mapId=153
clan.tree.zoneIdleMinutes=10
clan.tree.maxContentLevel=20
clan.tree.waterPerMemberPerDay=5
clan.tree.helpRequestCooldownMinutes=10
clan.tree.helpRequestExpireMinutes=120
clan.tree.productionCapHours=24
clan.tree.personalRewardMinContribution=2

clan.tree.waterItemId=<chốt khi triển khai>
clan.tree.fertilizerItemId=<chốt khi triển khai>
clan.tree.giftTicketItemId=<chốt khi triển khai>
clan.tree.waterDropMapIds=<danh sách map>
clan.tree.waterDropRate=<tỷ lệ>

clan.gift.sentPerDay=1
clan.gift.receivedPerDay=3
clan.gift.boundGemMin=3
clan.gift.boundGemMax=5

clan.potential.pvpMultiplier=0.50
clan.potential.resetCooldownDays=7
~~~

Các placeholder ID và tỷ lệ phải được kiểm tra với dữ liệu đang triển khai trước khi điền.

## 21. Kế hoạch mã nguồn

### 21.1. Server

Model mới đề xuất:

- nro.models.clan.ClanProgression
- nro.models.clan.ClanPotential
- nro.models.clan.ClanTree
- nro.models.clan.ClanTreasury
- nro.models.clan.ClanLedgerEntry
- nro.models.clan.ClanMemberDaily

Service mới:

- ClanProgressionService
- ClanPotentialService
- ClanTreeService
- ClanTreasuryService
- ClanGiftService
- ClanConfig

DAO/repository:

- ClanProgressionDAO
- ClanTreeDAO
- ClanTreasuryDAO
- ClanLedgerDAO
- ClanGiftDAO

Điểm tích hợp hiện có:

- Clan.java: trạng thái và quyền bang.
- ClanService.java: packet và đồng bộ.
- DrDrief.java: menu nâng cấp/lãnh địa.
- Manager.java: tải dữ liệu, bỏ đọc level/maxMember bằng byte.
- NPoint.java: áp dụng buff bang.
- ShopService.java: tầng shop, tồn kho và chi phí.
- UseItem.java: Bình nước/Phân bón/Phiếu quà nếu dùng qua hành trang.
- MapService/Zone manager: khu động map 153.
- Player login/logout/clan join/leave: đồng bộ và gỡ buff.

### 21.2. Client

Thực hiện đối xứng Game1/Game2:

- Mở rộng Clan model.
- Model ClanTree/ClanPotential/Treasury/Ledger.
- Service gửi subcommand.
- Controller đọc snapshot.
- Panel hiển thị menu và trạng thái.
- Clan chat hiển thị nút Giúp tưới.
- Menu thành viên thêm Tặng quà.
- Chuỗi ngôn ngữ và thông báo lỗi.
- Sprite cây theo cấp.

### 21.3. SQL

Tạo migration mới, không sửa trực tiếp dump làm bước triển khai chính.

Migration cần:

- ALTER các cột/bổ sung cột tương thích.
- CREATE các bảng mới.
- Tạo index/unique request ID.
- Backfill điểm tiềm năng.
- Tạo ClanTree mặc định cho bang hiện có.
- Có script rollback cấu trúc khi phù hợp.
- Chạy lặp lại an toàn hoặc có bảng version migration.

## 22. Lộ trình triển khai

### Giai đoạn 0: Nền giao thức

- Sửa kiểu dữ liệu cấp/maxMember.
- Thêm CLAN_PROFILE_V2.
- Đồng bộ Game1/Game2.
- Kiểm thử cấp 127/128.

### Giai đoạn 1: Dữ liệu và dịch vụ quỹ

- Migration.
- Ledger.
- Transaction đóng góp.
- Màn hình kho và lịch sử.

### Giai đoạn 2: Cây bang MVP

- Khu động map 153.
- Một cây/bang.
- Tưới trực tiếp và từ tab.
- Xin giúp trong chat.
- 5 lượt/ngày.
- Sinh trưởng và nhận thưởng bằng nút.

### Giai đoạn 3: Cấp và tiềm năng

- Clan EXP.
- Công thức nâng cấp.
- +5 điểm/cấp.
- Bảng phân bổ.
- Buff PvE/PvP có trần.

### Giai đoạn 4: Cửa hàng và quà

- Shop unlock/restock.
- Điểm cống hiến.
- Phiếu quà.
- Tặng quà online/offline.

### Giai đoạn 5: Giá trị bang và hoàn thiện

- Clan Value.
- Xếp hạng.
- Ngoại hình cây.
- Admin tools, số liệu kinh tế và cân bằng.

Mỗi giai đoạn nên có feature flag để tắt độc lập khi có lỗi.

## 23. Kế hoạch kiểm thử

### 23.1. Unit test

- Công thức EXP/chi phí ở cấp 1, 10, 127, 128, 1.000.
- Công thức giá bậc tiềm năng.
- Trần buff.
- Tính sản lượng khi offline 1 giờ, 24 giờ, hơn 24 giờ.
- Reset dayKey.
- Tính Clan Value.
- Kiểm tra tràn long.

### 23.2. Integration test

- Hai người cùng tưới lượt cuối.
- Hai bang chủ cùng bấm nâng cấp.
- Hai request đóng góp trùng requestId.
- Túi đầy khi nhận thưởng.
- Người nhận quà offline.
- Restart giữa lúc có pending reward.
- Rời bang ngay trước khi nhận.
- Chuyển chủ bang khi đang mở màn hình tiềm năng.

### 23.3. Kiểm thử map

- Hai bang vào map 153 không nhìn thấy nhau.
- Không đổi khu sang bang khác.
- Zone được hủy khi trống và tạo lại đúng trạng thái.
- Một bang không tạo hai cây hoặc hai Zone hoạt động.
- Bị kick trong map được đưa ra an toàn.

### 23.4. Kiểm thử client

- Game1 và Game2 cùng đọc đúng packet.
- Cấp 128 không hiển thị âm.
- Nút giúp hết hạn đổi trạng thái.
- Không bấm hai lần nhận thưởng.
- Số lớn hiển thị đúng định dạng.
- Màn hình nhỏ không che nút.

### 23.5. Kiểm thử tải

- Nhiều bang mở khu đồng thời.
- Nhiều người bấm giúp từ chat.
- Ledger phân trang với dữ liệu lớn.
- Không tạo timer riêng cho mọi cây gây tải nền.
- Zone động được giải phóng bộ nhớ.

## 24. Tiêu chí nghiệm thu

Hệ thống chỉ được coi là hoàn thành khi thỏa toàn bộ:

1. Mỗi bang chỉ nhìn thấy đúng một cây của mình tại map 153.
2. Khu bang khác không thể truy cập bằng đổi khu hoặc packet giả.
3. Tưới tại cây, tab và chat đều tiêu thụ đúng một Bình nước.
4. Một thành viên không vượt quá 5 lượt tưới/ngày bằng reconnect hoặc đổi thiết bị.
5. Không có vàng/vật phẩm cây rơi vật lý trên map.
6. Phần thưởng cá nhân chỉ chính người đủ điều kiện nhận được.
7. Thu hoạch chung vào đúng quỹ và có ledger.
8. Restart server không nhân đôi hoặc làm mất sản lượng.
9. Cấp bang trên 127 hiển thị đúng ở Game1 và Game2.
10. Mỗi cấp cấp đúng 5 điểm và migration không cấp lặp.
11. Rời bang gỡ buff ngay.
12. Buff chiến đấu không vượt trần và áp dụng đúng luật PvP.
13. Quỹ không âm trong mọi trường hợp đồng thời.
14. Thành viên thường không thể tiêu sạch quỹ chung.
15. Tặng quà không hoạt động với chính mình/cùng tài khoản và không vượt hạn mức.
16. Mọi giao dịch tài sản có lịch sử truy vết.
17. Các công thức chính có thể đổi bằng cấu hình mà không sửa NPC.

## 25. Chỉ số theo dõi sau phát hành

Trong 2–4 tuần đầu cần thu thập:

- Số người tưới trung bình mỗi bang/ngày.
- Tỷ lệ bang đạt đủ Sức sống ngày.
- Bình nước kiếm/tiêu/người/ngày.
- Vàng bang tạo từ cây và từ đóng góp.
- Vàng/ngọc bị tiêu ở nâng cấp/shop.
- Thời gian trung bình tăng một cấp bang/cây.
- Phân bố điểm tiềm năng.
- Tỷ lệ người nhận quà và số tài khoản bị chặn.
- Số Zone map 153 đồng thời và thời gian sống trung bình.
- Số transaction rollback/request lặp.

Nếu vàng sinh ra cao hơn vàng bị tiêu đáng kể, giảm sản lượng hoặc tăng sink trước khi mở thêm cấp.

## 26. Các giá trị cần chốt khi bắt đầu triển khai

Các quyết định kiến trúc trong tài liệu đã đủ để bắt đầu. Những giá trị sau nên được lấy từ dữ liệu kinh tế thực tế khi triển khai:

- ID và icon của Bình nước, Phân bón, Phiếu quà.
- Danh sách map và tỷ lệ rơi Bình nước.
- Giá trị vàng mỗi giờ theo cấp cây.
- Giá trị vàng phần thưởng cá nhân.
- Chi phí Capsule/Vàng/Ngọc theo cấp bang.
- Chi phí đột phá cây.
- Max rank và hiệu quả chính xác từng nhánh tiềm năng.
- Danh sách chế độ PvP giảm hoặc tắt buff.
- Mốc mở slot thành viên và trần thành viên.
- Cấp cây nội dung tối đa của bản đầu.

Các giá trị này phải nằm trong cấu hình/bảng dữ liệu, không rải rác trong source.

## 27. Kết luận

Thiết kế thống nhất nên xem Cây bang là trung tâm hoạt động xã hội, cấp bang là tiến trình dài hạn, Tiềm năng là lựa chọn xây dựng, Kho bang là nền kinh tế minh bạch và Tặng quà là phần thưởng tương tác có chi phí.

Ba nguyên tắc không được phá vỡ khi triển khai:

1. **Hoạt động tạo tiến trình; tiền chỉ hỗ trợ hoàn tất tiến trình.**
2. **Sức mạnh chiến đấu có trần; cấp cao tiếp tục mở tiện ích và hình thức.**
3. **Mọi tài sản và kết quả random do server kiểm soát, có transaction và lịch sử.**

Với các nguyên tắc này, hệ thống có thể tạo động lực cày bang lâu dài mà không gây spam vật thể ở map 153, không làm sức mạnh tăng vô hạn và hạn chế được việc lợi dụng tài khoản phụ.
