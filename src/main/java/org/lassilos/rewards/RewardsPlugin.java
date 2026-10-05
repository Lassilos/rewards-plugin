package org.lassilos.rewards;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import io.papermc.paper.advancement.AdvancementDisplay;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;

public final class RewardsPlugin extends JavaPlugin {
    private PointManager points;
    private NamespacedKey customItemKey;
    private ShopManager shop;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        points = new PointManager(this);
        customItemKey = new NamespacedKey(this, "custom-reward");
        shop = new ShopManager(this, points, customItemKey);
        getServer().getPluginManager().registerEvents(shop, this);
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                applyCustomEquipmentEffects(player);
            }
        }, 1L, 20L);
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onAdvancement(PlayerAdvancementDoneEvent event) {
                var key = event.getAdvancement().getKey();
                String path = key.getNamespace() + "/" + key.getKey();
                int reward = getAdvancementReward(event.getAdvancement().getDisplay(), path);
                String claimPath = "claimed-advancements." + event.getPlayer().getUniqueId() + "." + path.replace('/', '.');
                if (reward > 0 && !getConfig().getBoolean(claimPath, false)) {
                    getConfig().set(claimPath, true);
                    saveConfig();
                    points.add(event.getPlayer(), reward);
                    event.getPlayer().sendMessage(ChatColor.GREEN + "Challenge completed! +" + reward + " points");
                }
            }

            @EventHandler
            public void onPrepareEnchant(PrepareItemEnchantEvent event) {
                EnchantmentOffer[] offers = event.getOffers();
                for (int slot = 0; slot < offers.length; slot++) {
                    EnchantmentOffer offer = offers[slot];
                    if (offer == null) {
                        continue;
                    }
                    int upgradedLevel = upgradedTableLevel(offer.getEnchantment(), offer.getEnchantmentLevel());
                    if (upgradedLevel > offer.getEnchantmentLevel()) {
                        offer.setEnchantmentLevel(upgradedLevel);
                    }
                }
            }

            @EventHandler
            public void onPrepareAnvil(PrepareAnvilEvent event) {
                ItemStack first = event.getInventory().getFirstItem();
                ItemStack second = event.getInventory().getSecondItem();
                ItemStack result = event.getResult();
                if (first == null || second == null || result == null) {
                    return;
                }

                ItemMeta resultMeta = result.getItemMeta();
                if (resultMeta == null) {
                    return;
                }
                boolean changed = false;
                for (Enchantment enchantment : supportedHigherEnchantments()) {
                    int resultLevel = getItemEnchantmentLevel(result, enchantment);
                    if (resultLevel != enchantment.getMaxLevel()
                            || getItemEnchantmentLevel(first, enchantment) <= 0
                            && getItemEnchantmentLevel(second, enchantment) <= 0) {
                        continue;
                    }
                    setItemEnchantmentLevel(resultMeta, enchantment, resultLevel + 1);
                    changed = true;
                }
                if (changed) {
                    result.setItemMeta(resultMeta);
                    event.setResult(result);
                }
            }

        }, this);
    }

    private int upgradedTableLevel(Enchantment enchantment, int generatedLevel) {
        if (enchantment.getMaxLevel() > 1 && generatedLevel == enchantment.getMaxLevel()) {
            return generatedLevel + 1;
        }
        return generatedLevel;
    }

    private List<Enchantment> supportedHigherEnchantments() {
        return java.util.Arrays.stream(Enchantment.values())
                .filter(enchantment -> enchantment.getMaxLevel() > 1)
                .toList();
    }

    private int getItemEnchantmentLevel(ItemStack item, Enchantment enchantment) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storedMeta) {
            return storedMeta.getStoredEnchantLevel(enchantment);
        }
        return item.getEnchantmentLevel(enchantment);
    }

    private void setItemEnchantmentLevel(ItemMeta meta, Enchantment enchantment, int level) {
        if (meta instanceof EnchantmentStorageMeta storedMeta) {
            storedMeta.addStoredEnchant(enchantment, level, true);
        } else {
            meta.addEnchant(enchantment, level, true);
        }
    }

    private int getAdvancementReward(AdvancementDisplay display, String path) {
        int defaultExceptionReward = switch (path) {
            case "end/kill_dragon" -> 500;
            case "nether/summon_wither" -> 300;
            default -> -1;
        };
        int exceptionReward = getConfig().getInt("advancement-rewards.exceptions." + path, defaultExceptionReward);
        if (exceptionReward >= 0) {
            return exceptionReward;
        }
        if (display == null) {
            return 0;
        }
        String frame = display.frame().name().toLowerCase(Locale.ROOT);
        int defaultReward = switch (frame) {
            case "task" -> 10;
            case "goal" -> 25;
            case "challenge" -> 100;
            default -> 0;
        };
        return getConfig().getInt("advancement-rewards." + frame, defaultReward);
    }

    @Override
    public void onDisable() {
        if (points != null) {
            points.save();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "points" -> handlePoints(sender, args);
            case "points-shop" -> handlePointsShop(sender);
            case "points-add" -> handleGivePoints(sender, args);
            case "points-set" -> handlePointsSet(sender, args);
            default -> false;
        };
    }

    private boolean handlePoints(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("balance")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Only players can view their point balance.");
                return true;
            }
            sender.sendMessage(ChatColor.GOLD + "Points: " + ChatColor.WHITE + points.get(player));
            return true;
        }
        if (args.length == 1) {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }
            sender.sendMessage(ChatColor.GOLD + target.getName() + "'s points: "
                    + ChatColor.WHITE + points.get(target));
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "Usage: /points [player]");
        return true;
    }

    private boolean handlePointsShop(CommandSender sender) {
        if (sender instanceof Player player) {
            shop.openMain(player);
        } else {
            sender.sendMessage(ChatColor.RED + "Only players can open the shop.");
        }
        return true;
    }

    private boolean handleGivePoints(CommandSender sender, String[] args) {
        if (!sender.hasPermission("advancement-rewards.givepoints")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to do that.");
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /points-add <player> <amount>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }
        try {
            int amount = Integer.parseInt(args[1]);
            if (amount <= 0) {
                throw new NumberFormatException();
            }
            points.add(target, amount);
            sender.sendMessage(ChatColor.GREEN + target.getName() + " received " + amount + " points.");
            target.sendMessage(ChatColor.GREEN + "You received " + amount + " points.");
        } catch (NumberFormatException exception) {
            sender.sendMessage(ChatColor.RED + "The amount must be a positive integer.");
        }
        return true;
    }

    private boolean handlePointsSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("advancement-rewards.givepoints")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to do that.");
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /points-set <player> <amount>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }
        try {
            int amount = Integer.parseInt(args[1]);
            points.set(target, amount);
            sender.sendMessage(ChatColor.GREEN + target.getName() + " now has " + amount + " points.");
            target.sendMessage(ChatColor.GREEN + "Your point balance was set to " + amount + ".");
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(ChatColor.RED + "The amount must be a non-negative integer.");
        }
        return true;
    }

    private void applyCustomEquipmentEffects(Player player) {
        applyConfiguredEffect(player, player.getInventory().getHelmet(), "night-vision-helmet");
        applyConfiguredEffect(player, player.getInventory().getBoots(), "jump-boots");
        applyConfiguredEffect(player, player.getInventory().getLeggings(), "speed-leggings");
    }

    private void applyConfiguredEffect(Player player, ItemStack item, String itemId) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        String customId = item.getItemMeta().getPersistentDataContainer()
                .get(customItemKey, PersistentDataType.STRING);
        if (customId == null && item.getItemMeta().hasDisplayName()) {
            customId = getConfig().getString("shop.items." + itemId + ".reward.name");
            if (!item.getItemMeta().getDisplayName().equals(ChatColor.RESET + customId)) {
                customId = null;
            }
        }
        if (!itemId.equals(customId)) {
            return;
        }
        String effectName = getConfig().getString("shop.items." + itemId + ".reward.effect.type");
        PotionEffectType type = PotionEffectType.getByName(effectName);
        if (type == null) {
            return;
        }
        int amplifier = getConfig().getInt("shop.items." + itemId + ".reward.effect.amplifier", 0);
        player.addPotionEffect(new PotionEffect(type, 200, amplifier, true, false, false));
    }

    private boolean hasItems(Player player, String[] requirement, int amount) {
        int found = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (matchesRequirement(item, requirement)) {
                found += item.getAmount();
            }
        }
        return found >= amount;
    }

    private void removeItems(Player player, String[] requirement, int amount) {
        for (int slot = 0; slot < player.getInventory().getStorageContents().length && amount > 0; slot++) {
            ItemStack item = player.getInventory().getStorageContents()[slot];
            if (!matchesRequirement(item, requirement)) {
                continue;
            }
            int removed = Math.min(amount, item.getAmount());
            item.setAmount(item.getAmount() - removed);
            player.getInventory().setItem(slot, item.getAmount() == 0 ? null : item);
            amount -= removed;
        }
    }

    private boolean matchesRequirement(ItemStack item, String[] requirement) {
        if (item == null || item.getType() != Material.matchMaterial(requirement[0])) {
            return false;
        }
        if (requirement.length < 4) {
            return true;
        }
        org.bukkit.enchantments.Enchantment enchantment =
                org.bukkit.enchantments.Enchantment.getByName(normalizeEnchantmentName(requirement[2]));
        if (enchantment == null) {
            return false;
        }
        int requiredLevel = Integer.parseInt(requirement[3]);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storedMeta) {
            return storedMeta.hasStoredEnchant(enchantment)
                    && storedMeta.getStoredEnchantLevel(enchantment) == requiredLevel;
        }
        return item.containsEnchantment(enchantment)
                && item.getEnchantmentLevel(enchantment) == requiredLevel;
    }

    private String normalizeEnchantmentName(String name) {
        return switch (name.toUpperCase(Locale.ROOT)) {
            case "DURABILITY" -> "UNBREAKING";
            case "DAMAGE_ALL" -> "SHARPNESS";
            default -> name.toUpperCase(Locale.ROOT);
        };
    }
}
