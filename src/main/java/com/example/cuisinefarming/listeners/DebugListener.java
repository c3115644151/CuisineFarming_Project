package com.example.cuisinefarming.listeners;

import com.example.cuisinefarming.CuisineFarming;
import com.example.cuisinefarming.fertility.FertilityManager;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DebugListener implements Listener {

    private final NamespacedKey debugKey;
    private final FertilityManager fertilityManager;

    public DebugListener(CuisineFarming plugin) {
        this.debugKey = new NamespacedKey(plugin, "debug_tool");
        this.fertilityManager = plugin.getFertilityManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onDebugUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;
        
        if (!item.getItemMeta().getPersistentDataContainer().has(debugKey, PersistentDataType.BYTE)) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;
        
        event.setCancelled(true);
        Player player = event.getPlayer();
        
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
        
        double totalEfficiency = 1.0;
        double spiritBonus = 0.0;
        double baseEfficiency = 1.0;
        float g = 0.0f;
        double p = 0.0;
        int fertility = 0;
        double concentration = 0.0;
        double cuisineDrop = 0.0;
        
        if (soil != null) {
             // --- 1. 核心计算 (Unified Calculation) ---
             // 获取系统计算的权威值 (Base + Spirit)
             totalEfficiency = fertilityManager.calculateTotalEfficiency(soil);
             spiritBonus = fertilityManager.getEarthSpiritBonus(soil.getLocation());
             baseEfficiency = totalEfficiency - spiritBonus; // Derived Base
             
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
             
             // Display Base Efficiency Delta (e.g. 1.5 -> +50%)
             String effStr = (baseEfficiency >= 1.0 ? "+" : "") + String.format("%.2f%%", (baseEfficiency - 1.0) * 100);
             player.sendMessage(Component.text("  §7生长修正: §a" + effStr));
             
             if (fertility > 100) {
                 cuisineDrop = (fertility - 100) * 0.002;
                 player.sendMessage(Component.text("  §7掉落修正: §d+" + String.format("%.2f%%", cuisineDrop * 100)));
             }
             
             // --- 3. 环境参数 (g & p) ---
             player.sendMessage(Component.text("§e[环境参数]"));
             player.sendMessage(Component.text("  §7生长点数 (g): §f" + String.format("%.2f", g)));
             player.sendMessage(Component.text("  §7基础概率 (p): §f" + String.format("%.2f%%", p * 100)));

        } else {
             player.sendMessage(Component.text("§6[基础肥力] §7(无有效耕地)"));
             // Still try to get spirit bonus for location
             spiritBonus = fertilityManager.getEarthSpiritBonus(clickedBlock.getLocation());
        }
        
        // --- 4. 地域馈赠 & 地灵 ---
        double biomeEff = 0.0;
        double biomeDropMult = 1.0;
        
        String biomeName = (crop != null ? crop : clickedBlock).getWorld().getBiome((crop != null ? crop : clickedBlock).getLocation()).getKey().toString();
        
        // 地域馈赠 (BiomeGifts)
        try {
            org.bukkit.plugin.Plugin biomePlugin = Bukkit.getPluginManager().getPlugin("BiomeGifts");
            if (biomePlugin != null && biomePlugin.isEnabled()) {
                player.sendMessage(Component.text("§2[地域环境]"));
                player.sendMessage(Component.text("  §7当前群系: §f" + biomeName));

                Method getConfigManager = biomePlugin.getClass().getMethod("getConfigManager");
                Object configManager = getConfigManager.invoke(biomePlugin);
                
                Field cropConfigsField = configManager.getClass().getDeclaredField("cropConfigs");
                cropConfigsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<Material, Object> cropConfigs = (Map<Material, Object>) cropConfigsField.get(configManager);
                
                if (crop != null) {
                    Object currentCropConfig = cropConfigs.get(crop.getType());
                    if (currentCropConfig != null) {
                        Method getBiomeType = currentCropConfig.getClass().getMethod("getBiomeType", String.class);
                        Object typeEnum = getBiomeType.invoke(currentCropConfig, biomeName);
                        String type = typeEnum.toString();
                        
                        if ("RICH".equals(type)) {
                            Field richSpeedBonusF = currentCropConfig.getClass().getField("richSpeedBonus");
                            double richBonus = richSpeedBonusF.getDouble(currentCropConfig);
                            biomeEff = richBonus;
                            
                            Field richMultiplierF = currentCropConfig.getClass().getField("richMultiplier");
                            biomeDropMult = richMultiplierF.getDouble(currentCropConfig);
                            
                            player.sendMessage(Component.text("  §7当前作物: §a富集区 §7(生长 +" + (int)(richBonus*100) + "%, 掉落 x" + biomeDropMult + ")"));
                        } else if ("POOR".equals(type)) {
                            Field poorSpeedPenaltyF = currentCropConfig.getClass().getField("poorSpeedPenalty");
                            double poorPenalty = poorSpeedPenaltyF.getDouble(currentCropConfig);
                            biomeEff = -poorPenalty;
                            
                            Field poorMultiplierF = currentCropConfig.getClass().getField("poorMultiplier");
                            biomeDropMult = poorMultiplierF.getDouble(currentCropConfig);
                            
                            player.sendMessage(Component.text("  §7当前作物: §c贫瘠区 §7(生长 -" + (int)(poorPenalty*100) + "%, 掉落 x" + biomeDropMult + ")"));
                        } else {
                            player.sendMessage(Component.text("  §7当前作物: §f普通区"));
                        }
                    } else {
                        player.sendMessage(Component.text("  §7当前作物: §7未配置"));
                    }
                }
            }
        } catch (Exception e) {
            player.sendMessage(Component.text("  §c读取 BiomeGifts 数据失败。"));
        }

        // 地灵系统 (EarthSpirit) - 使用 unified value 但保留详细 debug 信息
        boolean spiritActive = (spiritBonus > 0);
        try {
            org.bukkit.plugin.Plugin spiritPlugin = Bukkit.getPluginManager().getPlugin("EarthSpirit");
            if (spiritPlugin != null && spiritPlugin.isEnabled()) {
                SpiritCheckResult result = checkSpirit(spiritPlugin, clickedBlock.getLocation());
                
                player.sendMessage(Component.text("§d[地灵共鸣]"));
                if (result.hasTown) {
                    if (result.hasMayor) {
                        player.sendMessage(Component.text("  §7城镇领主: §f" + result.mayorName));
                        
                        if (result.hasSpirit) {
                            if (spiritActive) {
                                // 使用真实的 spiritBonus
                                player.sendMessage(Component.text("  §a地灵共鸣: 激活 (+" + String.format("%.0f%%", spiritBonus * 100) + ")"));
                                player.sendMessage(Component.text("    §7- 地灵: " + result.spiritName));
                                player.sendMessage(Component.text("    §7- 状态: " + result.mode + " (心情 " + String.format("%.1f", result.mood) + ")"));
                            } else {
                                player.sendMessage(Component.text("  §c地灵共鸣: 未激活"));
                                if (!"GUARDIAN".equals(result.mode)) {
                                    player.sendMessage(Component.text("    §7- 原因: 地灵非守护态 (" + result.mode + ")"));
                                } else if (result.mood < 90) {
                                    player.sendMessage(Component.text("    §7- 原因: 地灵心情不足 (" + String.format("%.1f", result.mood) + "/90.0)"));
                                }
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
                    player.sendMessage(Component.text("  §7此处无城镇领地"));
                    if (!result.debugInfo.isEmpty()) player.sendMessage(Component.text("  §cDebug: " + result.debugInfo));
                }
            }
        } catch (Throwable t) {
            player.sendMessage(Component.text("  §c地灵检测出错: " + t.getMessage()));
            t.printStackTrace();
        }

        // --- 6. 综合预估 ---
        player.sendMessage(Component.text("§8----------------"));
        player.sendMessage(Component.text("§b[综合预估]"));
        
        // Final Total = (TotalEfficiency - 1.0) + BiomeEff
        // Example: Base(1.5) + Spirit(0.1) = 1.6 TotalEff. Delta = +0.6.
        // Biome = +0.2.
        // Final Delta = 0.6 + 0.2 = +0.8 (+80%).
        double finalDelta = (totalEfficiency - 1.0) + biomeEff;
        
        String totalGrowthStr = (finalDelta >= 0 ? "+" : "") + String.format("%.0f%%", finalDelta * 100);
        player.sendMessage(Component.text("  §f生长效率总计: §e" + totalGrowthStr));
        
        player.sendMessage(Component.text("  §f特产掉率加成:"));
        boolean anyDropBonus = false;
        
        if (cuisineDrop > 0) {
            player.sendMessage(Component.text("    §6肥力: §f+" + String.format("%.2f%%", cuisineDrop * 100) + " 概率"));
            anyDropBonus = true;
        }
        
        if (biomeDropMult != 1.0) {
            player.sendMessage(Component.text("    §2群系: §fx" + biomeDropMult + " 倍率"));
            anyDropBonus = true;
        }
        
        if (spiritActive) {
            player.sendMessage(Component.text("    §d地灵: §f+10% 额外判定"));
            anyDropBonus = true;
        }
        
        if (!anyDropBonus) {
            player.sendMessage(Component.text("    §7无活跃加成。"));
        }
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

    // 辅助类用于传递地灵检查结果
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
