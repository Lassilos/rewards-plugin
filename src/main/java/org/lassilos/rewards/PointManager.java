package org.lassilos.rewards;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

final class PointManager {
    private final JavaPlugin plugin;

    PointManager(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getConfig().addDefault("balances", null);
        plugin.getConfig().options().copyDefaults(true);
    }

    int get(Player player) {
        return plugin.getConfig().getInt("balances." + player.getUniqueId(), 0);
    }

    void add(Player player, int amount) {
        int current = get(player);
        if (amount > 0 && current > Integer.MAX_VALUE - amount) {
            throw new IllegalArgumentException("Point balance overflow");
        }
        plugin.getConfig().set("balances." + player.getUniqueId(), current + amount);
        save();
    }

    void set(Player player, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Point balance cannot be negative");
        }
        plugin.getConfig().set("balances." + player.getUniqueId(), amount);
        save();
    }

    void save() {
        plugin.saveConfig();
    }
}
