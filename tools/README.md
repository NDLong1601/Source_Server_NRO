# Công cụ vận hành

Các công cụ được tổ chức theo trách nhiệm để tránh phải tìm ở nhiều vị trí:

- `admin/`: backend cho Admin Data HTA và kiểm tra Năng Động.
- `server/`: build, điều khiển server và probe giao thức.
- `validation/`: kiểm tra cấu trúc Admin Data và dữ liệu map.
- `assets/`: cài đặt hoặc chuyển đổi asset.
- `maps/`: project, compiler, release và regression test cho map.
- `costumes/`: project, builder và asset source cho cải trang.
- `launcher/`: launcher HTA đã biên dịch.
- `tests/`: regression test Java, PowerShell và JScript.

Từ thư mục gốc, lệnh build chuẩn là:

```powershell
.\tools\server\server_control.ps1 -Action build
```
