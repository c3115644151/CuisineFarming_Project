package com.example.cuisinefarming.cooking;

import com.example.cuisinefarming.CuisineFarming;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * 烹饪交互监听器
 * 负责监听玩家对炼药锅的交互，并将其委托给对应的 CookingPot 实例。
 */
public class CookingPotListener implements Listener {

    private final CuisineFarming plugin;
    private final Map<Location, CookingPot> activePots = new HashMap<>();

    public CookingPotListener(CuisineFarming plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return; // 仅处理主手交互，防止双重触发
        
        Block block = event.getClickedBlock();
        if (block == null) return;
        
        // Debug
        // plugin.getLogger().info("Interact at " + block.getType());
        
        if (block.getType() != Material.CAULDRON && block.getType() != Material.WATER_CAULDRON) return;
        
        // 确保下方有热源
        Block below = block.getRelative(0, -1, 0);
        if (below.getType() != Material.CAMPFIRE && below.getType() != Material.SOUL_CAMPFIRE && below.getType() != Material.FIRE && below.getType() != Material.MAGMA_BLOCK) {
            // event.getPlayer().sendMessage("§c下方没有热源！");
            return;
        }
        
        event.setCancelled(true);
        Player player = event.getPlayer();
        Location loc = block.getLocation();
        ItemStack hand = event.getItem();
        
        // Debug
        // player.sendMessage("Interact Cauldron!");
        
        CookingPot pot = activePots.computeIfAbsent(loc, k -> new CookingPot(plugin, loc));
        
        boolean hasIngredients = pot.hasIngredients();
        boolean isCooking = pot.getState() == CookingPot.CookingState.COOKING;
        boolean isTool = isCookingTool(hand);

        // Condition A: Not Cooking AND No Ingredients -> Always Open GUI (Ignore Tool Interaction)
        if (!isCooking && !hasIngredients) {
            pot.openGUI(player);
            return;
        }

        // Condition B: (Not Cooking but Has Ingredients) OR (Cooking) -> Prioritize Tool
        if (isTool) {
            handleToolInteraction(pot, player, hand);
            return;
        }

        // Default: Open GUI
        // Feedback for GUI opening
        player.playSound(loc, Sound.BLOCK_CHEST_OPEN, 0.5f, 1.0f);
        pot.openGUI(player);
    }
    
    private boolean isCookingTool(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        
        // Check Custom Items
        if (plugin.getItemManager().isCustomItem(item, "HAND_FAN")) return true;
        if (plugin.getItemManager().isCustomItem(item, "WOODEN_LADLE")) return true;
        
        // Check Vanilla Items (Water)
        if (type == Material.WATER_BUCKET || type == Material.POTION) return true;
        
        // Check fallback for old items or debug (Feather/Paper/Bowl)
        return type == Material.FEATHER || type == Material.PAPER || type == Material.BOWL || type == Material.WATER_BUCKET || type == Material.POTION;
    }

    private void handleToolInteraction(CookingPot pot, Player player, ItemStack tool) {
        Material type = tool.getType();
        boolean isFan = type == Material.FEATHER || type == Material.PAPER || plugin.getItemManager().isCustomItem(tool, "HAND_FAN");
        boolean isLadle = type == Material.BOWL || plugin.getItemManager().isCustomItem(tool, "WOODEN_LADLE");
        boolean isWater = type == Material.WATER_BUCKET || type == Material.POTION;
        
        if (isFan) {
            pot.ignite();
            player.sendActionBar(Component.text("§c🔥 火力提升! 温度上升中..."));
        } else if (isLadle) {
            pot.stir(player);
            player.sendActionBar(Component.text("§e🥄 搅拌均匀! 防止糊锅..."));
        } else if (isWater) {
            double cooling = (type == Material.WATER_BUCKET) ? 50.0 : 20.0;
            pot.cool(cooling);
            
            if (type == Material.WATER_BUCKET) {
                // Empty bucket
                player.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));
            } else if (type == Material.POTION) {
                // Consume bottle
                player.getInventory().setItemInMainHand(new ItemStack(Material.GLASS_BOTTLE));
            }
            player.sendActionBar(Component.text("§b💧 加水降温! 温度降低 " + (int)cooling + "°C"));
            player.playSound(player.getLocation(), Sound.ITEM_BUCKET_EMPTY, 1.0f, 1.0f);
            player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.0f);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof CookingPotInventory) {
            ((CookingPotInventory) event.getInventory().getHolder()).handleClick(event);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof CookingPotInventory) {
            ((CookingPotInventory) event.getInventory().getHolder()).handleClose(event);
        }
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.CAULDRON) {
            Location loc = event.getBlock().getLocation();
            if (activePots.containsKey(loc)) {
                activePots.get(loc).destroy();
                activePots.remove(loc);
                event.getPlayer().sendMessage("§e[CookingPot] 烹饪锅已拆除。");
            }
        }
    }
}
