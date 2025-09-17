package com.bubblecraft.dungeons.gui;

import com.bubblecraft.dungeons.BubbleDungeonsPlugin;
import com.bubblecraft.dungeons.BubbleDungeonsPlugin.DungeonDefinition;
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
import java.util.stream.Collectors;

public class DungeonEditGUI implements Listener {
    private final BubbleDungeonsPlugin plugin;
    private final String dungeonKey;
    private Inventory inv;

    public DungeonEditGUI(BubbleDungeonsPlugin plugin, String dungeonKey) {
        this.plugin = plugin;
        this.dungeonKey = dungeonKey.toLowerCase();
        build();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void build() {
        DungeonDefinition dd = plugin.getDungeon(dungeonKey);
        if (dd == null) return;
        
        inv = Bukkit.createInventory(null, 54, plugin.color("&6&lBubble&bCraft&7 » &fEdit: &e" + dungeonKey));
        
        // Row 1: Basic Info
        inv.setItem(0, actionItem(Material.NAME_TAG, "&eWorld Name", 
            List.of("&7Current: &f" + dd.worldName(), "&eClick to change")));
        inv.setItem(1, actionItem(Material.COMPASS, "&eBase Location", 
            List.of("&7X: &f" + dd.x(), "&7Y: &f" + dd.y(), "&7Z: &f" + dd.z(), "&eClick to set to your location")));
        inv.setItem(2, actionItem(Material.DIAMOND_SWORD, "&eDifficulty", 
            List.of("&7Current: &f" + dd.difficulty(), "&eClick to change")));
        
        // Row 2: Spawn Locations  
        inv.setItem(9, actionItem(Material.PLAYER_HEAD, "&aPlayer Spawn", 
            List.of("&7X: &f" + dd.playerX(), "&7Y: &f" + dd.playerY(), "&7Z: &f" + dd.playerZ(), 
                    "&eLeft-click to set to your location", "&eRight-click to edit coordinates")));
        inv.setItem(10, actionItem(Material.WITHER_SKELETON_SKULL, "&cBoss Spawn", 
            List.of("&7X: &f" + dd.bossX(), "&7Y: &f" + dd.bossY(), "&7Z: &f" + dd.bossZ(), 
                    "&eLeft-click to set to your location", "&eRight-click to edit coordinates")));
        
        // Row 3: Advancement Settings
        inv.setItem(18, actionItem(Material.BOOK, "&6Advancement Title", 
            List.of("&7Current: &f" + dd.advTitle(), "&eClick to change")));
        inv.setItem(19, actionItem(Material.WRITABLE_BOOK, "&6Advancement Description", 
            List.of("&7Current: &f" + dd.advDescription(), "&eClick to change")));
        
        // Row 4: Mob Management
        inv.setItem(27, actionItem(Material.SPAWNER, "&eMobs", 
            List.of("&7Total mobs: &f" + dd.mobs().size(), "&eClick to edit mobs")));
        inv.setItem(28, actionItem(Material.WITHER_SKELETON_SKULL, "&cBoss", 
            List.of("&7Type: &f" + (dd.boss() != null ? dd.boss().type() : "None"), 
                    "&7Health: &f" + (dd.boss() != null ? dd.boss().health() : "N/A"), 
                    "&eClick to edit boss")));
        
        // Navigation
        inv.setItem(45, actionItem(Material.ARROW, "&cBack", List.of("&7Return to dungeons menu")));
        inv.setItem(49, actionItem(Material.BARRIER, "&cDelete Dungeon", List.of("&7Permanently delete this dungeon", "&cShift-click to confirm")));
        inv.setItem(53, actionItem(Material.EMERALD, "&aSave & Test", List.of("&7Save changes and teleport", "&7to test the dungeon")));
    }

    private ItemStack actionItem(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.color(name));
            List<String> colored = lore.stream().map(plugin::color).collect(Collectors.toList());
            meta.setLore(colored);
            it.setItemMeta(meta);
        }
        return it;
    }

    public void open(Player p) { 
        p.openInventory(inv); 
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().startsWith(plugin.color("&6&lBubble&bCraft&7 » &fEdit:"))) return;
        if (e.getClickedInventory() == null) return;
        e.setCancelled(true);
        
        Player player = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();
        
        switch (slot) {
            case 0: // World Name
                plugin.promptChatInput(player, plugin.color("&aEnter new world name:"), worldName -> {
                    plugin.updateDungeonWorldName(dungeonKey, worldName);
                    player.sendMessage(plugin.color("&aWorld name updated to: &f" + worldName));
                    build(); // Refresh GUI
                });
                break;
                
            case 1: // Base Location
                plugin.updateDungeonBaseLocation(dungeonKey, player.getLocation());
                player.sendMessage(plugin.color("&aBase location set to your current position"));
                build();
                break;
                
            case 2: // Difficulty
                plugin.promptChatInput(player, plugin.color("&aEnter difficulty (1-10):"), diffStr -> {
                    try {
                        int difficulty = Integer.parseInt(diffStr.trim());
                        plugin.updateDungeonDifficulty(dungeonKey, difficulty);
                        player.sendMessage(plugin.color("&aDifficulty updated to: &f" + difficulty));
                        build();
                    } catch (NumberFormatException ex) {
                        player.sendMessage(plugin.color("&cInvalid number!"));
                    }
                });
                break;
                
            case 9: // Player Spawn
                if (e.isLeftClick()) {
                    plugin.updateDungeonPlayerSpawn(dungeonKey, player.getLocation());
                    player.sendMessage(plugin.color("&aPlayer spawn set to your current position"));
                    build();
                } else if (e.isRightClick()) {
                    plugin.promptChatInput(player, plugin.color("&aEnter coordinates (x,y,z):"), coords -> {
                        parseAndSetPlayerSpawn(player, coords);
                    });
                }
                break;
                
            case 10: // Boss Spawn
                if (e.isLeftClick()) {
                    plugin.updateDungeonBossSpawn(dungeonKey, player.getLocation());
                    player.sendMessage(plugin.color("&aBoss spawn set to your current position"));
                    build();
                } else if (e.isRightClick()) {
                    plugin.promptChatInput(player, plugin.color("&aEnter coordinates (x,y,z):"), coords -> {
                        parseAndSetBossSpawn(player, coords);
                    });
                }
                break;
                
            case 18: // Advancement Title
                plugin.promptChatInput(player, plugin.color("&aEnter advancement title:"), title -> {
                    plugin.updateDungeonAdvancementTitle(dungeonKey, title);
                    player.sendMessage(plugin.color("&aAdvancement title updated"));
                    build();
                });
                break;
                
            case 19: // Advancement Description
                plugin.promptChatInput(player, plugin.color("&aEnter advancement description:"), desc -> {
                    plugin.updateDungeonAdvancementDescription(dungeonKey, desc);
                    player.sendMessage(plugin.color("&aAdvancement description updated"));
                    build();
                });
                break;
                
            case 27: // Mobs
                new DungeonMobsGUI(plugin, dungeonKey).open(player);
                break;
                
            case 28: // Boss
                editBoss(player);
                break;
                
            case 45: // Back
                new AdminDungeonGUI(plugin, newKey -> {
                    // Handle new dungeon creation if needed
                }).open(player);
                break;
                
            case 49: // Delete
                if (e.isShiftClick()) {
                    plugin.deleteDungeon(dungeonKey);
                    player.sendMessage(plugin.color("&cDungeon deleted: " + dungeonKey));
                    new AdminDungeonGUI(plugin, newKey -> {}).open(player);
                } else {
                    player.sendMessage(plugin.color("&eShift-click to confirm deletion"));
                }
                break;
                
            case 53: // Save & Test
                plugin.teleportToDungeon(player, dungeonKey);
                player.sendMessage(plugin.color("&aTeleported to dungeon for testing"));
                player.closeInventory();
                break;
        }
    }
    
    private void parseAndSetPlayerSpawn(Player player, String coords) {
        try {
            String[] parts = coords.split(",");
            if (parts.length != 3) throw new IllegalArgumentException("Need exactly 3 coordinates");
            double x = Double.parseDouble(parts[0].trim());
            double y = Double.parseDouble(parts[1].trim());
            double z = Double.parseDouble(parts[2].trim());
            plugin.updateDungeonPlayerSpawn(dungeonKey, x, y, z);
            player.sendMessage(plugin.color("&aPlayer spawn coordinates updated"));
            build();
        } catch (Exception ex) {
            player.sendMessage(plugin.color("&cInvalid coordinates format! Use: x,y,z"));
        }
    }
    
    private void parseAndSetBossSpawn(Player player, String coords) {
        try {
            String[] parts = coords.split(",");
            if (parts.length != 3) throw new IllegalArgumentException("Need exactly 3 coordinates");
            double x = Double.parseDouble(parts[0].trim());
            double y = Double.parseDouble(parts[1].trim());
            double z = Double.parseDouble(parts[2].trim());
            plugin.updateDungeonBossSpawn(dungeonKey, x, y, z);
            player.sendMessage(plugin.color("&aBoss spawn coordinates updated"));
            build();
        } catch (Exception ex) {
            player.sendMessage(plugin.color("&cInvalid coordinates format! Use: x,y,z"));
        }
    }
    
    private void editBoss(Player player) {
        plugin.promptChatInput(player, plugin.color("&aEnter boss definition (type,name,health,damage,speed,size,mythicId?)"), s -> {
            String[] parts = s.split(",");
            if (parts.length < 5) {
                player.sendMessage(plugin.color("&cInvalid format. Need: type,name,health,damage,speed,size,mythicId"));
                return;
            }
            try {
                String type = parts[0].trim().toUpperCase();
                String name = parts[1].trim();
                double health = Double.parseDouble(parts[2].trim()) * 2.0; // hearts to hp
                double damage = Double.parseDouble(parts[3].trim());
                double speed = Double.parseDouble(parts[4].trim());
                int size = parts.length > 5 ? Integer.parseInt(parts[5].trim()) : 0;
                String mythic = parts.length > 6 ? parts[6].trim() : null;
                plugin.updateDungeonBoss(dungeonKey, type, name, health, damage, speed, size, mythic);
                player.sendMessage(plugin.color("&aBoss updated"));
                build();
            } catch (Exception ex) {
                player.sendMessage(plugin.color("&cParse error: " + ex.getMessage()));
            }
        });
    }
}
