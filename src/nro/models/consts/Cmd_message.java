package nro.models.consts;

public class Cmd_message {

    public static final byte LOGIN = 0;

    public static final byte REGISTER = 1;

    public static final byte CLIENT_INFO = 2;

    public static final byte SEND_SMS = 3;

    public static final byte REGISTER_IMAGE = 4;

    public static final byte MESSAGE_TIME = 65;

    public static final byte LOGOUT = 0;

    public static final byte SELECT_PLAYER = 1;

    public static final byte CREATE_PLAYER = 2;

    public static final byte DELETE_PLAYER = 3;

    public static final byte UPDATE_VERSION = 4;

    public static final byte UPDATE_MAP = 6;

    public static final byte UPDATE_SKILL = 7;

    public static final byte UPDATE_ITEM = 8;

    public static final byte REQUEST_SKILL = 9;

    public static final byte REQUEST_MAPTEMPLATE = 10;

    public static final byte REQUEST_NPCTEMPLATE = 11;

    public static final byte REQUEST_NPCPLAYER = 12;

    public static final byte UPDATE_TYPE_PK = 35;

    public static final byte PLAYER_ATTACK_PLAYER = -60;

    public static final byte PLAYER_VS_PLAYER = -59;

    public static final byte CLAN_PARTY = -58;

    public static final byte CLAN_INVITE = -57;

    public static final byte CLAN_REMOTE = -56;

    public static final byte CLAN_LEAVE = -55;

    public static final byte CLAN_DONATE = -54;

    public static final byte CLAN_MESSAGE = -51;

    public static final byte CLAN_UPDATE = -52;

    public static final byte CLAN_INFO = -53;

    public static final byte CLAN_JOIN = -49;

    public static final byte CLAN_MEMBER = -50;

    public static final byte CLAN_SEARCH = -47;

    public static final byte CLAN_CREATE_INFO = -46;

    public static final byte CLIENT_OK = 13;

    public static final byte CLIENT_OK_INMAP = 14;

    public static final byte UPDATE_VERSION_OK = 15;

    public static final byte INPUT_CARD = 16;

    public static final byte CLEAR_TASK = 17;

    public static final byte CHANGE_NAME = 18;

    public static final byte UPDATE_PK = 20;

    public static final byte CREATE_CLAN = 21;

    public static final byte CONVERT_UPGRADE = 33;

    public static final byte INVITE_CLANDUN = 34;

    public static final byte NOT_USEACC = 35;

    public static final byte ME_LOAD_ACTIVE = 36;

    public static final byte ME_ACTIVE = 37;

    public static final byte ME_UPDATE_ACTIVE = 38;

    public static final byte ME_OPEN_LOCK = 39;

    public static final byte ITEM_SPLIT = 40;

    public static final byte ME_CLEAR_LOCK = 41;

    public static final byte GET_IMG_BY_NAME = 66;

    public static final byte ME_LOAD_ALL = 0;

    public static final byte ME_LOAD_CLASS = 1;

    public static final byte ME_LOAD_SKILL = 2;

    public static final byte ME_LOAD_INFO = 4;

    public static final byte ME_LOAD_HP = 5;

    public static final byte ME_LOAD_MP = 6;

    public static final byte PLAYER_LOAD_ALL = 7;

    public static final byte PLAYER_SPEED = 8;

    public static final byte PLAYER_LOAD_LEVEL = 9;

    public static final byte PLAYER_LOAD_VUKHI = 10;

    public static final byte PLAYER_LOAD_AO = 11;

    public static final byte PLAYER_LOAD_QUAN = 12;

    public static final byte PLAYER_LOAD_BODY = 13;

    public static final byte PLAYER_LOAD_HP = 14;

    public static final byte PLAYER_LOAD_LIVE = 15;

    public static final byte GOTO_PLAYER = 18;

    public static final byte POTENTIAL_UP = 16;

