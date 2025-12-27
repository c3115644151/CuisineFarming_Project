package com.example.cuisinefarming.genetics;

import com.example.cuisinefarming.CuisineFarming;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 基因管理器 (Genetics Manager) - [重构版]
 * 负责基因数据在物品（NBT）和方块（区块PDC）之间的序列化与传输。
 * 核心逻辑已升级为基于 Trait 和 GenePair 的孟德尔系统。
 */
public class GeneticsManager {

    private final NamespacedKey IDENTIFIED_KEY;
    // 新版 Key: 使用 Trait 名称作为 Key
    private final Map<Trait, NamespacedKey> TRAIT_KEYS;
    
    private final NamespacedKey CHUNK_GENE_DATA_KEY; 
    private final NamespacedKey CHUNK_POLLEN_DATA_KEY;
    private final NamespacedKey STAR_RATING_KEY; // New key for Star-Only items

    public GeneticsManager(CuisineFarming plugin) {
        this.IDENTIFIED_KEY = new NamespacedKey(plugin, "gene_identified");
        this.CHUNK_GENE_DATA_KEY = new NamespacedKey(plugin, "chunk_crop_genes");
        this.CHUNK_POLLEN_DATA_KEY = new NamespacedKey(plugin, "chunk_crop_pollen");
        this.STAR_RATING_KEY = new NamespacedKey(plugin, "food_star_rating");
        this.TRAIT_KEYS = new java.util.EnumMap<>(Trait.class);
        
        for (Trait trait : Trait.values()) {
            // Key 示例: "trait_growth_speed"
            TRAIT_KEYS.put(trait, new NamespacedKey(plugin, "trait_" + trait.name().toLowerCase()));
        }
    }

    // ==========================================
    // 物品逻辑 (种子 NBT)
    // ==========================================

