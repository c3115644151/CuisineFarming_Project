package com.example.cuisinefarming.listeners;

import com.example.cuisinefarming.CuisineFarming;
import com.example.cuisinefarming.gui.SeedAnalyzerGUI;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class SeedAnalyzerListener implements Listener {

    private final CuisineFarming plugin;
    private final NamespacedKey analyzerKey;

    public SeedAnalyzerListener(CuisineFarming plugin) {
        this.plugin = plugin;
        this.analyzerKey = new NamespacedKey(plugin, "is_seed_analyzer");
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (plugin.getItemManager().isCustomItem(item, "SEED_ANALYZER")) {
            Block block = event.getBlockPlaced();
            // Mark the block as an analyzer using PDC (if it supports TileState)
            if (block.getState() instanceof TileState tileState) {
                PersistentDataContainer pdc = tileState.getPersistentDataContainer();
                pdc.set(analyzerKey, PersistentDataType.BYTE, (byte) 1);
                tileState.update();
                
                event.getPlayer().sendMessage(Component.text("§a[Cuisine] §f种子分析仪已就绪！右键即可使用。"));
            }
        }
    }

    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        if (isAnalyzer(block)) {
            event.setCancelled(true);
            new SeedAnalyzerGUI(plugin, event.getPlayer()).open();
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (isAnalyzer(block)) {
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(block.getLocation(), plugin.getItemManager().getItem("SEED_ANALYZER"));
        }
    }

    private boolean isAnalyzer(Block block) {
        if (block.getType() != Material.IRON_BLOCK) return false;
        if (block.getState() instanceof TileState tileState) {
            PersistentDataContainer pdc = tileState.getPersistentDataContainer();
            return pdc.has(analyzerKey, PersistentDataType.BYTE);
        }
        return false;
    }
}
