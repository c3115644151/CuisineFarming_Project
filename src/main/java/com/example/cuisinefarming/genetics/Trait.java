package com.example.cuisinefarming.genetics;

/**
 * 性状定义 (Trait Definition)
 * 定义了作物可能拥有的所有特性类型。
 */
public enum Trait {
    // --- 形态类 (Morphology) ---
    /**
     * 生长速度
     * +: 萌动/活力/躁动/疯长
     * -: 慵懒/沉睡/僵硬/石化
     */
    GROWTH_SPEED("生长速度", "影响作物成熟快慢", "萌动", "活力", "躁动", "疯长", "慵懒", "沉睡", "僵硬", "石化", 'A'),
    
    /**
     * 产量倍率
     * +: 饱满/沉重/满溢/炸裂
     * -: 轻飘/干瘪/空心/尘埃
     */
    YIELD("丰产能力", "影响收割时的掉落量", "饱满", "沉重", "满溢", "炸裂", "轻飘", "干瘪", "空心", "尘埃", 'B'),

    // --- 适应类 (Adaptation) ---
    /**
     * 温度适应性
     * +: 厚皮/粗糙/绒毛/披甲
     * -: 薄皮/娇嫩/剔透/气泡
     */
    TEMPERATURE_TOLERANCE("温度适应", "决定对极端温度的耐受力", "厚皮", "粗糙", "绒毛", "披甲", "薄皮", "娇嫩", "剔透", "气泡", 'C'),

    /**
     * 水分/肥力需求
     * +: 壮实/黝黑/贪吃/暴食
     * -: 瘦弱/苍白/敏感/焦黑
     */
    SOIL_TOLERANCE("土壤适应", "决定对土地肥料浓度的耐受力", "壮实", "黝黑", "贪吃", "暴食", "瘦弱", "苍白", "敏感", "焦黑", 'D'),

    // --- 特殊类 (Special) ---
    /**
     * 特产潜力
     * +: 光洁/晶莹/金边/虹光
     * -: 灰暗/斑驳/霉斑/漆黑
     */
    LUCK("珍稀潜力", "影响伴生特产的发现率", "光洁", "晶莹", "金边", "虹光", "灰暗", "斑驳", "霉斑", "漆黑", 'E'),

    /**
     * 风味/品质
     * +: 清香/蜜渍/溢香/发光
     * -: 生涩/发苦/腥臭/腐烂
     */
    QUALITY("风味品质", "影响烹饪时的星级上限", "清香", "蜜渍", "溢香", "发光", "生涩", "发苦", "腥臭", "腐烂", 'F');

    private final String name;
    private final String description;
    
    // Positive Tiers 1-4
    private final String posT1; // 3.0 - 4.9
    private final String posT2; // 5.0 - 6.9
    private final String posT3; // 7.0 - 8.9
    private final String posT4; // 9.0+
    
    // Negative Tiers 1-4
    private final String negT1; // 3.0 - 4.9
    private final String negT2; // 5.0 - 6.9
    private final String negT3; // 7.0 - 8.9
    private final String negT4; // 9.0+
    
    private final char geneLetter;

    Trait(String name, String description, 
          String posT1, String posT2, String posT3, String posT4,
          String negT1, String negT2, String negT3, String negT4, 
          char geneLetter) {
        this.name = name;
        this.description = description;
        this.posT1 = posT1;
        this.posT2 = posT2;
        this.posT3 = posT3;
        this.posT4 = posT4;
        this.negT1 = negT1;
        this.negT2 = negT2;
        this.negT3 = negT3;
        this.negT4 = negT4;
        this.geneLetter = geneLetter;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public char getGeneLetter() { return geneLetter; }

    /**
     * 获取性状形容词
     * @param score 基因型分数
     * @return 形容词，若未达到阈值(Abs<3)返回 null
     */
    public String getAdjective(double score) {
        double abs = Math.abs(score);
        if (abs < 3.0) return null;

        if (score > 0) {
            if (abs >= 9.0) return posT4;
            if (abs >= 7.0) return posT3;
            if (abs >= 5.0) return posT2;
            return posT1;
        } else {
            if (abs >= 9.0) return negT4;
            if (abs >= 7.0) return negT3;
            if (abs >= 5.0) return negT2;
            return negT1;
        }
    }
}
