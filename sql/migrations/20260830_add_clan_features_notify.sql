-- Hướng dẫn các hoạt động bang hội.
-- Migration idempotent: chạy lại không tạo bản ghi trùng.

UPDATE `notify`
SET `text` = CONCAT(
    'Chào mừng bạn đến với các hoạt động bang hội!', CHAR(10), CHAR(10),
    'Bắt đầu bằng cách tạo hoặc gia nhập một bang để cùng đồng đội nhận nhiệm vụ và tích lũy Capsule Bang.', CHAR(10), CHAR(10),
    '- NPC Dr.Brief tại Lãnh địa Bang Hội: xem Nhiệm vụ Bang, Hợp đồng tuần và Cửa hàng Bang hội. ',
    'Bang chủ có thêm các mục quản lý, nâng cấp và tập hợp thành viên.', CHAR(10),
    '- NPC Giu-ma Đầu Bò: điểm danh nhận 1 Capsule Bang mỗi ngày; bang chủ có thể gọi Gấu Tướng Cướp khi có đủ đồng đội.', CHAR(10),
    '- Tây Thánh địa: nơi tìm vật phẩm và mảnh hồn bông tai Porata.', CHAR(10),
    '- Cùng hoàn thành nhiệm vụ và hợp đồng tuần để mang thêm Capsule Bang về cho bang.', CHAR(10),
    '- Khi mua vật phẩm hoặc nâng cấp, điểm cá nhân được ưu tiên; bang chủ và bang phó có thể dùng quỹ chung của bang.',
    CHAR(10), CHAR(10),
    'Hãy rủ đồng đội cùng tham gia để bang hội phát triển nhanh hơn!'
)
WHERE `name` = 'Bang hội - Cập nhật tính năng';

INSERT INTO `notify` (`name`, `text`)
SELECT
    'Bang hội - Cập nhật tính năng',
    CONCAT(
        'Chào mừng bạn đến với các hoạt động bang hội!', CHAR(10), CHAR(10),
        'Bắt đầu bằng cách tạo hoặc gia nhập một bang để cùng đồng đội nhận nhiệm vụ và tích lũy Capsule Bang.', CHAR(10), CHAR(10),
        '- NPC Dr.Brief tại Lãnh địa Bang Hội: xem Nhiệm vụ Bang, Hợp đồng tuần và Cửa hàng Bang hội. ',
        'Bang chủ có thêm các mục quản lý, nâng cấp và tập hợp thành viên.', CHAR(10),
        '- NPC Giu-ma Đầu Bò: điểm danh nhận 1 Capsule Bang mỗi ngày; bang chủ có thể gọi Gấu Tướng Cướp khi có đủ đồng đội.', CHAR(10),
        '- Tây Thánh địa: nơi tìm vật phẩm và mảnh hồn bông tai Porata.', CHAR(10),
        '- Cùng hoàn thành nhiệm vụ và hợp đồng tuần để mang thêm Capsule Bang về cho bang.', CHAR(10),
        '- Khi mua vật phẩm hoặc nâng cấp, điểm cá nhân được ưu tiên; bang chủ và bang phó có thể dùng quỹ chung của bang.',
        CHAR(10), CHAR(10),
        'Hãy rủ đồng đội cùng tham gia để bang hội phát triển nhanh hơn!'
    )
WHERE NOT EXISTS (
    SELECT 1 FROM `notify`
    WHERE `name` = 'Bang hội - Cập nhật tính năng'
);
