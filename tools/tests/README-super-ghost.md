# Kiểm thử Siêu Ma Cảm Tử v2

Chạy từ thư mục server, cần JDK 17+ và .NET SDK 8+. Không khởi động server, không sửa dữ liệu nhân vật. Các test C# biên dịch **hai file effect thật** từ client, chỉ thay engine/network/clock bằng stub để có thể kiểm tra xác định.

```powershell
$ghostTestOut = Join-Path $env:TEMP 'codex_superghost_tests'
New-Item -ItemType Directory -Path $ghostTestOut -Force | Out-Null
javac --release 17 -encoding UTF-8 -d $ghostTestOut src/nro/models/skill/SuperGhostFormation.java src/nro/models/skill/SuperGhostPacket.java tools/tests/SuperGhostFormationTest.java tools/tests/SuperGhostWireFixture.java
if ($LASTEXITCODE -ne 0) { throw 'Java compile failed' }
java -cp $ghostTestOut SuperGhostFormationTest
if ($LASTEXITCODE -ne 0) { throw 'Lifecycle tests failed' }
java -cp $ghostTestOut SuperGhostWireFixture (Join-Path $ghostTestOut 'wire.bin')
if ($LASTEXITCODE -ne 0) { throw 'Wire fixture failed' }
dotnet run --project tools/tests/ghost-client/GhostClientTests.csproj -- (Join-Path $ghostTestOut 'wire.bin')
if ($LASTEXITCODE -ne 0) { throw 'Client replay failed' }
```

Client mặc định: `C:/Users/PC/Music/PRJ_2Tab_550K`. Khi chuyển máy, truyền MSBuild property `-p:ClientRoot=<đường-dẫn-client>` cho `dotnet run`.

## Phạm vi

- Java: số lượng/cấp, thời gian, thứ tự hit, giữ hồn chưa dùng, thu hồi, damage share không tự tăng, không gia hạn, tối đa một projectile, hai caster độc lập.
- Wire fixture: 88 payload qua serializer production `SuperGhostPacket`, đúng cấu trúc và kích thước.
- C#: đọc toàn payload, hai nhánh Game1/Game2 vẽ cùng frame/vị trí/hướng; cả ba kiểu bay và hai hướng; 7 → 2 hit → 5; ba frame triệu hồi; thời gian 50 giây không tràn short; hết hạn dù thiếu END; snapshot muộn/trùng/cũ; hai caster độc lập.
- Packaging: `powershell -NoProfile -ExecutionPolicy Bypass -File tools/tests/Test-RuntimeJarCleanup.ps1` xác nhận loại inner class lỗi thời của class vừa compile nhưng giữ class hiện tại, dependency và resource. Chỉ chạy trên JAR fixture trong `output/`, không dừng hoặc sửa server.
- Không thay thế test engine thật, PvP/PvE, alpha/texture import, FPS, đổi map/chết/disconnect và đăng nhập A → B. Xem báo cáo bàn giao để biết phần nào đã/chưa được chạy.
