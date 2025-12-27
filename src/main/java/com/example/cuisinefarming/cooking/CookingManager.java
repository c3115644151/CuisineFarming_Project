package com.example.cuisinefarming.cooking;

import com.example.cuisinefarming.CuisineFarming;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * 烹饪系统管理器
 * 负责维护食材标签映射、食谱注册以及配方匹配逻辑。
 */
public class CookingManager {

    private final CuisineFarming plugin;
    // 物品材质 -> 标签集合 (一个物品可能有多个标签)
    private final Map<Material, Set<FoodTag>> materialTags = new HashMap<>();
    
    // 所有注册的食谱
    private final List<CookingRecipe> recipes = new ArrayList<>();

    public CookingManager(CuisineFarming plugin) {
        this.plugin = plugin;
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
        // --- Tier 1: 饱食 (Sustenance) ---
        // 家常炖肉 (Simple Stew): 2肉 + 1蔬菜 (20s, 80-150°C)
        Map<FoodTag, Integer> stewReq = new HashMap<>();
        stewReq.put(FoodTag.MEAT, 2);
        stewReq.put(FoodTag.VEGGIE, 1);
        registerRecipe(new CookingRecipe("simple_stew", "家常炖肉", 1, stewReq, plugin.getItemManager().getItem("SIMPLE_STEW"), 400, 80.0, 150.0));

        // 蔬菜汤 (Veggie Soup): 2蔬菜 + 1液体 (16s, 70-130°C)
        Map<FoodTag, Integer> soupReq = new HashMap<>();
        soupReq.put(FoodTag.VEGGIE, 2);
        soupReq.put(FoodTag.LIQUID, 1);
        registerRecipe(new CookingRecipe("veggie_soup", "清淡蔬菜汤", 1, soupReq, plugin.getItemManager().getItem("VEGGIE_SOUP"), 320, 70.0, 130.0));

        // 酥脆炸鱼 (Crispy Fish): 1鱼 + 1淀粉 + 1香料 (20s, 160-220°C)
        Map<FoodTag, Integer> fishReq = new HashMap<>();
        fishReq.put(FoodTag.FISH, 1);
        fishReq.put(FoodTag.STARCH, 1);
        fishReq.put(FoodTag.SPICE, 1);
        registerRecipe(new CookingRecipe("crispy_fish", "酥脆炸鱼", 1, fishReq, plugin.getItemManager().getItem("CRISPY_FISH"), 400, 160.0, 220.0));

        // 农夫早餐 (Farmer's Breakfast): 1蛋 + 1淀粉 + 1乳制品 (15s, 60-120°C)
        Map<FoodTag, Integer> breakfastReq = new HashMap<>();
        breakfastReq.put(FoodTag.EGG, 1);
        breakfastReq.put(FoodTag.STARCH, 1);
        breakfastReq.put(FoodTag.DAIRY, 1);
        registerRecipe(new CookingRecipe("farmers_breakfast", "农夫早餐", 1, breakfastReq, plugin.getItemManager().getItem("FARMERS_BREAKFAST"), 300, 60.0, 120.0));

        // --- Tier 2: 效能 (Utility) ---
        // 矿工特饮 (Miner's Tonic): 1液体 + 1水果 + 1香料 (10s, 40-90°C)
        Map<FoodTag, Integer> minerReq = new HashMap<>();
        minerReq.put(FoodTag.LIQUID, 1);
        minerReq.put(FoodTag.FRUIT, 1);
        minerReq.put(FoodTag.SPICE, 1);
        registerRecipe(new CookingRecipe("miners_tonic", "矿工特饮", 2, minerReq, plugin.getItemManager().getItem("MINERS_TONIC"), 200, 40.0, 90.0));

        // 牧羊人派 (Shepherd's Pie): 2淀粉 + 2肉 + 1蔬菜 (40s, 150-250°C)
        Map<FoodTag, Integer> pieReq = new HashMap<>();
        pieReq.put(FoodTag.STARCH, 2);
        pieReq.put(FoodTag.MEAT, 2);
        pieReq.put(FoodTag.VEGGIE, 1);
        registerRecipe(new CookingRecipe("shepherds_pie", "牧羊人派", 2, pieReq, plugin.getItemManager().getItem("SHEPHERDS_PIE"), 800, 150.0, 250.0));
        
        // 海陆大餐 (Surf and Turf): 1肉 + 1鱼 + 1淀粉 (30s, 120-200°C)
        Map<FoodTag, Integer> surfReq = new HashMap<>();
        surfReq.put(FoodTag.MEAT, 1);
        surfReq.put(FoodTag.FISH, 1);
        surfReq.put(FoodTag.STARCH, 1);
        registerRecipe(new CookingRecipe("surf_and_turf", "海陆大餐", 2, surfReq, plugin.getItemManager().getItem("SURF_AND_TURF"), 600, 120.0, 200.0));

        // 辛辣咖喱 (Spicy Curry): 1肉 + 1蔬菜 + 1香料 (30s, 100-180°C)
        Map<FoodTag, Integer> curryReq = new HashMap<>();
        curryReq.put(FoodTag.MEAT, 1);
        curryReq.put(FoodTag.VEGGIE, 1);
        curryReq.put(FoodTag.SPICE, 1);
        registerRecipe(new CookingRecipe("spicy_curry", "辛辣咖喱", 2, curryReq, plugin.getItemManager().getItem("SPICY_CURRY"), 600, 100.0, 180.0));

        // --- Tier 3: 珍馐 (Delicacy) ---
        // 智慧浓汤 (Wisdom Broth): 1蘑菇 + 1蔬菜 + 1乳制品 + 1香料 (50s, 90-140°C)
        Map<FoodTag, Integer> wisdomReq = new HashMap<>();
        wisdomReq.put(FoodTag.MUSHROOM, 1);
        wisdomReq.put(FoodTag.VEGGIE, 1);
        wisdomReq.put(FoodTag.DAIRY, 1);
        wisdomReq.put(FoodTag.SPICE, 1);
        registerRecipe(new CookingRecipe("wisdom_broth", "智慧浓汤", 3, wisdomReq, plugin.getItemManager().getItem("WISDOM_BROTH"), 1000, 90.0, 140.0));

        // 皇家盛宴 (Royal Feast): 2肉 + 1鱼 + 1水果 + 1乳制品 + 1淀粉 (60s, 140-220°C)
        Map<FoodTag, Integer> royalReq = new HashMap<>();
        royalReq.put(FoodTag.MEAT, 2);
        royalReq.put(FoodTag.FISH, 1);
        royalReq.put(FoodTag.FRUIT, 1);
        royalReq.put(FoodTag.DAIRY, 1);
        royalReq.put(FoodTag.STARCH, 1);
        registerRecipe(new CookingRecipe("royal_feast", "皇家盛宴", 3, royalReq, plugin.getItemManager().getItem("ROYAL_FEAST"), 1200, 140.0, 220.0));
    }
    
