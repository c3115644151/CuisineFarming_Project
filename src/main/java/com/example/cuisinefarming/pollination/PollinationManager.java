package com.example.cuisinefarming.pollination;

import com.example.cuisinefarming.CuisineFarming;
import com.example.cuisinefarming.genetics.GeneData;
import com.example.cuisinefarming.genetics.GeneticsManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 花粉管理器
 * 负责花粉采集和授粉的核心业务逻辑。
 * 
 * 上游链路: PollinationListener (玩家交互监听)
 * 下游链路: GeneticsManager (数据存取)
 * 
 * 维护说明: 确保花粉采集时检查基因鉴定状态，授粉时检查是否重复。
 */
public class PollinationManager {

    private final CuisineFarming plugin;
    private final GeneticsManager geneticsManager;

    public PollinationManager(CuisineFarming plugin) {
        this.plugin = plugin;
        this.geneticsManager = plugin.getGeneticsManager();
    }

    /**
     * 采集作物花粉。
     * @param player 玩家
     * @param cropBlock 作物方块
     * @param paperItem 纸物品（将被消耗）
     */
    public void collectPollen(Player player, Block cropBlock, ItemStack paperItem) {
        if (!(cropBlock.getBlockData() instanceof Ageable)) return;

        GeneData cropGenes = geneticsManager.getGenesFromBlock(cropBlock);
        if (cropGenes == null) {
            player.sendMessage(Component.text("§c该作物没有基因数据，无法采集花粉。"));
            return;
        }

        if (!cropGenes.isIdentified()) {
            player.sendMessage(Component.text("§c该作物基因未鉴定，无法采集花粉。请先使用分析仪鉴定种子。"));
            return;
        }

        // 消耗一张纸
        paperItem.subtract(1);

        // 给予花粉纸
        ItemStack pollenPaper = plugin.getItemManager().getItem("POLLEN_PAPER");
        geneticsManager.saveGenesToItem(pollenPaper, cropGenes);
        
        player.getInventory().addItem(pollenPaper);
        
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        player.sendMessage(Component.text("§a成功采集花粉！"));
    }

    /**
     * 对作物进行授粉。
     * @param player 玩家
     * @param cropBlock 作物方块
     * @param pollenItem 花粉纸物品（将被消耗）
     */
    public void pollinate(Player player, Block cropBlock, ItemStack pollenItem) {
        if (!(cropBlock.getBlockData() instanceof Ageable)) return;
        
        GeneData pollenGenes = geneticsManager.getGenesFromItem(pollenItem);
        if (pollenGenes == null || !pollenGenes.isIdentified()) {
            player.sendMessage(Component.text("§c花粉失效或未鉴定。"));
            return;
        }
        
        // 检查是否已授粉
        if (geneticsManager.getPollenFromBlock(cropBlock) != null) {
            player.sendMessage(Component.text("§c该作物已经授粉过了。"));
            return;
        }
        
        // 应用花粉
        geneticsManager.savePollenToBlock(cropBlock, pollenGenes);
        
        // 特效
        cropBlock.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, cropBlock.getLocation().add(0.5, 0.5, 0.5), 10);
        player.playSound(player.getLocation(), Sound.BLOCK_BEEHIVE_ENTER, 1.0f, 1.0f);
        player.sendMessage(Component.text("§d授粉成功！收割时将获得杂交种子。"));
        
        // 消耗花粉纸
        pollenItem.subtract(1);
    }
}
