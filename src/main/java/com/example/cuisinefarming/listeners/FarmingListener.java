package com.example.cuisinefarming.listeners;

import com.example.cuisinefarming.CuisineFarming;
import com.example.cuisinefarming.fertility.FertilityManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.MoistureChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.data.type.Farmland;

import java.util.Random;

public class FarmingListener implements Listener {

    private final CuisineFarming plugin;
    private final FertilityManager fertilityManager;
    private final Random random = new Random();

    public FarmingListener(CuisineFarming plugin) {
        this.plugin = plugin;
        this.fertilityManager = plugin.getFertilityManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // 0. Monitor Moisture Change (Fix: Only count time when wet)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMoistureChange(MoistureChangeEvent event) {
        if (event.getBlock().getType() != Material.FARMLAND) return;
        
        // If becoming wet (from 0 to >0)
        boolean wasDry = false;
        if (event.getBlock().getBlockData() instanceof Farmland oldState) {
            wasDry = oldState.getMoisture() == 0;
        }
        
        boolean willBeWet = false;
        if (event.getNewState() instanceof Farmland newState) {
            willBeWet = newState.getMoisture() > 0;
        }
        
        if (wasDry && willBeWet) {
            // Reset timer so the dry period doesn't count for recovery
            fertilityManager.resetRecoveryTimer(event.getBlock());
        }
    }

    // 1. Monitor Crop Growth
    @EventHandler(priority = EventPriority.NORMAL)
    public void onCropGrow(BlockGrowEvent event) {
        Block block = event.getBlock();
        Block soil = block.getRelative(0, -1, 0);

        if (soil.getType() != Material.FARMLAND) return;

        // Check fertility
        // Use the unified calculation method for consistency
        double totalEfficiency = fertilityManager.calculateTotalEfficiency(soil);
        
        // Efficiency Logic:
        // Case 1: Low Efficiency (efficiency < 1.0)
        // We act as a filter for vanilla events.
        // If efficiency is 0.7, we want 70% of vanilla events to pass.
        // So we cancel if random > 0.7.
        // Chance to cancel = 1.0 - efficiency.
        
        if (totalEfficiency < 1.0) {
            // Ensure efficiency is non-negative for probability calculation
            double probability = Math.max(0.0, totalEfficiency);
            
            // If random roll (0.0 to 1.0) is GREATER than efficiency, we cancel.
            // Example: Eff = 0.7. Random = 0.8 -> Cancelled. Random = 0.5 -> Allowed.
            if (random.nextDouble() > probability) {
                event.setCancelled(true);
                return;
            }
        }
        
        // Case 2: High Efficiency (efficiency >= 1.0)
        // We do NOTHING here. We let the vanilla event pass 100%.
        // The EXTRA growth is handled by GrowthTask (Active Ticking).
        // Total Rate = Vanilla(1.0) + Task(efficiency - 1.0).

        // Consume fertility on growth (Small amount)
        fertilityManager.consumeFertility(soil, 2);
    }

    // 2. Monitor Harvest (Block Break)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCropHarvest(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!(block.getBlockData() instanceof Ageable ageable)) return;

        // Only care if it's fully grown
        if (ageable.getAge() != ageable.getMaximumAge()) return;

        Block soil = block.getRelative(0, -1, 0);
        if (soil.getType() != Material.FARMLAND) return;

        int fertility = fertilityManager.calculateCurrentFertility(soil);

