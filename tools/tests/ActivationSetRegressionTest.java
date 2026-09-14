package nro.models.services;

import nro.models.map.service.MapService;

/** Regression coverage for activation-set pairing, drop categories and box 1538. */
public final class ActivationSetRegressionTest {

    private ActivationSetRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        assertSetOptionPairs();
        assertEquipmentDropCategories();
        assertFiveStarActivationBox();
        assertSpecialActivationBox();
        assertSpecialActivationSetGroups();
        assertStackableActivationBoxes();
        assertActivationDropMapScope();
        assertSealDropOptions();
        System.out.println("ACTIVATION_SET_REGRESSION_TEST_OK");
    }

    private static void assertSetOptionPairs() {
        assertArrayEquals(new int[]{139}, ItemService.gI().getOptionIdsBySKH(127),
                "Thiên Xin Hăng must pair with its blind-duration bonus");
        assertArrayEquals(new int[]{140}, ItemService.gI().getOptionIdsBySKH(128),
                "Kirin must pair with its Quả Cầu Kênh Khí bonus");
        assertArrayEquals(new int[]{141}, ItemService.gI().getOptionIdsBySKH(129),
                "Songoku pairing changed unexpectedly");
    }

    private static void assertEquipmentDropCategories() {
        assertEquals(4, ItemService.activationEquipmentTypeForRoll(0), "0 must select radar");
        assertEquals(4, ItemService.activationEquipmentTypeForRoll(9), "9 must select radar");
        assertEquals(0, ItemService.activationEquipmentTypeForRoll(10), "10 must select áo");
        assertEquals(0, ItemService.activationEquipmentTypeForRoll(31), "31 must select áo");
        assertEquals(1, ItemService.activationEquipmentTypeForRoll(32), "32 must select quần");
        assertEquals(1, ItemService.activationEquipmentTypeForRoll(54), "54 must select quần");
        assertEquals(2, ItemService.activationEquipmentTypeForRoll(55), "55 must select giày");
        assertEquals(2, ItemService.activationEquipmentTypeForRoll(76), "76 must select giày");
        assertEquals(3, ItemService.activationEquipmentTypeForRoll(77), "77 must select găng");
        assertEquals(3, ItemService.activationEquipmentTypeForRoll(99), "99 must select găng");
    }

    private static void assertFiveStarActivationBox() {
        assertTrue(ItemService.isFiveStarActivationSetBox(1538), "1538 must be routed to the full-set box handler");
        assertTrue(!ItemService.isFiveStarActivationSetBox(1537), "only 1538 may use the full-set box handler");

        int[][][] expectedTemplates = {
            {{0, 6, 27, 21, 12}, {33, 35, 30, 24, 57}},
            {{1, 7, 28, 22, 12}, {41, 43, 47, 46, 57}},
            {{2, 8, 29, 23, 12}, {49, 51, 55, 53, 57}}
        };
        int[][][] expectedSetPairs = {
            {{129, 141}, {127, 139}, {128, 140}},
            {{131, 143}, {132, 144}, {130, 142}},
            {{135, 138}, {133, 136}, {134, 137}}
        };
        for (int gender = 0; gender < 3; gender++) {
            for (int tier = 0; tier < 2; tier++) {
                assertArrayEquals(expectedTemplates[gender][tier],
                        ItemService.fiveStarActivationSetTemplateIds(gender, tier),
                        "1538 must grant all five matching equipment slots");
            }
            for (int setIndex = 0; setIndex < 3; setIndex++) {
                assertArrayEquals(expectedSetPairs[gender][setIndex],
                        ItemService.activationSetOptionPair(gender, setIndex),
                        "1538 must use a same-race activation set");
            }
        }
    }

    private static void assertSealDropOptions() {
        assertArrayEquals(new int[]{127, 139, 36},
                ItemService.sealEquipmentDropOptionIds(true, 0, 1, 2, true),
                "cold-map drops must combine one activation pair with one Nhật ấn");
        assertArrayEquals(new int[]{127, 139},
                ItemService.sealEquipmentDropOptionIds(true, 0, 1, 2, false),
                "cold-map activation equipment must not always receive a seal");
        assertArrayEquals(new int[]{131, 143},
                ItemService.sealEquipmentDropOptionIds(false, 1, 0, 0, true),
                "standard-map drops must carry an activation pair without a seal");
        assertArrayEquals(new int[]{134, 137},
                ItemService.sealEquipmentDropOptionIds(false, 2, 2, 1, false),
                "standard-map drops must remain activation equipment when the seal roll fails");
        assertTrue(ItemService.hasSealOptionForRoll(0), "seal roll 0 must succeed");
        assertTrue(ItemService.hasSealOptionForRoll(29), "seal roll 29 must succeed");
        assertTrue(!ItemService.hasSealOptionForRoll(30), "seal roll 30 must fail");
        assertTrue(!ItemService.hasSealOptionForRoll(99), "seal roll 99 must fail");
    }

    private static void assertActivationDropMapScope() {
        MapService maps = MapService.gI();
        assertTrue(maps.isMapActivationEquipmentDrop(1), "Trái Đất maps must drop activation equipment");
        assertTrue(maps.isMapActivationEquipmentDrop(11), "Namếc maps must drop activation equipment");
        assertTrue(maps.isMapActivationEquipmentDrop(17), "Xayda maps must drop activation equipment");
        assertTrue(maps.isMapActivationEquipmentDrop(63), "Fide maps must drop activation equipment");
        assertTrue(maps.isMapActivationEquipmentDrop(105), "cold maps must drop activation equipment");
        assertTrue(maps.isMapActivationEquipmentDrop(152), "Vùng đất băng giá must be a cold activation-drop map");
        assertTrue(!maps.isMapActivationEquipmentDrop(49), "Kaio training maps must not inherit standard-map drops");
        assertTrue(!maps.isMapActivationEquipmentDrop(92), "future maps must not inherit standard-map drops");
    }

    private static void assertSpecialActivationBox() {
        assertTrue(ItemService.isSpecialActivationSetBox(2275), "2275 must be routed to the special activation-set box handler");
        assertTrue(!ItemService.isSpecialActivationSetBox(2274), "only 2275 may use the special activation-set box handler");
        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12},
                ItemService.specialActivationSetLevels(),
                "special box must choose from every common equipment level 1 through 12");
    }

    private static void assertSpecialActivationSetGroups() {
        int[][][] expectedGroups = {
            {{129, 141}, {127, 139}, {128, 140}, {233, 234}, {245, 246, 247, 248}},
            {{131, 143}, {132, 144}, {130, 142}, {233, 234}, {237, 238, 239, 240}},
            {{135, 138}, {133, 136}, {134, 137}, {233, 234}, {241, 242, 243, 244}}
        };
        for (int gender = 0; gender < expectedGroups.length; gender++) {
            for (int setIndex = 0; setIndex < expectedGroups[gender].length; setIndex++) {
                assertArrayEquals(expectedGroups[gender][setIndex],
                        ItemService.specialActivationSetOptionGroup(gender, setIndex),
                        "2275 must offer every activation set for its character race");
            }
        }
    }

    private static void assertStackableActivationBoxes() {
        assertTrue(ItemService.isStackableActivationSetBox(1538), "1538 must stack with identical boxes");
        assertTrue(ItemService.isStackableActivationSetBox(2275), "2275 must stack with identical boxes");
        assertTrue(!ItemService.isStackableActivationSetBox(2274), "only activation-set boxes may use this stack migration");
    }

    private static void assertArrayEquals(int[] expected, int[] actual, String message) {
        if (expected.length != actual.length) {
            throw new AssertionError(message + ": expected length=" + expected.length + ", actual length=" + actual.length);
        }
        for (int index = 0; index < expected.length; index++) {
            if (expected[index] != actual[index]) {
                throw new AssertionError(message + ": expected=" + expected[index] + ", actual=" + actual[index]);
            }
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
