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
        registerItem("SEED_ANALYZER", Material.IRON_BLOCK, "种子分析仪", 20004, "§7用于鉴定未知种子的基因数据。", "§e放置后右键打开工作界面。");

        // 杂交工具
        registerItem("POLLEN_PAPER", Material.PAPER, "花粉采样纸", 20005, "§7用于采集和传播作物花粉。", "§e右键作物采集花粉，", "§e再次右键其他作物进行授粉。");

        plugin.getLogger().info("Registered " + customItems.size() + " cuisine items.");
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
