# Costume Tools

`projects/` chứa các draft JSON do tab **Tạo Cải Trang** lưu cục bộ.

Draft chỉ mô tả metadata, mapping HEAD/BODY/LEG, đường dẫn ảnh nguồn và offset. Tab không tự ghi `part`, `item_template`, không chép ảnh vào `data/icon`, không tăng cache version và không restart server.

Quy trình publish sẽ được bổ sung sau khi mapping asset đã được duyệt và phải giữ đúng contract:

- HEAD: 3 slot;
- BODY: 17 slot;
- LEG: 14 slot;
- mỗi slot là `[imageId, dx, dy]`;
- `imageId` nằm trong `0..32767`, `dx/dy` nằm trong `-128..127`.
