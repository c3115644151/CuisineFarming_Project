package com.example.cuisinefarming.genetics;

import java.util.Objects;

/**
 * 基因对 (Gene Pair)
 * 代表控制某一个性状的一对等位基因。
 * 例如: [S, D] 控制 "生长速度"
 */
public class GenePair {
    private final Allele first;
    private final Allele second;

    // 解析 "W:D" -> GenePair
    // 注意：旧版存档可能包含 "W", "D", "S"，需要做兼容处理
    // 但由于 Allele 枚举已变，旧的 .valueOf 会失效。
    // 我们依赖 GeneticsManager 的 parseAllele 方法来处理兼容。
    public GenePair(Allele first, Allele second) {
        // 排序以保证 [A1, a1] 和 [a1, A1] 被视为相同
        if (first.getValue() >= second.getValue()) {
            this.first = first;
            this.second = second;
        } else {
            this.first = second;
            this.second = first;
        }
    }

    public Allele getFirst() {
        return first;
    }

    public Allele getSecond() {
        return second;
    }

    /**
     * 获取表现值 (Phenotype Value)
     * 计算逻辑：
     * 1. 纯合子 (Homozygous): A + B
     * 2. 杂合子 (Heterozygous): (A + B) * 1.5 (杂种优势 Heterosis)
     */
    public double getPhenotypeValue() {
        double rawSum = first.getValue() + second.getValue();
        
        if (isHeterozygous()) {
            // 杂种优势：加成 100% (2.0x)
            // 使得 A3 + A2 (5 * 2 = 10) 远超 A3 + A3 (6)
            return rawSum * 2.0;
        } else {
            // 纯合子：无加成
            return rawSum;
        }
    }

    /**
     * 是否为杂合子
     */
    public boolean isHeterozygous() {
        return first != second;
    }

    /**
     * 获取显示字符串，如 "[A1, a1]"
     */
    public String getDisplayString(Trait trait) {
        return String.format("[%s, %s]", first.getCode(trait), second.getCode(trait));
    }

    @Override
    public String toString() {
        // Fallback for debugging if Trait is unknown
        return String.format("[%d, %d]", first.getValue(), second.getValue());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GenePair genePair = (GenePair) o;
        return first == genePair.first && second == genePair.second;
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second);
    }
}