    public static final byte SKILL_UP = 17;

    public static final byte BAG_SORT = 18;

    public static final byte BOX_SORT = 19;

    public static final byte BOX_COIN_OUT = 21;

    public static final byte REQUEST_ITEM = 22;

    public static final byte ME_ADD_SKILL = 23;

    public static final byte ME_UPDATE_SKILL = 62;

    public static final byte GET_PLAYER_MENU = 63;

    public static final byte PLAYER_MENU_ACTION = 64;

    public static final byte SAVE_RMS = 60;

    public static final byte LOAD_RMS = 61;

    public static final byte USE_BOOK_SKILL = 43;

    public static final byte LOCK_INVENTORY = -104;

    public static final byte CHANGE_FLAG = -103;

    public static final byte LOGINFAIL = -102;

    public static final byte LOGIN2 = -101;

    public static final byte KIGUI = -100;

    public static final byte ENEMY_LIST = -99;

    public static final byte ANDROID_IAP = -98;

    public static final byte UPDATE_ACTIVEPOINT = -97;

    public static final byte TOP = -96;

    public static final byte MOB_ME_UPDATE = -95;

    public static final byte UPDATE_COOLDOWN = -94;

    public static final byte BGITEM_VERSION = -93;

    public static final byte SET_CLIENTTYPE = -92;

    public static final byte MAP_TRASPORT = -91;

    public static final byte UPDATE_BODY = -90;

    public static final byte SERVERSCREEN = -88;

    public static final byte UPDATE_DATA = -87;

    public static final byte GIAO_DICH = -86;

    public static final byte MOB_CAPCHA = -85;

    public static final byte MOB_MAX_HP = -84;

    public static final byte CALL_DRAGON = -83;

    public static final byte TILE_SET = -82;

    public static final byte COMBINNE = -81;

    public static final byte FRIEND = -80;

    public static final byte PLAYER_MENU = -79;

    public static final byte CHECK_MOVE = -78;

    public static final byte SMALLIMAGE_VERSION = -77;

    public static final byte ARCHIVEMENT = -76;

    public static final byte NPC_BOSS = -75;

    public static final byte GET_IMAGE_SOURCE = -74;

    public static final byte NPC_ADD_REMOVE = -73;

    public static final byte CHAT_PLAYER = -72;

    public static final byte CHAT_THEGIOI_CLIENT = -71;

    public static final byte BIG_MESSAGE = -70;

    public static final byte MAXSTAMINA = -69;

    public static final byte STAMINA = -68;

    public static final byte REQUEST_ICON = -67;

    public static final byte GET_EFFDATA = -66;

    public static final byte TELEPORT = -65;

    public static final byte UPDATE_BAG = -64;

    public static final byte GET_BAG = -63;

    public static final byte CLAN_IMAGE = -62;

    public static final byte UPDATE_CLANID = -61;

    public static final byte SKILL_NOT_FOCUS = -45;

    public static final byte SHOP = -44;

    public static final byte USE_ITEM = -43;

    public static final byte ME_LOAD_POINT = -42;

    public static final byte UPDATE_CAPTION = -41;

    public static final byte GET_ITEM = -40;

    public static final byte FINISH_LOADMAP = -39;

    public static final byte FINISH_UPDATE = -38;

    public static final byte BODY = -37;

    public static final byte BAG = -36;

    public static final byte BOX = -35;

    public static final byte MAGIC_TREE = -34;

    public static final byte MAP_OFFLINE = -33;

    public static final byte BACKGROUND_TEMPLATE = -32;

    public static final byte ITEM_BACKGROUND = -31;

    public static final byte SUB_COMMAND = -30;

    public static final byte NOT_LOGIN = -29;

    public static final byte NOT_MAP = -28;

    public static final byte GET_SESSION_ID = -27;

    public static final byte DIALOG_MESSAGE = -26;

    public static final byte SERVER_MESSAGE = -25;

