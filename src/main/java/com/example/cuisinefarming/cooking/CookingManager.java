package com.example.cuisinefarming.cooking;

import com.example.cuisinefarming.CuisineFarming;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;

import java.util.*;

/**
 * 烹饪系统管理器
 * 负责维护食材标签映射、食谱注册以及配方匹配逻辑。
 */
public class CookingManager {

    // 物品材质 -> 标签集合 (一个物品可能有多个标签)
    private final Map<Material, Set<FoodTag>> materialTags = new HashMap<>();
    
    // 所有注册的食谱
    private final List<CookingRecipe> recipes = new ArrayList<>();

    public CookingManager(CuisineFarming plugin) {
        initializeTags();
        initializeRecipes();
    }

    /**
     * 初始化原版物品的标签映射
     */
    private void initializeTags() {
        // Starch
        registerTag(Material.WHEAT, FoodTag.STARCH);
        registerTag(Material.POTATO, FoodTag.STARCH, FoodTag.ROOT, FoodTag.VEGGIE);
        registerTag(Material.BAKED_POTATO, FoodTag.STARCH, FoodTag.ROOT);
        registerTag(Material.BREAD, FoodTag.STARCH);
        registerTag(Material.BEETROOT, FoodTag.VEGGIE, FoodTag.ROOT);

        // Meat
        registerTag(Material.BEEF, FoodTag.MEAT);
        registerTag(Material.COOKED_BEEF, FoodTag.MEAT);
        registerTag(Material.PORKCHOP, FoodTag.MEAT);
        registerTag(Material.COOKED_PORKCHOP, FoodTag.MEAT);
        registerTag(Material.CHICKEN, FoodTag.MEAT);
        registerTag(Material.COOKED_CHICKEN, FoodTag.MEAT);
        registerTag(Material.MUTTON, FoodTag.MEAT);
        registerTag(Material.COOKED_MUTTON, FoodTag.MEAT);
        registerTag(Material.RABBIT, FoodTag.MEAT);
        
        // Veggie & Fruit
        registerTag(Material.CARROT, FoodTag.VEGGIE, FoodTag.ROOT);
        registerTag(Material.APPLE, FoodTag.FRUIT);
        registerTag(Material.SWEET_BERRIES, FoodTag.FRUIT);
        registerTag(Material.MELON_SLICE, FoodTag.FRUIT);
        registerTag(Material.PUMPKIN, FoodTag.VEGGIE);
        registerTag(Material.KELP, FoodTag.VEGGIE);

        // Fish
        registerTag(Material.COD, FoodTag.FISH, FoodTag.MEAT);
        registerTag(Material.SALMON, FoodTag.FISH, FoodTag.MEAT);
        registerTag(Material.TROPICAL_FISH, FoodTag.FISH, FoodTag.MEAT);
        registerTag(Material.PUFFERFISH, FoodTag.FISH, FoodTag.MEAT);
        registerTag(Material.COOKED_COD, FoodTag.FISH, FoodTag.MEAT);
        registerTag(Material.COOKED_SALMON, FoodTag.FISH, FoodTag.MEAT);
        
        // Misc
        registerTag(Material.EGG, FoodTag.EGG);
        registerTag(Material.MILK_BUCKET, FoodTag.DAIRY, FoodTag.LIQUID);
        registerTag(Material.WATER_BUCKET, FoodTag.LIQUID);
        registerTag(Material.POTION, FoodTag.LIQUID);
        registerTag(Material.SUGAR, FoodTag.SPICE);
        registerTag(Material.BROWN_MUSHROOM, FoodTag.MUSHROOM, FoodTag.VEGGIE);
        registerTag(Material.RED_MUSHROOM, FoodTag.MUSHROOM, FoodTag.VEGGIE);
    }

    private void registerTag(Material mat, FoodTag... tags) {
        materialTags.computeIfAbsent(mat, k -> new HashSet<>()).addAll(Arrays.asList(tags));
    }