    /**
     * 从物品堆栈中读取基因数据。
     * 即使是未鉴定的种子，也会尝试读取其隐藏的基因数据。
     */
    public GeneData getGenesFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return new GeneData(); 
        }
        
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        
        // 检查是否已鉴定 (玩家可见性)
        boolean identified = pdc.has(IDENTIFIED_KEY, PersistentDataType.BYTE) && 
                             pdc.get(IDENTIFIED_KEY, PersistentDataType.BYTE) == 1;

        GeneData data = new GeneData();
        data.setIdentified(identified);
        
        // 尝试读取基因数据 (无论是否鉴定，数据都可能存在于 PDC 中)
        for (Trait trait : Trait.values()) {
            NamespacedKey key = TRAIT_KEYS.get(trait);
            if (pdc.has(key, PersistentDataType.STRING)) {
                String val = pdc.get(key, PersistentDataType.STRING);
                data.setGenePair(trait, parseGenePair(val));
            }
        }
        
        // 如果没有基因数据，但有星级数据 (烹饪食材被当作种子种下)
        // [Modified] 既然我们不再支持 "星级食材复种" (防止刷基因)，这里直接忽略星级数据，返回空基因(或默认基因)
        // 玩家如果种下纯星级食材(如小麦)，它将长成默认的野生作物。
        // 这符合逻辑：加工过的食材可能失去了遗传特性，或者只能长出最普通的。
        // 或者，我们可以完全禁止种植纯星级物品? 不，这太麻烦。
        // 暂时保持 "hasData" 检查。如果没有基因数据，它就是一个全新的默认基因。
        
        /*
        if (!hasData && pdc.has(STAR_RATING_KEY, PersistentDataType.INTEGER)) {
            int stars = pdc.get(STAR_RATING_KEY, PersistentDataType.INTEGER);
            return generateRandomGenesByStars(stars);
        }
        */
        
        // 如果没有数据 (全新的物品)，返回默认数据
        return data;
    }

    /**
     * 根据星级生成随机基因 (Deprecated - Removed to prevent exploit)
     */
     /*
    private GeneData generateRandomGenesByStars(int targetStars) {
        ...
    }
    */

    /**
     * 将基因数据写入物品堆栈。
     * 始终保存真实基因数据，但根据 identified 状态决定显示内容。
     */
    public void saveGenesToItem(ItemStack item, GeneData data) {
        if (item == null || item.getType().isAir()) return;
        
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        
        NamespacedKey debugKey = new NamespacedKey(CuisineFarming.getPlugin(CuisineFarming.class), "debug_mode");
        if (pdc.has(debugKey, PersistentDataType.STRING)) {
            return;
        }

        // 1. 保存鉴定状态
        pdc.set(IDENTIFIED_KEY, PersistentDataType.BYTE, (byte) (data.isIdentified() ? 1 : 0));
        
        // 2. 始终保存基因数据 (Hidden NBT)
        for (Trait trait : Trait.values()) {
            GenePair pair = data.getGenePair(trait);
            String val = pair.getFirst().getCode(trait) + ":" + pair.getSecond().getCode(trait);
            pdc.set(TRAIT_KEYS.get(trait), PersistentDataType.STRING, val);
        }
        
        // 3. 更新显示 (Name & Lore)
        updateItemDisplay(item, meta, data);
        item.setItemMeta(meta);
    }
    
    // ==========================================
    // 星级物品逻辑 (用于烹饪食材)
    // ==========================================

    /**
     * 将物品转换为仅含星级的食材 (移除基因数据，支持堆叠)
     */
    public void saveStarToItem(ItemStack item, int stars) {
        if (item == null || item.getType().isAir()) return;
        
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        
        // 1. 清除旧数据 (基因 & 鉴定状态)
        pdc.remove(IDENTIFIED_KEY);
        for (NamespacedKey key : TRAIT_KEYS.values()) {
            pdc.remove(key);
        }
        
        // 2. 写入星级数据
        pdc.set(STAR_RATING_KEY, PersistentDataType.INTEGER, stars);
        
        // 3. 更新显示 (Lore) - Align with PastureSong style
        List<Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();
        
        // 清除旧的星级Lore (如果存在)
        // 简单策略: 清除第一行如果它看起来像星级
        // 但这里我们假设是新生成的物品，或者完全覆盖
        // 为了安全，我们直接添加到头部，或者重建Lore
        
        // PastureSong style: Gold color, Solid/Hollow stars
        StringBuilder starStr = new StringBuilder("§6");
        for (int i = 0; i < 5; i++) {
            if (i < stars) starStr.append("★");
            else starStr.append("☆");
        }
        
        // Insert at top
        if (!lore.isEmpty() && lore.get(0).toString().contains("★")) {
             lore.set(0, Component.text(starStr.toString()));
        } else {
             lore.add(0, Component.text(starStr.toString()));
        }
        
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    /**
     * 检查物品是否是仅含星级的食材
     */
    public boolean isStarItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(STAR_RATING_KEY, PersistentDataType.INTEGER);
    }

    // 解析 "W:D" -> GenePair
    private GenePair parseGenePair(String str) {
        if (str == null || !str.contains(":")) return new GenePair(Allele.DOMINANT_1, Allele.RECESSIVE_1);
        String[] parts = str.split(":");
        Allele a1 = parseAllele(parts[0]);
        Allele a2 = parseAllele(parts[1]);
        return new GenePair(a1, a2);
    }
    
    private Allele parseAllele(String code) {
        // 兼容旧存档: W -> A1, D -> A2, S -> A3
        if ("W".equals(code)) return Allele.DOMINANT_1;
        if ("D".equals(code)) return Allele.DOMINANT_2;
        if ("S".equals(code)) return Allele.DOMINANT_3;
        
        if (code == null || code.length() < 2) return Allele.DOMINANT_1;

        // 解析 A1, b2 等格式
        char letter = code.charAt(0);
        boolean isDominant = Character.isUpperCase(letter);
        
        int level = 1;
        try {
            level = Integer.parseInt(code.substring(1));
        } catch (NumberFormatException e) {
            // 忽略格式错误，默认 1
        }
        
        if (isDominant) {
            if (level >= 4) return Allele.DOMINANT_4;
            if (level == 3) return Allele.DOMINANT_3;
            if (level == 2) return Allele.DOMINANT_2;
            return Allele.DOMINANT_1;
        } else {
            if (level >= 4) return Allele.RECESSIVE_4;
            if (level == 3) return Allele.RECESSIVE_3;
            if (level == 2) return Allele.RECESSIVE_2;
            return Allele.RECESSIVE_1;
        }
    }

    // ==========================================
    // 统一显示逻辑
    // ==========================================
    
    /**
     * 生成基因信息的 Lore 组件列表 (详细版)。
     */
    public List<Component> generateGeneLore(GeneData data) {
        List<Component> lore = new ArrayList<>();
        
        // 基因评级
        int totalStars = data.calculateStarRating();
        String colorCode = getQualityColorCode(totalStars);
        
        StringBuilder starBuilder = new StringBuilder(colorCode);
        for (int i = 0; i < 5; i++) {
            if (i < totalStars) starBuilder.append("★");
            else starBuilder.append("☆");
        }
        
        // [Modified] 统一显示：既显示星级(食材属性)也显示基因(种植属性)
        // 这里的 "品质" 兼容了食材星级显示，避免 lore 冲突
        // 使用与 saveStarToItem 一致的星级显示，但保留 "品质:" 前缀? 
        // 不，saveStarToItem 现在只显示星星。这里如果显示 "品质: ★★★☆☆" 也可以。
        // 但为了统一，最好也只显示星星? 或者 saveStarToItem 也应该有 "品质:"?
        // PastureSong (Meat) only shows stars. "★★★★★"
        // Let's stick to PastureSong style: Just the stars line.
        
        lore.add(Component.text(starBuilder.toString()));
        
        // 遍历所有性状显示
        for (Trait trait : Trait.values()) {
            addTraitLine(lore, trait, data);
        }
        
        return lore;
    }
    
    /**
     * 检查物品是否包含基因数据 (Traits)
     */
    public boolean hasGeneData(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        for (NamespacedKey key : TRAIT_KEYS.values()) {
            if (pdc.has(key, PersistentDataType.STRING)) return true;
        }
        return false;
    }

    /**
     * 生成模糊的基因信息 (未鉴定版)。
     * 仅显示已发现的显著性状 (Abs >= 3.0)。
     */
    public List<Component> generateVagueLore(GeneData data) {
        List<Component> lore = new ArrayList<>();
        
        // Removed "Unidentified" line as requested
        
        // 收集所有显著性状标签
        List<Component> tags = new ArrayList<>();
        for (Trait trait : Trait.values()) {
            double val = data.getGenePair(trait).getPhenotypeValue();
            String adj = trait.getAdjective(val);
            
            if (adj != null) {
                // 颜色判定
                String color = getTierColor(val);
                tags.add(Component.text(color + "[" + adj + "]"));
            }
        }
        
        if (!tags.isEmpty()) {
            lore.add(Component.text("§7性状特征:")); // Title
            Component tagLine = Component.text("  ");
            for (Component tag : tags) {
                tagLine = tagLine.append(tag).append(Component.text(" "));
            }
            lore.add(tagLine);
        } else {
            // lore.add(Component.text("  §7(平平无奇)")); // Can remove or keep? User said "Remove Unidentified block", implies simplifying.
            // If no tags, maybe just show nothing or "None"? 
            // Let's keep "Ordinary" for clarity if truly nothing is shown.
            lore.add(Component.text("§7性状特征:"));
            lore.add(Component.text("  §7(平平无奇)"));
        }
        
        lore.add(Component.text("§8(使用种子分析机查看完整图谱)"));
        return lore;
    }

    private void addTraitLine(List<Component> lore, Trait trait, GeneData data) {
        GenePair pair = data.getGenePair(trait);
        double val = pair.getPhenotypeValue();
        
        // 颜色判定 (基于数值强度)
        String color = getTierColor(val);
        if (Math.abs(val) < 3.0) color = "§7"; // Gray for ordinary in detailed view
        
        // 获取形容词
        String adj = trait.getAdjective(val);
        String desc = (adj != null) ? adj : "普通";
        
        lore.add(Component.text(String.format("  §f%s: %s%s §7(%s)", 
            trait.getName(), color, pair.getDisplayString(trait), desc)));
    }
    
    private String getQualityColorCode(int stars) {
        switch (stars) {
            case 1: return "§a"; // Green
            case 2: return "§b"; // Aqua
            case 3: return "§d"; // Light Purple
            case 4: return "§e"; // Yellow
            case 5: return "§6"; // Gold
            default: return "§f";
        }
    }
    
    private String getTierColor(double val) {
        double abs = Math.abs(val);
        if (val > 0) {
            if (abs >= 11.0) return "§d"; // T4: Light Purple
            if (abs >= 8.0) return "§6"; // T3: Gold
            if (abs >= 5.0) return "§b"; // T2: Aqua (Blue-ish)
            return "§a";                 // T1: Green
        } else {
            if (abs >= 11.0) return "§8"; // T4: Dark Gray
            if (abs >= 8.0) return "§5"; // T3: Dark Purple
            if (abs >= 5.0) return "§4"; // T2: Dark Red
            return "§c";                 // T1: Red
        }
    }
    
    private void updateItemDisplay(ItemStack item, ItemMeta meta, GeneData data) {
        // 1. Lore 更新
        List<Component> newLore = new ArrayList<>();
        if (data.isIdentified()) {
            newLore.addAll(generateGeneLore(data));
        } else {
            newLore.addAll(generateVagueLore(data));
        }
        meta.lore(newLore);
        
        // 2. Name 更新 (Adjective System)
        // 寻找最强的性状 (Max Abs Value)
        Trait dominantTrait = null;
        double maxAbsVal = 0.0;
        double realVal = 0.0;
        
        for (Trait trait : Trait.values()) {
            double val = data.getGenePair(trait).getPhenotypeValue();
            double abs = Math.abs(val);
            if (abs > maxAbsVal) {
                maxAbsVal = abs;
                realVal = val;
                dominantTrait = trait;
            }
        }
        
        String prefix = "平平无奇的";
        String colorCode = "§7"; // Gray
        
        if (dominantTrait != null && maxAbsVal >= 3.0) {
            String adj = dominantTrait.getAdjective(realVal);
            if (adj != null) {
                prefix = adj + "的";
                colorCode = getTierColor(realVal);
            }
        }
        
        // "速生的 小麦种子"
        Component prefixComp = Component.text(colorCode + prefix + " ");
        Component name = prefixComp.append(Component.translatable(item.getType().translationKey()));
        meta.displayName(name);
    }
    
    // ==========================================
    // 方块逻辑 (区块 PDC)
    // ==========================================

    public void saveGenesToBlock(Block block, GeneData data) {
        Chunk chunk = block.getChunk();
        Map<String, GeneData> chunkGenes = loadChunkData(chunk, CHUNK_GENE_DATA_KEY);
        chunkGenes.put(getBlockKey(block), data);
        saveChunkData(chunk, chunkGenes, CHUNK_GENE_DATA_KEY);
    }

    public GeneData getGenesFromBlock(Block block) {
        Chunk chunk = block.getChunk();
        Map<String, GeneData> chunkGenes = loadChunkData(chunk, CHUNK_GENE_DATA_KEY);
        return chunkGenes.get(getBlockKey(block));
    }
    
    public void removeGenesFromBlock(Block block) {
        Chunk chunk = block.getChunk();
        Map<String, GeneData> chunkGenes = loadChunkData(chunk, CHUNK_GENE_DATA_KEY);
        if (chunkGenes.remove(getBlockKey(block)) != null) {
            saveChunkData(chunk, chunkGenes, CHUNK_GENE_DATA_KEY);
        }
    }

    public void savePollenToBlock(Block block, GeneData data) {
        Chunk chunk = block.getChunk();
        Map<String, GeneData> chunkPollen = loadChunkData(chunk, CHUNK_POLLEN_DATA_KEY);
        chunkPollen.put(getBlockKey(block), data);
        saveChunkData(chunk, chunkPollen, CHUNK_POLLEN_DATA_KEY);
    }

    public GeneData getPollenFromBlock(Block block) {
        Chunk chunk = block.getChunk();
        Map<String, GeneData> chunkPollen = loadChunkData(chunk, CHUNK_POLLEN_DATA_KEY);
        return chunkPollen.get(getBlockKey(block));
    }

    public void removePollenFromBlock(Block block) {
        Chunk chunk = block.getChunk();
        Map<String, GeneData> chunkPollen = loadChunkData(chunk, CHUNK_POLLEN_DATA_KEY);
        if (chunkPollen.remove(getBlockKey(block)) != null) {
            saveChunkData(chunk, chunkPollen, CHUNK_POLLEN_DATA_KEY);
        }
    }

    private String getBlockKey(Block block) {
        return block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private Map<String, GeneData> loadChunkData(Chunk chunk, NamespacedKey key) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        String rawData = pdc.get(key, PersistentDataType.STRING);
        
        Map<String, GeneData> map = new HashMap<>();
        if (rawData == null || rawData.isEmpty()) return map;
        
        // 格式: "LocKey|ID:1,GROWTH_SPEED:W:D,YIELD:S:S;..."
        String[] entries = rawData.split(";");
        for (String entry : entries) {
            if (entry.isEmpty()) continue;
            String[] parts = entry.split("\\|");
            if (parts.length != 2) continue;
            
            String locKey = parts[0];
            String geneStr = parts[1];
            
            GeneData data = new GeneData();
            
            String[] genePairs = geneStr.split(",");
            for (String pair : genePairs) {
                String[] kv = pair.split("="); // 使用 = 分隔 Key 和 Value
                if (kv.length == 2) {
                    try {
                        if (kv[0].equals("ID")) {
                            data.setIdentified("1".equals(kv[1]));
                        } else {
                            Trait trait = Trait.valueOf(kv[0]);
                            data.setGenePair(trait, parseGenePair(kv[1]));
                        }
                    } catch (Exception ignored) {}
                }
            }
            map.put(locKey, data);
        }
        return map;
    }
    
    private void saveChunkData(Chunk chunk, Map<String, GeneData> map, NamespacedKey key) {
        if (map.isEmpty()) {
            chunk.getPersistentDataContainer().remove(key);
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, GeneData> entry : map.entrySet()) {
            sb.append(entry.getKey()).append("|");
            
            GeneData data = entry.getValue();
            List<String> geneStrings = new ArrayList<>();
            
            geneStrings.add("ID=" + (data.isIdentified() ? "1" : "0"));
            
            Map<Trait, GenePair> genes = data.getAllGenes();
            for (Map.Entry<Trait, GenePair> gene : genes.entrySet()) {
                GenePair pair = gene.getValue();
                String val = pair.getFirst().getCode(gene.getKey()) + ":" + pair.getSecond().getCode(gene.getKey());
                geneStrings.add(gene.getKey().name() + "=" + val);
            }
            sb.append(String.join(",", geneStrings));
            sb.append(";");
        }
        
        chunk.getPersistentDataContainer().set(key, PersistentDataType.STRING, sb.toString());
    }
    
    // ==========================================
    // 杂交逻辑 (孟德尔遗传)
    // ==========================================
    
    /**
     * 自交算法 (Self-Pollination)
     * 用于模拟单株作物产生种子时的基因重组。
     * 相当于父本和母本是同一个体。
     */
    public GeneData selfPollinate(GeneData parent, double mutationRate) {
        return hybridize(parent, parent, mutationRate);
    }

    public GeneData selfPollinate(GeneData parent) {
        return selfPollinate(parent, 0.05); // Default 5%
    }

    /**
     * 获取特产掉落倍率 (Yield Multiplier for Special Drops)
     * 基于 Trait B (结实量)
     * 规则:
     *   Score < 0: 1.0 (无加成)
     *   Score 0-10: 1.0 ~ 2.0 (线性增加)
     */
    public double getYieldMultiplier(GeneData data) {
        if (data == null) return 1.0;
        double score = data.getGenePair(Trait.YIELD).getPhenotypeValue();
        if (score <= 0) return 1.0;
        
        // Map 0 -> 10 to 1.0 -> 2.0
        // Formula: 1.0 + (score / 10.0)
        return 1.0 + (score / 10.0);
    }
    
    /**
     * 孟德尔杂交算法
     * 1. 父母各随机贡献一个等位基因 (配子)
     * 2. 组合成子代基因对
     * 3. 极低概率发生基因突变 (W -> D, D -> S)
     */
    public GeneData hybridize(GeneData mother, GeneData father) {
        return hybridize(mother, father, 0.05); // Default 5%
    }

    public GeneData hybridize(GeneData mother, GeneData father, double mutationRate) {
        GeneData offspring = new GeneData();
        offspring.setIdentified(false); // 杂交后的种子初始为未鉴定状态，保持探索感
        
        ThreadLocalRandom random = ThreadLocalRandom.current();
        
        for (Trait trait : Trait.values()) {
            GenePair motherPair = mother.getGenePair(trait);
            GenePair fatherPair = father.getGenePair(trait);
            
            // 1. 配子分离 (Segregation)
            Allele fromMother = random.nextBoolean() ? motherPair.getFirst() : motherPair.getSecond();
            Allele fromFather = random.nextBoolean() ? fatherPair.getFirst() : fatherPair.getSecond();
            
            // 2. 突变 (Mutation) - 动态概率
            if (random.nextDouble() < mutationRate) {
                fromMother = mutate(fromMother, random);
            }
            if (random.nextDouble() < mutationRate) {
                fromFather = mutate(fromFather, random);
            }
            
            offspring.setGenePair(trait, new GenePair(fromMother, fromFather));
        }
        
        return offspring;
    }
    
    private Allele mutate(Allele original, ThreadLocalRandom random) {
        // 进化/退化逻辑更新
        // 正向突变: a4 -> a3 -> a2 -> a1 -> A1 -> A2 -> A3 -> A4
        // 负向突变: A4 -> A3 -> A2 -> A1 -> a1 -> a2 -> a3 -> a4
        
        boolean positive = random.nextBoolean();
        int val = original.getValue();
        
        if (positive) {
            // 向上进化
            if (val == -4) return Allele.RECESSIVE_3;
            if (val == -3) return Allele.RECESSIVE_2;
            if (val == -2) return Allele.RECESSIVE_1;
            if (val == -1) return Allele.DOMINANT_1;
            if (val == 1) return Allele.DOMINANT_2;
            if (val == 2) return Allele.DOMINANT_3;
            if (val == 3) return Allele.DOMINANT_4;
        } else {
            // 向下退化
            if (val == 4) return Allele.DOMINANT_3;
            if (val == 3) return Allele.DOMINANT_2;
            if (val == 2) return Allele.DOMINANT_1;
            if (val == 1) return Allele.RECESSIVE_1;
            if (val == -1) return Allele.RECESSIVE_2;
            if (val == -2) return Allele.RECESSIVE_3;
            if (val == -3) return Allele.RECESSIVE_4;
        }
        return original;
    }
}