    private void registerRecipe(CookingRecipe recipe) {
        recipes.add(recipe);
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

    // --- Cooking Pot Management ---
    private final Map<Location, CookingPot> activePots = new HashMap<>();

    public CookingPot getPot(Location location) {
        return activePots.get(location);
    }

    public boolean hasPot(Location location) {
        return activePots.containsKey(location);
    }

    public CookingPot createPot(Location location) {
        CookingPot pot = new CookingPot(plugin, location);
        activePots.put(location, pot);
        return pot;
    }

    public void removePot(Location location) {
        CookingPot pot = activePots.remove(location);
        if (pot != null) {
            pot.destroy();
        }
    }

    public void savePots() {
        File file = new File(plugin.getDataFolder(), "cooking_pots.yml");
        YamlConfiguration config = new YamlConfiguration();
        
        int index = 0;
        for (CookingPot pot : activePots.values()) {
            String path = "pots." + index;
            config.set(path + ".world", pot.getLocation().getWorld().getName());
            config.set(path + ".x", pot.getLocation().getBlockX());
            config.set(path + ".y", pot.getLocation().getBlockY());
            config.set(path + ".z", pot.getLocation().getBlockZ());
            
            // Save Inventory
            for (int i = 0; i < pot.getInventory().getSize(); i++) {
                ItemStack item = pot.getInventory().getItem(i);
                if (item != null && item.getType() != Material.AIR) {
                    config.set(path + ".inventory." + i, item);
                }
            }
            index++;
        }
        
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save cooking pots!", e);
        }
    }

    public void loadPots() {
        File file = new File(plugin.getDataFolder(), "cooking_pots.yml");
        if (!file.exists()) return;
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection potsSection = config.getConfigurationSection("pots");
        if (potsSection == null) return;
        
        for (String key : potsSection.getKeys(false)) {
            try {
                String worldName = potsSection.getString(key + ".world");
                int x = potsSection.getInt(key + ".x");
                int y = potsSection.getInt(key + ".y");
                int z = potsSection.getInt(key + ".z");
                
                org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
                if (world == null) continue;
                
                Location loc = new Location(world, x, y, z);
                // Verify it's still a cauldron
                if (loc.getBlock().getType() != Material.CAULDRON && loc.getBlock().getType() != Material.WATER_CAULDRON) {
                    continue; 
                }
                
                CookingPot pot = createPot(loc);
                
                // Load Inventory
                ConfigurationSection invSection = potsSection.getConfigurationSection(key + ".inventory");
                if (invSection != null) {
                    for (String slotStr : invSection.getKeys(false)) {
                        int slot = Integer.parseInt(slotStr);
                        ItemStack item = invSection.getItemStack(slotStr);
                        pot.getInventory().setItem(slot, item);
                    }
                }
                
                // Refresh Visuals
                pot.updateVisuals();
                
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load a cooking pot: " + key);
            }
        }
    }
}
