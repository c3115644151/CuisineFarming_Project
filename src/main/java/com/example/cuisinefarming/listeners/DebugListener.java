package com.example.cuisinefarming.listeners;

import com.example.cuisinefarming.CuisineFarming;
import com.example.cuisinefarming.fertility.FertilityManager;
import com.example.cuisinefarming.genetics.Allele;
import com.example.cuisinefarming.genetics.GeneData;
import com.example.cuisinefarming.genetics.GenePair;
import com.example.cuisinefarming.genetics.GeneticsManager;
import com.example.cuisinefarming.genetics.Trait;
import com.example.cuisinefarming.cooking.CookingRecipe;
import com.example.cuisinefarming.cooking.FoodTag;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DebugListener implements Listener {

    private final CuisineFarming plugin;
    private final NamespacedKey debugKey;
    private final NamespacedKey starRatingKey;
    private final FertilityManager fertilityManager;
    private final GeneticsManager geneticsManager;

    // Player Debug Mode State
    private final Map<UUID, DebugMode> playerModes = new HashMap<>();

    public enum DebugMode {
        INSPECT("Inspect (查看信息)"),
        RANDOMIZE_HAND("Randomize Hand (随机基因)"),
        IDENTIFY_HAND("Identify Hand (鉴定手持)"),
        GIVE_SEED_DEFAULT("Give Seed: Default (普通 A1/a1)"),
        GIVE_SEED_MYTHICAL("Give Seed: Mythical (传说 A4/A4)"),
        GIVE_SEED_LEGENDARY("Give Seed: Legendary (完美 A3/A3)"),
        GIVE_SEED_WEAKEST("Give Seed: Weakest (最弱 a4/a4)"),
        GIVE_SEED_CHAOS("Give Seed: Chaos (随机/混乱)"),
        GIVE_FOOD_STAR("Give Food: Star Only (纯星级食材)"),
        SET_CONC_HIGH("Set Soil: High (设置高肥 150)"),
        SET_CONC_TOXIC("Set Soil: Toxic (设置毒性 250)"),
        SIMULATE_HARVEST("Simulate: Harvest (模拟收割计算)"),
        TEST_RECIPE_MATCH("Test: Recipe Match (测试食谱匹配)");
        
        final String desc;
        DebugMode(String desc) { this.desc = desc; }
    }

    public DebugListener(CuisineFarming plugin) {
        this.plugin = plugin;
        this.debugKey = new NamespacedKey(plugin, "debug_tool");
        this.starRatingKey = new NamespacedKey(plugin, "food_star_rating");
        this.fertilityManager = plugin.getFertilityManager();
        this.geneticsManager = plugin.getGeneticsManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        // Check for Debug Tool (Golden Hoe)
        if (item == null || item.getType() != Material.GOLDEN_HOE || !item.hasItemMeta()) return;
        
        // Verify it is the actual debug tool using PDC
        if (!item.getItemMeta().getPersistentDataContainer().has(debugKey, PersistentDataType.BYTE)) {
            return;
        }
        
        event.setCancelled(true);
        Action action = event.getAction();
        
        if (!playerModes.containsKey(player.getUniqueId())) {
            playerModes.put(player.getUniqueId(), DebugMode.INSPECT);
        }
        DebugMode currentMode = playerModes.get(player.getUniqueId());

        // LEFT CLICK: Cycle Mode
        if (action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR) {
            cycleMode(player);
        }
        
        // RIGHT CLICK: Execute Action
        if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
            executeAction(player, currentMode, event.getClickedBlock());
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

            case GIVE_SEED_DEFAULT:
                giveSeed(player, Allele.DOMINANT_1, Allele.RECESSIVE_1, "Default (A1/a1)");
                break;

            case GIVE_SEED_MYTHICAL:
                giveSeed(player, Allele.DOMINANT_4, Allele.DOMINANT_4, "Mythical (A4/A4)");
                break;

            case GIVE_SEED_LEGENDARY:
                giveSeed(player, Allele.DOMINANT_3, Allele.DOMINANT_3, "Legendary (A3/A3)");
                break;
            
            case GIVE_SEED_WEAKEST:
                giveSeed(player, Allele.RECESSIVE_4, Allele.RECESSIVE_4, "Weakest (a4/a4)");
                break;

            case GIVE_SEED_CHAOS:
                giveChaosSeed(player);
                break;

            case GIVE_FOOD_STAR:
                giveStarFood(player);
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

            case TEST_RECIPE_MATCH:
                testRecipeMatch(player);
                break;
        }
    }

    private void testRecipeMatch(Player player) {
        List<ItemStack> ingredients = new ArrayList<>();
        
        // Add Offhand item
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && offhand.getType() != Material.AIR) {
            ingredients.add(offhand);
        }
        
        // Add Hotbar items (0-8) excluding the debug tool itself
        for (int i = 0; i < 9; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() != Material.AIR && item.getType() != Material.GOLDEN_HOE) {
                ingredients.add(item);
            }
        }
        
        player.sendMessage(Component.text("§8§m--------------------------------"));
        player.sendMessage(Component.text("§b[Cooking Recipe Test]"));
        
        if (ingredients.isEmpty()) {
            player.sendMessage(Component.text("§cNo ingredients found in hotbar/offhand!"));
            return;
        }
        
        player.sendMessage(Component.text("§7Ingredients:"));
        for (ItemStack item : ingredients) {
            Set<FoodTag> tags = plugin.getCookingManager().getItemTags(item);
            String tagName = tags.isEmpty() ? "None" : tags.toString();
            player.sendMessage(Component.text("  - " + item.getType() + " x" + item.getAmount() + " §8[" + tagName + "]"));
        }
        
        CookingRecipe match = plugin.getCookingManager().matchRecipe(ingredients);
        
        player.sendMessage(Component.text("§7Result:"));
        if (match != null) {
            player.sendMessage(Component.text("  §a✔ MATCHED: §f" + match.getDisplayName()));
            player.sendMessage(Component.text("  §7Tier: " + match.getTier()));
            player.sendMessage(Component.text("  §7Output: " + match.getResultTemplate().getType()));
        } else {
            player.sendMessage(Component.text("  §c✘ NO MATCH (Dark Dish)"));
        }
        player.sendMessage(Component.text("§8§m--------------------------------"));
    }

    private boolean isCrop(Material type) {
        return type == Material.WHEAT || type == Material.CARROTS || type == Material.POTATOES || type == Material.BEETROOTS 
            || type == Material.NETHER_WART || type == Material.COCOA || type == Material.PITCHER_CROP 
            || type == Material.SWEET_BERRY_BUSH || type == Material.CAVE_VINES || type == Material.CAVE_VINES_PLANT;
    }

    private void handleBlockInspect(Player player, Block clickedBlock) {
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
                soil = null;
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
             breakdown = fertilityManager.calculateEfficiencyBreakdown(soil);
             
             if (crop != null) {
                 g = fertilityManager.calculateGrowthPoints(crop, soil);
                 p = 1.0 / (Math.floor(25.0 / g) + 1.0);
             }
             
             fertility = fertilityManager.calculateCurrentFertility(soil);
             concentration = fertilityManager.getConcentration(soil);
             
             player.sendMessage(Component.text("§6[基础肥力]"));
             player.sendMessage(Component.text("  §7当前肥力: §e" + fertility));
             player.sendMessage(Component.text("  §7肥料浓度: §b" + String.format("%.1f", concentration)));
             
             String effStr = (breakdown.fertilityBonus >= 0 ? "+" : "") + String.format("%.0f%%", breakdown.fertilityBonus * 100);
             player.sendMessage(Component.text("  §7肥力修正: §a" + effStr));
             
             player.sendMessage(Component.text("§e[环境参数]"));
             player.sendMessage(Component.text("  §7生长点数 (g): §f" + String.format("%.2f", g)));
             player.sendMessage(Component.text("  §7基础概率 (p): §f" + String.format("%.2f%%", p * 100)));
             
             if (crop != null) {
                 analyzeGrowthFactors(player, crop, soil);
             }

             int randomTickSpeed = 3;
             try {
                 Integer rule = crop != null ? crop.getWorld().getGameRuleValue(org.bukkit.GameRule.RANDOM_TICK_SPEED) : 3;
                 if (rule != null) randomTickSpeed = rule;
             } catch (Exception ignored) {}
             
             boolean isRegistered = crop != null && plugin.getFarmingListener().isCropRegistered(crop.getLocation());
             
             player.sendMessage(Component.text("  §b[主动生长]"));
             
             if (breakdown.totalEfficiency > 1.0) {
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
        
        player.sendMessage(Component.text("§2[外部修正]"));
        String biomeBonusStr = (breakdown.biomeBonus >= 0 ? "+" : "") + String.format("%.0f%%", breakdown.biomeBonus * 100);
        player.sendMessage(Component.text("  §7群系修正: §a" + biomeBonusStr));
        String spiritBonusStr = (breakdown.spiritBonus >= 0 ? "+" : "") + String.format("%.0f%%", breakdown.spiritBonus * 100);
        player.sendMessage(Component.text("  §7地灵修正: §a" + spiritBonusStr));

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
    
    private void analyzeGrowthFactors(Player player, Block crop, Block soil) {
         // Placeholder for detailed growth factor analysis if needed
    }

    private void handleEnvironmentDebug(Player player, Block clickedBlock) {
        player.sendMessage(Component.text("§b[耕食系统调试信息 - 环境模式]"));
        
        try {
            org.bukkit.plugin.Plugin biomePlugin = Bukkit.getPluginManager().getPlugin("BiomeGifts");
            if (biomePlugin != null && biomePlugin.isEnabled()) {
                Method getManager = biomePlugin.getClass().getMethod("getBiomeManager");
                Object manager = getManager.invoke(biomePlugin);
                Method getConfig = manager.getClass().getMethod("getBiomeConfig", org.bukkit.block.Biome.class);
                Object config = getConfig.invoke(manager, clickedBlock.getBiome());
                
                player.sendMessage(Component.text("  §7群系: §f" + clickedBlock.getBiome()));
                
                if (config != null) {
                     Method getBonus = config.getClass().getMethod("getFarmingBonus");
                     double bonus = (double) getBonus.invoke(config);
                     player.sendMessage(Component.text("  §7群系加成: §a" + (bonus * 100) + "%"));
                }
            }
        } catch (Exception e) {}
        
        try {
            org.bukkit.plugin.Plugin spiritPlugin = Bukkit.getPluginManager().getPlugin("EarthSpirit");
            if (spiritPlugin != null && spiritPlugin.isEnabled()) {
                SpiritCheckResult result = checkSpirit(spiritPlugin, clickedBlock.getLocation());
                
                player.sendMessage(Component.text("§6[地灵系统]"));
                if (result.hasTown) {
                    player.sendMessage(Component.text("  §7城镇: §e" + result.townName));
                    if (result.hasMayor) {
                        player.sendMessage(Component.text("  §7领主: §f" + result.mayorName));
                        if (result.hasSpirit) {
                            player.sendMessage(Component.text("  §7守护灵: §d" + result.spiritName));
                            player.sendMessage(Component.text("  §7心情: " + String.format("%.1f", result.mood)));
                            player.sendMessage(Component.text("  §7加成: " + (result.isActive ? "§a生效中" : "§c未激活 (心情<50)")));
                            if (!result.debugInfo.isEmpty()) player.sendMessage(Component.text("  §cDebug: " + result.debugInfo));
                        } else {
                            player.sendMessage(Component.text("  §c城镇无领主?"));
                            if (!result.debugInfo.isEmpty()) player.sendMessage(Component.text("  §cDebug: " + result.debugInfo));
                        }
                    } else {
                        player.sendMessage(Component.text("  §7此处为荒野 (无领地)"));
                        if (!result.debugInfo.isEmpty()) player.sendMessage(Component.text("  §cDebug: " + result.debugInfo));
                    }
                }
            }
        } catch (Throwable t) {
            player.sendMessage(Component.text("  §c地灵检测出错: " + t.getMessage()));
        }
    }

    private ItemStack getTargetItem(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main.getType() == Material.GOLDEN_HOE && main.hasItemMeta()) {
             ItemStack off = player.getInventory().getItemInOffHand();
             return off;
        }
        return main;
    }
    
    private boolean isSeed(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        Material type = item.getType();
        String name = type.name();
        
        if (name.contains("_SEEDS")) return true;
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
        double yieldBonus = data.getGenePair(Trait.YIELD).getPhenotypeValue();
        player.sendMessage(Component.text("§8§m--------------------------------"));
        player.sendMessage(Component.text("§b[收获模拟 (Harvest Simulation)]"));
        player.sendMessage(Component.text("  §7当前产量基因: §e" + String.format("%.2f", yieldBonus) + "x"));

        player.sendMessage(Component.text("  §61. 原版掉落加成 (Vanilla Bonus):"));
        if (yieldBonus <= 0.0) {
            player.sendMessage(Component.text("    §7- 无加成 (<= 0.0)"));
        } else {
            double remaining = yieldBonus;
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

        player.sendMessage(Component.text("  §d2. 特产掉落修正 (Specialty Modifier):"));
        double bonusPercent = (yieldBonus * 1.25 - 4.25) / 7.0;
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
        
        GeneData geneData = geneticsManager.getGenesFromBlock(block);
        if (geneData != null) {
            double yieldVal = geneData.getGenePair(Trait.YIELD).getPhenotypeValue();
            player.sendMessage("  §7基因产量加成: " + String.format("%.2f", yieldVal));
            
            Material type = block.getType();
            boolean isDual = (type == Material.POTATOES || type == Material.CARROTS);
            boolean isSeedBlock = (type == Material.WHEAT || type == Material.BEETROOTS);
            
            player.sendMessage("  §7是否双重用途 (Potato/Carrot): " + isDual);
            if (isDual) {
                player.sendMessage("  §a-> 掉落预测: 全部保留基因 (种子+食材)");
            } else if (isSeedBlock) {
                 player.sendMessage("  §a-> 掉落预测: 种子保留基因，产物仅星级");
            } else {
                 player.sendMessage("  §a-> 掉落预测: 复杂掉落 (视具体物品而定)");
            }
        } else {
            player.sendMessage("  §c该方块没有基因数据！");
        }
        player.sendMessage(Component.text("§8§m--------------------------------"));
    }

    private void inspectHand(Player player) {
        ItemStack hand = getTargetItem(player);
        if (hand == null || hand.getType() == Material.AIR) {
            player.sendMessage("§cHold a seed/item to inspect (Offhand if using Debug Tool).");
            return;
        }

        // Check for Star-Only Item manually using PDC
        ItemMeta meta = hand.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        boolean isStarOnly = pdc.has(starRatingKey, PersistentDataType.INTEGER);
        
        GeneData data = geneticsManager.getGenesFromItem(hand);
        // Note: geneticsManager.getGenesFromItem might return empty genes for star-only items, 
        // or valid genes if we are not careful.
        // If it's star only, we expect NO genes or IGNORED genes.
        
        player.sendMessage(Component.text("§8§m--------------------------------"));
        player.sendMessage(Component.text("§b[Hand Inspection]"));
        player.sendMessage(Component.text("  §7Item: " + hand.getType()));
        player.sendMessage(Component.text("  §7Is Star-Only Food: " + (isStarOnly ? "§aYes" : "§7No")));
        
        if (geneticsManager.hasGeneData(hand)) {
             printGeneInfo(player, data);
        } else {
            if (isStarOnly) {
                 int stars = pdc.getOrDefault(starRatingKey, PersistentDataType.INTEGER, 0);
                 player.sendMessage(Component.text("  §e[Note] This item is a Star-Only food."));
                 player.sendMessage(Component.text("  §eIt has NO genes, but has a Quality Rating."));
                 player.sendMessage(Component.text("  §eRating: " + stars + " Stars"));
            } else {
                 player.sendMessage(Component.text("  §7No Gene Data found on this item."));
            }
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
        if (hand.getType() == Material.GOLDEN_HOE) {
             player.sendMessage("§cCannot randomize Golden Hoe!");
             return;
        }
        
        GeneData data = geneticsManager.getGenesFromItem(hand);
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
            Method getChunkDataMethod = FertilityManager.class.getDeclaredMethod("getChunkData", org.bukkit.Chunk.class);
            getChunkDataMethod.setAccessible(true);
            Object chunkDataObj = getChunkDataMethod.invoke(fertilityManager, block.getChunk());
            
            // Assuming ChunkFertilityData class name, might need adjustment if it's different
            // But based on previous code it seemed fine.
            // Using reflection to be safe if class is not visible
            Method setFertilizerData = chunkDataObj.getClass().getMethod("setFertilizerData", int.class, int.class, int.class, double.class, long.class);
            
            long now = System.currentTimeMillis();
            setFertilizerData.invoke(chunkDataObj, block.getX() & 15, block.getY(), block.getZ() & 15, amount, now);
            
            player.sendMessage("§a[Debug] Set Soil Concentration to " + amount);
        } catch (Exception e) {
            player.sendMessage("§c[Debug] Error setting concentration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void printGeneInfo(Player player, GeneData data) {
        player.sendMessage(Component.text("§b[基因详细信息]"));
        player.sendMessage(Component.text("  §7鉴定状态: " + (data.isIdentified() ? "§a已鉴定" : "§c未鉴定")));
        
        for (Trait trait : Trait.values()) {
            GenePair pair = data.getGenePair(trait);
            String display = pair.getDisplayString(trait); // e.g. "A4A3" or "A1a1"
            double val = pair.getPhenotypeValue();
            String adj = trait.getAdjective(val);
            if (adj == null) adj = "普通";
            
            player.sendMessage(Component.text(String.format("  §7%s: %s §7(%s) §8[%.1f]", 
                trait.getName(), display, adj, val)));
        }
    }

    private void giveSeed(Player player, Allele a1, Allele a2, String typeName) {
        ItemStack seed = new ItemStack(Material.POTATO);
        GeneData data = new GeneData();
        data.setIdentified(true);
        for (Trait trait : Trait.values()) {
            data.setGenePair(trait, new GenePair(a1, a2));
        }
        geneticsManager.saveGenesToItem(seed, data);
        player.getInventory().addItem(seed);
        player.sendMessage("§a[Debug] 已给予 " + typeName + " 种子 (全属性 " + a1.getCode(Trait.YIELD) + "/" + a2.getCode(Trait.YIELD) + ")");
    }

    private void giveChaosSeed(Player player) {
        ItemStack seed = new ItemStack(Material.CARROT);
        GeneData data = new GeneData();
        data.randomize(); 
        data.setIdentified(true);
        geneticsManager.saveGenesToItem(seed, data);
        player.getInventory().addItem(seed);
        player.sendMessage("§a[Debug] 已给予随机 (Chaos) 种子");
        printGeneInfo(player, data);
    }

    private void giveStarFood(Player player) {
        ItemStack food = new ItemStack(Material.BAKED_POTATO);
        ItemMeta meta = food.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        
        // Set Star Rating (4 Stars)
        pdc.set(starRatingKey, PersistentDataType.INTEGER, 4);
        
        // Ensure Name/Lore reflects this (simple version)
        meta.displayName(Component.text("§6★★★★ 烤马铃薯"));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("§7Quality: §6★★★★"));
        lore.add(Component.text("§8(Debug Generated)"));
        meta.lore(lore);
        
        food.setItemMeta(meta);
        player.getInventory().addItem(food);
        player.sendMessage("§a[Debug] 已给予纯星级食物 (4星, 无基因)");
    }

    private static class SpiritCheckResult {
        boolean hasTown = false;
        String townName = "";
        boolean hasMayor = false;
        String mayorName = "";
        boolean hasSpirit = false;
        String spiritName = "";
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
            
            Object mayor = null;
            try {
                for (Method m : townyIntegrationClass.getMethods()) {
                    if (m.getName().equals("getMayor") && m.getParameterCount() == 1) {
                        mayor = m.invoke(null, town);
                        break;
                    }
                }
            } catch (Exception e) {
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
                    
                    Method getMood = spirit.getClass().getMethod("getMood");
                    result.mood = (double) getMood.invoke(spirit);
                    
                    Method isActive = spirit.getClass().getMethod("isActive");
                    result.isActive = (boolean) isActive.invoke(spirit);
                }
            }
        }
        return result;
    }
}