        // Specialty Drop Logic (Integration with BiomeGifts)
        // Only if fertility > 100
        if (fertility > 100) {
            int overflow = fertility - 100;
            // +0.2% per point
            double dropChance = overflow * 0.002;
            
            if (random.nextDouble() < dropChance) {
                // Try to get drop item from BiomeGifts via Reflection
                ItemStack specialty = getBiomeGiftsSpecialty(block);
                
                if (specialty != null) {
                    block.getWorld().dropItemNaturally(block.getLocation(), specialty);
                    block.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, block.getLocation().add(0.5, 0.5, 0.5), 10);
                    event.getPlayer().sendMessage(Component.text("§6[地域馈赠] §f这块土地孕育出了罕见的珍品！(肥力加成)"));
                }
            }
        }

        // Consume larger amount of fertility on harvest
        fertilityManager.consumeFertility(soil, 15);
    }
    
    private ItemStack getBiomeGiftsSpecialty(Block block) {
        try {
            // 1. Get Plugin Instance
            org.bukkit.plugin.Plugin biomeGiftsPlugin = plugin.getServer().getPluginManager().getPlugin("BiomeGifts");
            if (biomeGiftsPlugin == null || !biomeGiftsPlugin.isEnabled()) return null;

            // 2. Get ConfigManager
            java.lang.reflect.Method getConfigManagerMethod = biomeGiftsPlugin.getClass().getMethod("getConfigManager");
            Object configManager = getConfigManagerMethod.invoke(biomeGiftsPlugin);

            // 3. Get CropConfig for this block type
            java.lang.reflect.Method getCropConfigMethod = configManager.getClass().getMethod("getCropConfig", Material.class);
            Object cropConfig = getCropConfigMethod.invoke(configManager, block.getType());
            if (cropConfig == null) return null;

            // 4. Get dropItem name (String field)
            java.lang.reflect.Field dropItemField = cropConfig.getClass().getField("dropItem");
            String dropItemKey = (String) dropItemField.get(cropConfig);

            // 5. Get ItemManager
            java.lang.reflect.Method getItemManagerMethod = biomeGiftsPlugin.getClass().getMethod("getItemManager");
            Object itemManager = getItemManagerMethod.invoke(biomeGiftsPlugin);

            // 6. Get ItemStack
            java.lang.reflect.Method getItemMethod = itemManager.getClass().getMethod("getItem", String.class);
            return (ItemStack) getItemMethod.invoke(itemManager, dropItemKey);

        } catch (Exception e) {
            // Fail silently or log debug
            // plugin.getLogger().warning("Failed to integrate with BiomeGifts: " + e.getMessage());
            return null;
        }
    }

    // 3. Monitor Fertilization (Player Interact)
    @EventHandler(priority = EventPriority.NORMAL)
    public void onFertilize(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;
        
        // Fix: Allow clicking on crops to fertilize the soil below
        if (clickedBlock.getType() != Material.FARMLAND) {
            Block below = clickedBlock.getRelative(0, -1, 0);
            if (below.getType() == Material.FARMLAND && clickedBlock.getBlockData() instanceof Ageable) {
                clickedBlock = below;
            } else {
                return;
            }
        }
        
        ItemStack item = event.getItem();
        if (item == null) return;

        double concAmount = 0.0;
        
        boolean isOrganic = plugin.getItemManager().isCustomItem(item, "ORGANIC_FERTILIZER");
        boolean isChemical = plugin.getItemManager().isCustomItem(item, "CHEMICAL_FERTILIZER");

        if (isOrganic) {
            concAmount = 20.0;
        } else if (isChemical) {
            concAmount = 50.0;
        }

        if (concAmount > 0) {
            event.setCancelled(true); 
            
            // Apply fertility concentration
            fertilityManager.applyFertilizer(clickedBlock, concAmount);
            
            item.subtract(1);
            
            // Visual feedback
            clickedBlock.getWorld().spawnParticle(Particle.COMPOSTER, clickedBlock.getLocation().add(0.5, 1.1, 0.5), 10);
            double newConc = fertilityManager.getConcentration(clickedBlock);
            event.getPlayer().sendMessage(Component.text("§a施肥成功！肥料浓度已增加 (当前: " + String.format("%.1f", newConc) + ")。"));
        }
    }

    // 4. Initialize Fertility on Hoe (Land Creation)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHoeLand(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;
        
        // Check if item is a hoe
        ItemStack item = event.getItem();
        if (item == null || !item.getType().name().endsWith("_HOE")) return;
        
        // Check if block is Dirt/Grass -> Farmland
        Material type = clickedBlock.getType();
        if (type == Material.DIRT || type == Material.GRASS_BLOCK || type == Material.DIRT_PATH) {
            // Note: The event is MONITOR, so the block might already be changed to FARMLAND by the server logic
            // Or we check if the block *became* Farmland. 
            // Actually, for MONITOR, the block state in the world might have updated if the event wasn't cancelled.
            // But usually, block changes happen after the event.
            // Let's use a task to check the next tick.
            
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (clickedBlock.getType() == Material.FARMLAND) {
                    // Initialize fertility data if missing
                    // We modify by 0, which triggers initialization in modifyBaseFertility if entry is null
                    fertilityManager.modifyBaseFertility(clickedBlock, 0);
                    // event.getPlayer().sendMessage(Component.text("§7[Debug] 土地已开垦，肥力系统已激活。"));
                }
            });
        }
    }
}
