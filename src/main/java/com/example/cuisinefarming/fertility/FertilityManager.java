package com.example.cuisinefarming.fertility;

import com.example.cuisinefarming.CuisineFarming;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.block.data.Ageable;
import org.bukkit.Particle;
import java.util.Random;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class FertilityManager implements Listener {

    private final Map<Chunk, ChunkFertilityData> cache = new HashMap<>();
    private final NamespacedKey pdcKey;
    private final Random random = new Random();
    
    // Constants
    private static final int MAX_FERTILITY_BASE = 100;
    private static final int MIN_FERTILITY_BASE = -100;
    private static final int INITIAL_FERTILITY = 0;
    
    // Concentration Constants
    private static final double CONC_DECAY_PER_SEC = 1.0 / 60.0; // 1.0 per minute
    private static final double SAFE_CONC_THRESHOLD = 100.0;
    private static final double TOXIC_CONC_THRESHOLD = 200.0;
    
    private static final double K_BASE = 0.002;
    private static final double K_BONUS_PER_CONC = 0.0001; // 100 conc -> +0.01 (5x base)
    
    // Growth Constants (Benchmarks)
    // Vanilla: 3 random ticks per section (16x16x16 = 4096 blocks) per tick (Default GameRule)
    private static final double VANILLA_RANDOM_TICK_PROB = 3.0 / 4096.0; // ~0.000732
    
    // Task Interval: 20 ticks (1 second)
    private static final int TASK_INTERVAL_TICKS = 20;
    
    /*
     * [机制量化说明 - Native Mechanics Quantification (Revised)]
     * 
     * 1. 随机刻 (Random Tick):
     *    - 频率: 每 tick (0.05秒) 每个区段 (4096方块) 选 3 个方块。
     *    - 单个方块被中概率: 3 / 4096 ≈ 0.000732 (约 0.073%)。
     * 
     * 2. 生长概率 (Growth Chance):
     *    - 动态计算: 插件现在根据作物周围的实际环境计算 g 值 (Vanilla Logic)。
     *    - 公式: P = 1 / (floor(25/g) + 1)。
     *    - 密集种植 (Mass Farming): g=5 -> P ≈ 16.7%。
     *    - 隔行种植 (Ideal Rows): g=10 -> P ≈ 33.3%。
     *    - 插件将基于此概率 P 计算需要补充的生长尝试次数，实现真正的倍率加速。
     * 
     * 3. 效率系数 (Efficiency):
     *    - Efficiency = 2.0: 意味着总生长速度是原版的 2 倍。
     *    - 插件通过主动任务补充 (Efficiency - 1.0) 倍的生长期望。
     */

    public FertilityManager(CuisineFarming plugin) {
        this.pdcKey = new NamespacedKey(plugin, "chunk_fertility");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Pure Calculation: Get the current effective fertility.
     * Does NOT modify data.
     */
    public int calculateCurrentFertility(Block block) {
        if (block.getType() != Material.FARMLAND) {
            return 0;
        }

        ChunkFertilityData.Entry entry = getEntry(block);
        if (entry == null) {
            return INITIAL_FERTILITY;
        }

        long now = System.currentTimeMillis();
        return calculateRecovery(entry, block, now);
    }
    
    /**
     * Get current fertilizer concentration.
     */
    public double getConcentration(Block block) {
        if (block.getType() != Material.FARMLAND) return 0.0;
        ChunkFertilityData.Entry entry = getEntry(block);
        if (entry == null) return 0.0;
        
        // Calculate current decayed concentration
        long now = System.currentTimeMillis();
        double currentConc = entry.fertilizerConcentration;
        
        if (entry.lastUpdateTime < now) {
            boolean isWet = false;
            if (block.getBlockData() instanceof Farmland farmland) {
                isWet = farmland.getMoisture() > 0;
            }
            if (isWet) {
                double dt = (now - entry.lastUpdateTime) / 1000.0;
                double decay = CONC_DECAY_PER_SEC * dt;
                currentConc = Math.max(0, currentConc - decay);
            }
        }
        return currentConc;
    }
    
    /**
     * Reset the recovery timer (e.g. when soil turns wet).
     * This prevents dry periods from counting towards recovery.
     */
    public void resetRecoveryTimer(Block block) {
        if (block.getType() != Material.FARMLAND) return;
        
        ChunkFertilityData data = getChunkData(block.getChunk());
        ChunkFertilityData.Entry entry = getEntry(block);
        long now = System.currentTimeMillis();
        
        if (entry != null) {
            // Just update timestamp, keep values
            data.setBaseData(block.getX() & 15, block.getY(), block.getZ() & 15, entry.baseFertility, now);
        } else {
            // Init
            data.setBaseData(block.getX() & 15, block.getY(), block.getZ() & 15, INITIAL_FERTILITY, now);
        }
    }

    /**
     * Core Logic: Recovery Calculation with Concentration
     */
    private int calculateRecovery(ChunkFertilityData.Entry entry, Block block, long now) {
        long lastUpdate = entry.lastUpdateTime;
        int current = entry.baseFertility;
        double concentration = entry.fertilizerConcentration;
        
        if (now <= lastUpdate) return current;

        // Check moisture
        boolean isWet = false;
        if (block.getBlockData() instanceof Farmland farmland) {
            isWet = farmland.getMoisture() > 0;
        }
        if (!isWet) return current; // No recovery if dry

        double dt = (now - lastUpdate) / 1000.0;
        
        // 1. Calculate Average Concentration over dt
        // Linear Decay: C(t) = C0 - decay * t
        double decayAmount = CONC_DECAY_PER_SEC * dt;
        double endConcentration = Math.max(0, concentration - decayAmount);
        
        double avgConc = (concentration + endConcentration) / 2.0;
        
        // 2. Determine K (Recovery Rate) based on Average Concentration
        double k = K_BASE;
        
        if (avgConc <= SAFE_CONC_THRESHOLD) {
            // Safe range: Boost K
            k += avgConc * K_BONUS_PER_CONC;
        } else {
            // Toxic range
            double safeBonus = SAFE_CONC_THRESHOLD * K_BONUS_PER_CONC;
            double excess = avgConc - SAFE_CONC_THRESHOLD;
            double penalty = excess * (K_BONUS_PER_CONC * 2.0); 
            
            k = (K_BASE + safeBonus) - penalty;
        }
        
        // 3. Apply Formula
        int max = MAX_FERTILITY_BASE;
        if (avgConc > 0 && avgConc < TOXIC_CONC_THRESHOLD) {
            max = 150; 
        }
        
        if (k >= 0) {
            current = applyRecoveryFormula(current, max, k, dt);
        } else {
            current = applyRecoveryFormula(current, MIN_FERTILITY_BASE, -k, dt);
        }
        
        // Clamp
        if (current < MIN_FERTILITY_BASE) current = MIN_FERTILITY_BASE;
        
        return current;
    }

    private int applyRecoveryFormula(int current, int target, double k, double t) {
        if (current == target) return current;
        double deficit = target - current;
        double remainingDeficit = deficit * Math.exp(-k * t);
        return target - (int) remainingDeficit;
    }

    /**
     * Modify Fertility (e.g., consumption).
     * Triggers a save and update.
     */
    public int modifyBaseFertility(Block block, int delta) {
        if (block.getType() != Material.FARMLAND) return 0;

        ChunkFertilityData data = getChunkData(block.getChunk());
        ChunkFertilityData.Entry entry = getEntry(block);
        
        long now = System.currentTimeMillis();
        int currentVal;
        double currentConc = 0;

        if (entry == null) {
            currentVal = INITIAL_FERTILITY;
            data.setBaseData(block.getX() & 15, block.getY(), block.getZ() & 15, currentVal, now);
        } else {
            currentVal = calculateRecovery(entry, block, now);
            
            if (entry.lastUpdateTime < now) {
                boolean isWet = false;
                if (block.getBlockData() instanceof Farmland farmland) {
                    isWet = farmland.getMoisture() > 0;
                }
                if (isWet) {
                    double dt = (now - entry.lastUpdateTime) / 1000.0;
                    double decay = CONC_DECAY_PER_SEC * dt;
                    currentConc = Math.max(0, entry.fertilizerConcentration - decay);
                } else {
                    currentConc = entry.fertilizerConcentration;
                }
            } else {
                currentConc = entry.fertilizerConcentration;
            }
        }

        // Apply delta
        currentVal += delta;
        if (currentVal < MIN_FERTILITY_BASE) currentVal = MIN_FERTILITY_BASE;
        
        data.setFertilizerData(block.getX() & 15, block.getY(), block.getZ() & 15, currentConc, now);
        data.setBaseData(block.getX() & 15, block.getY(), block.getZ() & 15, currentVal, now);
        
        updateVisuals(block, currentVal);
        
        return currentVal;
    }

    /**
     * Apply Fertilizer: Increases Concentration.
     */
    public void applyFertilizer(Block block, double concentrationAmount) {
        if (block.getType() != Material.FARMLAND) return;

        ChunkFertilityData data = getChunkData(block.getChunk());
        ChunkFertilityData.Entry entry = getEntry(block);
        
        long now = System.currentTimeMillis();
        
        // 1. Settle current state
        int currentVal = INITIAL_FERTILITY;
        double currentConc = 0;
        
        if (entry != null) {
            currentVal = calculateRecovery(entry, block, now);
            // Decay existing conc
            boolean isWet = false;
            if (block.getBlockData() instanceof Farmland farmland) {
                isWet = farmland.getMoisture() > 0;
            }
            if (isWet) {
                double dt = (now - entry.lastUpdateTime) / 1000.0;
                currentConc = Math.max(0, entry.fertilizerConcentration - (CONC_DECAY_PER_SEC * dt));
            } else {
                currentConc = entry.fertilizerConcentration;
            }
        }

        // 2. Add new concentration
        currentConc += concentrationAmount;

        // 3. Save
        data.setFertilizerData(block.getX() & 15, block.getY(), block.getZ() & 15, currentConc, now);
        data.setBaseData(block.getX() & 15, block.getY(), block.getZ() & 15, currentVal, now);
        
        updateVisuals(block, currentVal);
    }

    public void consumeFertility(Block block, int baseConsumption) {
        int currentTotal = calculateCurrentFertility(block);
        
        // Entropy model: Higher fertility -> Higher consumption
        double multiplier = 1.0 + (currentTotal / 200.0);
        int finalCost = (int) (baseConsumption * multiplier);
        
        modifyBaseFertility(block, -finalCost);
    }

    public void updateVisuals(Block block, int fertility) {
        // Placeholder
    }

    private ChunkFertilityData.Entry getEntry(Block block) {
        ChunkFertilityData data = getChunkData(block.getChunk());
        int x = block.getX() & 15;
        int y = block.getY();
        int z = block.getZ() & 15;
        return data.getData(x, y, z);
    }

    private ChunkFertilityData getChunkData(Chunk chunk) {
        if (cache.containsKey(chunk)) {
            return cache.get(chunk);
        }
        
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        byte[] bytes = pdc.get(pdcKey, PersistentDataType.BYTE_ARRAY);
        ChunkFertilityData data = ChunkFertilityData.deserialize(bytes);
        cache.put(chunk, data);
        return data;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // Load happens lazily
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        if (cache.containsKey(chunk)) {
            ChunkFertilityData data = cache.get(chunk);
            if (data.isDirty()) {
                chunk.getPersistentDataContainer().set(pdcKey, PersistentDataType.BYTE_ARRAY, data.serialize());
                data.setClean();
            }
            cache.remove(chunk);
        }
    }
    
    public void saveAll() {
        for (Map.Entry<Chunk, ChunkFertilityData> entry : cache.entrySet()) {
            if (entry.getValue().isDirty()) {
                entry.getKey().getPersistentDataContainer().set(pdcKey, PersistentDataType.BYTE_ARRAY, entry.getValue().serialize());
                entry.getValue().setClean();
            }
        }
    }

    /**
     * Public API to get total efficiency for a crop location.
     * This sums up all factors: Base Fertility + BiomeGifts + EarthSpirit.
     */
    public double calculateTotalEfficiency(Block soil) {
        if (soil.getType() != Material.FARMLAND) return 1.0;
        
        ChunkFertilityData.Entry entry = getEntry(soil);
        long now = System.currentTimeMillis();
        int fertility = (entry != null) ? calculateRecovery(entry, soil, now) : INITIAL_FERTILITY;
        
        // Base Efficiency from Fertility: 1.0 + (Fertility * 0.005)
        // Range: 0.5 (at -100) to 1.5 (at 100)
        int effectiveFertility = Math.min(100, Math.max(-100, fertility));
        double baseEfficiency = 1.0 + (effectiveFertility * 0.005);
        
        double externalBonus = getExternalGrowthBonus(soil);
        
        // Total Efficiency
        return baseEfficiency + externalBonus;
    }

    /**
     * Active Growth Tick: Called by GrowthTask.
     * Randomly ticks crops on fertile land to simulate "extra random ticks".
     * Uses dynamic calculation of 'g' and 'p' to match vanilla mechanics per crop.
     */
    public void performGrowthTick() {
        long now = System.currentTimeMillis();
        
        // Iterate over all loaded chunks with fertility data
        for (Map.Entry<Chunk, ChunkFertilityData> chunkEntry : cache.entrySet()) {
            Chunk chunk = chunkEntry.getKey();
            if (!chunk.isLoaded()) continue;
            
            ChunkFertilityData data = chunkEntry.getValue();
            
            // Iterate over all known fertile blocks in this chunk
            for (Map.Entry<Integer, ChunkFertilityData.Entry> entry : data.getAllEntries().entrySet()) {
                ChunkFertilityData.Entry fertEntry = entry.getValue();
                
                // Only process if it has some positive attribute (fertility or concentration)
                // OR if we need to process slowdown (but performGrowthTick is primarily for acceleration)
                // Actually, slowdown is passive (event cancellation). Acceleration is active.
                // So here we only care about acceleration (Efficiency > 1.0).
                
                if (fertEntry.baseFertility <= 0 && fertEntry.fertilizerConcentration <= 0) {
                     // Even if fertility is low, external bonuses might boost it above 1.0
                     // So we should check total efficiency, but to save perf, maybe simple check first?
                     // Let's proceed to check total efficiency properly.
                }

                int key = entry.getKey();
                int x = ChunkFertilityData.unpackX(key);
                int y = ChunkFertilityData.unpackY(key);
                int z = ChunkFertilityData.unpackZ(key);
                
                Block soil = chunk.getBlock(x, y, z);
                if (soil.getType() != Material.FARMLAND) continue;

                // Use the unified calculation method
                double totalEfficiency = calculateTotalEfficiency(soil);

                // Only process ACCELERATION here (totalEfficiency > 1.0)
                if (totalEfficiency <= 1.0) continue;

                // --- DYNAMIC VANILLA CALCULATION ---
                Block cropBlock = soil.getRelative(0, 1, 0);
                if (!(cropBlock.getBlockData() instanceof Ageable)) continue; // Not a crop
                
                // Calculate 'g' points for this specific crop layout
                float g = calculateGrowthPoints(cropBlock, soil);
                
                // Calculate Vanilla Probability 'p'
                // Formula: 1 / (floor(25/g) + 1)
                double p = 1.0 / (Math.floor(25.0 / g) + 1.0);
                
                // Calculate Task Probability
                // We want to add (Efficiency - 1.0) worth of vanilla speed.
                // Vanilla speed per tick = VANILLA_RANDOM_TICK_PROB * p
                // Task runs every TASK_INTERVAL_TICKS
                // Vanilla events in interval = TASK_INTERVAL_TICKS * VANILLA_RANDOM_TICK_PROB * p
                
                double vanillaEventsPerInterval = TASK_INTERVAL_TICKS * VANILLA_RANDOM_TICK_PROB * p;
                
                // Extra events needed = vanillaEventsPerInterval * (totalEfficiency - 1.0)
                double extraChance = vanillaEventsPerInterval * (totalEfficiency - 1.0);

                if (extraChance > 0 && random.nextDouble() < extraChance) {
                    Ageable ageable = (Ageable) cropBlock.getBlockData();
                    // Vanilla Requirement: Light Level >= 9
                    if (cropBlock.getLightLevel() < 9) continue;

                    if (ageable.getAge() < ageable.getMaximumAge()) {
                        ageable.setAge(ageable.getAge() + 1);
                        cropBlock.setBlockData(ageable);
                        
                        cropBlock.getWorld().spawnParticle(Particle.HEART, cropBlock.getLocation().add(0.5, 0.5, 0.5), 3);
                        
                        // Consume Fertility
                        consumeFertility(soil, 2);
                    }
                }
            }
        }
    }
    
    /**
     * Calculate vanilla growth points 'g' for a specific crop block.
     * Logic matches Minecraft Wiki:
     * - Base: 1.0
     * - Soil below: Wet Farmland (+3), Dry Farmland (+1).
     * - Surrounding 8 blocks:
     *   - Wet Farmland: +0.75
     *   - Dry Farmland: +0.25
     * - Penalty:
     *   - If same crop is adjacent (NS/EW) or diagonal: Penalty logic.
     *   - Specifically: If any diagonal same crop OR (EW same AND NS same).
     */
    public float calculateGrowthPoints(Block cropBlock, Block soilBlock) {
        float points = 1.0f;
        
        // Soil below points
        if (soilBlock.getType() == Material.FARMLAND) {
            Farmland farmland = (Farmland) soilBlock.getBlockData();
            points += (farmland.getMoisture() > 0) ? 3.0f : 1.0f;
        }
        
        // Surrounding blocks (check 8 neighbors of soil)
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                
                Block neighborSoil = soilBlock.getRelative(x, 0, z);
                float neighborPoints = 0.0f;
                if (neighborSoil.getType() == Material.FARMLAND) {
                    Farmland f = (Farmland) neighborSoil.getBlockData();
                    neighborPoints = (f.getMoisture() > 0) ? 0.75f : 0.25f;
                }
                points += neighborPoints;
            }
        }
        
        // Penalty Check (check 8 neighbors of crop)
        boolean diagonalSame = false;
        boolean northSouthSame = false;
        boolean eastWestSame = false;
        
        Material cropType = cropBlock.getType();
        
        // Diagonals
        if (isSameCrop(cropBlock.getRelative(-1, 0, -1), cropType) ||
            isSameCrop(cropBlock.getRelative(-1, 0, 1), cropType) ||
            isSameCrop(cropBlock.getRelative(1, 0, -1), cropType) ||
            isSameCrop(cropBlock.getRelative(1, 0, 1), cropType)) {
            diagonalSame = true;
        }
        
        // Orthogonal
        if (isSameCrop(cropBlock.getRelative(-1, 0, 0), cropType) || isSameCrop(cropBlock.getRelative(1, 0, 0), cropType)) {
            eastWestSame = true;
        }
        if (isSameCrop(cropBlock.getRelative(0, 0, -1), cropType) || isSameCrop(cropBlock.getRelative(0, 0, 1), cropType)) {
            northSouthSame = true;
        }
        
        if (diagonalSame || (eastWestSame && northSouthSame)) {
            points /= 2.0f;
        }
        
        return points;
    }
    
    private boolean isSameCrop(Block block, Material type) {
        return block.getType() == type;
    }
    
    /**
     * Helper to calculate external growth bonuses (BiomeGifts, EarthSpirit).
     * Returns a value to be ADDED to efficiency (e.g. +0.3 for 30%).
     */
    private double getExternalGrowthBonus(Block soil) {
        double bonus = 0.0;
        
        try {
            // 1. BiomeGifts Integration
            if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("BiomeGifts")) {
                org.bukkit.plugin.Plugin biomePlugin = org.bukkit.Bukkit.getPluginManager().getPlugin("BiomeGifts");
                Block cropBlock = soil.getRelative(0, 1, 0);
                if (cropBlock.getBlockData() instanceof Ageable) {
                     bonus += getBiomeGiftsBonus(biomePlugin, cropBlock);
                }
            }
            
            // 2. EarthSpirit Integration
            if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("EarthSpirit")) {
                bonus += getEarthSpiritBonus(soil.getLocation());
            }
            
        } catch (Exception e) {
            // Suppress
        }
        
        return bonus;
    }

    private double getBiomeGiftsBonus(org.bukkit.plugin.Plugin plugin, Block cropBlock) {
        try {
            Method getConfigManager = plugin.getClass().getMethod("getConfigManager");
            Object configManager = getConfigManager.invoke(plugin);
            
            Method getCropConfig = configManager.getClass().getMethod("getCropConfig", Material.class);
            Object cropConfig = getCropConfig.invoke(configManager, cropBlock.getType());
            
            if (cropConfig != null) {
                String biomeName = cropBlock.getWorld().getBiome(cropBlock.getLocation()).getKey().toString();
                Method getBiomeType = cropConfig.getClass().getMethod("getBiomeType", String.class);
                Object typeEnum = getBiomeType.invoke(cropConfig, biomeName);
                String type = typeEnum.toString();
                
                if ("RICH".equals(type)) {
                    Field richSpeedBonusF = cropConfig.getClass().getField("richSpeedBonus");
                    return richSpeedBonusF.getDouble(cropConfig); 
                } else if ("POOR".equals(type)) {
                    Field poorSpeedPenaltyF = cropConfig.getClass().getField("poorSpeedPenalty");
                    return -poorSpeedPenaltyF.getDouble(cropConfig);
                }
            }
        } catch (Exception e) {
            return 0.0;
        }
        return 0.0;
    }
    
    public double getEarthSpiritBonus(org.bukkit.Location loc) {
        try {
            org.bukkit.plugin.Plugin spiritPlugin = org.bukkit.Bukkit.getPluginManager().getPlugin("EarthSpirit");
            if (spiritPlugin == null) return 0.0;
            
            // Access SpiritManager via reflection
            Method getManager = spiritPlugin.getClass().getMethod("getManager");
            Object manager = getManager.invoke(spiritPlugin);
            
            // Call optimized getSpiritGrowthBonus API
            Method getBonus = manager.getClass().getMethod("getSpiritGrowthBonus", org.bukkit.Location.class);
            return (double) getBonus.invoke(manager, loc);
            
        } catch (Exception e) {
            return 0.0;
        }
    }
}