package org.lassilos.rewards;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;
import org.bukkit.enchantments.Enchantment;

import java.util.ArrayList;
import java.util.List;

final class ShopManager implements Listener {
    private final JavaPlugin plugin;
    private final org.lassilos.rewards.PointManager points;
    private final NamespacedKey customItemKey;

    ShopManager(JavaPlugin plugin, org.lassilos.rewards.PointManager points, NamespacedKey customItemKey) {
        this.plugin = plugin;
        this.points = points;
        this.customItemKey = customItemKey;
    }

    void openMain(Player player) {
        Inventory inventory = Bukkit.createInventory(null, inventorySize("shop.ui.main.size", 27),
                title("shop.ui.main.title", "Rewards Shop"));
        ItemStack filler = filler();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        inventory.setItem(configuredSlot("shop.ui.main.books-slot", 11),
                linkedIcon(Material.ENCHANTED_BOOK, colorName("shop.ui.main.books-name", "Enchanted Books")));
        inventory.setItem(configuredSlot("shop.ui.main.player-slot", 4), playerIcon(player));
        inventory.setItem(configuredSlot("shop.ui.main.equipment-slot", 15),
                linkedIcon(Material.NETHERITE_CHESTPLATE,
                        colorName("shop.ui.main.equipment-name", "Custom Equipment")));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onShopClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!isShopTitle(title)) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (title.equals(title("shop.ui.main.title", "Rewards Shop"))) {
            if (event.getRawSlot() == configuredSlot("shop.ui.main.books-slot", 11)) {
                openCategory(player, false);
            } else if (event.getRawSlot() == configuredSlot("shop.ui.main.equipment-slot", 15)) {
                openCategory(player, true);
            }
            return;
        }
        if (event.getRawSlot() == configuredSlot("shop.ui.category.back-slot", 26)) {
            openMain(player);
            return;
        }
        String path = findItemPathBySlot(event.getRawSlot(), title.equals(title("shop.ui.equipment.title", "Custom Equipment")));
        if (path != null) {
            purchase(player, path);
        }
    }

    private void openCategory(Player player, boolean equipment) {
        String section = equipment ? "shop.ui.equipment" : "shop.ui.books";
        Inventory inventory = Bukkit.createInventory(null, inventorySize(section + ".size", 27),
                title(section + ".title", equipment ? "Custom Equipment" : "Enchanted Books"));
        ItemStack filler = filler();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        List<String> paths = categoryPaths(equipment);
        int startSlot = categoryStartSlot(equipment, paths.size());
        for (int index = 0; index < paths.size(); index++) {
            int slot = startSlot + index;
            if (slot < inventory.getSize()) {
                inventory.setItem(slot, shopIcon(paths.get(index)));
            }
        }
        inventory.setItem(configuredSlot("shop.ui.category.back-slot", 26),
                namedItem(Material.ARROW, ChatColor.YELLOW + "Back"));
        player.openInventory(inventory);
    }

    private String findItemPathBySlot(int slot, boolean equipment) {
        List<String> paths = categoryPaths(equipment);
        int index = slot - categoryStartSlot(equipment, paths.size());
        if (index >= 0 && index < paths.size()) {
            return paths.get(index);
        }
        return null;
    }

    private int categoryStartSlot(boolean equipment, int itemCount) {
        String section = equipment ? "shop.ui.equipment" : "shop.ui.books";
        int size = inventorySize(section + ".size", 27);
        int rowStart = size / 2 - 4;
        return rowStart + Math.max(0, (9 - Math.min(itemCount, 9)) / 2);
    }

    private List<String> categoryPaths(boolean equipment) {
        List<String> paths = new ArrayList<>();
        for (String key : plugin.getConfig().getConfigurationSection("shop.items").getKeys(false)) {
            String path = "shop.items." + key;
            Material icon = Material.matchMaterial(plugin.getConfig().getString(path + ".icon", "DIAMOND"));
            if ((icon != Material.ENCHANTED_BOOK) == equipment) {
                paths.add(path);
            }
        }
        if (equipment) {
            paths.sort(java.util.Comparator.comparingInt(this::equipmentOrder));
        }
        return paths;
    }

    private int equipmentOrder(String path) {
        Material material = Material.matchMaterial(plugin.getConfig().getString(path + ".icon", "DIAMOND"));
        if (material == null) {
            return Integer.MAX_VALUE;
        }
        String name = material.name();
        if (name.endsWith("_HELMET")) {
            return 0;
        }
        if (name.endsWith("_CHESTPLATE")) {
            return 1;
        }
        if (name.endsWith("_LEGGINGS")) {
            return 2;
        }
        if (name.endsWith("_BOOTS")) {
            return 3;
        }
        return 4;
    }

    private ItemStack playerIcon(Player player) {
        ItemStack icon = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) icon.getItemMeta();
        meta.setOwningPlayer(player);
        meta.setDisplayName(ChatColor.GOLD + player.getName());
        meta.setLore(List.of(ChatColor.GRAY + "Your points: " + ChatColor.GOLD + points.get(player)));
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack linkedIcon(Material material, String name) {
        ItemStack icon = namedItem(material, name);
        ItemMeta meta = icon.getItemMeta();
        meta.setLore(List.of(ChatColor.GRAY + "Click to open the shop"));
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack shopIcon(String path) {
        Material material = Material.matchMaterial(plugin.getConfig().getString(path + ".icon", "DIAMOND"));
        ItemStack icon = new ItemStack(material == null ? Material.DIAMOND : material);
        ItemMeta meta = icon.getItemMeta();
        meta.getPersistentDataContainer().set(customItemKey, PersistentDataType.STRING, path);
        meta.setDisplayName(ChatColor.GREEN + plugin.getConfig().getString(path + ".name", "Reward"));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Cost: " + ChatColor.GOLD + plugin.getConfig().getInt(path + ".points") + " points");
        for (String requirement : plugin.getConfig().getStringList(path + ".requirements")) {
            lore.add(ChatColor.GRAY + requirement);
        }
        for (String potion : plugin.getConfig().getStringList(path + ".required-potions")) {
            String[] parts = potion.split(":");
            int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            lore.add(ChatColor.GRAY + "Required: " + amount + "x " + potionName(parts[0]));
        }

        lore.add(ChatColor.YELLOW + "Click to purchase");
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private boolean isShopTitle(String title) {
        return title.equals(title("shop.ui.main.title", "Rewards Shop"))
                || title.equals(title("shop.ui.books.title", "Enchanted Books"))
                || title.equals(title("shop.ui.equipment.title", "Custom Equipment"));
    }

    private ItemStack filler() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("shop.ui.filler.material",
                "GRAY_STAINED_GLASS_PANE"));
        return namedItem(material == null ? Material.GRAY_STAINED_GLASS_PANE : material,
                plugin.getConfig().getString("shop.ui.filler.name", " "));
    }

    private String title(String path, String fallback) {
        return ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString(path, fallback));
    }

    private String colorName(String path, String fallback) {
        return ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString(path, fallback));
    }

    private int configuredSlot(String path, int fallback) {
        return plugin.getConfig().getInt(path, fallback);
    }

    private int inventorySize(String path, int fallback) {
        int size = plugin.getConfig().getInt(path, fallback);
        return Math.max(9, Math.min(54, size - size % 9));
    }

    private void purchase(Player player, String path) {
        String itemName = plugin.getConfig().getString(path + ".name", path);
        int cost = plugin.getConfig().getInt(path + ".points");
        int balance = points.get(player);
        if (balance < cost) {
            player.sendMessage(ChatColor.RED + "You do not have enough points. ("
                    + balance + "/" + cost + ")");
            logFailure(player, itemName, "not enough points (" + balance + "/" + cost + ")");
            return;
        }
        for (String entry : plugin.getConfig().getStringList(path + ".required-potions")) {
            String[] parts = entry.split(":");
            int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            PotionType type = PotionType.valueOf(parts[0]);
            if (!hasPotions(player, type, amount)) {
                player.sendMessage(ChatColor.RED + "Missing: " + amount + "x " + potionName(parts[0]) + ".");
                logFailure(player, itemName, "required potion missing: " + entry);
                return;
            }
        }
        for (String entry : plugin.getConfig().getStringList(path + ".required-items")) {
            String[] parts = entry.split(":");
            int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            if (Material.matchMaterial(parts[0]) == null || !hasItems(player, parts, amount)) {
                player.sendMessage(ChatColor.RED + "Missing: " + amount + "x " + parts[0] + ".");
                logFailure(player, itemName, "required item missing: " + entry);
                return;
            }
        }
        for (String entry : plugin.getConfig().getStringList(path + ".required-items")) {
            String[] parts = entry.split(":");
            removeItems(player, parts, Integer.parseInt(parts[1]));
        }
        for (String entry : plugin.getConfig().getStringList(path + ".required-potions")) {
            String[] parts = entry.split(":");
            removePotions(player, PotionType.valueOf(parts[0]), parts.length > 1 ? Integer.parseInt(parts[1]) : 1);
        }
        points.add(player, -cost);
        ItemStack reward = new ItemStack(Material.valueOf(plugin.getConfig().getString(path + ".reward.material", "DIAMOND")),
                plugin.getConfig().getInt(path + ".reward.amount", 1));
        ItemMeta meta = reward.getItemMeta();
        String customId = plugin.getConfig().getString(path + ".reward.custom-id");
        if (customId != null) {
            meta.getPersistentDataContainer().set(customItemKey, PersistentDataType.STRING, customId);
        }
        if (plugin.getConfig().getBoolean(path + ".reward.glider", false)) {
            meta.setGlider(true);
        }
        List<String> rewardLore = plugin.getConfig().getStringList(path + ".reward.lore");
        if (!rewardLore.isEmpty()) {
            meta.setLore(rewardLore);
        }
        if (plugin.getConfig().contains(path + ".reward.name")) {
            meta.setDisplayName(ChatColor.RESET + plugin.getConfig().getString(path + ".reward.name"));
        }
        for (String enchantment : plugin.getConfig().getStringList(path + ".reward.enchants")) {
            String[] parts = enchantment.split(":");
            Enchantment type = Enchantment.getByName(normalizeEnchantmentName(parts[0]));
            int level = Integer.parseInt(parts[1]);
            if (meta instanceof EnchantmentStorageMeta storedMeta) {
                storedMeta.addStoredEnchant(type, level, true);
            } else {
                meta.addEnchant(type, level, true);
            }
        }
        reward.setItemMeta(meta);
        player.getInventory().addItem(reward);
        player.sendMessage(ChatColor.GREEN + "Purchase successful!");
        plugin.getLogger().info(player.getName() + " purchased " + itemName + " for " + cost + " points.");
    }

    private boolean hasPotions(Player player, PotionType type, int amount) {
        int found = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == Material.POTION
                    && item.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta potion
                    && potion.getBasePotionType() == type) {
                found += item.getAmount();
            }
        }
        return found >= amount;
    }

    private void removePotions(Player player, PotionType type, int amount) {
        for (int slot = 0; slot < player.getInventory().getStorageContents().length && amount > 0; slot++) {
            ItemStack item = player.getInventory().getStorageContents()[slot];
            if (item == null || item.getType() != Material.POTION
                    || !(item.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta potion)
                    || potion.getBasePotionType() != type) {
                continue;
            }
            int removed = Math.min(amount, item.getAmount());
            item.setAmount(item.getAmount() - removed);
            player.getInventory().setItem(slot, item.getAmount() == 0 ? null : item);
            amount -= removed;
        }
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
        Enchantment enchantment = Enchantment.getByName(normalizeEnchantmentName(requirement[2]));
        if (enchantment == null) {
            return false;
        }
        int requiredLevel = Integer.parseInt(requirement[3]);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storedMeta) {
            return storedMeta.hasStoredEnchant(enchantment)
                    && storedMeta.getStoredEnchantLevel(enchantment) == requiredLevel;
        }
        return item.containsEnchantment(enchantment) && item.getEnchantmentLevel(enchantment) == requiredLevel;
    }

    private void logFailure(Player player, String itemName, String reason) {
        plugin.getLogger().info(player.getName() + " could not purchase " + itemName + ": " + reason);
    }

    private String potionName(String typeName) {
        return switch (typeName.toUpperCase(java.util.Locale.ROOT)) {
            case "NIGHT_VISION" -> "Potion of Night Vision";
            case "LEAPING" -> "Potion of Leaping";
            case "SWIFTNESS" -> "Potion of Swiftness";
            default -> "Potion of " + formatName(typeName);
        };
    }

    private String formatName(String name) {
        String[] words = name.toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder formatted = new StringBuilder();
        for (String word : words) {
            if (formatted.length() > 0) {
                formatted.append(' ');
            }
            formatted.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return formatted.toString();
    }

    private String normalizeEnchantmentName(String name) {
        return switch (name.toUpperCase(java.util.Locale.ROOT)) {
            case "DURABILITY" -> "UNBREAKING";
            case "DAMAGE_ALL" -> "SHARPNESS";
            default -> name.toUpperCase(java.util.Locale.ROOT);
        };
    }
}
