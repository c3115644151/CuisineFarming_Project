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

import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.metadata.FixedMetadataValue;

import com.example.cuisinefarming.genetics.GeneData;
import com.example.cuisinefarming.genetics.GeneType;
import com.example.cuisinefarming.genetics.GeneticsManager;

public class FarmingListener implements Listener {

    private final CuisineFarming plugin;
    private final FertilityManager fertilityManager;
    private final GeneticsManager geneticsManager;
    private final Random random = new Random();
    
    private static final String BONEMEAL_METADATA_KEY = "bonemealed";

    // Registry for Active Ticking (Smooth Growth)
    // Key: Chunk Key (Long), Value: Set of Crop Locations
    private final java.util.Map<Long, java.util.Set<org.bukkit.Location>> cropRegistry = new java.util.HashMap<>();

    public FarmingListener(CuisineFarming plugin) {
        this.plugin = plugin;
        this.fertilityManager = plugin.getFertilityManager();
        this.geneticsManager = plugin.getGeneticsManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        // Start Active Growth Ticker
        startGrowthTask();
    }

    private void startGrowthTask() {
        // Run every 5 ticks (0.25 seconds)
        // This balances "Visual Smoothness" with "Server Performance".
        // 5 ticks is fast enough to feel random, but reduces CPU overhead by 80% compared to 1 tick.
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                tickCrops();
            }
        }.runTaskTimer(plugin, 5L, 5L); 
    }

    private void tickCrops() {
        // Iterate over all loaded chunks
        for (org.bukkit.Chunk chunk : plugin.getServer().getWorld("world").getLoadedChunks()) { // Adjust "world" if needed or loop all worlds
            long chunkKey = chunk.getChunkKey();
            java.util.Set<org.bukkit.Location> crops = cropRegistry.get(chunkKey);
            
            if (crops == null || crops.isEmpty()) continue;
            
            // Get World GameRule Speed (Dynamic)
            int randomTickSpeed = 3;
            try {
                Integer rule = chunk.getWorld().getGameRuleValue(org.bukkit.GameRule.RANDOM_TICK_SPEED);
                if (rule != null) randomTickSpeed = rule;
            } catch (Exception ignored) {}
            
            // Base Probability per tick per block (Vanilla Logic)
            // Vanilla checks 'Speed' blocks per section (4096 blocks).
            // P = Speed / 4096.0
            // We run every 5 ticks, so we multiply probability by 5 to maintain the same average rate.
            double baseChance = (randomTickSpeed / 4096.0) * 5.0;

            // Iterate crops in this chunk
            // Use iterator to allow safe removal
            java.util.Iterator<org.bukkit.Location> iterator = crops.iterator();
            while (iterator.hasNext()) {
                org.bukkit.Location loc = iterator.next();
                
                // Lazy Validation: Check if it's still a crop
                Block block = loc.getBlock();
                if (!(block.getBlockData() instanceof Ageable)) {
                    iterator.remove();
                    continue;
                }
                
                // Check Fertility Efficiency
                Block soil = block.getRelative(0, -1, 0);
                if (soil.getType() != Material.FARMLAND) {
                    iterator.remove();
                    continue;
                }

                double efficiency = fertilityManager.calculateTotalEfficiency(soil);
                
                // --- GENE INTEGRATION: GROWTH SPEED ---
                GeneData geneData = geneticsManager.getGenesFromBlock(block);
                if (geneData != null) {
                    double speedMultiplier = geneData.getGene(GeneType.GROWTH_SPEED);
                    efficiency *= speedMultiplier;
                }
                // --------------------------------------
                
                // Active Ticker ONLY handles Acceleration (E > 1.0)
                if (efficiency <= 1.0) continue;
                
                // [Optimization] Vanilla Logic Integration
                // We must calculate the vanilla growth probability 'g' to ensure environmental factors apply.
                // Formula: P_grow = 1 / (floor(25/g) + 1)
                float g = fertilityManager.calculateGrowthPoints(block, soil);
                double vanillaGrowthChance = 1.0 / (Math.floor(25.0 / g) + 1);

                // Calculate Extra Probability
                // We want to simulate (Efficiency - 1.0) * VanillaEvents.
                // A "Vanilla Event" happens when:
                // 1. Random Tick hits (baseChance)
                // 2. Crop logic succeeds (vanillaGrowthChance)
                
                double extraChance = (efficiency - 1.0) * baseChance * vanillaGrowthChance;
                
                // Roll for Growth
                // Handle high probabilities (e.g. if Speed=10000, chance > 1.0)
                while (extraChance > 0) {
                    double roll = random.nextDouble();
                    if (roll < extraChance) {
                        // Grow!
                        performGrowth(block, soil);
                        extraChance -= 1.0; // Consume 1.0 probability (guaranteed growth)
                    } else {
                        break; // Failed the fractional part
                    }
                }
            }
        }
    }

    private void performGrowth(Block block, Block soil) {
        if (!(block.getBlockData() instanceof Ageable ageable)) return;
        
        int currentAge = ageable.getAge();
        if (currentAge >= ageable.getMaximumAge()) return;
        
        // Increment Age
        ageable.setAge(currentAge + 1);
        
        // Fire Event
        org.bukkit.block.BlockState newState = block.getState();
        newState.setBlockData(ageable);
        
        BlockGrowEvent growEvent = new BlockGrowEvent(block, newState);
        plugin.getServer().getPluginManager().callEvent(growEvent);
        
        if (!growEvent.isCancelled()) {
            // Apply changes
            growEvent.getNewState().update(true);
            
            // Visuals
            block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 0.5, 0.5), 1);
        }
    }

    private void registerCrop(Block block) {
        long chunkKey = block.getChunk().getChunkKey();
        cropRegistry.computeIfAbsent(chunkKey, k -> new java.util.HashSet<>()).add(block.getLocation());
    }
    
    private void unregisterCrop(Block block) {
        long chunkKey = block.getChunk().getChunkKey();
        java.util.Set<org.bukkit.Location> set = cropRegistry.get(chunkKey);
        if (set != null) {
            set.remove(block.getLocation());
            if (set.isEmpty()) cropRegistry.remove(chunkKey);
        }
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
        
        // Check for Bone Meal (Artificial Growth)
        if (block.hasMetadata(BONEMEAL_METADATA_KEY)) {
            // Do not consume fertility for artificial growth
            return;
        }

        Block soil = block.getRelative(0, -1, 0);

        if (soil.getType() != Material.FARMLAND) return;

        // Register crop for Active Ticking (Self-Healing)
        registerCrop(block);

        // Check fertility
        // Use the unified calculation method for consistency
        double totalEfficiency = fertilityManager.calculateTotalEfficiency(soil);
        
        // --- GENE INTEGRATION: GROWTH SPEED & FERTILITY RESISTANCE ---
        GeneData geneData = geneticsManager.getGenesFromBlock(block);
        if (geneData != null) {
            double speedMultiplier = geneData.getGene(GeneType.GROWTH_SPEED);
            double fertilityResistance = geneData.getGene(GeneType.FERTILITY_RESISTANCE);
            
            // Fertility Resistance Logic:
            // If fertility is high (>100) and resistance is sufficient,
            // we unlock the high-fertility speed bonus that is normally clamped or penalized.
            // Currently, calculateTotalEfficiency returns a value based on fertility concentration.
            // Let's refine how we apply resistance.
            
            // Re-fetch raw fertility to apply custom resistance logic
             double rawFertility = fertilityManager.getConcentration(soil);
             
             // If fertility is very high (potential burn zone), but resistance is high enough,
             // we BOOST efficiency instead of letting it just be "safe".
             // Or simply: The gene multiplier itself is the primary boost.
             // The user said: "Fertility resistance allows the seed to adapt to higher fertility levels... integrated into E-coefficient."
             
             // New Logic (Refined 2025-12-21):
             // If Concentration > 100 (Overcharge Zone):
             //   - If Resistance >= Concentration: Perfect Adaptation.
             //     Bonus = (Concentration - 100) * 0.005. (e.g. 150 -> +25% Speed)
             //   - If Resistance < Concentration: Burn.
             //     Penalty = (Concentration - Resistance) * 0.01. (e.g. Conc 150, Res 100 -> -50% Speed)
             
             if (rawFertility > 100.0) {
                 double overcharge = rawFertility - 100.0;
                 
                 if (fertilityResistance >= rawFertility) {
                     // Reward: Adapt to high fertility
                     double bonus = overcharge * 0.005; 
                     speedMultiplier += bonus;
                 } else {
                     // Penalty: Cannot handle the heat
                     double burnDiff = rawFertility - fertilityResistance;
                     double penalty = burnDiff * 0.01;
                     speedMultiplier -= penalty;
                     
                     // Ensure it doesn't stop growing completely (unless extreme)
                     if (speedMultiplier < 0.1) speedMultiplier = 0.1;
                 }
             }
             
             totalEfficiency *= speedMultiplier;
        }
        // -----------------------------------------------------------
        
        // Efficiency Logic:
        // Case 1: Low Efficiency (efficiency < 1.0)
        // We act as a filter for vanilla events.
        if (totalEfficiency < 1.0) {
            // Ensure efficiency is non-negative for probability calculation
            double probability = Math.max(0.0, totalEfficiency);
            
            // If random roll (0.0 to 1.0) is GREATER than efficiency, we cancel.
            if (random.nextDouble() > probability) {
                event.setCancelled(true);
                return;
            }
            // Pass through, consume standard cost
            fertilityManager.consumeFertility(soil, 2);
            return;
        }
        
        // Case 2: High Efficiency (efficiency >= 1.0)
        // We act to BOOST the vanilla event via Active Ticking.
        // So for this specific vanilla event, we just let it pass normally.
        // The Active Ticker (tickCrops) will handle the "Extra" events.
        
        // Consume standard cost for this vanilla growth
        fertilityManager.consumeFertility(soil, 2);
    }

    // 2. Monitor Harvest (Block Break) - Modified to Transfer Genes
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCropHarvest(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!(block.getBlockData() instanceof Ageable ageable)) return;

        // Unregister from Active Ticking
        unregisterCrop(block);

        // --- GENE INTEGRATION: TRANSFER TO DROPS ---
        GeneData geneData = geneticsManager.getGenesFromBlock(block);
        
        // Cleanup: Always remove genes from chunk data when block is broken
        geneticsManager.removeGenesFromBlock(block);
        
        // If we have gene data, we must ensure it transfers to the drops
        if (geneData != null) {
            // Cancel vanilla drops so we can spawn our own modified ones
            event.setDropItems(false);
            
            // Calculate drops based on tool (Fortune, etc.)
            ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
            java.util.Collection<ItemStack> drops = block.getDrops(tool);
            
            // Apply gene data to ALL drops (Seed and Produce)
            for (ItemStack drop : drops) {
                geneticsManager.saveGenesToItem(drop, geneData);
            }
            
            // Yield Bonus Logic
            if (ageable.getAge() == ageable.getMaximumAge()) {
                double yieldBonus = geneData.getGene(GeneType.YIELD);
                
                // Formula: Chunk Length (1.0) -> 50% chance for +1 item.
                // Ratio: 2 (Length) : 1 (Prob). P = Length / 2.
                // Yield = 3.4
                // 1.0 - 2.0 (Length 1.0) -> 50% +1
                // 2.0 - 3.0 (Length 1.0) -> 50% +1
                // 3.0 - 3.4 (Length 0.4) -> 20% +1
                
                if (yieldBonus > 1.0) {
                    double remaining = yieldBonus - 1.0;
                    int extraItems = 0;
                    
                    while (remaining > 0) {
                        double chunk = Math.min(1.0, remaining);
                        double prob = chunk / 2.0; // Ratio 2:1
                        
                        if (random.nextDouble() < prob) {
                            extraItems++;
                        }
                        
                        remaining -= chunk;
                    }
                    
                    if (extraItems > 0 && !drops.isEmpty()) {
                         // Find the main produce item to duplicate
                         // Heuristic: Use the item that is NOT a seed, or if all seeds (potato), use that.
                         // Or just duplicate the first item found as before.
                         // Let's try to be smarter: duplicate the item that matches the block type if possible?
                         // Actually, for Wheat, we want Wheat (Item), not Seeds.
                         // Wheat drops: Wheat (1) + Seeds (N).
                         // Iterating drops: usually Wheat is first? Not guaranteed.
                         // Let's filter for the most valuable item?
                         // Simple approach: Use the first item that isn't a seed, unless it's a seed crop (Nether Wart).
                         
                         ItemStack template = null;
                         for (ItemStack d : drops) {
                             if (!d.getType().name().contains("SEEDS")) {
                                 template = d;
                                 break;
                             }
                         }
                         if (template == null && !drops.isEmpty()) template = drops.iterator().next();
                         
                         if (template != null) {
                             ItemStack extraDrop = template.clone();
                             extraDrop.setAmount(extraItems);
                             geneticsManager.saveGenesToItem(extraDrop, geneData); // Ensure genes
                             block.getWorld().dropItemNaturally(block.getLocation(), extraDrop);
                         }
                    }
                }
            }
            
            // Spawn modified drops
            for (ItemStack drop : drops) {
                block.getWorld().dropItemNaturally(block.getLocation(), drop);
            }
        }
        // -----------------------------------------

        // Only care if it's fully grown for fertility
        if (ageable.getAge() != ageable.getMaximumAge()) return;
        
        // Check for Bone Meal (Artificial Growth)
        if (block.hasMetadata(BONEMEAL_METADATA_KEY)) {
            // Remove metadata (cleanup)
            block.removeMetadata(BONEMEAL_METADATA_KEY, plugin);
            // Do not consume fertility for artificial harvest
            return;
        }

        Block soil = block.getRelative(0, -1, 0);
        if (soil.getType() != Material.FARMLAND) return;

        // Consume larger amount of fertility on harvest
        fertilityManager.consumeFertility(soil, 15);
    }

    // 2.5 Monitor Planting (Block Place)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCropPlant(org.bukkit.event.block.BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (block.getBlockData() instanceof Ageable) {
             Block soil = block.getRelative(0, -1, 0);
             if (soil.getType() == Material.FARMLAND) {
                 registerCrop(block);
                 
                 // --- GENE INTEGRATION: ITEM -> BLOCK ---
                 ItemStack item = event.getItemInHand();
                 GeneData genes = geneticsManager.getGenesFromItem(item);
                 // If seed has genes (identified or not), save to block
                 // Note: Even default genes should probably be saved if we want to support breeding later
                 // For now, let's only save if it's explicitly identified or has data
                 geneticsManager.saveGenesToBlock(block, genes);
                 // ---------------------------------------
             }
        }
    }
    
    // 2.6 Monitor Chunk Unload (Memory Cleanup)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(org.bukkit.event.world.ChunkUnloadEvent event) {
        long chunkKey = event.getChunk().getChunkKey();
        cropRegistry.remove(chunkKey);
    }

    // 2.7 Monitor Vanilla Bone Meal
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFertilize(BlockFertilizeEvent event) {
        // Mark all affected blocks as "BoneMealed"
        for (org.bukkit.block.BlockState state : event.getBlocks()) {
            Block block = state.getBlock();
            block.setMetadata(BONEMEAL_METADATA_KEY, new FixedMetadataValue(plugin, true));
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

    /**
     * Debug/API Method: Check if a crop is registered for active ticking.
     * Used by DebugListener to verify system state.
     */
    public boolean isCropRegistered(org.bukkit.Location loc) {
        long chunkKey = loc.getChunk().getChunkKey();
        java.util.Set<org.bukkit.Location> crops = cropRegistry.get(chunkKey);
        return crops != null && crops.contains(loc);
    }
}
