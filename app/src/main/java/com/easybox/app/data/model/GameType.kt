package com.easybox.app.data.model

enum class GameType(val id: String, val displayName: String, val category: Category) {
    CHINESE_CHESS("chinese_chess", "中国象棋", Category.GAME),
    INTERNATIONAL_CHESS("international_chess", "国际象棋", Category.GAME),
    FLIGHT_CHESS("flight_chess", "飞行棋", Category.GAME),
    MILITARY_CHESS("military_chess", "军棋", Category.GAME),
    DOU_DI_ZHU("dou_di_zhu", "斗地主", Category.GAME),
    MAHJONG("mahjong", "麻将", Category.GAME),
    SPINNER("spinner", "随机转盘", Category.TOOL);

    enum class Category { GAME, TOOL }
}
