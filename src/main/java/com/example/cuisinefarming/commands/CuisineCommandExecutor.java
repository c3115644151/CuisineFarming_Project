package com.example.cuisinefarming.commands;

import com.example.cuisinefarming.CuisineFarming;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class CuisineCommandExecutor implements CommandExecutor {

    private final CuisineFarming plugin;

    public CuisineCommandExecutor(CuisineFarming plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // 确保发送者是玩家
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("§c只有玩家可以使用此命令。"));
            return true;
        }

        // 检查权限
        if (!player.hasPermission("cuisinefarming.get")) {
            player.sendMessage(Component.text("§c你没有权限使用此命令。"));
            return true;
        }

        String itemKey = null;
        String successMessage = "";

        // 根据命令标签判断需要获取的物品
        switch (label.toLowerCase()) {
            case "getorganic":
                itemKey = "ORGANIC_FERTILIZER";
                successMessage = "§a成功获取 有机肥料！";
                break;
            case "getchemical":
                itemKey = "CHEMICAL_FERTILIZER";
                successMessage = "§a成功获取 高效化肥！";
                break;
            case "getmonocle":
                itemKey = "FARMER_MONOCLE";
                successMessage = "§a成功获取 农夫单片镜！";
                break;
            case "getanalyzer":
                itemKey = "SEED_ANALYZER";
                successMessage = "§a成功获取 种子分析仪！";
                break;
            case "getfan":
                itemKey = "HAND_FAN";
                successMessage = "§a成功获取 蒲扇！";
                break;
            case "getladle":
                itemKey = "WOODEN_LADLE";
                successMessage = "§a成功获取 木汤勺！";
                break;
            case "getpot":
                itemKey = "COOKING_POT";
                successMessage = "§a成功获取 厨锅！";
                break;
            default:
                return false;
        }

        // 给予物品
        // 由于 default 分支返回 false，此处 itemKey 一定不为 null
        ItemStack item = plugin.getItemManager().getItem(itemKey);
        if (item != null) {
            player.getInventory().addItem(item);
            player.sendMessage(Component.text(successMessage));
        } else {
            player.sendMessage(Component.text("§c获取失败：物品 " + itemKey + " 未注册或初始化错误。"));
        }
        return true;
    }
}