    /**
     * 初始化默认食谱
     */
    private void initializeRecipes() {
        // Tier 1: 简单的烤肉/炖菜
        // 炖肉 (Stew): 2肉 + 1蔬菜 (20s, 80-150°C)
        Map<FoodTag, Integer> stewReq = new HashMap<>();
        stewReq.put(FoodTag.MEAT, 2);
        stewReq.put(FoodTag.VEGGIE, 1);
        registerRecipe(new CookingRecipe("simple_stew", "家常炖肉", 1, stewReq, createResult(Material.RABBIT_STEW, "§f家常炖肉"), 400, 80.0, 150.0));

        // 蔬菜汤 (Veggie Soup): 2蔬菜 + 1液体 (16s, 70-130°C)
        Map<FoodTag, Integer> soupReq = new HashMap<>();
        soupReq.put(FoodTag.VEGGIE, 2);
        soupReq.put(FoodTag.LIQUID, 1);
        registerRecipe(new CookingRecipe("veggie_soup", "清淡蔬菜汤", 1, soupReq, createResult(Material.BEETROOT_SOUP, "§f清淡蔬菜汤"), 320, 70.0, 130.0));

        // Tier 2: 复合料理
        // 牧羊人派 (Shepherds Pie): 2淀粉 + 2肉 + 1蔬菜 (40s, 150-250°C)
        Map<FoodTag, Integer> pieReq = new HashMap<>();
        pieReq.put(FoodTag.STARCH, 2);
        pieReq.put(FoodTag.MEAT, 2);
        pieReq.put(FoodTag.VEGGIE, 1);
        registerRecipe(new CookingRecipe("shepherds_pie", "牧羊人派", 2, pieReq, createResult(Material.PUMPKIN_PIE, "§6牧羊人派"), 800, 150.0, 250.0));
        
        // 海陆大餐 (Surf and Turf): 1肉 + 1鱼 + 1淀粉 (30s, 120-200°C)
        Map<FoodTag, Integer> surfReq = new HashMap<>();
        surfReq.put(FoodTag.MEAT, 1);
        surfReq.put(FoodTag.FISH, 1);
        surfReq.put(FoodTag.STARCH, 1);
        registerRecipe(new CookingRecipe("surf_and_turf", "海陆大餐", 2, surfReq, createResult(Material.COOKED_SALMON, "§b海陆大餐"), 600, 120.0, 200.0));
    }
    
    private void registerRecipe(CookingRecipe recipe) {
        recipes.add(recipe);
    }

    private ItemStack createResult(Material type, String name) {
        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 获取物品的标签集合
     */
    public Set<FoodTag> getItemTags(ItemStack item) {
        if (item == null) return Collections.emptySet();
        return materialTags.getOrDefault(item.getType(), Collections.emptySet());
    }

    /**
     * 核心逻辑: 根据投入的食材匹配最佳食谱
     * @param ingredients 锅内的食材列表
     * @return 匹配到的食谱，如果没有匹配则返回 null (黑暗料理)
     */
    public CookingRecipe matchRecipe(List<ItemStack> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) return null;

        // 1. 统计提供的所有标签
        Map<FoodTag, Integer> providedTags = new HashMap<>();
        
        for (ItemStack item : ingredients) {
            if (item == null || item.getType() == Material.AIR) continue;
            
            Set<FoodTag> tags = getItemTags(item);
            int amount = item.getAmount(); // 通常投进去是一个一个投，但以防万一
            
            for (FoodTag tag : tags) {
                providedTags.put(tag, providedTags.getOrDefault(tag, 0) + amount);
            }
        }

        // 2. 遍历食谱寻找匹配
        // 优先匹配 Tier 高的，如果 Tier 相同，优先匹配需求标签数多的 (更精准的)
        CookingRecipe bestMatch = null;
        
        for (CookingRecipe recipe : recipes) {
            if (recipe.matches(providedTags)) {
                if (bestMatch == null) {
                    bestMatch = recipe;
                } else {
                    // 比较优先级
                    if (recipe.getTier() > bestMatch.getTier()) {
                        bestMatch = recipe;
                    } else if (recipe.getTier() == bestMatch.getTier()) {
                        // 同级比较复杂度 (需求的总数量)
                        int currentReqCount = recipe.getRequirements().values().stream().mapToInt(Integer::intValue).sum();
                        int bestReqCount = bestMatch.getRequirements().values().stream().mapToInt(Integer::intValue).sum();
                        
                        if (currentReqCount > bestReqCount) {
                            bestMatch = recipe;
                        }
                    }
                }
            }
        }
        
        return bestMatch;
    }
}
