package com.example.cuisinefarming.cooking;

/**
 * 食材标签枚举
 * 用于定义食材的种类，支持模糊配方匹配。
 * 一个物品可以拥有多个标签 (例如：胡萝卜既是 VEGGIE 也是 ROOT)。
 */
public enum FoodTag {
    // 基础分类
    STARCH("淀粉"),       // 小麦, 土豆, 面包
    MEAT("肉类"),         // 牛肉, 猪肉, 鸡肉, 腐肉
    VEGGIE("蔬菜"),       // 胡萝卜, 甜菜根
    FRUIT("水果"),        // 苹果, 浆果, 西瓜
    FISH("鱼类"),         // 鳕鱼, 鲑鱼
    
    // 调味与特殊
    SPICE("香料"),        // 糖, 辣椒(未来)
    DAIRY("乳制品"),      // 牛奶
    EGG("蛋类"),          // 鸡蛋
    MUSHROOM("蘑菇"),     // 红/棕蘑菇
    
    // 形态描述 (可选，用于更细致的匹配)
    ROOT("根茎类"),       // 土豆, 胡萝卜
    LEAFY("叶菜类"),      // (未来扩展)
    LIQUID("液体");       // 牛奶, 水, 汤

    private final String displayName;

    FoodTag(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