    public static final byte MAP_INFO = -24;

    public static final byte MAP_CHANGE = -23;

    public static final byte MAP_CLEAR = -22;

    public static final byte ITEMMAP_REMOVE = -21;

    public static final byte ITEMMAP_MYPICK = -20;

    public static final byte ITEMMAP_PLAYERPICK = -19;

    public static final byte ME_THROW = -18;

    public static final byte ME_DIE = -17;

    public static final byte ME_LIVE = -16;

    public static final byte ME_BACK = -15;

    public static final byte PLAYER_THROW = -14;

    public static final byte NPC_LIVE = -13;

    public static final byte NPC_DIE = -12;

    public static final byte NPC_ATTACK_ME = -11;

    public static final byte NPC_ATTACK_PLAYER = -10;

    public static final byte MOB_HP = -9;

    public static final byte PLAYER_DIE = -8;

    public static final byte PLAYER_MOVE = -7;

    public static final byte PLAYER_REMOVE = -6;

    public static final byte PLAYER_ADD = -5;

    public static final byte PLAYER_ATTACK_N_P = -4;

    public static final byte PLAYER_UP_EXP = -3;

    public static final byte ME_UP_COIN_LOCK = -2;

    public static final byte ME_CHANGE_COIN = -1;

    public static final byte ITEM_BUY = 6;

    public static final byte ITEM_SALE = 7;

    public static final byte UPPEARL_LOCK = 13;

    public static final byte UPGRADE = 14;

    public static final byte PLEASE_INPUT_PARTY = 16;

    public static final byte ACCEPT_PLEASE_PARTY = 17;

    public static final byte REQUEST_PLAYERS = 18;

    public static final byte UPDATE_ACHIEVEMENT = 19;

    public static final byte PHUBANG_INFO = 20;

    public static final byte ZONE_CHANGE = 21;

    public static final byte MENU = 22;

    public static final byte OPEN_UI = 23;

    public static final byte OPTION_HAT = 24;

    public static final byte OPEN_UI_PT = 25;

    public static final byte OPEN_UI_SHOP = 26;

    public static final byte OPEN_MENU_ID = 27;

    public static final byte OPEN_UI_COLLECT = 28;

    public static final byte OPEN_UI_ZONE = 29;

    public static final byte OPEN_UI_TRADE = 30;

    public static final byte OPEN_UI_SAY = 38;

    public static final byte OPEN_UI_CONFIRM = 32;

    public static final byte OPEN_UI_MENU = 33;

    public static final byte SKILL_SELECT = 34;

    public static final byte REQUEST_ITEM_INFO = 35;

    public static final byte TRADE_INVITE = 36;

    public static final byte TRADE_INVITE_ACCEPT = 37;

    public static final byte TRADE_LOCK_ITEM = 38;

    public static final byte TRADE_ACCEPT = 39;

    public static final byte TASK_GET = 40;

    public static final byte TASK_NEXT = 41;

    public static final byte GAME_INFO = 50;

    public static final byte TASK_UPDATE = 43;

    public static final byte CHAT_MAP = 44;

    public static final byte NPC_MISS = 45;

    public static final byte RESET_POINT = 46;

    public static final byte ALERT_MESSAGE = 47;

    public static final byte AUTO_SERVER = 48;

    public static final byte ALERT_SEND_SMS = 49;

    public static final byte TRADE_INVITE_CANCEL = 50;

    public static final byte BOSS_SKILL = 51;

    public static final byte MABU_HOLD = 52;

    public static final byte FRIEND_INVITE = 53;

    public static final byte PLAYER_ATTACK_NPC = 54;

    public static final byte HAVE_ATTACK_PLAYER = 56;

    public static final byte OPEN_UI_NEWMENU = 57;

    public static final byte MOVE_FAST = 58;

    public static final byte TEST_INVITE = 59;

    public static final byte ADD_CUU_SAT = 62;

