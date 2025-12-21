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

/**
 * Handles the serialization and transfer of GeneData between Items (NBT) and Blocks (Chunk PDC).
 * Since crops are not TileEntities, we store their gene data in the Chunk's PersistentDataContainer.
 */
public class GeneticsManager {

    // private final CuisineFarming plugin; // Unused field removed
    private final NamespacedKey IDENTIFIED_KEY;
    private final Map<GeneType, NamespacedKey> GENE_KEYS;
    private final NamespacedKey CHUNK_GENE_DATA_KEY; // Key for storing all crop genes in a chunk

    public GeneticsManager(CuisineFarming plugin) {
        // this.plugin = plugin; 
        this.IDENTIFIED_KEY = new NamespacedKey(plugin, "gene_identified");
        this.CHUNK_GENE_DATA_KEY = new NamespacedKey(plugin, "chunk_crop_genes");
        this.GENE_KEYS = new java.util.EnumMap<>(GeneType.class);
        
        for (GeneType type : GeneType.values()) {
            GENE_KEYS.put(type, new NamespacedKey(plugin, "gene_" + type.name().toLowerCase()));
        }
    }

    // ==========================================
    // Item Logic (Seed NBT)
    // ==========================================

    /**
     * Reads GeneData from an ItemStack.
     */
    public GeneData getGenesFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return new GeneData(); // Default
        }
        
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        
        if (!pdc.has(IDENTIFIED_KEY, PersistentDataType.BYTE)) {
             return new GeneData(); // Not a gene-seed yet
        }

        boolean identified = pdc.get(IDENTIFIED_KEY, PersistentDataType.BYTE) == 1;
        GeneData data = new GeneData();
        data.setIdentified(identified);
        
        for (GeneType type : GeneType.values()) {
            NamespacedKey key = GENE_KEYS.get(type);
            if (pdc.has(key, PersistentDataType.DOUBLE)) {
                data.setGene(type, pdc.get(key, PersistentDataType.DOUBLE));
            }
        }
        
        return data;
    }

    /**
     * Writes GeneData to an ItemStack.
     */
    public void saveGenesToItem(ItemStack item, GeneData data) {
        if (item == null || item.getType().isAir()) return;
        
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        
        // Fix 2: Prevent saving genes to Debug Tool
        NamespacedKey debugKey = new NamespacedKey(CuisineFarming.getPlugin(CuisineFarming.class), "debug_mode");
        if (pdc.has(debugKey, PersistentDataType.STRING)) {
            return;
        }

        // Save ID status
        pdc.set(IDENTIFIED_KEY, PersistentDataType.BYTE, (byte) (data.isIdentified() ? 1 : 0));
        
        // Optimize: If unidentified, DO NOT write gene values (save space/clean NBT)
        // If identified, write all genes.
        if (data.isIdentified()) {
            for (GeneType type : GeneType.values()) {
                pdc.set(GENE_KEYS.get(type), PersistentDataType.DOUBLE, data.getGene(type));
            }
        } else {
            // Remove gene keys if they exist (to ensure clean state)
            for (GeneType type : GeneType.values()) {
                pdc.remove(GENE_KEYS.get(type));
            }
        }
        
        // Update Lore
        updateItemLore(meta, data);
        
        item.setItemMeta(meta);
    }
    
    // ==========================================
    // Unified Display Logic (Added 2025-12-21)
    // ==========================================
    
    public List<Component> generateGeneLore(GeneData data) {
        List<Component> lore = new ArrayList<>();
        
        if (!data.isIdentified()) {
            lore.add(Component.text("§7❓ 未鉴定种子 (Unidentified)"));
            lore.add(Component.text("§8请使用种子分析机查看属性"));
            return lore;
        }
        
        int totalStars = data.calculateStarRating();
        lore.add(Component.text("§e🔬 基因图谱 " + getStarDisplay(totalStars)));
        
        // Growth Speed
        addGeneLine(lore, "生长速度", data, GeneType.GROWTH_SPEED, "%.0f%%", 100.0);
        
        // Yield
        addGeneLine(lore, "基础产量", data, GeneType.YIELD, "%.2fx", 1.0);
        
        // Optimal Temp (Special formatting)
        // Optimal temp itself is a preference, not a quality, but we can show it neutrally.
        // Tolerance IS a quality.
        double optTemp = data.getGene(GeneType.OPTIMAL_TEMP);
        double tol = data.getGene(GeneType.TEMP_TOLERANCE);
        int tolStars = data.getGeneStar(GeneType.TEMP_TOLERANCE);
        String color = getStarColor(tolStars);
        
        lore.add(Component.text(String.format("§7适宜温度: §f%.1f°C %s(±%.1f)", optTemp, color, tol)));
        
        // Fertility Resistance
        addGeneLine(lore, "耐肥上限", data, GeneType.FERTILITY_RESISTANCE, "%.0f", 1.0);
        
        return lore;
    }
    
    private void addGeneLine(List<Component> lore, String name, GeneData data, GeneType type, String format, double multiplier) {
        double val = data.getGene(type);
        int stars = data.getGeneStar(type);
        String color = getStarColor(stars);
        
        String valStr = String.format(format, val * multiplier);
        String starStr = getStarDisplay(stars);
        
        lore.add(Component.text(String.format("§7%s: %s%s §8%s", name, color, valStr, starStr)));
    }
    
    public String getStarColor(int stars) {
        switch (stars) {
            case 5: return "§6"; // Gold
            case 4: return "§d"; // Pink
            case 3: return "§b"; // Aqua
            case 2: return "§a"; // Green
            default: return "§7"; // Gray
        }
    }
    
    public String getStarDisplay(int stars) {
        StringBuilder sb = new StringBuilder();
        // sb.append(getStarColor(stars));
        for (int i = 0; i < 5; i++) {
            if (i < stars) sb.append("★");
            else sb.append("☆");
        }
        return sb.toString();
    }
    
    private void updateItemLore(ItemMeta meta, GeneData data) {
        List<Component> currentLore = meta.lore();
        List<Component> newLore = new ArrayList<>();
        
        // Preserve non-gene lore (if any)
        // Strategy: We replace the gene section if it exists, or append if not.
        // For simplicity in this project, we assume we control the lore.
        // But let's try to keep description lines if they exist (top lines usually).
        
        // Actually, let's just use the generated lore.
        newLore.addAll(generateGeneLore(data));
        
        meta.lore(newLore);
    }
    
    // ==========================================
    // Block Logic (Chunk PDC)
    // ==========================================

    /**
     * Saves GeneData to a specific block location in the chunk.
     */
    public void saveGenesToBlock(Block block, GeneData data) {
        Chunk chunk = block.getChunk();
        Map<String, GeneData> chunkGenes = loadChunkGenes(chunk);
        
        String key = getBlockKey(block);
        chunkGenes.put(key, data);
        
        saveChunkGenes(chunk, chunkGenes);
    }

    /**
     * Retrieves GeneData from a specific block location.
     * Returns null if no data exists.
     */
    public GeneData getGenesFromBlock(Block block) {
        Chunk chunk = block.getChunk();
        Map<String, GeneData> chunkGenes = loadChunkGenes(chunk);
        return chunkGenes.get(getBlockKey(block));
    }
    
    /**
     * Removes gene data for a block (e.g. on harvest).
     */
    public void removeGenesFromBlock(Block block) {
        Chunk chunk = block.getChunk();
        Map<String, GeneData> chunkGenes = loadChunkGenes(chunk);
        
        String key = getBlockKey(block);
        if (chunkGenes.remove(key) != null) {
            saveChunkGenes(chunk, chunkGenes);
        }
    }

    private String getBlockKey(Block block) {
        // Local coordinates within chunk (0-15, y, 0-15) are sufficient but absolute is safer for map keys
        // Let's use "x,y,z" relative to chunk or absolute?
        // Since it's stored IN the chunk, relative coords are better for storage size, but absolute is easier.
        // Let's use compact string: "x,y,z" (Absolute)
        return block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private Map<String, GeneData> loadChunkGenes(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        String rawData = pdc.get(CHUNK_GENE_DATA_KEY, PersistentDataType.STRING);
        
        Map<String, GeneData> map = new HashMap<>();
        if (rawData == null || rawData.isEmpty()) return map;
        
        // Format: "LocKey|ID:1,SPEED:1.0,YIELD:2.0;..."
        String[] entries = rawData.split(";");
        for (String entry : entries) {
            if (entry.isEmpty()) continue;
            String[] parts = entry.split("\\|");
            if (parts.length != 2) continue;
            
            String locKey = parts[0];
            String geneStr = parts[1];
            
            GeneData data = new GeneData();
            
            // Parse genes: "ID:1,SPEED:1.0,YIELD:2.0"
            String[] genePairs = geneStr.split(",");
            for (String pair : genePairs) {
                String[] kv = pair.split(":");
                if (kv.length == 2) {
                    try {
                        // Handle ID flag specially
                        if (kv[0].equals("ID")) {
                            data.setIdentified("1".equals(kv[1]));
                        } else {
                            GeneType type = GeneType.valueOf(kv[0]);
                            double val = Double.parseDouble(kv[1]);
                            data.setGene(type, val);
                        }
                    } catch (Exception ignored) {}
                }
            }
            map.put(locKey, data);
        }
        return map;
    }
    
    private void saveChunkGenes(Chunk chunk, Map<String, GeneData> map) {
        if (map.isEmpty()) {
            chunk.getPersistentDataContainer().remove(CHUNK_GENE_DATA_KEY);
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, GeneData> entry : map.entrySet()) {
            sb.append(entry.getKey()).append("|");
            
            GeneData data = entry.getValue();
            List<String> geneStrings = new ArrayList<>();
            
            // Save ID status first
            geneStrings.add("ID:" + (data.isIdentified() ? "1" : "0"));
            
            Map<GeneType, Double> genes = data.getAllGenes();
            for (Map.Entry<GeneType, Double> gene : genes.entrySet()) {
                geneStrings.add(gene.getKey().name() + ":" + gene.getValue());
            }
            sb.append(String.join(",", geneStrings));
            sb.append(";");
        }
        
        chunk.getPersistentDataContainer().set(CHUNK_GENE_DATA_KEY, PersistentDataType.STRING, sb.toString());
    }
}

