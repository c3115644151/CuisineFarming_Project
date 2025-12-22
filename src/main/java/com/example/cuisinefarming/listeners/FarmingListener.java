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
import com.example.cuisinefarming.genetics.GeneticsManager;
import com.example.cuisinefarming.genetics.Trait;

/**
 * 农业核心监听器
 * 负责作物生长、收割、种植以及环境因素（肥力、水分）的监听与处理。
 * 包含主动生长Ticker (Active Ticking) 以实现平滑生长效果。
 * 
 * 上游链路: Bukkit Event API
 * 下游链路: FertilityManager, GeneticsManager
 * 
 * 维护说明: 
 * 1. 所有的生长逻辑都在 tickCrops 中处理，不要在 BlockGrowEvent 中添加额外生长逻辑，仅作取消/允许判断。
 * 2. 杂交和基因掉落逻辑在 onCropHarvest 中处理。
 */
public class FarmingListener implements Listener {

    private final CuisineFarming plugin;
    private final FertilityManager fertilityManager;
    private final GeneticsManager geneticsManager;
    private final Random random = new Random();
    
    private static final String BONEMEAL_METADATA_KEY = "bonemealed";

    // 主动生长的注册表 (平滑生长)
    // 键: 区块Key (Long), 值: 作物位置集合
    private final java.util.Map<Long, java.util.Set<org.bukkit.Location>> cropRegistry = new java.util.HashMap<>();

    public FarmingListener(CuisineFarming plugin) {
        this.plugin = plugin;
        this.fertilityManager = plugin.getFertilityManager();
        this.geneticsManager = plugin.getGeneticsManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        // 启动主动生长任务
        startGrowthTask();
    }

