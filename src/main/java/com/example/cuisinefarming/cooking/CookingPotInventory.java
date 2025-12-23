package com.example.cuisinefarming.cooking;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class CookingPotInventory implements InventoryHolder {

    private final CookingPot pot;
    private final Inventory inventory;

    public CookingPotInventory(CookingPot pot) {
        this.pot = pot;
        this.inventory = Bukkit.createInventory(this, 54, Component.text("烹饪锅 (Cooking Pot)"));
        initializeItems();
    }

    private void initializeItems() {
        // Filler items for background
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        var meta = filler.getItemMeta();
        meta.displayName(Component.text(" "));
        filler.setItemMeta(meta);

        // Fill entire inventory first
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        // Clear Ingredient Slots
        for (int slot : CookingPot.INGREDIENT_SLOTS) {
            inventory.setItem(slot, null); // Empty for items
        }

        // Clear Output Slot
        inventory.setItem(CookingPot.OUTPUT_SLOT, null);

        // Clear Fuel Slot
        inventory.setItem(CookingPot.FUEL_SLOT, null);
        
        // Optional: Decoration (e.g. Arrow between input and output)
        // Arrow Slots: 22, 23, 24
        ItemStack arrow = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
        var arrowMeta = arrow.getItemMeta();
        arrowMeta.displayName(Component.text("§e➡"));
        arrow.setItemMeta(arrowMeta);
        
        inventory.setItem(22, arrow);
        inventory.setItem(23, arrow);
    }

    public void updateIngredientsFromView() {
        // Sync inventory back to pot ingredients
        // 1. Read items first (before clearing!)
        List<ItemStack> itemsToSave = new ArrayList<>();
        for (int slot : CookingPot.INGREDIENT_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                itemsToSave.add(item);
            }
        }
        
        // 2. Clear pot logic
        pot.clearIngredients();
        
        // 3. Add back
        for (ItemStack item : itemsToSave) {
            pot.addIngredientDirectly(item);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public CookingPot getPot() {
        return pot;
    }
    
    // Event handlers called by listener
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        
        // Allow interaction with Top Inventory (Pot) only in specific slots
        if (slot < event.getView().getTopInventory().getSize()) {
            // Check if slot is an allowed slot
            boolean isIngredient = false;
            for (int s : CookingPot.INGREDIENT_SLOTS) {
                if (s == slot) {
                    isIngredient = true;
                    break;
                }
            }
            
            boolean isFuel = (slot == CookingPot.FUEL_SLOT);
            boolean isOutput = (slot == CookingPot.OUTPUT_SLOT);
            
            if (isIngredient || isFuel || isOutput) {
                // Allowed
                // If pot is COOKING, ingredients should be locked?
                if (pot.getState() == CookingPot.CookingState.COOKING && isIngredient) {
                    event.setCancelled(true);
                }
                // Output usually allowed to take
            } else {
                // Filler slots
                event.setCancelled(true);
            }
        }
        // Allow interaction with Bottom Inventory (Player)
    }
    
    public void handleClose(InventoryCloseEvent event) {
        updateIngredientsFromView();
        pot.updateVisuals(); // Refresh visuals on pot
    }
}
