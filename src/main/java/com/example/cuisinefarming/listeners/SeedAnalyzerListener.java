package com.example.cuisinefarming.listeners;

import com.example.cuisinefarming.CuisineFarming;
import com.example.cuisinefarming.gui.GeneticAnalyzerGUI;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class SeedAnalyzerListener implements Listener {

    private final CuisineFarming plugin;
    private final NamespacedKey chunkKey;

    public SeedAnalyzerListener(CuisineFarming plugin) {
        this.plugin = plugin;
        this.chunkKey = new NamespacedKey(plugin, "chunk_seed_analyzers");
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (plugin.getItemManager().isCustomItem(item, "SEED_ANALYZER")) {
            Block block = event.getBlockPlaced();
            addAnalyzer(block);
            event.getPlayer().sendMessage(Component.text("§a[Cuisine] §f遗传分析仪已就绪！右键即可使用。"));
        }
    }

    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        // Ensure we only check Iron Blocks to be safe
        if (block.getType() == Material.IRON_BLOCK && isAnalyzer(block)) {
            event.setCancelled(true);
            new GeneticAnalyzerGUI(plugin, event.getPlayer()).open();
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.IRON_BLOCK && isAnalyzer(block)) {
            removeAnalyzer(block);
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(block.getLocation(), plugin.getItemManager().getItem("SEED_ANALYZER"));
        }
    }

    private boolean isAnalyzer(Block block) {
        int code = encodeLocation(block);
        int[] codes = getAnalyzerCodes(block.getChunk());
        if (codes == null) return false;

        for (int c : codes) {
            if (c == code) return true;
        }
        return false;
    }

    private void addAnalyzer(Block block) {
        Chunk chunk = block.getChunk();
        int code = encodeLocation(block);
        int[] current = getAnalyzerCodes(chunk);

        int[] next;
        if (current == null) {
            next = new int[]{code};
        } else {
            // Check duplicate
            for (int c : current) {
                if (c == code) return;
            }
            next = new int[current.length + 1];
            System.arraycopy(current, 0, next, 0, current.length);
            next[current.length] = code;
        }

        chunk.getPersistentDataContainer().set(chunkKey, PersistentDataType.INTEGER_ARRAY, next);
    }

    private void removeAnalyzer(Block block) {
        Chunk chunk = block.getChunk();
        int code = encodeLocation(block);
        int[] current = getAnalyzerCodes(chunk);

        if (current == null) return;

        List<Integer> list = new ArrayList<>();
        boolean found = false;
        for (int c : current) {
            if (c == code) {
                found = true;
                continue;
            }
            list.add(c);
        }

        if (found) {
            if (list.isEmpty()) {
                chunk.getPersistentDataContainer().remove(chunkKey);
            } else {
                int[] next = list.stream().mapToInt(i -> i).toArray();
                chunk.getPersistentDataContainer().set(chunkKey, PersistentDataType.INTEGER_ARRAY, next);
            }
        }
    }

    private int[] getAnalyzerCodes(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        return pdc.get(chunkKey, PersistentDataType.INTEGER_ARRAY);
    }

    private int encodeLocation(Block block) {
        // Format: x (4) | z (4) | y (24)
        // x, z are 0-15
        int x = block.getX() & 0xF;
        int z = block.getZ() & 0xF;
        int y = block.getY(); 
        
        // y can be negative, so we don't use simple shift if we want to be safe with sign?
        // Actually, simple bit packing works if we handle it correctly.
        // But y is int. 
        // Let's use: x | (z << 4) | ((y + 1024) << 8) to ensure positive.
        // Minecraft Y is usually -64 to 320.
        // +1024 shifts it to positive range safe for logic.
        
        return x | (z << 4) | ((y + 1024) << 8);
    }
}