    public static final byte ME_CUU_SAT = 63;

    public static final byte CLEAR_CUU_SAT = 64;

    public static final byte PLAYER_UP_EXPDOWN = 65;

    public static final byte ME_DIE_EXP_DOWN = 66;

    public static final byte PLAYER_ATTACK_P_N = 67;

    public static final byte ITEMMAP_ADD = 68;

    public static final byte DEL_ACC = 69;

    public static final byte USE_SKILL_MY_BUFF = 70;

    public static final byte NPC_CHANGE = 74;

    public static final byte PARTY_INVITE = 75;

    public static final byte PARTY_ACCEPT = 76;

    public static final byte PARTY_CANCEL = 77;

    public static final byte PLAYER_IN_PARTY = 78;

    public static final byte PARTY_OUT = 79;

    public static final byte FRIEND_ADD = 80;

    public static final byte NPC_IS_DISABLE = 81;

    public static final byte NPC_IS_MOVE = 82;

    public static final byte SUMON_ATTACK = 83;

    public static final byte RETURN_POINT_MAP = 84;

    public static final byte NPC_IS_FIRE = 85;

    public static final byte NPC_IS_ICE = 86;

    public static final byte NPC_IS_WIND = 87;

    public static final byte OPEN_TEXT_BOX_ID = 88;

    public static final byte REQUEST_ITEM_PLAYER = 90;

    public static final byte CHAT_PRIVATE = 91;

    public static final byte CHAT_THEGIOI_SERVER = 92;

    public static final byte CHAT_VIP = 93;

    public static final byte SERVER_ALERT = 94;

    public static final byte ME_UP_COIN_BAG = 95;

    public static final byte GET_TASK_ORDER = 96;

    public static final byte GET_TASK_UPDATE = 97;

    public static final byte CLEAR_TASK_ORDER = 98;

    public static final byte ADD_ITEM_MAP = 99;

    public static final byte TRANSPORT = -105;

    public static final byte ITEM_TIME = -106;

    public static final byte PET_INFO = -107;

    public static final byte PET_STATUS = -108;

    public static final byte SERVER_DATA = -110;

    public static final byte CLIENT_INPUT = -125;

    public static final byte HOLD = -124;

    public static final byte SHOW_ADS = 121;

    public static final byte LOGIN_DE = 122;

    public static final byte SET_POS = 123;

    public static final byte NPC_CHAT = 124;

    public static final byte FUSION = 125;

    public static final byte ANDROID_PACK = 126;

    public static final byte GET_IMAGE_SOURCE2 = -111;

    public static final byte CHAGE_MOD_BODY = -112;

    public static final byte CHANGE_ONSKILL = -113;

    public static final byte REQUEST_PEAN = -114;

    public static final byte POWER_INFO = -115;

    public static final byte AUTOPLAY = -116;

    public static final byte MABU = -117;

    public static final byte THACHDAU = -118;

    public static final byte THELUC = -119;

    public static final byte UPDATECHAR_MP = -123;

    public static final byte REFRESH_ITEM = 100;

    public static final byte CHECK_CONTROLLER = -120;

    public static final byte CHECK_MAP = -121;

    public static final byte BIG_BOSS = 101;

    public static final byte BIG_BOSS_2 = 102;

    public static final byte DUAHAU = -122;

    public static final byte QUAYSO = -126;

    public static final byte USER_INFO = 42;

    public static final byte OPEN3HOUR = -89;

    public static final byte STATUS_PET = 31;

    public static final byte SPEACIAL_SKILL = 112;

    public static final byte SERVER_EFFECT = 113;

    public static final byte INAPP = 114;

    public static final byte LUCKY_ROUND = -127;

    public static final byte RADA_CARD = Byte.MAX_VALUE;

    public static final byte CHAR_EFFECT = -128;
    
    public static final byte MOVE_ITEM_TO_SUU_TAM_BOX = 103;
}
