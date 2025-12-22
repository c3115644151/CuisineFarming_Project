package com.example.cuisinefarming.genetics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 基因数据 (Gene Data) - [重构版]
 * 存储一颗种子或作物的所有遗传信息。
 * 核心结构: Map<Trait, GenePair>
 */
public class GeneData {

    private final Map<Trait, GenePair> genes;
    private boolean identified;

    public GeneData() {
        this.genes = new HashMap<>();
        this.identified = false;
        initializeDefaults();
    }
    
    public GeneData(Map<Trait, GenePair> genes, boolean identified) {
        this.genes = new HashMap<>(genes);
        this.identified = identified;
        // 确保所有性状都存在
        for (Trait trait : Trait.values()) {
            this.genes.putIfAbsent(trait, new GenePair(Allele.DOMINANT_1, Allele.RECESSIVE_1));
        }
    }

    private void initializeDefaults() {
        // 默认全是普通型 (A1 + a1 = 0)
        for (Trait trait : Trait.values()) {
            genes.put(trait, new GenePair(Allele.DOMINANT_1, Allele.RECESSIVE_1));
        }
    }
    
    public boolean isDefault() {
        for (Trait trait : Trait.values()) {
            GenePair pair = genes.get(trait);
            // Default is DOMINANT_1 (1) + RECESSIVE_1 (-1)
            if (pair.getFirst() != Allele.DOMINANT_1 || pair.getSecond() != Allele.RECESSIVE_1) {
                return false;
            }
        }
        return true;
    }

    public GenePair getGenePair(Trait trait) {
        return genes.getOrDefault(trait, new GenePair(Allele.DOMINANT_1, Allele.RECESSIVE_1));
    }

    public void setGenePair(Trait trait, GenePair pair) {
        genes.put(trait, pair);
    }

    public boolean isIdentified() {
        return identified;
    }

    public void setIdentified(boolean identified) {
        this.identified = identified;
    }
    
    public Map<Trait, GenePair> getAllGenes() {
        return new HashMap<>(genes);
    }
    
    // --- 随机生成逻辑 ---
    
    /**
     * 随机生成初始基因 (用于战利品/商店种子)
     * 正态分布设计：
     * - 60% 普通 (0分): [A1, a1]
     * - 20% 偏差I (±1/±2分): [A1, A1] 或 [a1, a1] 或 [A2, a1]
     * - 15% 偏差II (±3/±4分): [A2, A2] 或 [a2, a2]
     * - 5% 极端 (±5/±6分): [A3, A3] 或 [a3, a3]
     */
    public void randomize() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        
        for (Trait trait : Trait.values()) {
            genes.put(trait, generateRandomPair(random));
        }
    }
    
    private GenePair generateRandomPair(ThreadLocalRandom random) {
        double roll = random.nextDouble();
        
        // 60% 普通: A1 + a1 = 0
        if (roll < 0.60) {
            return new GenePair(Allele.DOMINANT_1, Allele.RECESSIVE_1);
        }
        
        // 20% 轻微偏差 (±1 ~ ±2)
        else if (roll < 0.80) {
            // 随机偏向正向或负向
            boolean positive = random.nextBoolean();
            if (positive) {
                // 正向: A1+A1(+2) 或 A2+a1(+1)
                return random.nextBoolean() ? 
                    new GenePair(Allele.DOMINANT_1, Allele.DOMINANT_1) :
                    new GenePair(Allele.DOMINANT_2, Allele.RECESSIVE_1);
            } else {
                // 负向: a1+a1(-2) 或 A1+a2(-1)
                return random.nextBoolean() ?
                    new GenePair(Allele.RECESSIVE_1, Allele.RECESSIVE_1) :
                    new GenePair(Allele.DOMINANT_1, Allele.RECESSIVE_2);
            }
        }
        
        // 15% 显著偏差 (±3 ~ ±4)
        else if (roll < 0.95) {
             boolean positive = random.nextBoolean();
             if (positive) {
                 // 正向: A2+A2(+4) 或 A3+a1(+2 -> 实际上+2在上一档，这里我们给强一点的)
                 // 修正: A2+A1(+3)
                 return new GenePair(Allele.DOMINANT_2, Allele.DOMINANT_1);
             } else {
                 // 负向: a2+a1(-3)
                 return new GenePair(Allele.RECESSIVE_2, Allele.RECESSIVE_1);
             }
        }
        
        // 5% 极端突变 (±5 ~ ±6)
        else {
             boolean positive = random.nextBoolean();
             if (positive) {
                 // A3+A3(+6) 或 A3+A2(+5)
                 return random.nextBoolean() ?
                    new GenePair(Allele.DOMINANT_3, Allele.DOMINANT_3) :
                    new GenePair(Allele.DOMINANT_3, Allele.DOMINANT_2);
             } else {
                 // a3+a3(-6) 或 a3+a2(-5)
                 return random.nextBoolean() ?
                    new GenePair(Allele.RECESSIVE_3, Allele.RECESSIVE_3) :
                    new GenePair(Allele.RECESSIVE_3, Allele.RECESSIVE_2);
             }
        }
    }
    
    // --- 辅助方法 ---
    
    /**
     * 计算该作物的总评分 (用于显示星级)
     * 简单累加所有基因的表现值
     */
    public int calculateStarRating() {
        double totalScore = 0;
        for (GenePair pair : genes.values()) {
            totalScore += pair.getPhenotypeValue();
        }
        
        // 归一化到 1-5 星
        // 假设有 5 个性状
        // 最低: 5 * 2 = 10 分 -> 1星
        // 最高: 5 * 6 = 30 分 -> 5星
        
        // 重新调整星级算法
        double avgScore = totalScore / Trait.values().length;
        
        if (avgScore >= 8.0) return 5;
        if (avgScore >= 6.0) return 4;
        if (avgScore >= 4.0) return 3;
        if (avgScore >= 3.0) return 2;
        return 1;
    }

    @Override
    public String toString() {
        return "GeneData{genes=" + genes + "}";
    }
}
