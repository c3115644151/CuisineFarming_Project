package com.example.cuisinefarming.commands;

import com.example.cuisinefarming.CuisineFarming;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collections;

public class DebugCommandExecutor implements CommandExecutor {

    private final NamespacedKey debugKey;

    public DebugCommandExecutor(CuisineFarming plugin) {
        this.debugKey = new NamespacedKey(plugin, "debug_tool");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("cuisinefarming.debug")) {
            player.sendMessage(Component.text("§cYou do not have permission to use this command."));
            return true;
        }

        ItemStack debugStick = new ItemStack(Material.GOLDEN_HOE);
        ItemMeta meta = debugStick.getItemMeta();
        meta.displayName(Component.text("§b§l[Server Debug Tool]"));
        meta.lore(Collections.singletonList(Component.text("§7Right-click blocks to inspect detailed data.")));
        meta.getPersistentDataContainer().set(debugKey, PersistentDataType.BYTE, (byte) 1);
        debugStick.setItemMeta(meta);

        player.getInventory().addItem(debugStick);
        player.sendMessage(Component.text("§a已获取服务器调试工具！右键方块查看信息。"));

        return true;
    }
}
