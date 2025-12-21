package com.example.cuisinefarming.listeners;

import com.example.cuisinefarming.CuisineFarming;
import com.example.cuisinefarming.fertility.FertilityManager;
import com.example.cuisinefarming.genetics.GeneData;
import com.example.cuisinefarming.genetics.GeneType;
import com.example.cuisinefarming.genetics.GeneticsManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DebugListener implements Listener {

    private final CuisineFarming plugin;
    private final NamespacedKey debugKey;
    private final FertilityManager fertilityManager;
    private final GeneticsManager geneticsManager;

    // Player Debug Mode State
    private final Map<UUID, DebugMode> playerModes = new HashMap<>();

    public enum DebugMode {
        INSPECT("Inspect (Default)"),
        RANDOMIZE_HAND("Randomize Hand"),
        IDENTIFY_HAND("Identify Hand"),
        SET_CONC_HIGH("Set Soil Concentration (150 - High)"),
        SET_CONC_TOXIC("Set Soil Concentration (250 - Toxic)"),
        SIMULATE_HARVEST("Simulate Harvest (Drop Calc)");
        
        final String desc;
        DebugMode(String desc) { this.desc = desc; }
    }

    public DebugListener(CuisineFarming plugin) {
        this.plugin = plugin;
        this.debugKey = new NamespacedKey(plugin, "debug_tool");
        this.fertilityManager = plugin.getFertilityManager();
        this.geneticsManager = plugin.getGeneticsManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onDebugUse(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;
        
        // Check if it is the debug tool
        if (!item.getItemMeta().getPersistentDataContainer().has(debugKey, PersistentDataType.BYTE)) return;
        
        // Only trigger on Right Click or Left Click (ignore physical interactions like pressure plates)
        if (event.getAction() == Action.PHYSICAL) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // Initialize mode if not present
        playerModes.putIfAbsent(uuid, DebugMode.INSPECT);
        DebugMode currentMode = playerModes.get(uuid);

        // Interaction Logic
        Action action = event.getAction();
        Block clickedBlock = event.getClickedBlock();
        
        // LEFT CLICK: Cycle Mode
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            cycleMode(player);
            return;
        }

        // RIGHT CLICK: Execute Action
        if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
            executeAction(player, currentMode, clickedBlock);
        }
    }
    
    private void cycleMode(Player player) {
        DebugMode current = playerModes.get(player.getUniqueId());
        int nextOrdinal = (current.ordinal() + 1) % DebugMode.values().length;
        DebugMode next = DebugMode.values()[nextOrdinal];
        playerModes.put(player.getUniqueId(), next);
        player.sendMessage(Component.text("§e[Debug] Switched Mode: §f" + next.desc));
    }

    private void executeAction(Player player, DebugMode mode, Block clickedBlock) {
        switch (mode) {
            case INSPECT:
                if (clickedBlock != null) {
                    // Original Block Inspection Logic
                    handleBlockInspect(player, clickedBlock);
                } else {
                    inspectHand(player);
                }
                break;
                
            case RANDOMIZE_HAND:
                randomizeHand(player);
                break;
                
            case IDENTIFY_HAND:
                identifyHand(player);
                break;
                
            case SET_CONC_HIGH:
                if (clickedBlock != null) setConcentration(player, clickedBlock, 150.0);
                else player.sendMessage("§cClick a block!");
                break;
                
            case SET_CONC_TOXIC:
                if (clickedBlock != null) setConcentration(player, clickedBlock, 250.0);
                else player.sendMessage("§cClick a block!");
                break;

            case SIMULATE_HARVEST:
                if (clickedBlock != null && isCrop(clickedBlock.getType())) {
                    simulateBlockHarvest(player, clickedBlock);
                } else {
                    simulateHarvest(player);
                }
                break;
        }
    }

    private void handleBlockInspect(Player player, Block clickedBlock) {
        // 1. 识别目标 (土壤 vs 作物 vs 环境)
        Block soil = null;
        Block crop = null;
        boolean isEnvironmentMode = false;

        if (clickedBlock.getType() == Material.FARMLAND) {
            soil = clickedBlock;
            Block above = clickedBlock.getRelative(0, 1, 0);
            if (isCrop(above.getType())) {
                crop = above;
            }
        } else if (isCrop(clickedBlock.getType())) {
            crop = clickedBlock;
            soil = clickedBlock.getRelative(0, -1, 0);
            if (soil.getType() != Material.FARMLAND) {
                soil = null; // 可能是水培或非耕地种植
            }
        } else {
             isEnvironmentMode = true;
        }

        player.sendMessage(Component.text("§8§m--------------------------------"));
        
        if (isEnvironmentMode) {
            handleEnvironmentDebug(player, clickedBlock);
        } else {
            handleCropDebug(player, crop, soil, clickedBlock);
        }
        
        player.sendMessage(Component.text("§8§m--------------------------------"));
    }

    private void handleCropDebug(Player player, Block crop, Block soil, Block clickedBlock) {
        player.sendMessage(Component.text("§b[耕食系统调试信息 - 作物模式]"));
        
        FertilityManager.EfficiencyBreakdown breakdown;
        float g = 0.0f;
        double p = 0.0;
        int fertility = 0;
        double concentration = 0.0;
        
        if (soil != null) {
             // --- 1. 核心计算 (Unified Calculation) ---
             breakdown = fertilityManager.calculateEfficiencyBreakdown(soil);
             
             // 获取 g 值和 p 值 (Vanilla Mechanics)
             if (crop != null) {
                 g = fertilityManager.calculateGrowthPoints(crop, soil);
                 p = 1.0 / (Math.floor(25.0 / g) + 1.0);
             }
             
             // --- 2. 基础肥力展示 ---
             fertility = fertilityManager.calculateCurrentFertility(soil);
             concentration = fertilityManager.getConcentration(soil);
             
             player.sendMessage(Component.text("§6[基础肥力]"));
             player.sendMessage(Component.text("  §7当前肥力: §e" + fertility));
             player.sendMessage(Component.text("  §7肥料浓度: §b" + String.format("%.1f", concentration)));
             
             // 肥力修正
             String effStr = (breakdown.fertilityBonus >= 0 ? "+" : "") + String.format("%.0f%%", breakdown.fertilityBonus * 100);
             player.sendMessage(Component.text("  §7肥力修正: §a" + effStr));
             
             // --- 3. 环境参数 (g & p) ---
             player.sendMessage(Component.text("§e[环境参数]"));
             player.sendMessage(Component.text("  §7生长点数 (g): §f" + String.format("%.2f", g)));
             player.sendMessage(Component.text("  §7基础概率 (p): §f" + String.format("%.2f%%", p * 100)));
             
             // Analyze G-Value composition if crop exists
             if (crop != null) {
                 analyzeGrowthFactors(player, crop, soil);
             }

             // --- Active Growth Info ---
             // Get Actual GameRule
             int randomTickSpeed = 3;
             try {
                 Integer rule = crop != null ? crop.getWorld().getGameRuleValue(org.bukkit.GameRule.RANDOM_TICK_SPEED) : 3;
                 if (rule != null) randomTickSpeed = rule;
             } catch (Exception ignored) {}
             
             // Get Registry Status
             boolean isRegistered = crop != null && plugin.getFarmingListener().isCropRegistered(crop.getLocation());
             
             player.sendMessage(Component.text("  §b[主动生长]"));
             
             if (breakdown.totalEfficiency > 1.0) {
                 // Calculate DYNAMIC Active Chance based on actual server state
                 // Formula: (Efficiency - 1.0) * BaseChance * VanillaChance
                 // BaseChance = (RandomTickSpeed / 4096.0) * 5.0 (since we run every 5 ticks)
                 
                 double baseChance = (randomTickSpeed / 4096.0) * 5.0;
                 double activeChance = (breakdown.totalEfficiency - 1.0) * baseChance * p;
                 
                 String statusColor = isRegistered ? "§a✔ 已激活" : "§c✘ 异常 (未注册)";
                 if (isRegistered && randomTickSpeed == 0) statusColor = "§e⚠ 暂停 (RandomTick=0)";
                 
                 player.sendMessage(Component.text("    §7状态: " + statusColor));
                 player.sendMessage(Component.text("    §7判定间隔: §f5 ticks (0.25s)"));
                 player.sendMessage(Component.text("    §7GameRule Speed: §f" + randomTickSpeed));
                 player.sendMessage(Component.text("    §7单次概率: §f" + String.format("%.4f%%", activeChance * 100)));
                 
                 if (!isRegistered) {
                     player.sendMessage(Component.text("    §c[警告] 作物满足条件但未被系统追踪！"));
                     player.sendMessage(Component.text("    §c尝试破坏并重新种植以修复。"));
                 }
             } else {
                 player.sendMessage(Component.text("    §7状态: §c✘ 未激活 (效率<=1.0)"));
                 if (isRegistered) {
                      player.sendMessage(Component.text("    §e[提示] 作物仍在列表但效率不足，将自动移除。"));
                 }
             }

        } else {
             player.sendMessage(Component.text("§6[基础肥力] §7(无有效耕地)"));
             breakdown = new FertilityManager.EfficiencyBreakdown(1.0, 0.0, 0.0, fertilityManager.getEarthSpiritBonus(clickedBlock.getLocation()));
        }
        
        // --- 4. 外部修正详情 ---
        player.sendMessage(Component.text("§2[外部修正]"));
        
        // 群系
        String biomeBonusStr = (breakdown.biomeBonus >= 0 ? "+" : "") + String.format("%.0f%%", breakdown.biomeBonus * 100);
        player.sendMessage(Component.text("  §7群系修正: §a" + biomeBonusStr));
        
        // 地灵
        String spiritBonusStr = (breakdown.spiritBonus >= 0 ? "+" : "") + String.format("%.0f%%", breakdown.spiritBonus * 100);
        player.sendMessage(Component.text("  §7地灵修正: §d" + spiritBonusStr));

        // --- 5. 综合统计 ---
        player.sendMessage(Component.text("§8----------------"));
        player.sendMessage(Component.text("§b[综合预估]"));
        
        // 总效率 (Total Efficiency)
        // 显示: 100% (Base) + Bonus%
        // 或者直接显示总加速比
        
        double totalBonus = breakdown.totalEfficiency - 1.0;
        String totalBonusStr = (totalBonus >= 0 ? "+" : "") + String.format("%.0f%%", totalBonus * 100);
        
        player.sendMessage(Component.text("  §f生长效率总计: §e" + totalBonusStr));
        player.sendMessage(Component.text("  §7(构成: 肥力" + String.format("%.0f%%", breakdown.fertilityBonus*100) + 
            " + 群系" + String.format("%.0f%%", breakdown.biomeBonus*100) + 
            " + 地灵" + String.format("%.0f%%", breakdown.spiritBonus*100) + ")"));
            
        // --- 6. 特产掉落预估 ---
        player.sendMessage(Component.text("§8----------------"));
        player.sendMessage(Component.text("§d[特产掉落预估]"));
        
        try {
            org.bukkit.plugin.Plugin biomePlugin = Bukkit.getPluginManager().getPlugin("BiomeGifts");
            if (biomePlugin != null && biomePlugin.isEnabled()) {
                Method calculateMethod = biomePlugin.getClass().getMethod("calculateDropDetails", Block.class);
                @SuppressWarnings("unchecked")
                Map<String, Object> details = (Map<String, Object>) calculateMethod.invoke(biomePlugin, crop != null ? crop : clickedBlock);
                
                String dropItem = (String) details.get("dropItem");
                
                if (dropItem == null) {
                     // Try to see if it's just not configured or really not supported
                     if (isCrop(clickedBlock.getType()) || (crop != null && isCrop(crop.getType()))) {
                        player.sendMessage(Component.text("  §c该作物未配置特产掉落 (Config Missing)。"));
                     } else {
                        player.sendMessage(Component.text("  §7目标不是有效作物，无特产。"));
                     }
                } else {
                    double baseChance = (double) details.getOrDefault("baseChance", 0.0);
                    double geneMultiplier = (double) details.getOrDefault("geneMultiplier", 1.0);
                    double fertilityBonus = (double) details.getOrDefault("fertilityBonus", 0.0);
                    double spiritBonus = (double) details.getOrDefault("spiritBonus", 0.0);
                    double totalChance = (double) details.getOrDefault("totalChance", 0.0);
                
                    player.sendMessage(Component.text("  §7目标物品: §f" + dropItem));
                    player.sendMessage(Component.text("  §7总概率: §e" + String.format("%.2f%%", totalChance * 100)));
                    player.sendMessage(Component.text("    §7- 基础: " + String.format("%.2f%%", baseChance * 100)));
                    player.sendMessage(Component.text("    §7- 基因: " + String.format("x%.2f", geneMultiplier)));
                    player.sendMessage(Component.text("    §7- 肥力: " + String.format("+%.2f%%", fertilityBonus * 100)));
                    player.sendMessage(Component.text("    §7- 地灵: " + String.format("+%.2f%%", spiritBonus * 100)));
                }
            } else {
                 player.sendMessage(Component.text("  §cBiomeGifts 插件未启用。"));
            }
        } catch (Exception e) {
            player.sendMessage(Component.text("  §c计算出错: " + e.getMessage()));
        }

        // --- 7. 基因信息 (Added 2025-12-21) ---
        if (crop != null) {
            GeneData geneData = geneticsManager.getGenesFromBlock(crop);
            if (geneData != null) {
                printGeneInfo(player, geneData);
            } else {
                player.sendMessage(Component.text("§8----------------"));
                player.sendMessage(Component.text("§b[基因信息] §7无基因数据 (原生作物)"));
            }
        }
    }

    // 辅助分析方法：拆解 g 值构成
    private void analyzeGrowthFactors(Player player, Block cropBlock, Block soilBlock) {
        float points = 1.0f;
        List<String> details = new ArrayList<>();
        
        // 1. Soil
        if (soilBlock.getType() == Material.FARMLAND) {
            org.bukkit.block.data.type.Farmland farmland = (org.bukkit.block.data.type.Farmland) soilBlock.getBlockData();
            boolean wet = farmland.getMoisture() > 0;
            points += wet ? 3.0f : 1.0f;
            details.add(wet ? "湿润耕地(+3.0)" : "干燥耕地(+1.0)");
        } else {
            details.add("非耕地(+0.0)");
        }
        
        // 2. Neighbors
        float neighborPoints = 0.0f;
        int wetNeighbors = 0;
        int dryNeighbors = 0;
        
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                Block neighborSoil = soilBlock.getRelative(x, 0, z);
                if (neighborSoil.getType() == Material.FARMLAND) {
                    org.bukkit.block.data.type.Farmland f = (org.bukkit.block.data.type.Farmland) neighborSoil.getBlockData();
                    if (f.getMoisture() > 0) {
                        neighborPoints += 0.75f;
                        wetNeighbors++;
                    } else {
                        neighborPoints += 0.25f;
                        dryNeighbors++;
                    }
                }
            }
        }
        points += neighborPoints;
        details.add("周围耕地: 湿" + wetNeighbors + "/干" + dryNeighbors + " (+" + String.format("%.2f", neighborPoints) + ")");
        
        // 3. Penalty
        boolean diagonalSame = false;
        boolean northSouthSame = false;
        boolean eastWestSame = false;
        Material cropType = cropBlock.getType();
        
        if (isSameCrop(cropBlock.getRelative(-1, 0, -1), cropType) || isSameCrop(cropBlock.getRelative(-1, 0, 1), cropType) ||
            isSameCrop(cropBlock.getRelative(1, 0, -1), cropType) || isSameCrop(cropBlock.getRelative(1, 0, 1), cropType)) {
            diagonalSame = true;
        }
        if (isSameCrop(cropBlock.getRelative(-1, 0, 0), cropType) || isSameCrop(cropBlock.getRelative(1, 0, 0), cropType)) {
            eastWestSame = true;
        }
        if (isSameCrop(cropBlock.getRelative(0, 0, -1), cropType) || isSameCrop(cropBlock.getRelative(0, 0, 1), cropType)) {
            northSouthSame = true;
        }
        
        boolean penalty = false;
        if (diagonalSame || (eastWestSame && northSouthSame)) {
            points /= 2.0f;
            penalty = true;
        }
        
        if (penalty) {
            String reason = diagonalSame ? "对角线同种" : "十字密集种植";
            if (diagonalSame && (eastWestSame && northSouthSame)) reason = "密集种植(完全)";
            details.add("§c惩罚生效: " + reason + " (总分减半)");
        } else {
            details.add("§a种植布局良好 (无惩罚)");
        }
        
        // Output details
        player.sendMessage(Component.text("    §7[g值构成 (总分: " + String.format("%.2f", points) + ")] " + String.join(", ", details)));
    }
    
    private boolean isSameCrop(Block block, Material type) {
        return block.getType() == type;
    }

    private void handleEnvironmentDebug(Player player, Block clickedBlock) {
        player.sendMessage(Component.text("§b[环境信息分析]"));
        String biomeName = clickedBlock.getWorld().getBiome(clickedBlock.getLocation()).getKey().toString();
        player.sendMessage(Component.text("  §7当前群系: §f" + biomeName));

        // BiomeGifts - 矿物/作物列表
        try {
            org.bukkit.plugin.Plugin biomePlugin = Bukkit.getPluginManager().getPlugin("BiomeGifts");
            if (biomePlugin != null && biomePlugin.isEnabled()) {
                Method getConfigManager = biomePlugin.getClass().getMethod("getConfigManager");
                Object configManager = getConfigManager.invoke(biomePlugin);
                
                // 作物列表
                Field cropConfigsField = configManager.getClass().getDeclaredField("cropConfigs");
                cropConfigsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<Material, Object> cropConfigs = (Map<Material, Object>) cropConfigsField.get(configManager);
                printRichPoorLists(player, cropConfigs, biomeName, "作物");
                
                // 矿物列表
                Field oreConfigsField = configManager.getClass().getDeclaredField("oreConfigs");
                oreConfigsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<Material, Object> oreConfigs = (Map<Material, Object>) oreConfigsField.get(configManager);
                printRichPoorLists(player, oreConfigs, biomeName, "矿物");
            }
        } catch (Exception e) {
            player.sendMessage(Component.text("  §c读取 BiomeGifts 数据失败。"));
        }

        // EarthSpirit - 领地/地灵信息
        try {
            org.bukkit.plugin.Plugin spiritPlugin = Bukkit.getPluginManager().getPlugin("EarthSpirit");
            if (spiritPlugin != null && spiritPlugin.isEnabled()) {
                SpiritCheckResult result = checkSpirit(spiritPlugin, clickedBlock.getLocation());
                
                player.sendMessage(Component.text("§d[领地与地灵]"));
                if (result.hasTown) {
                    player.sendMessage(Component.text("  §7所属城镇: §f" + result.townName));
                    
                    if (result.hasMayor) {
                        player.sendMessage(Component.text("  §7城镇领主: §f" + result.mayorName));
                        
                        if (result.hasSpirit) {
                            player.sendMessage(Component.text("  §7地灵状态:"));
                            player.sendMessage(Component.text("    §7名字: " + result.spiritName));
                            player.sendMessage(Component.text("    §7模式: " + ("GUARDIAN".equals(result.mode) ? "§aGUARDIAN" : "§e" + result.mode)));
                            player.sendMessage(Component.text("    §7心情: " + (result.mood >= 90 ? "§a" : "§e") + (int)result.mood));
                            
                            if (result.isActive) {
                                 player.sendMessage(Component.text("    §a[√] 共鸣加成已激活 (显示蓝色粒子)"));
                            } else {
                                 player.sendMessage(Component.text("    §c[x] 共鸣加成未激活"));
                                 if (!"GUARDIAN".equals(result.mode)) player.sendMessage(Component.text("      §7- 需要切换至 GUARDIAN 模式"));
                                 if (result.mood < 90) player.sendMessage(Component.text("      §7- 心情需达到 90"));
                            }
                        } else {
                            player.sendMessage(Component.text("  §c领主未拥有地灵"));
                            if (!result.debugInfo.isEmpty()) player.sendMessage(Component.text("  §cDebug: " + result.debugInfo));
                        }
                    } else {
                        player.sendMessage(Component.text("  §c城镇无领主?"));
                        if (!result.debugInfo.isEmpty()) player.sendMessage(Component.text("  §cDebug: " + result.debugInfo));
                    }
                } else {
                    player.sendMessage(Component.text("  §7此处为荒野 (无领地)"));
                    if (!result.debugInfo.isEmpty()) player.sendMessage(Component.text("  §cDebug: " + result.debugInfo));
                }
            }
        } catch (Throwable t) {
            player.sendMessage(Component.text("  §c地灵检测出错: " + t.getMessage()));
            t.printStackTrace();
        }
    }

    private void printRichPoorLists(Player player, Map<Material, Object> configs, String biomeName, String label) throws Exception {
        List<String> richList = new ArrayList<>();
        List<String> poorList = new ArrayList<>();
        
        for (Map.Entry<Material, Object> entry : configs.entrySet()) {
            Object config = entry.getValue();
            Method getBiomeType = config.getClass().getMethod("getBiomeType", String.class);
            Object typeEnum = getBiomeType.invoke(config, biomeName);
            String type = typeEnum.toString();
            
            if ("RICH".equals(type)) {
                richList.add(entry.getKey().name());
            } else if ("POOR".equals(type)) {
                poorList.add(entry.getKey().name());
            }
        }
        
        if (!richList.isEmpty()) player.sendMessage(Component.text("  §a富集" + label + ": §f" + String.join(", ", richList)));
        else player.sendMessage(Component.text("  §a富集" + label + ": §7无"));
        
        if (!poorList.isEmpty()) player.sendMessage(Component.text("  §c贫瘠" + label + ": §f" + String.join(", ", poorList)));
        else player.sendMessage(Component.text("  §c贫瘠" + label + ": §7无"));
    }

    // --- New Debug Helper Methods (Added 2025-12-21) ---

    // --- Helper to get Target Item (Main Hand vs Off Hand) ---
    // If the player is holding the Debug Tool in Main Hand, return Off Hand.
    // Otherwise return Main Hand.
    private ItemStack getTargetItem(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        
        // Check if main hand is Debug Tool
        if (main.getType() == Material.GOLDEN_HOE && main.hasItemMeta()) {
             ItemStack off = player.getInventory().getItemInOffHand();
             // Even if offhand is AIR, we return it so the caller knows the target is empty
             // instead of falling back to Main Hand (Debug Tool).
             return off;
        }
        
        return main;
    }
    
    private boolean isSeed(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        Material type = item.getType();
        String name = type.name();
        
        // Basic seeds
        if (name.contains("_SEEDS")) return true;
        
        // Special crops that are their own seeds
        if (type == Material.POTATO || type == Material.CARROT || type == Material.NETHER_WART) return true;
        if (type == Material.COCOA_BEANS) return true;
        if (type == Material.PITCHER_POD) return true;
        if (type == Material.SWEET_BERRIES) return true;
        if (type == Material.GLOW_BERRIES) return true;
        
        return false;
    }

    private void simulateHarvest(Player player) {
        ItemStack hand = getTargetItem(player);
        
        if (hand == null || hand.getType() == Material.AIR) {
            player.sendMessage("§c请在副手持有一个带有基因数据的种子/物品 (若主手为调试工具)。");
            return;
        }
        
        if (!isSeed(hand)) {
             player.sendMessage("§c目标物品不是有效的种子/作物。");
             return;
        }

        GeneData data = geneticsManager.getGenesFromItem(hand);
        if (data == null) {
            player.sendMessage("§c该物品没有基因数据。");
            return;
        }
        
        player.sendMessage("§7正在模拟物品: " + hand.getType());

        double yieldBonus = data.getGene(GeneType.YIELD);
        player.sendMessage(Component.text("§8§m--------------------------------"));
        player.sendMessage(Component.text("§b[收获模拟 (Harvest Simulation)]"));
        player.sendMessage(Component.text("  §7当前产量基因: §e" + String.format("%.2f", yieldBonus) + "x"));

        // 1. 原版掉落模拟 (Vanilla Drops)
        player.sendMessage(Component.text("  §61. 原版掉落加成 (Vanilla Bonus):"));
        if (yieldBonus <= 1.0) {
            player.sendMessage(Component.text("    §7- 无加成 (<= 1.0)"));
        } else {
            double remaining = yieldBonus - 1.0;
            int step = 1;
            int totalExtra = 0;
            
            while (remaining > 0) {
                double chunk = Math.min(1.0, remaining);
                double prob = chunk / 2.0;
                boolean success = Math.random() < prob;
                if (success) totalExtra++;
                
                String resultColor = success ? "§aYES" : "§cNO";
                player.sendMessage(Component.text(String.format("    §7- 区间 %d (%.1f): 概率 %.0f%% -> %s", 
                    step, chunk, prob * 100, resultColor)));
                
                remaining -= chunk;
                step++;
            }
            player.sendMessage(Component.text("    §a=> 模拟结果: 额外掉落 " + totalExtra + " 个"));
        }

        // 2. 特产掉落模拟 (Specialty Drops)
        player.sendMessage(Component.text("  §d2. 特产掉落修正 (Specialty Modifier):"));
        // Formula: Bonus = -0.25 + (Yield / 5.0) * 1.25
        double bonusPercent = -0.25 + (yieldBonus / 5.0) * 1.25;
        double multiplier = 1.0 + bonusPercent;
        if (multiplier < 0.0) multiplier = 0.0;
        
        String sign = bonusPercent >= 0 ? "+" : "";
        player.sendMessage(Component.text(String.format("    §7- 修正幅度: %s%.1f%%", sign, bonusPercent * 100)));
        player.sendMessage(Component.text(String.format("    §7- 最终乘区: x%.2f", multiplier)));
        
        player.sendMessage(Component.text("§8§m--------------------------------"));
    }

    private void simulateBlockHarvest(Player player, Block block) {
        player.sendMessage(Component.text("§8§m--------------------------------"));
        player.sendMessage(Component.text("§b[收获模拟 - 田间作物]"));
        player.sendMessage(Component.text("  §7目标: " + block.getType()));

        // Call BiomeGifts via Reflection
        try {
            org.bukkit.plugin.Plugin biomePlugin = Bukkit.getPluginManager().getPlugin("BiomeGifts");
            if (biomePlugin != null && biomePlugin.isEnabled()) {
                Method calculateMethod = biomePlugin.getClass().getMethod("calculateDropDetails", Block.class);
                @SuppressWarnings("unchecked")
                Map<String, Object> details = (Map<String, Object>) calculateMethod.invoke(biomePlugin, block);
                
                String dropItem = (String) details.get("dropItem");
                
                if (dropItem == null) {
                    player.sendMessage(Component.text("  §c该作物未配置特产掉落 (Config Missing)。"));
                } else {
                    double baseChance = (double) details.getOrDefault("baseChance", 0.0);
                    double geneMultiplier = (double) details.getOrDefault("geneMultiplier", 1.0);
                    double fertilityBonus = (double) details.getOrDefault("fertilityBonus", 0.0);
                    double spiritBonus = (double) details.getOrDefault("spiritBonus", 0.0);
                    double totalChance = (double) details.getOrDefault("totalChance", 0.0);
                
                    player.sendMessage(Component.text("  §d特产掉落预估 (BiomeGifts算法):"));
                    player.sendMessage(Component.text("    §7- 目标物品: §f" + dropItem));
                    player.sendMessage(Component.text("    §7- 基础概率: §f" + String.format("%.2f%%", baseChance * 100)));
                    player.sendMessage(Component.text("    §7- 基因修正: §f" + String.format("x%.2f", geneMultiplier)));
                    player.sendMessage(Component.text("    §7- 肥力加成: §f" + String.format("+%.2f%%", fertilityBonus * 100)));
                    player.sendMessage(Component.text("    §7- 地灵加成: §f" + String.format("+%.2f%%", spiritBonus * 100)));
                    player.sendMessage(Component.text("    §7=> 最终概率: §e" + String.format("%.2f%%", totalChance * 100)));
                }
            } else {
                 player.sendMessage(Component.text("  §cBiomeGifts 插件未启用，无法计算。"));
            }
        } catch (Exception e) {
            player.sendMessage(Component.text("  §c调用 BiomeGifts 算法失败: " + e.getMessage()));
            e.printStackTrace();
        }
        player.sendMessage(Component.text("§8§m--------------------------------"));
    }

    private void inspectHand(Player player) {
        ItemStack hand = getTargetItem(player);
        if (hand == null || hand.getType() == Material.AIR) {
            player.sendMessage("§cHold a seed/item to inspect (Offhand if using Debug Tool).");
            return;
        }

        GeneData data = geneticsManager.getGenesFromItem(hand);
        player.sendMessage(Component.text("§8§m--------------------------------"));
        player.sendMessage(Component.text("§b[Hand Inspection]"));
        player.sendMessage(Component.text("  §7Item: " + hand.getType()));
        
        if (data != null) {
            printGeneInfo(player, data);
        } else {
            player.sendMessage(Component.text("  §7No Gene Data found on this item."));
        }
        player.sendMessage(Component.text("§8§m--------------------------------"));
    }

    private void randomizeHand(Player player) {
        ItemStack hand = getTargetItem(player);
        if (hand == null || hand.getType() == Material.AIR) {
            player.sendMessage("§c请在副手持有种子 (若主手为调试工具)。");
            return;
        }
        
        if (!isSeed(hand)) {
             player.sendMessage("§c目标不是种子，无法进行基因随机化。");
             return;
        }
        
        // Double check we are not randomizing the debug tool itself (Defensive)
        if (hand.getType() == Material.GOLDEN_HOE) {
             player.sendMessage("§cCannot randomize Golden Hoe!");
             return;
        }
        
        GeneData data = geneticsManager.getGenesFromItem(hand);
        // Create new if null
        if (data == null) data = new GeneData();
        
        data.randomize();
        data.setIdentified(true);
        geneticsManager.saveGenesToItem(hand, data);
        
        player.sendMessage("§a[Debug] 基因随机化成功 (已应用欧皇算法)!");
        inspectHand(player);
    }

    private void identifyHand(Player player) {
        ItemStack hand = getTargetItem(player);
        if (hand == null || hand.getType() == Material.AIR) {
            player.sendMessage("§cHold a seed/item first (Offhand if using Debug Tool).");
            return;
        }
        
        GeneData data = geneticsManager.getGenesFromItem(hand);
        if (data == null) {
            player.sendMessage("§cNo gene data to identify.");
            return;
        }
        
        data.setIdentified(true);
        geneticsManager.saveGenesToItem(hand, data);
        
        player.sendMessage("§a[Debug] Identification Success!");
        inspectHand(player);
    }

    private void setConcentration(Player player, Block block, double amount) {
        try {
            // Use Reflection to access private getChunkData
            Method getChunkDataMethod = FertilityManager.class.getDeclaredMethod("getChunkData", org.bukkit.Chunk.class);
            getChunkDataMethod.setAccessible(true);
            Object chunkDataObj = getChunkDataMethod.invoke(fertilityManager, block.getChunk());
            
            // Now cast to ChunkFertilityData (it's public)
            com.example.cuisinefarming.fertility.ChunkFertilityData data = (com.example.cuisinefarming.fertility.ChunkFertilityData) chunkDataObj;
            
            // Set data
            long now = System.currentTimeMillis();
            data.setFertilizerData(block.getX() & 15, block.getY(), block.getZ() & 15, amount, now);
            
            player.sendMessage("§a[Debug] Set Soil Concentration to " + amount);
            
            // Trigger visual update if possible (optional)
            
        } catch (Exception e) {
            player.sendMessage("§c[Debug] Error setting concentration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void printGeneInfo(Player player, GeneData data) {
        player.sendMessage(Component.text("§b[基因信息]"));
        
        // Use unified display logic from GeneticsManager
        List<Component> lore = geneticsManager.generateGeneLore(data);
        for (Component line : lore) {
            player.sendMessage(line);
        }
    }

    private static class SpiritCheckResult {
        boolean hasTown = false;
        String townName = "";
        boolean hasMayor = false;
        String mayorName = "";
        boolean hasSpirit = false;
        String spiritName = "";
        String mode = "";
        double mood = 0;
        boolean isActive = false;
        String debugInfo = "";
    }

    private SpiritCheckResult checkSpirit(org.bukkit.plugin.Plugin spiritPlugin, Location loc) throws Exception {
        SpiritCheckResult result = new SpiritCheckResult();
        
        Class<?> townyIntegrationClass = spiritPlugin.getClass().getClassLoader().loadClass("com.example.earthspirit.TownyIntegration");
        Method getTownAt = townyIntegrationClass.getMethod("getTownAt", Location.class);
        Object town = getTownAt.invoke(null, loc);
        
        if (town != null) {
            result.hasTown = true;
            Method getName = town.getClass().getMethod("getName");
            result.townName = (String) getName.invoke(town);
            
            // Use TownyIntegration.getMayor(town) for robustness
            Object mayor = null;
            try {
                for (Method m : townyIntegrationClass.getMethods()) {
                    if (m.getName().equals("getMayor") && m.getParameterCount() == 1) {
                        mayor = m.invoke(null, town);
                        break;
                    }
                }
            } catch (Exception e) {
                // Fallback to direct call if static helper fails
                try {
                    Method getMayor = town.getClass().getMethod("getMayor");
                    mayor = getMayor.invoke(town);
                } catch (Exception ex) {}
            }
            
            if (mayor != null) {
                result.hasMayor = true;
                Method getMayorName = mayor.getClass().getMethod("getName");
                result.mayorName = (String) getMayorName.invoke(mayor);
                
                Method getUUID = mayor.getClass().getMethod("getUUID");
                UUID mayorId = (UUID) getUUID.invoke(mayor);
                
                Method getManager = spiritPlugin.getClass().getMethod("getManager");
                Object manager = getManager.invoke(spiritPlugin);
                
                Method getSpiritByOwner = manager.getClass().getMethod("getSpiritByOwner", UUID.class);
                Object spirit = getSpiritByOwner.invoke(manager, mayorId);
                
                // Debug UUIDs if spirit is null
                if (spirit == null) {
                    Method getAllSpirits = manager.getClass().getMethod("getAllSpirits");
                    @SuppressWarnings("unchecked")
                    Map<UUID, Object> allSpirits = (Map<UUID, Object>) getAllSpirits.invoke(manager);
                    result.debugInfo = "MayorUUID: " + mayorId + ", StoredUUIDs: " + allSpirits.keySet();
                }

                if (spirit != null) {
                    result.hasSpirit = true;
                    Method getSpiritName = spirit.getClass().getMethod("getName");
                    result.spiritName = (String) getSpiritName.invoke(spirit);
                    
                    Method getMode = spirit.getClass().getMethod("getMode");
                    Object modeObj = getMode.invoke(spirit);
                    result.mode = modeObj.toString();
                    
                    Method getMood = spirit.getClass().getMethod("getMood");
                    result.mood = (double) getMood.invoke(spirit);
                    
                    if ("GUARDIAN".equals(result.mode) && result.mood >= 90) {
                        result.isActive = true;
                    }
                }
            } else {
                String extraInfo = "";
                try {
                    Method getResidents = town.getClass().getMethod("getResidents");
                    List<?> residents = (List<?>) getResidents.invoke(town);
                    extraInfo = " Residents: " + (residents != null ? residents.size() : "null");
                    if (residents != null && !residents.isEmpty()) {
                        Object firstRes = residents.get(0);
                        Method getResName = firstRes.getClass().getMethod("getName");
                        extraInfo += ", First: " + getResName.invoke(firstRes);
                    }
                } catch (Exception e) {
                    extraInfo = " (Error getting residents: " + e.getMessage() + ")";
                }
                result.debugInfo = "Town found but Mayor is NULL. Town: " + result.townName + extraInfo;
            }
        }
        return result;
    }

    private boolean isCrop(Material material) {
        return material == Material.WHEAT || 
               material == Material.CARROTS || 
               material == Material.POTATOES || 
               material == Material.BEETROOTS ||
               material == Material.MELON_STEM ||
               material == Material.PUMPKIN_STEM ||
               material == Material.ATTACHED_MELON_STEM ||
               material == Material.ATTACHED_PUMPKIN_STEM ||
               material == Material.NETHER_WART ||
               material == Material.COCOA;
    }
}
