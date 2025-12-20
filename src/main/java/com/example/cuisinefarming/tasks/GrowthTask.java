package com.example.cuisinefarming.tasks;

import com.example.cuisinefarming.CuisineFarming;
import org.bukkit.scheduler.BukkitRunnable;

public class GrowthTask extends BukkitRunnable {

    private final CuisineFarming plugin;

    public GrowthTask(CuisineFarming plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (plugin.getFertilityManager() != null) {
            plugin.getFertilityManager().performGrowthTick();
        }
    }
}
