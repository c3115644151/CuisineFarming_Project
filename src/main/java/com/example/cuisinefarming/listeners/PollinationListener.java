package com.example.cuisinefarming.listeners;

import com.example.cuisinefarming.CuisineFarming;
import com.example.cuisinefarming.pollination.PollinationManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * 授粉监听器
 * 监听玩家与作物、纸张、花粉纸的交互事件。
 * 
 * 上游链路: Bukkit Event API
 * 下游链路: PollinationManager
 * 
 * 维护说明: 确保只响应右键方块事件，且正确区分采集和授粉操作。
 */
public class PollinationListener implements Listener {

    private final CuisineFarming plugin;
    private final PollinationManager pollinationManager;

    public PollinationListener(CuisineFarming plugin, PollinationManager manager) {
        this.plugin = plugin;
        this.pollinationManager = manager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        
        Block block = event.getClickedBlock();
        if (block == null || !(block.getBlockData() instanceof Ageable)) return;
        
        ItemStack item = event.getItem();
        if (item == null) return;
        
        // 检查纸张 -> 采集
        // 确保是普通纸张（没有 Meta 或没有显示名称和自定义模型数据）
        if (item.getType() == Material.PAPER) {
            boolean isPlain = !item.hasItemMeta() || (!item.getItemMeta().hasDisplayName() && !item.getItemMeta().hasCustomModelData());
            
            if (isPlain) {
                pollinationManager.collectPollen(event.getPlayer(), block, item);
                return;
            }
        }
        
        // 检查花粉纸 -> 授粉
        if (plugin.getItemManager().isCustomItem(item, "POLLEN_PAPER")) {
            event.setCancelled(true); // 防止放置或其他交互
            pollinationManager.pollinate(event.getPlayer(), block, item);
        }
    }
}
