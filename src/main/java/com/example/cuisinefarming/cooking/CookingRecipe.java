package com.example.cuisinefarming.cooking;

import org.bukkit.inventory.ItemStack;
import java.util.Map;

/**
 * 烹饪食谱定义
 * 定义了一道菜所需的食材标签组合、层级 (Tier) 以及成品。
 */
public class CookingRecipe {

    private final String id;
    private final String displayName;
    private final int tier; // 1, 2, 3
    private final Map<FoodTag, Integer> requirements; // 需要的标签及数量
    private final ItemStack resultTemplate; // 成品模板 (不含 PDC 数据，PDC 在烹饪结束时动态生成)
    
    // QTE & Cooking Parameters
    private final int cookingTime; // 烹饪所需时长 (ticks)
    private final double optimalTempMin; // 最佳温区下限
    private final double optimalTempMax; // 最佳温区上限

    public CookingRecipe(String id, String displayName, int tier, Map<FoodTag, Integer> requirements, ItemStack resultTemplate, int cookingTime, double optimalTempMin, double optimalTempMax) {
        this.id = id;
        this.displayName = displayName;
        this.tier = tier;
        this.requirements = requirements;
        this.resultTemplate = resultTemplate;
        this.cookingTime = cookingTime;
        this.optimalTempMin = optimalTempMin;
        this.optimalTempMax = optimalTempMax;
    }
    
    public int getCookingTime() {
        return cookingTime;
    }
    
    public double getOptimalTempMin() {
        return optimalTempMin;
    }
    
    public double getOptimalTempMax() {
        return optimalTempMax;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getTier() {
        return tier;
    }

    public Map<FoodTag, Integer> getRequirements() {
        return requirements;
    }

    public ItemStack getResultTemplate() {
        return resultTemplate.clone();
    }
    
    /**
     * 检查给定的标签统计是否满足此配方要求
     * @param providedTags 提供的标签及其数量
     * @return 是否满足
     */
    public boolean matches(Map<FoodTag, Integer> providedTags) {
        for (Map.Entry<FoodTag, Integer> entry : requirements.entrySet()) {
            FoodTag requiredTag = entry.getKey();
            int requiredAmount = entry.getValue();
            
            if (providedTags.getOrDefault(requiredTag, 0) < requiredAmount) {
                return false;
            }
        }
        return true;
    }
}
