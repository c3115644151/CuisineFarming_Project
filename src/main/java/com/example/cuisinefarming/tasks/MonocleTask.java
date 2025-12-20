package com.example.cuisinefarming.tasks;

import com.example.cuisinefarming.CuisineFarming;
import com.example.cuisinefarming.fertility.FertilityManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class MonocleTask extends BukkitRunnable {

    private final CuisineFarming plugin;
    private final FertilityManager fertilityManager;

    public MonocleTask(CuisineFarming plugin) {
        this.plugin = plugin;
        this.fertilityManager = plugin.getFertilityManager();
    }

    @Override
    public void run() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (hasMonocle(player)) {
                // Raytrace with fluid handling and ignore passables if needed
                // But simple getTargetBlockExact(10) hits the first block.
                // We need to penetrate crops.
                
                Block target = player.getTargetBlockExact(10);
                
                // If target is null or AIR, try manual raytrace or just skip.
                if (target == null) continue;

                Block soil = null;

                if (target.getType() == Material.FARMLAND) {
                    soil = target;
                } else if (isCrop(target.getType())) {
                    // If looking at crop, check block below
                    Block below = target.getRelative(0, -1, 0);
                    if (below.getType() == Material.FARMLAND) {
                        soil = below;
                    }
                }

                if (soil != null) {
                    int fertility = fertilityManager.calculateCurrentFertility(soil);
                    double concentration = fertilityManager.getConcentration(soil);
                    
                    // Fertility Feedback
                    String color;
                    if (fertility >= 100) color = "§6"; // Gold for overflow
                    else if (fertility > 50) color = "§a"; // Green for good
                    else if (fertility > 0) color = "§e"; // Yellow for ok
                    else color = "§c"; // Red for bad
                    
                    String status = "";
                    if (fertility > 100) status = " (溢出)";
                    else if (fertility < 0) status = " (贫瘠)";
                    
                    // Concentration Feedback
                    String concColor = "§a";
                    String concStatus = "";
                    if (concentration > 200) {
                        concColor = "§4"; // Dark Red (Toxic)
                        concStatus = " (剧毒)";
                    } else if (concentration > 100) {
                        concColor = "§c"; // Red (Warning)
                        concStatus = " (过量)";
                    } else if (concentration > 0) {
                        concColor = "§e"; // Yellow (Active)
                    } else {
                        concColor = "§7"; // Gray (None)
                    }
                    
                    player.sendActionBar(Component.text(color + "土地肥力: " + fertility + status + 
                        " §f| " + concColor + "肥料浓度: " + String.format("%.1f", concentration) + concStatus));
                }
            }
        }
    }

    private boolean isCrop(Material material) {
        // Simple check for common crops
        return material == Material.WHEAT || 
               material == Material.CARROTS || 
               material == Material.POTATOES || 
               material == Material.BEETROOTS ||
               material == Material.MELON_STEM ||
               material == Material.PUMPKIN_STEM ||
               material == Material.ATTACHED_MELON_STEM ||
               material == Material.ATTACHED_PUMPKIN_STEM ||
               material == Material.NETHER_WART;
    }

    private boolean hasMonocle(Player player) {
        // Only Check Helmet
        return isMonocle(player.getInventory().getHelmet());
    }

    private boolean isMonocle(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        
        return plugin.getItemManager().isCustomItem(item, "FARMER_MONOCLE");
    }
}