    private void startGrowthTask() {
        // 每 5 tick 运行一次 (0.25 秒)
        // 这平衡了 "视觉平滑度" 和 "服务器性能"。
        // 5 tick 足够快以感觉随机，但相比 1 tick 减少了 80% 的 CPU 开销。
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                tickCrops();
            }
        }.runTaskTimer(plugin, 5L, 5L); 
    }

    private void tickCrops() {
        // 遍历所有加载的区块
        for (org.bukkit.Chunk chunk : plugin.getServer().getWorld("world").getLoadedChunks()) { // 如果需要支持多世界，请调整
            long chunkKey = chunk.getChunkKey();
            java.util.Set<org.bukkit.Location> crops = cropRegistry.get(chunkKey);
            
            if (crops == null || crops.isEmpty()) continue;
            
            // 获取世界规则 Random Tick Speed (动态)
            int randomTickSpeed = 3;
            try {
                Integer rule = chunk.getWorld().getGameRuleValue(org.bukkit.GameRule.RANDOM_TICK_SPEED);
                if (rule != null) randomTickSpeed = rule;
            } catch (Exception ignored) {}
            
            // 每个 tick 每个方块的基础概率 (原版逻辑)
            // 原版检查每个 section (4096方块) 'Speed' 个方块。
            // P = Speed / 4096.0
            // 我们每 5 tick 运行一次，所以乘以 5 以保持相同的平均速率。
            double baseChance = (randomTickSpeed / 4096.0) * 5.0;

            // 遍历该区块中的作物
            // 使用迭代器以允许安全移除
            java.util.Iterator<org.bukkit.Location> iterator = crops.iterator();
            while (iterator.hasNext()) {
                org.bukkit.Location loc = iterator.next();
                
                // 懒惰验证: 检查是否仍是作物
                Block block = loc.getBlock();
                if (!(block.getBlockData() instanceof Ageable)) {
                    iterator.remove();
                    continue;
                }
                
                // 检查肥力效率
                Block soil = block.getRelative(0, -1, 0);
                if (soil.getType() != Material.FARMLAND) {
                    iterator.remove();
                    continue;
                }

                double efficiency = fertilityManager.calculateTotalEfficiency(soil);
                
                // --- 基因集成: 生长速度 & 耐肥性 (Refactored 2025-12-22) ---
                GeneData geneData = geneticsManager.getGenesFromBlock(block);
                if (geneData != null) {
                    // 1. 获取效率细分
                    FertilityManager.EfficiencyBreakdown breakdown = fertilityManager.calculateEfficiencyBreakdown(soil);
                    
                    // 基础效率 (包含: 1.0 + 基础肥力加成 + 群系 + 地灵)
                    // 修正: 保留基础肥力加成 (Fertility Bonus)，因为这是土壤的基础属性。
                    double currentEfficiency = breakdown.totalEfficiency;
                    
                    // 2. 耐肥性 (Fertility Resistance) (Trait D)
                    // 决定对土地肥料浓度的耐受力。
                    double resistance = geneData.getGenePair(Trait.SOIL_TOLERANCE).getPhenotypeValue() * 40.0;
                    double concentration = fertilityManager.getConcentration(soil);
                    
                    if (resistance < 1.0) resistance = 1.0; 
                    
                    double resistanceBonus = 0.01 * concentration * (1.0 - concentration / (2.0 * resistance));
                    
                    // 3. 生长速度 (Growth Speed) (Trait A)
                    // Map [-8, 8] to [-1.0, 1.0]
                    double growthGeneVal = geneData.getGenePair(Trait.GROWTH_SPEED).getPhenotypeValue();
                    double growthBonus = growthGeneVal / 8.0;
                    
                    // 4. 汇总
                    // Efficiency = (Base + Fertility + Biome...) + ActiveConcentrationBonus + GeneSpeedBonus
                    efficiency = currentEfficiency + resistanceBonus + growthBonus;
                }
                // --------------------------------------
                
                // 主动 Ticker 仅处理加速 (E > 1.0)
                if (efficiency <= 1.0) continue;
                
                // [优化] 原版逻辑集成
                // 我们必须计算原版生长概率 'g' 以确保环境因素适用。
                // 公式: P_grow = 1 / (floor(25/g) + 1)
                float g = fertilityManager.calculateGrowthPoints(block, soil);
                double vanillaGrowthChance = 1.0 / (Math.floor(25.0 / g) + 1);

                // 计算额外概率
                // 我们想要模拟 (Efficiency - 1.0) * VanillaEvents。
                // "Vanilla Event" 发生当:
                // 1. Random Tick 命中 (baseChance)
                // 2. 作物逻辑成功 (vanillaGrowthChance)
                
                double extraChance = (efficiency - 1.0) * baseChance * vanillaGrowthChance;
                
                // 掷骰子生长
                // 处理高概率 (例如 Speed=10000, chance > 1.0)
                while (extraChance > 0) {
                    double roll = random.nextDouble();
                    if (roll < extraChance) {
                        // 生长!
                        performGrowth(block, soil);
                        extraChance -= 1.0; // 消耗 1.0 概率 (保证生长)
                    } else {
                        break; // 失败于小数部分
                    }
                }
            }
        }
    }

    private void performGrowth(Block block, Block soil) {
        if (!(block.getBlockData() instanceof Ageable ageable)) return;
        
        int currentAge = ageable.getAge();
        if (currentAge >= ageable.getMaximumAge()) return;
        
        // 增加年龄
        ageable.setAge(currentAge + 1);
        
        // 触发事件
        org.bukkit.block.BlockState newState = block.getState();
        newState.setBlockData(ageable);
        
        BlockGrowEvent growEvent = new BlockGrowEvent(block, newState);
        plugin.getServer().getPluginManager().callEvent(growEvent);
        
        if (!growEvent.isCancelled()) {
            // 应用更改
            growEvent.getNewState().update(true);
            
            // 视觉效果
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

    // 0. 监听水分变化 (修复: 仅统计湿润时间)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMoistureChange(MoistureChangeEvent event) {
        if (event.getBlock().getType() != Material.FARMLAND) return;
        
        // 如果变湿 (从 0 到 >0)
        boolean wasDry = false;
        if (event.getBlock().getBlockData() instanceof Farmland oldState) {
            wasDry = oldState.getMoisture() == 0;
        }
        
        boolean willBeWet = false;
        if (event.getNewState() instanceof Farmland newState) {
            willBeWet = newState.getMoisture() > 0;
        }
        
        if (wasDry && willBeWet) {
            // 重置计时器，这样干燥期就不计入恢复
            fertilityManager.resetRecoveryTimer(event.getBlock());
        }
    }

    // 1. 监听作物生长
    @EventHandler(priority = EventPriority.NORMAL)
    public void onCropGrow(BlockGrowEvent event) {
        Block block = event.getBlock();
        
        // 检查骨粉 (人工生长)
        if (block.hasMetadata(BONEMEAL_METADATA_KEY)) {
            // 不消耗肥力用于人工生长
            return;
        }

        Block soil = block.getRelative(0, -1, 0);

        if (soil.getType() != Material.FARMLAND) return;

        // 注册作物用于主动 Ticking (自愈)
        registerCrop(block);

        // 检查肥力
        // 使用统一的计算方法以保持一致性
        double totalEfficiency = fertilityManager.calculateTotalEfficiency(soil);
        
        // --- 基因集成: 生长速度 & 耐肥性 (Refactored 2025-12-22) ---
        GeneData geneData = geneticsManager.getGenesFromBlock(block);
        if (geneData != null) {
            // 1. 获取效率细分，以便我们可以替换肥力部分
            FertilityManager.EfficiencyBreakdown breakdown = fertilityManager.calculateEfficiencyBreakdown(soil);
            
            // 重置为基础效率 (去除旧版肥力加成)
            double baseEfficiencyWithoutFertility = breakdown.totalEfficiency - breakdown.fertilityBonus;
            
            // 2. 耐肥性 (Fertility Resistance) (Trait D)
            // 决定对土地肥料浓度的耐受力。
            // 公式: Bonus = 0.01 * C * (1 - C / (2 * R))
            double resistance = geneData.getGenePair(Trait.SOIL_TOLERANCE).getPhenotypeValue() * 40.0;
            double concentration = fertilityManager.getConcentration(soil);
            
            if (resistance < 1.0) resistance = 1.0; 
            
            double resistanceBonus = 0.01 * concentration * (1.0 - concentration / (2.0 * resistance));
            
            // 3. 生长速度 (Growth Speed) (Trait A)
            // Phenotype Value Range: -10.0 ~ +10.0
            // Target Range: -1.0 ~ +1.0
            double growthGeneVal = geneData.getGenePair(Trait.GROWTH_SPEED).getPhenotypeValue();
            // Map [-10, 10] to [-1.0, 1.0]
            double growthBonus = growthGeneVal / 10.0;
            
            // 4. 汇总
            totalEfficiency = baseEfficiencyWithoutFertility + resistanceBonus + growthBonus;
        }
        // -----------------------------------------------------------
        
        // 效率逻辑:
        // 情况 1: 低效率 (efficiency < 1.0)
        // 我们作为原版事件的过滤器。
        if (totalEfficiency < 1.0) {
            // 确保效率非负用于概率计算
            double probability = Math.max(0.0, totalEfficiency);
            
            // 如果随机掷骰子 (0.0 到 1.0) 大于效率，我们取消。
            if (random.nextDouble() > probability) {
                event.setCancelled(true);
                return;
            }
            // 通过，消耗标准成本
            fertilityManager.consumeFertility(soil, 2);
            return;
        }
        
        // 情况 2: 高效率 (efficiency >= 1.0)
        // 我们通过主动 Ticking 来提升原版事件。
        // 所以对于这个特定的原版事件，我们只是让它正常通过。
        // 主动 Ticker (tickCrops) 将处理 "额外" 事件。
        
        // 为此原版生长消耗标准成本
        fertilityManager.consumeFertility(soil, 2);
    }

    // 2. 监听收割 (方块破坏) - 修改为转移基因
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCropHarvest(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!(block.getBlockData() instanceof Ageable ageable)) return;

        // 从主动 Ticking 中注销
        unregisterCrop(block);

        // --- 基因集成: 转移到掉落物 ---
        GeneData geneData = geneticsManager.getGenesFromBlock(block);
        GeneData pollenData = geneticsManager.getPollenFromBlock(block);
        
        // 清理: 当方块被破坏时，始终从区块数据中移除基因
        geneticsManager.removeGenesFromBlock(block);
        geneticsManager.removePollenFromBlock(block);
        
        // 如果我们有基因数据，我们必须确保它转移到掉落物
        if (geneData != null) {
            // 杂交逻辑
            GeneData dropGenes = geneData; // 默认为母本
            
            if (pollenData != null && ageable.getAge() == ageable.getMaximumAge()) {
                dropGenes = geneticsManager.hybridize(geneData, pollenData);
                // 成功繁殖的特效
                block.getWorld().spawnParticle(Particle.HEART, block.getLocation().add(0.5, 0.5, 0.5), 3);
            } else {
                // 如果没有杂交
                // 1. 如果母本是默认基因 (野生作物)，则生成随机基因 (模拟鉴定/发现过程)
                if (geneData.isDefault()) {
                    dropGenes = new GeneData();
                    dropGenes.randomize(); // 正态分布随机
                } 
                // 2. 如果母本已有特定基因，则继承 (克隆)
                else {
                    // TODO: 可以在这里添加微小的自然突变概率?
                    dropGenes = geneData; 
                }
                
                // 强制重置为未鉴定，以展示 Adjective 系统 (Vague Lore)
                dropGenes.setIdentified(false);
            }
            
            // 取消原版掉落，以便我们可以生成我们修改后的掉落物
            event.setDropItems(false);
            
            // 根据工具计算掉落物 (时运等)
            ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
            java.util.Collection<ItemStack> drops = block.getDrops(tool);
            
            // 将基因数据应用到所有掉落物 (种子和产物)
            for (ItemStack drop : drops) {
                geneticsManager.saveGenesToItem(drop, dropGenes);
            }
            
            // 产量加成逻辑 (Trait B - YIELD)
            if (ageable.getAge() == ageable.getMaximumAge()) {
                double yieldScore = geneData.getGenePair(Trait.YIELD).getPhenotypeValue();
                
                // 规则: 若总分小于0，不额外掉落。
                // 若总分大于0，每 1 分提供 1 个判定区间，每个区间 25% 概率。
                // 这意味着 1 分期望提供 0.25 个额外物品。
                // 若有小数部分，向上取整作为一个完整区间 (Ceiling)。
                
                if (yieldScore > 0) {
                    // 1 Score = 1 Interval
                    int loops = (int) Math.ceil(yieldScore);
                    int extraItems = 0;
                    
                    for (int i = 0; i < loops; i++) {
                        if (random.nextDouble() < 0.25) {
                            extraItems++;
                        }
                    }
                    
                    if (extraItems > 0 && !drops.isEmpty()) {
                         // 找到主要产物物品进行复制
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
                             geneticsManager.saveGenesToItem(extraDrop, dropGenes); // 确保基因
                             block.getWorld().dropItemNaturally(block.getLocation(), extraDrop);
                         }
                    }
                }
            }
            
            // 生成修改后的掉落物
            for (ItemStack drop : drops) {
                block.getWorld().dropItemNaturally(block.getLocation(), drop);
            }
        }
        // -----------------------------------------

        // 仅关心完全成熟的作物的肥力
        if (ageable.getAge() != ageable.getMaximumAge()) return;
        
        // 检查骨粉 (人工生长)
        if (block.hasMetadata(BONEMEAL_METADATA_KEY)) {
            // 移除元数据 (清理)
            block.removeMetadata(BONEMEAL_METADATA_KEY, plugin);
            // 不消耗肥力用于人工收割
            return;
        }

        Block soil = block.getRelative(0, -1, 0);
        if (soil.getType() != Material.FARMLAND) return;

        // 收割时消耗大量肥力
        fertilityManager.consumeFertility(soil, 15);
    }

    // 2.5 监听种植 (方块放置)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCropPlant(org.bukkit.event.block.BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (block.getBlockData() instanceof Ageable) {
             Block soil = block.getRelative(0, -1, 0);
             if (soil.getType() == Material.FARMLAND) {
                 registerCrop(block);
                 
                 // --- 基因集成: 物品 -> 方块 ---
                 ItemStack item = event.getItemInHand();
                 GeneData genes = geneticsManager.getGenesFromItem(item);
                 // 如果种子有基因 (无论是否鉴定)，保存到方块
                 // 注意: 即使是默认基因也应该保存，如果我们想支持后续育种
                 // 目前，让我们仅在已鉴定或有数据时保存
                 geneticsManager.saveGenesToBlock(block, genes);
                 // ---------------------------------------
             }
        }
    }
    
    // 2.6 监听区块卸载 (内存清理)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(org.bukkit.event.world.ChunkUnloadEvent event) {
        long chunkKey = event.getChunk().getChunkKey();
        cropRegistry.remove(chunkKey);
    }

    // 2.7 监听原版骨粉
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFertilize(BlockFertilizeEvent event) {
        // 标记所有受影响的方块为 "已催熟"
        for (org.bukkit.block.BlockState state : event.getBlocks()) {
            Block block = state.getBlock();
            block.setMetadata(BONEMEAL_METADATA_KEY, new FixedMetadataValue(plugin, true));
        }
    }

    // 3. 监听施肥 (玩家交互)
    @EventHandler(priority = EventPriority.NORMAL)
    public void onFertilize(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;
        
        // 修复: 允许点击作物来给下方的土壤施肥
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
            
            // 应用肥力浓度
            fertilityManager.applyFertilizer(clickedBlock, concAmount);
            
            item.subtract(1);
            
            // 视觉反馈
            clickedBlock.getWorld().spawnParticle(Particle.COMPOSTER, clickedBlock.getLocation().add(0.5, 1.1, 0.5), 10);
            double newConc = fertilityManager.getConcentration(clickedBlock);
            event.getPlayer().sendMessage(Component.text("§a施肥成功！肥料浓度已增加 (当前: " + String.format("%.1f", newConc) + ")。"));
        }
    }

    // 4. 监听锄地初始化肥力
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHoeLand(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;
        
        // 检查是否是锄头
        ItemStack item = event.getItem();
        if (item == null || !item.getType().name().endsWith("_HOE")) return;
        
        // 检查方块是否是泥土/草方块 -> 耕地
        Material type = clickedBlock.getType();
        if (type == Material.DIRT || type == Material.GRASS_BLOCK || type == Material.DIRT_PATH) {
            // 注意: 事件是 MONITOR，所以方块可能已经被服务器逻辑更改为 FARMLAND
            // 或者我们检查方块是否 *变成* 了耕地。
            // 实际上，对于 MONITOR，如果事件未被取消，世界中的方块状态可能已更新。
            // 但通常，方块更改发生在事件之后。
            // 让我们使用任务在下一个 tick 检查。
            
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (clickedBlock.getType() == Material.FARMLAND) {
                    // 如果缺失则初始化肥力数据
                    // 我们修改 0，这会触发 modifyBaseFertility 中的初始化 (如果条目为 null)
                    fertilityManager.modifyBaseFertility(clickedBlock, 0);
                    // event.getPlayer().sendMessage(Component.text("§7[Debug] 土地已开垦，肥力系统已激活。"));
                }
            });
        }
    }

    // 5. 监听种子物品生成 (为野生种子添加Lore)
    @EventHandler(priority = EventPriority.LOW)
    public void onItemSpawn(org.bukkit.event.entity.ItemSpawnEvent event) {
        ItemStack item = event.getEntity().getItemStack();
        Material type = item.getType();
        
        // 检查是否为农作物种子
        if (isCropSeed(type)) {
            // 如果物品没有 Lore (说明是原生/野生掉落)，则为其初始化“未鉴定”状态
            if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) {
                 GeneData defaultData = new GeneData(); // 默认为未鉴定
                 geneticsManager.saveGenesToItem(item, defaultData);
                 event.getEntity().setItemStack(item); // 更新实体数据
            }
        }
    }

    private boolean isCropSeed(Material type) {
        return type == Material.WHEAT_SEEDS || 
               type == Material.POTATO || 
               type == Material.CARROT || 
               type == Material.BEETROOT_SEEDS ||
               type == Material.MELON_SEEDS ||
               type == Material.PUMPKIN_SEEDS;
    }

    /**
     * 调试/API 方法: 检查作物是否已注册为主动 ticking。
     * 由 DebugListener 用于验证系统状态。
     */
    public boolean isCropRegistered(org.bukkit.Location loc) {
        long chunkKey = loc.getChunk().getChunkKey();
        java.util.Set<org.bukkit.Location> crops = cropRegistry.get(chunkKey);
        return crops != null && crops.contains(loc);
    }
}