-- Persistent inventory marker used by FishingService for the bait/tackle a
-- player has selected. IDs 251..254 preserve the contiguous option-template
-- Option IDs are indexed directly by the client. Keep the catalog at 252
-- entries (0..251), which is within the one-byte protocol count.
START TRANSACTION;
DELETE FROM `item_option_template` WHERE `id` BETWEEN 252 AND 255;
INSERT INTO `item_option_template` (`id`, `NAME`) VALUES
    (251, 'Đang sử dụng')
ON DUPLICATE KEY UPDATE `NAME` = VALUES(`NAME`);
COMMIT;
