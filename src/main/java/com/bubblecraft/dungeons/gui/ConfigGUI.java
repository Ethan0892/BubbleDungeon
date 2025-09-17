package com.bubblecraft.dungeons.gui;

import com.bubblecraft.dungeons.BubbleDungeonsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ConfigGUI implements Listener {

    private final BubbleDungeonsPlugin plugin;
    private final Inventory inv;

    public ConfigGUI(BubbleDungeonsPlugin plugin) {
        this.plugin = plugin;
        this.inv = Bukkit.createInventory(null, 54, plugin.color("&6&lBubble&bCraft&7 » &fConfig Settings"));
        build();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void build() {
        // World Creation Settings
        addToggleItem(10, "auto-create-worlds", "Auto Create Worlds", 
            "Automatically create missing dungeon worlds", "world-creation");
        addToggleItem(11, "prefer-multiverse", "Prefer Multiverse", 
            "Use Multiverse-Core when available", "world-creation");
        addToggleItem(12, "disable-natural-spawning", "Disable Natural Spawning", 
            "Prevent natural mob/animal spawning", "world-creation");
        addToggleItem(13, "set-time-noon", "Set Time to Noon", 
            "Keep dungeon worlds at noon", "world-creation");
        addToggleItem(14, "disable-weather", "Disable Weather", 
            "Prevent rain/storms in dungeons", "world-creation");
        addToggleItem(15, "disable-pvp", "Disable PvP", 
            "Prevent player vs player combat", "world-creation");

        // Block Protection Settings
        addToggleItem(19, "enabled", "Block Protection", 
            "Prevent block breaking/placing", "block-protection");
        addToggleItem(20, "admin-override", "Admin Override", 
            "Allow admins to bypass protection", "block-protection");

        // Feature Settings
        addToggleItem(28, "boss-bar", "Boss Bar HUD", 
            "Show progress bar during dungeons", "features");
        addToggleItem(29, "economy-rewards", "Economy Rewards", 
            "Give money rewards on completion", "features");
        addToggleItem(30, "loot-rewards", "Loot Rewards", 
            "Give item rewards on completion", "features");
        addToggleItem(31, "wave-system", "Wave System", 
            "Enable two-wave mob spawning", "features");

        // Navigation
        addNavigationItem(45, Material.ARROW, "&e← Back to Admin Menu", "back");
        addNavigationItem(49, Material.BOOK, "&aReload Config", "reload");
        addNavigationItem(53, Material.BARRIER, "&cClose", "close");
    }

    private void addToggleItem(int slot, String configKey, String name, String description, String section) {
        boolean enabled = plugin.getConfig().getBoolean(section + "." + configKey, true);
        Material material = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
        String status = enabled ? "&aEnabled" : "&cDisabled";
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.color("&f" + name + " " + status));
            List<String> lore = new ArrayList<>();
            lore.add(plugin.color("&7" + description));
            lore.add("");
            lore.add(plugin.color("&eClick to toggle"));
            lore.add(plugin.color("&7Current: " + status));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        inv.setItem(slot, item);
    }

    private void addNavigationItem(int slot, Material material, String name, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.color(name));
            List<String> lore = new ArrayList<>();
            lore.add(plugin.color("&7Click to " + action));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        inv.setItem(slot, item);
    }

    public void open(Player player) {
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inv)) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int slot = event.getSlot();

        // Handle navigation items
        if (slot == 45) { // Back
            player.closeInventory();
            plugin.openAdminGui(player);
            return;
        }
        if (slot == 49) { // Reload
            plugin.reloadLocal(true);
            player.sendMessage(plugin.color("&6&lBubble&bCraft &8» &aConfig reloaded!"));
            build(); // Rebuild GUI with new values
            return;
        }
        if (slot == 53) { // Close
            player.closeInventory();
            return;
        }

        // Handle toggle items
        String configPath = getConfigPathForSlot(slot);
        if (configPath != null) {
            boolean currentValue = plugin.getConfig().getBoolean(configPath, true);
            plugin.getConfig().set(configPath, !currentValue);
            plugin.saveConfig();
            
            String settingName = getSettingNameForSlot(slot);
            String newStatus = !currentValue ? "&aEnabled" : "&cDisabled";
            player.sendMessage(plugin.color("&6&lBubble&bCraft &8» &f" + settingName + " " + newStatus));
            
            build(); // Rebuild GUI to show new state
        }
    }

    private String getConfigPathForSlot(int slot) {
        return switch (slot) {
            case 10 -> "world-creation.auto-create-worlds";
            case 11 -> "world-creation.prefer-multiverse";
            case 12 -> "world-creation.disable-natural-spawning";
            case 13 -> "world-creation.set-time-noon";
            case 14 -> "world-creation.disable-weather";
            case 15 -> "world-creation.disable-pvp";
            case 19 -> "block-protection.enabled";
            case 20 -> "block-protection.admin-override";
            case 28 -> "features.boss-bar";
            case 29 -> "features.economy-rewards";
            case 30 -> "features.loot-rewards";
            case 31 -> "features.wave-system";
            default -> null;
        };
    }

    private String getSettingNameForSlot(int slot) {
        return switch (slot) {
            case 10 -> "Auto Create Worlds";
            case 11 -> "Prefer Multiverse";
            case 12 -> "Disable Natural Spawning";
            case 13 -> "Set Time to Noon";
            case 14 -> "Disable Weather";
            case 15 -> "Disable PvP";
            case 19 -> "Block Protection";
            case 20 -> "Admin Override";
            case 28 -> "Boss Bar HUD";
            case 29 -> "Economy Rewards";
            case 30 -> "Loot Rewards";
            case 31 -> "Wave System";
            default -> "Unknown Setting";
        };
    }
}
