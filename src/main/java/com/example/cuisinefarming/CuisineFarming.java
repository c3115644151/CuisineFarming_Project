package com.example.cuisinefarming;

import com.example.cuisinefarming.fertility.FertilityManager;
import com.example.cuisinefarming.listeners.FarmingListener;
import com.example.cuisinefarming.commands.CuisineCommandExecutor;
import org.bukkit.plugin.java.JavaPlugin;

public class CuisineFarming extends JavaPlugin {

    private static CuisineFarming instance;
    private FertilityManager fertilityManager;
    private CuisineItemManager itemManager;

    @Override
    public void onEnable() {
        instance = this;
        
        // Initialize Managers
        this.itemManager = new CuisineItemManager(this);
        this.fertilityManager = new FertilityManager(this);
        
        // Register Listeners
        new FarmingListener(this);
        
        // Register Commands
        CuisineCommandExecutor commandExecutor = new CuisineCommandExecutor(this);
        if (getCommand("getorganic") != null) getCommand("getorganic").setExecutor(commandExecutor);
        if (getCommand("getchemical") != null) getCommand("getchemical").setExecutor(commandExecutor);
        if (getCommand("getmonocle") != null) getCommand("getmonocle").setExecutor(commandExecutor);
        
        // Debug Command
        if (getCommand("getdebugtool") != null) getCommand("getdebugtool").setExecutor(new com.example.cuisinefarming.commands.DebugCommandExecutor(this));

        // Register Debug Listener
        new com.example.cuisinefarming.listeners.DebugListener(this);

        // Start Tasks
        new com.example.cuisinefarming.tasks.MonocleTask(this).runTaskTimer(this, 20L, 10L);
        new com.example.cuisinefarming.tasks.GrowthTask(this).runTaskTimer(this, 20L, 20L); // 1 second
        
        getLogger().info("CuisineFarming has been enabled!");
    }

    @Override
    public void onDisable() {
        if (fertilityManager != null) {
            fertilityManager.saveAll();
        }
        getLogger().info("CuisineFarming has been disabled!");
    }

    public static CuisineFarming getInstance() {
        return instance;
    }

    public FertilityManager getFertilityManager() {
        return fertilityManager;
    }
    
    public CuisineItemManager getItemManager() {
        return itemManager;
    }
}
