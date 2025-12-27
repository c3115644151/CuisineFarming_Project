package com.example.cuisinefarming.gui;

import com.example.cuisinefarming.CuisineFarming;
import com.example.cuisinefarming.genetics.GeneData;
import com.example.cuisinefarming.genetics.GeneticsManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class GeneticAnalyzerGUI implements InventoryHolder, Listener {

    private final CuisineFarming plugin;
    private final Inventory inventory;
    private final Player player;
    
    // Slot Constants
    private static final int SLOT_INPUT = 10;
    private static final int SLOT_BUTTON = 13;
    private static final int SLOT_OUTPUT = 16;
    private static final int GUI_SIZE = 27;

    public GeneticAnalyzerGUI(CuisineFarming plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, GUI_SIZE, Component.text("§8遗传分析仪 (Genetic Analyzer)"));
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
                setMeta(button, "§a[点击分析] §7(Click to Analyze)", "§7消耗1个未鉴定样本");
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
                
                ItemStack input = inventory.getItem(SLOT_INPUT);
                if (input != null && input.getType() != Material.AIR) {
                    // Debug info
                    String reason = "Unknown";
                    if (!isValidInput(input)) {
                         if (input.getType() == Material.FLINT) {
                             Plugin ps = Bukkit.getPluginManager().getPlugin("PastureSong");
                             if (ps == null) reason = "No PastureSong";
                             else {
                                 NamespacedKey key = new NamespacedKey(ps, "gene_identified");
                                 ItemMeta m = input.getItemMeta();
                                 if (m == null) reason = "No Meta";
                                 else if (!m.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) reason = "No NBT";
                                 else {
                                     Byte v = m.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
                                     reason = "ID Val: " + v;
                                 }
                             }
                         } else {
                             reason = "Not Seed/Sample";
                         }
                    }
                    setMeta(button, "§c[无效输入] §7(Invalid Input)", "§7原因: " + reason, "§7请放入未鉴定种子或DNA样本");
                } else {
                    setMeta(button, "§c[等待输入] §7(Waiting for Input)", "§7请在左侧放入未鉴定种子或DNA样本");
                }
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
            if (isValidInput(input)) {
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
        
        if (isValidInput(input)) {
            if (output == null || output.getType() == Material.AIR) {
                updateButton(ButtonState.READY);
            } else {
                updateButton(ButtonState.DONE); // Output full
            }
        } else {
            updateButton(ButtonState.IDLE);
        }
    }
    
    private boolean isValidInput(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        
        // Check 1: CuisineFarming Seeds
        boolean isSeed = item.getType().toString().contains("SEEDS") || item.getType() == Material.POTATO || item.getType() == Material.CARROT;
        if (isSeed) {
            GeneticsManager geneticsManager = plugin.getGeneticsManager();
            GeneData genes = geneticsManager.getGenesFromItem(item);
            // Only accept unidentified seeds
            return genes == null || !genes.isIdentified();
        }
        
        // Check 2: PastureSong DNA Sample
        // Accept both Paper (Legacy/Docs) and Flint (Tool acting as container)
        // We rely on NBT data presence
        if (isPastureSongSample(item)) {
             return isPastureSongUnidentified(item);
        }
        
        return false;
    }
    
    private boolean isPastureSongSample(ItemStack item) {
        // Removed Material.PAPER check to allow Flint (DNA Sampler) or other containers
        Plugin pastureSong = Bukkit.getPluginManager().getPlugin("PastureSong");
        if (pastureSong == null) return false;
        
        // Check for gene_identified key (Standard for PastureSong gene carriers)
        NamespacedKey key = new NamespacedKey(pastureSong, "gene_identified");
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private boolean isPastureSongUnidentified(ItemStack item) {
        Plugin pastureSong = Bukkit.getPluginManager().getPlugin("PastureSong");
        if (pastureSong == null) return false;
        NamespacedKey key = new NamespacedKey(pastureSong, "gene_identified");
        if (item.getItemMeta() == null) return false;
        Byte val = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        // In PastureSong, 0 means unidentified, 1 means identified.
        return val != null && val == 0;
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
    
    private void completeAnalysis(ItemStack item) {
        if (isPastureSongSample(item)) {
            analyzePastureSongItem(item);
        } else {
            analyzeCuisineFarmingItem(item);
        }
    }
    
    private void analyzePastureSongItem(ItemStack item) {
        try {
            Plugin psPlugin = Bukkit.getPluginManager().getPlugin("PastureSong");
            if (psPlugin == null) return;
            
            // Get GeneticsManager via reflection from PastureSong main class
            // Assuming PastureSong has public getGeneticsManager()
            Method getGeneticsManagerMethod = psPlugin.getClass().getMethod("getGeneticsManager");
            Object geneticsManager = getGeneticsManagerMethod.invoke(psPlugin);
            
            // Get getGenesFromItem method
            Method getGenesMethod = geneticsManager.getClass().getMethod("getGenesFromItem", ItemStack.class);
            Object geneData = getGenesMethod.invoke(geneticsManager, item);
            
            // Set identified = true
            Method setIdentifiedMethod = geneData.getClass().getMethod("setIdentified", boolean.class);
            setIdentifiedMethod.invoke(geneData, true);
            
            // Save genes back
            Method saveGenesMethod = geneticsManager.getClass().getMethod("saveGenesToItem", ItemStack.class, geneData.getClass());
            saveGenesMethod.invoke(geneticsManager, item, geneData);
            
            // Update Lore
            Method updateLoreMethod = geneticsManager.getClass().getMethod("updateGeneLore", ItemStack.class, geneData.getClass());
            updateLoreMethod.invoke(geneticsManager, item, geneData);
            
            inventory.setItem(SLOT_OUTPUT, item);
            updateButton(ButtonState.IDLE);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.0f);
            player.sendMessage(Component.text("§a[Genetics] §fDNA 样本鉴定成功！"));
            
            checkState();
            
        } catch (Exception e) {
            e.printStackTrace();
            player.sendMessage(Component.text("§c[Error] 无法分析该样本，请联系管理员。"));
        }
    }
    
    private void analyzeCuisineFarmingItem(ItemStack seedItem) {
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
}
