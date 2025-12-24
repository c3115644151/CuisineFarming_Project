package com.example.cuisinefarming.listeners;

import com.example.cuisinefarming.CuisineFarming;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;

public class FoodConsumeListener implements Listener {

    private final CuisineFarming plugin;
    private final Random random = new Random();

    public FoodConsumeListener(CuisineFarming plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item.getType() == Material.AIR) return;
        if (!item.hasItemMeta() || !item.getItemMeta().hasCustomModelData()) return;

        int modelData = item.getItemMeta().getCustomModelData();
        Player player = event.getPlayer();
        ItemMeta meta = item.getItemMeta();
        
        // Get Star Rating
        int stars = 3; // Default
        NamespacedKey starKey = new NamespacedKey(plugin, "star_rating");
        if (meta.getPersistentDataContainer().has(starKey, PersistentDataType.INTEGER)) {
            stars = meta.getPersistentDataContainer().get(starKey, PersistentDataType.INTEGER);
        }
        
        // Calculate Multiplier
        float multiplier = 1.0f;
        switch (stars) {
            case 2: multiplier = 0.7f; break;
            case 3: multiplier = 1.0f; break;
            case 4: multiplier = 1.3f; break;
            case 5: multiplier = 2.0f; break;
            default: multiplier = 1.0f; // 1-star is dark food usually, but safe fallback
        }

        // Tier 1: Sustenance (High Saturation/Food)
        if (modelData == 20101) { // Simple Stew
            applySustenance(player, (int)(16 * multiplier), 20.0f * multiplier);
        } else if (modelData == 20102) { // Veggie Soup
            applySustenance(player, (int)(10 * multiplier), 12.0f * multiplier);
        } else if (modelData == 20103) { // Crispy Fish
            applySustenance(player, (int)(12 * multiplier), 14.0f * multiplier);
        } else if (modelData == 20104) { // Farmer's Breakfast
            applySustenance(player, (int)(14 * multiplier), 18.0f * multiplier);
        }
        
        // Tier 2: Utility (Multiplier affects Duration)
        else if (modelData == 20201) { // Miner's Tonic
            addEffect(player, PotionEffectType.HASTE, (int)(3600 * multiplier), 0);
            addEffect(player, PotionEffectType.NIGHT_VISION, (int)(3600 * multiplier), 0);
            player.playSound(player.getLocation(), Sound.ENTITY_WITCH_DRINK, 1.0f, 1.0f);
        } else if (modelData == 20202) { // Shepherd's Pie
            addEffect(player, PotionEffectType.ABSORPTION, (int)(6000 * multiplier), 1);
            applySustenance(player, 0, 10.0f * multiplier);
        } else if (modelData == 20203) { // Surf and Turf
            addEffect(player, PotionEffectType.STRENGTH, (int)(3600 * multiplier), 0);
            applySustenance(player, 0, 12.0f * multiplier);
        } else if (modelData == 20204) { // Spicy Curry
            addEffect(player, PotionEffectType.FIRE_RESISTANCE, (int)(12000 * multiplier), 0);
        }

        // Tier 3: Delicacy (Special)
        else if (modelData == 20301) { // Wisdom Broth
            handleWisdomBroth(player, stars);
        }
        // Royal Feast (20302) logic can be added later
    }

    private void applySustenance(Player player, int foodLevel, float saturation) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (foodLevel > 0) {
                player.setFoodLevel(Math.min(20, player.getFoodLevel() + foodLevel));
            }
            if (saturation > 0) {
                player.setSaturation(Math.min(player.getFoodLevel(), player.getSaturation() + saturation));
            }
        });
    }
    
    private void addEffect(Player player, PotionEffectType type, int duration, int amplifier) {
         player.addPotionEffect(new PotionEffect(type, duration, amplifier));
    }
    
    private void handleWisdomBroth(Player player, int stars) {
        double r = random.nextDouble(); // 0.0 - 1.0
        
        // Probabilities
        double pGold = 0.0026; // Base ~0.26% (1/384)
        double pHigh = 0.20;
        double pMed = 0.40;
        // pLow = remainder
        
        // Adjust by stars
        if (stars == 2) { 
            pGold = 0.001; 
            pHigh = 0.10; 
            pMed = 0.40; 
        } else if (stars == 4) { 
            pGold = 0.005; 
            pHigh = 0.35; 
            pMed = 0.40; 
        } else if (stars == 5) { 
            pGold = 0.010; // ~1%
            pHigh = 0.60; 
            pMed = 0.30; 
        }
        
        int xp = 0;
        
        if (r < pGold) {
            // GOLD! (Ultra Rare)
            xp = 2000;
            player.sendMessage("§d§l[Cuisine] §6§l传说降临！§e你在浓汤中领悟了宇宙终极真理！ (+2000 XP)");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 0.5f, 2.0f);
            // Broadcast
            plugin.getServer().broadcast(Component.text("§e§l[全服通告] §b玩家 " + player.getName() + " §b品尝了一碗绝世美味的智慧浓汤，顿悟飞升！"));
        } else if (r < pGold + pHigh) {
            // High (100-200)
            xp = 100 + random.nextInt(101);
            player.sendMessage("§d§l[Cuisine] §r灵光一闪！你的思绪如泉涌般爆发。 (+" + xp + " XP)");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        } else if (r < pGold + pHigh + pMed) {
            // Medium (50-100)
            xp = 50 + random.nextInt(51);
            player.sendMessage("§d§l[Cuisine] §r你感到思维变得敏捷了。 (+" + xp + " XP)");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        } else {
            // Low (20-50)
            xp = 20 + random.nextInt(31);
            player.sendMessage("§d§l[Cuisine] §7虽然有些平淡，但还是学到了一点东西。 (+" + xp + " XP)");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.8f);
        }
        
        player.giveExp(xp);
    }
}