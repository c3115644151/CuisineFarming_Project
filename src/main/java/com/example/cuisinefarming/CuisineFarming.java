package com.example.cuisinefarming;

import com.example.cuisinefarming.fertility.FertilityManager;
import com.example.cuisinefarming.listeners.FarmingListener;
import com.example.cuisinefarming.listeners.SeedAnalyzerListener;
import com.example.cuisinefarming.commands.CuisineCommandExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import com.example.cuisinefarming.genetics.GeneticsManager;

public class CuisineFarming extends JavaPlugin {

    private static CuisineFarming instance;
    private FertilityManager fertilityManager;
    private CuisineItemManager itemManager;
    private GeneticsManager geneticsManager;
    private com.example.cuisinefarming.pollination.PollinationManager pollinationManager;
    private com.example.cuisinefarming.cooking.CookingManager cookingManager;
    private FarmingListener farmingListener;
    // private MonocleTask monocleTask;

    @Override
    public void onEnable() {
        instance = this;
        
        // Initialize Managers
        this.fertilityManager = new FertilityManager(this);
        this.itemManager = new CuisineItemManager(this);
        this.geneticsManager = new GeneticsManager(this); // Initialize here
        this.pollinationManager = new com.example.cuisinefarming.pollination.PollinationManager(this);
        this.cookingManager = new com.example.cuisinefarming.cooking.CookingManager(this);

        // Register Listeners
        this.farmingListener = new FarmingListener(this);
        new com.example.cuisinefarming.listeners.PollinationListener(this, pollinationManager);
        new com.example.cuisinefarming.cooking.CookingPotListener(this); // Register CookingPotListener
        new com.example.cuisinefarming.listeners.FoodConsumeListener(this); // Register FoodConsumeListener
        getServer().getPluginManager().registerEvents(new SeedAnalyzerListener(this), this);
        
        // Register Commands
        CuisineCommandExecutor commandExecutor = new CuisineCommandExecutor(this);
        if (getCommand("getorganic") != null) getCommand("getorganic").setExecutor(commandExecutor);
        if (getCommand("getchemical") != null) getCommand("getchemical").setExecutor(commandExecutor);
        if (getCommand("getmonocle") != null) getCommand("getmonocle").setExecutor(commandExecutor);
        if (getCommand("getanalyzer") != null) getCommand("getanalyzer").setExecutor(commandExecutor);
        if (getCommand("getfan") != null) getCommand("getfan").setExecutor(commandExecutor);
        if (getCommand("getladle") != null) getCommand("getladle").setExecutor(commandExecutor);
        if (getCommand("getpot") != null) getCommand("getpot").setExecutor(commandExecutor);
        
        // Debug Command
        if (getCommand("getdebugtool") != null) getCommand("getdebugtool").setExecutor(new com.example.cuisinefarming.commands.DebugCommandExecutor(this));

        // Register Debug Listener
        new com.example.cuisinefarming.listeners.DebugListener(this);

        // Start Tasks
        new com.example.cuisinefarming.tasks.MonocleTask(this).runTaskTimer(this, 20L, 10L);
        new com.example.cuisinefarming.tasks.GrowthTask(this).runTaskTimer(this, 20L, 20L); // 1 second
        
        // Load Cooking Pots
        if (cookingManager != null) {
            cookingManager.loadPots();
        }

        getLogger().info("CuisineFarming has been enabled!");
    }

    @Override
    public void onDisable() {
        if (fertilityManager != null) {
            fertilityManager.saveAll();
        }
        if (cookingManager != null) {
            cookingManager.savePots();
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

    public GeneticsManager getGeneticsManager() {
        return geneticsManager;
    }

    public FarmingListener getFarmingListener() {
        return farmingListener;
    }

    public com.example.cuisinefarming.cooking.CookingManager getCookingManager() {
        return cookingManager;
    }
}
