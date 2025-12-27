package com.example.cuisinefarming;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CuisineItemManager {
    private final CuisineFarming plugin;
    private final Map<String, ItemStack> customItems = new HashMap<>();

    public CuisineItemManager(CuisineFarming plugin) {
        this.plugin = plugin;
        registerItems();
    }

    private void registerItems() {
        plugin.getLogger().info("Registering cuisine farming items...");

        // 肥料
        registerItem("ORGANIC_FERTILIZER", Material.BONE_MEAL, "有机肥料", 20001, "§7由腐殖质发酵而成。", "§a+20 土地肥力");
        registerItem("CHEMICAL_FERTILIZER", Material.SUGAR, "高效化肥", 20002, "§7工业合成的高效肥料。", "§b+50 土地肥力", "§c注意：过度使用可能导致土地板结。");
        
        // 农具
        registerItem("FARMER_MONOCLE", Material.LEATHER_HELMET, "农夫单片镜", 20003, "§7戴上它，看透土地的本质。", "§e佩戴在头上时，看向耕地可显示肥力。");
        
        // 机器 (工作方块)
        registerItem("SEED_ANALYZER", Material.IRON_BLOCK, "遗传分析仪", 20004, "§7用于鉴定未知种子或生物DNA的基因数据。", "§e放置后右键打开工作界面。");

        // 杂交工具
        registerItem("POLLEN_PAPER", Material.PAPER, "花粉采样纸", 20005, "§7用于采集和传播作物花粉。", "§e右键作物采集花粉，", "§e再次右键其他作物进行授粉。");

        // 烹饪 - 工具
        registerItem("HAND_FAN", Material.FEATHER, "蒲扇", 20006, "§7轻轻一扇，火势大增。", "§e对着营火/厨锅右键扇风。", "§6用途: 控制火候");
        registerItem("WOODEN_LADLE", Material.BOWL, "木汤勺", 20007, "§7用来搅拌汤汁或盛出料理。", "§e右键搅拌厨锅。", "§6用途: 混合/降温/确认烹饪");
        
        // 烹饪 - 容器
        registerItem("COOKING_POT", Material.CAULDRON, "厨锅", 20008, "§7一口厚实的铁锅。", "§e放置在营火上方使用。", "§6用途: 烹饪食物");

        // --- 烹饪成品 (Foods) ---
        // Tier 1: 饱食 (Sustenance) - 高饱食度，性价比高
        registerFood("SIMPLE_STEW", Material.RABBIT_STEW, "家常炖肉", 20101, 1, "§f基础料理", "§7普通的炖肉，管饱。", "§a恢复大量饱食度(16)");
        registerFood("VEGGIE_SOUP", Material.BEETROOT_SOUP, "清淡蔬菜汤", 20102, 1, "§f基础料理", "§7适合素食者。", "§a恢复中等饱食度(10)");
        registerFood("CRISPY_FISH", Material.COOKED_COD, "酥脆炸鱼", 20103, 1, "§f基础料理", "§7外酥里嫩。", "§a恢复中等饱食度(12)");
        registerFood("FARMERS_BREAKFAST", Material.BREAD, "农夫早餐", 20104, 1, "§f基础料理", "§7开启活力满满的一天。", "§a恢复大量饱食度(14)");

        // Tier 2: 效能 (Utility) - 提供 Buff
        registerFood("MINERS_TONIC", Material.HONEY_BOTTLE, "矿工特饮", 20201, 2, "§b功能饮料", "§7富含矿物质。", "§b效果: 急迫 & 夜视 (3分钟)");
        registerFood("SHEPHERDS_PIE", Material.PUMPKIN_PIE, "牧羊人派", 20202, 2, "§b经典美食", "§7一层肉一层土豆泥。", "§b效果: 伤害吸收 II (5分钟)");
        registerFood("SURF_AND_TURF", Material.COOKED_SALMON, "海陆大餐", 20203, 2, "§b经典美食", "§7山珍海味的完美结合。", "§b效果: 力量 I (3分钟)");
        registerFood("SPICY_CURRY", Material.MUSHROOM_STEW, "辛辣咖喱", 20204, 2, "§b异域风情", "§7辣得过瘾！", "§b效果: 抗火 (10分钟)");

        // Tier 3: 珍馐 (Delicacy) - 永久增益/特殊效果
        registerFood("WISDOM_BROTH", Material.MUSHROOM_STEW, "智慧浓汤", 20301, 3, "§d传说料理", "§7据说能让人变聪明。", "§d效果: 获得随机经验值");
        registerFood("ROYAL_FEAST", Material.CAKE, "皇家盛宴", 20302, 3, "§d传说料理", "§7足以招待国王的宴席。", "§d效果: 全员恢复 + 饱和 (范围光环)");

        plugin.getLogger().info("Registered " + customItems.size() + " cuisine items.");
    }

    private void registerFood(String key, Material material, String name, int modelData, int tier, String type, String desc, String effect) {
        String tierStr = "";
        switch (tier) {
            case 1: tierStr = "§a[Tier 1: 饱食]"; break;
            case 2: tierStr = "§b[Tier 2: 效能]"; break;
            case 3: tierStr = "§d[Tier 3: 珍馐]"; break;
        }
        registerItem(key, material, name, modelData, tierStr, type, desc, effect);
    }

    private void registerItem(String key, Material material, String name, int modelData, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.GREEN));
            meta.setCustomModelData(modelData);
            
            List<Component> lore = new java.util.ArrayList<>();
            for (String line : loreLines) {
                lore.add(Component.text(line));
            }
            meta.lore(lore);
            
            item.setItemMeta(meta);
        }
        customItems.put(key, item);
    }

    public ItemStack getItem(String key) {
        return customItems.get(key) != null ? customItems.get(key).clone() : null;
    }
    
    public boolean isCustomItem(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemStack target = customItems.get(key);
        if (target == null) return false;
        
        // Check CustomModelData match
        if (item.getType() != target.getType()) return false;
        if (!item.getItemMeta().hasCustomModelData()) return false;
        return item.getItemMeta().getCustomModelData() == target.getItemMeta().getCustomModelData();
    }
}
