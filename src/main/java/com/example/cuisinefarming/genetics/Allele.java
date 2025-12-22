package com.example.cuisinefarming.genetics;

/**
 * 等位基因 (Allele) - 孟德尔分级体系
 * 包含显性 (Dominant, +) 和隐性 (Recessive, -) 两个方向。
 * 强度分为 1-3 级。
 */
public enum Allele {
    // --- 显性基因 (Dominant) ---
    /** 显性 I 级 (A1): 基础增强 (+1) */
    DOMINANT_1(1),
    /** 显性 II 级 (A2): 进阶增强 (+2) */
    DOMINANT_2(2),
    /** 显性 III 级 (A3): 强力增强 (+3) */
    DOMINANT_3(3),

    // --- 隐性基因 (Recessive) ---
    /** 隐性 I 级 (a1): 基础减弱 (-1) */
    RECESSIVE_1(-1),
    /** 隐性 II 级 (a2): 进阶减弱 (-2) */
    RECESSIVE_2(-2),
    /** 隐性 III 级 (a3): 强力减弱 (-3) */
    RECESSIVE_3(-3);

    private final int value;

    Allele(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    /**
     * 获取存储用代码 (例如 "A1", "b2")
     */
    public String getCode(Trait trait) {
        char letter = trait.getGeneLetter();
        boolean isDominant = value > 0;
        // 显性大写，隐性小写
        char displayChar = isDominant ? Character.toUpperCase(letter) : Character.toLowerCase(letter);
        return displayChar + String.valueOf(Math.abs(value));
    }

    /**
     * 获取显示名称 (带颜色)
     */
    public String getDisplayName(Trait trait) {
        String code = getCode(trait);
        // 显性根据强度给色
        if (value > 0) {
            if (value == 3) return "§6" + code; // A3 Gold
            if (value == 2) return "§2" + code; // A2 Dark Green
            return "§a" + code; // A1 Green
        } else {
            // 隐性
            if (value == -3) return "§8" + code; // a3 Dark Gray
            if (value == -2) return "§4" + code; // a2 Dark Red
            return "§c" + code; // a1 Red
        }
    }
    
    // Deprecated methods for compatibility if needed, or remove them.
    // Removing getCode() and getDisplayName() without args to force update.
}
