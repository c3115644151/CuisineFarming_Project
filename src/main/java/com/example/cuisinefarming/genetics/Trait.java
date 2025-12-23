package com.example.cuisinefarming.genetics;

/**
 * 性状定义 (Trait Definition)
 * 定义了作物可能拥有的所有特性类型。
 */
public enum Trait {
    // --- 形态类 (Morphology) ---
    /**
     * 生长势 (原:生长速度)
     * +: 萌动/活力/躁动/疯长
     * -: 慵懒/沉睡/僵硬/石化
     */
    GROWTH_SPEED("生长势", "决定株高与分蘖速率", "萌动", "活力", "躁动", "疯长", "慵懒", "沉睡", "僵硬", "石化", 'A'),
    
    /**
     * 结实量 (原:丰产能力)
     * +: 饱满/沉重/满溢/炸裂
     * -: 轻飘/干瘪/空心/尘埃
     */
    YIELD("结实量", "决定穗粒结构与果实密度", "饱满", "沉重", "满溢", "炸裂", "轻飘", "干瘪", "空心", "尘埃", 'B'),

    // --- 适应类 (Adaptation) ---
    /**
     * 表皮结构 (原:温度适应)
     * +: 厚皮/粗糙/绒毛/披甲
     * -: 薄皮/娇嫩/剔透/气泡
     */
    TEMPERATURE_TOLERANCE("表皮结构", "决定角质层厚度与气孔密度", "厚皮", "粗糙", "绒毛", "披甲", "薄皮", "娇嫩", "剔透", "气泡", 'C'),

    /**
     * 根系发达度 (原:土壤适应)
     * +: 壮实/黝黑/贪吃/暴食
     * -: 瘦弱/苍白/敏感/焦黑
     */
    SOIL_TOLERANCE("根系发达度", "决定根冠比与侧根生物量", "壮实", "黝黑", "贪吃", "暴食", "瘦弱", "苍白", "敏感", "焦黑", 'D'),

    // --- 特殊类 (Special) ---
    /**
     * 伴生性状 (原:珍稀潜力)
     * +: 光洁/晶莹/金边/虹光
     * -: 灰暗/斑驳/霉斑/漆黑
     */
    LUCK("伴生性状", "决定共生菌群与特殊代谢产物", "光洁", "晶莹", "金边", "虹光", "灰暗", "斑驳", "霉斑", "漆黑", 'E'),

    /**
     * 营养质地 (原:风味品质)
     * +: 清香/蜜渍/溢香/发光
     * -: 生涩/发苦/腥臭/腐烂
     */
    QUALITY("营养质地", "决定干物质积累与纤维含量", "清香", "蜜渍", "溢香", "发光", "生涩", "发苦", "腥臭", "腐烂", 'F');

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
            if (abs >= 11.0) return posT4;
            if (abs >= 8.0) return posT3;
            if (abs >= 5.0) return posT2;
            return posT1;
        } else {
            if (abs >= 11.0) return negT4;
            if (abs >= 8.0) return negT3;
            if (abs >= 5.0) return negT2;
            return negT1;
        }
    }
}
