package com.example.cuisinefarming.gui;

import com.example.cuisinefarming.CuisineFarming;
import com.example.cuisinefarming.genetics.GeneData;
import com.example.cuisinefarming.genetics.GeneticsManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class SeedAnalyzerGUI implements InventoryHolder, Listener {

    private final CuisineFarming plugin;
    private final Inventory inventory;
    private final Player player;
    
    // Slot Constants
    private static final int SLOT_INPUT = 10;
    private static final int SLOT_BUTTON = 13;
    private static final int SLOT_OUTPUT = 16;
    private static final int GUI_SIZE = 27;

    public SeedAnalyzerGUI(CuisineFarming plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, GUI_SIZE, Component.text("§8种子分析仪 (Seed Analyzer)"));
        initializeItems();
        
        // Register this instance as a listener temporarily
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
    
    public void open() {
        player.openInventory(inventory);
    }

    private void initializeItems() {
        // Fill background
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(Component.text(" "));
        glass.setItemMeta(meta);
        
        for (int i = 0; i < GUI_SIZE; i++) {
            if (i != SLOT_INPUT && i != SLOT_OUTPUT && i != SLOT_BUTTON) {
                inventory.setItem(i, glass);
            }
        }
        
        updateButton(ButtonState.IDLE);
    }
    
    private enum ButtonState {
        IDLE, READY, PROCESSING, DONE
    }
    
    private void updateButton(ButtonState state) {
        ItemStack button;
        switch (state) {
            case READY:
                button = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
                setMeta(button, "§a[点击分析] §7(Click to Analyze)", "§7消耗1个未鉴定种子");
                break;
            case PROCESSING:
                button = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
                setMeta(button, "§e[分析中...] §7(Analyzing)", "§7请稍候...");
                break;
            case DONE:
                button = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
                setMeta(button, "§6[分析完成] §7(Analysis Complete)", "§7请取出结果");
                break;
            case IDLE:
            default:
                button = new ItemStack(Material.RED_STAINED_GLASS_PANE);
                setMeta(button, "§c[等待输入] §7(Waiting for Seed)", "§7请在左侧放入未鉴定种子");
                break;
        }
        inventory.setItem(SLOT_BUTTON, button);
    }
    
    private void setMeta(ItemStack item, String name, String... lore) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(Component.text(s));
        meta.lore(l);
        item.setItemMeta(meta);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory() != inventory) return;
        
        int slot = event.getRawSlot();
        
        // Allow player inventory interactions
        if (slot >= GUI_SIZE) return;
        
        // Allow Input/Output interaction
        if (slot == SLOT_INPUT || slot == SLOT_OUTPUT) {
            // Check logic after click (delay 1 tick)
            new BukkitRunnable() {
                @Override
                public void run() {
                    checkState();
                }
            }.runTaskLater(plugin, 1);
            return;
        }
        
        // Cancel everything else
        event.setCancelled(true);
        
        // Handle Button Click
        if (slot == SLOT_BUTTON) {
            ItemStack input = inventory.getItem(SLOT_INPUT);
            if (isValidSeed(input)) {
                startAnalysis(input);
            } else {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            }
        }
    }
    
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory() != inventory) return;
        // Simple protection: if dragging into non-input slots, cancel
        for (int slot : event.getRawSlots()) {
            if (slot < GUI_SIZE && slot != SLOT_INPUT && slot != SLOT_OUTPUT) {
                event.setCancelled(true);
                return;
            }
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                checkState();
            }
        }.runTaskLater(plugin, 1);
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory() != inventory) return;
        
        // Return items
        returnItem(SLOT_INPUT);
        returnItem(SLOT_OUTPUT);
        
        // Unregister listener
        InventoryClickEvent.getHandlerList().unregister(this);
        InventoryCloseEvent.getHandlerList().unregister(this);
        InventoryDragEvent.getHandlerList().unregister(this);
    }
    
    private void returnItem(int slot) {
        ItemStack item = inventory.getItem(slot);
        if (item != null && item.getType() != Material.AIR) {
            player.getInventory().addItem(item).forEach((k, v) -> player.getWorld().dropItem(player.getLocation(), v));
        }
    }

    private void checkState() {
        ItemStack input = inventory.getItem(SLOT_INPUT);
        ItemStack output = inventory.getItem(SLOT_OUTPUT);
        
        if (isValidSeed(input)) {
            if (output == null || output.getType() == Material.AIR) {
                updateButton(ButtonState.READY);
            } else {
                updateButton(ButtonState.DONE); // Output full
            }
        } else {
            updateButton(ButtonState.IDLE);
        }
    }
    
    private boolean isValidSeed(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (!item.getType().toString().contains("SEEDS") && item.getType() != Material.POTATO && item.getType() != Material.CARROT) {
             // Basic check, better to use GeneticsManager.isCrop
             return false;
        }
        
        GeneticsManager geneticsManager = plugin.getGeneticsManager();
        GeneData genes = geneticsManager.getGenesFromItem(item);
        
        // Only accept unidentified seeds
        return genes == null || !genes.isIdentified();
    }
    
    private void startAnalysis(ItemStack input) {
        // Deduct 1 item
        ItemStack processItem = input.clone();
        processItem.setAmount(1);
        input.setAmount(input.getAmount() - 1);
        inventory.setItem(SLOT_INPUT, input);
        
        updateButton(ButtonState.PROCESSING);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 2.0f);
        
        // Animation Task
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick >= 20) { // 1 second
                    completeAnalysis(processItem);
                    this.cancel();
                    return;
                }
                
                // Visual effect
                if (tick % 5 == 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.5f + (tick/40f));
                }
                tick += 2;
            }
        }.runTaskTimer(plugin, 0, 2);
    }
    
    private void completeAnalysis(ItemStack seedItem) {
        GeneticsManager geneticsManager = plugin.getGeneticsManager();
        
        // 1. Get existing or create new genes
        GeneData genes = geneticsManager.getGenesFromItem(seedItem);
        if (genes == null) genes = new GeneData();
        
        // 2. Randomize ONLY if no existing gene data found (e.g. wild/store-bought seeds)
        // If the seed was harvested from a crop, it should already have gene data in PDC.
        if (!geneticsManager.hasGeneData(seedItem)) {
            genes.randomize();
        }
        
        genes.setIdentified(true);
        
        // 3. Save to item
        ItemStack result = seedItem; // Save in place
        geneticsManager.saveGenesToItem(result, genes);
        
        inventory.setItem(SLOT_OUTPUT, result);
        updateButton(ButtonState.IDLE);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.0f);
        
        int totalStars = genes.calculateStarRating();
        StringBuilder starBuilder = new StringBuilder();
        for (int i = 0; i < totalStars; i++) starBuilder.append("⭐");
        starBuilder.append("§8");
        for (int i = totalStars; i < 5; i++) starBuilder.append("⭐");
        
        player.sendMessage(Component.text("§a[Cuisine] §f种子鉴定成功！基因评级: §e" + starBuilder.toString() + " §7(" + totalStars + "星)"));
        
        checkState(); // Re-check button state
    }
    
    // Legacy helper removed in favor of GeneticsManager

}
