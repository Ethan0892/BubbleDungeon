package com.bubblecraft.dungeons.gui;

import com.bubblecraft.dungeons.BubbleDungeonsPlugin;
import com.bubblecraft.dungeons.BubbleDungeonsPlugin.MobDefinition;
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
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class DungeonMobsGUI implements Listener {
    private final BubbleDungeonsPlugin plugin;
    private final String dungeonKey;
    private Inventory inv;

    public DungeonMobsGUI(BubbleDungeonsPlugin plugin, String dungeonKey) {
        this.plugin = plugin;
        this.dungeonKey = dungeonKey.toLowerCase();
        build();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void build() {
        List<MobDefinition> mobs = plugin.getMobsForDungeon(dungeonKey);
        inv = Bukkit.createInventory(null, 54, plugin.color("&6&lBubble&bCraft&7 » &fMobs: &e" + dungeonKey));
        int slot = 0;
        for (MobDefinition md : mobs) {
            if (slot >= inv.getSize()) break;
            inv.setItem(slot++, mobItem(md));
        }
        // Add mob button (top right)
        inv.setItem(53, actionItem(Material.EMERALD_BLOCK, "&aAdd Mob", List.of("&7Click to add new mob")));
        // Back button (bottom left)
        inv.setItem(45, actionItem(Material.ARROW, "&cBack", List.of("&7Return to dungeons menu")));
    }

    private ItemStack mobItem(MobDefinition md) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Type: &f" + md.type());
        lore.add("&7Name: &f" + (md.name() == null ? "(none)" : md.name()));
        lore.add("&7Health (hp): &f" + md.health());
        lore.add("&7Damage: &f" + md.damage());
        lore.add("&7Speed: &f" + md.speed());
        lore.add("&7Size: &f" + md.size());
        if (md.mythicId() != null) lore.add("&7Mythic: &f" + md.mythicId());
        lore.add("&eClick to edit");
        return actionItem(Material.ZOMBIE_HEAD, "&eMob", lore);
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

    public void open(Player p) { p.openInventory(inv); }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().startsWith(plugin.color("&6&lBubble&bCraft&7 » &fMobs:"))) return;
        if (e.getClickedInventory() == null) return;
        e.setCancelled(true);
        int raw = e.getRawSlot();
        if (raw == 53) {
            // Add mob button
            plugin.promptChatInput((Player) e.getWhoClicked(), plugin.color("&aEnter mob definition (type,name,health,damage,speed,size,mythicId?)"), s -> {
                String[] parts = s.split(",");
                if (parts.length < 5) {
                    e.getWhoClicked().sendMessage(plugin.color("&cInvalid format."));
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
                    plugin.addMobToDungeon(dungeonKey, type, name, health, damage, speed, size, mythic);
                    e.getWhoClicked().sendMessage(plugin.color("&aMob added."));
                } catch (Exception ex) {
                    e.getWhoClicked().sendMessage(plugin.color("&cParse error: " + ex.getMessage()));
                }
                build();
                ((Player)e.getWhoClicked()).openInventory(inv);
            });
        } else if (raw == 45) {
            // Back button - return to admin dungeon GUI
            Player player = (Player) e.getWhoClicked();
            plugin.getParticleEffects().playGUIEffect(player);
            plugin.openAdminGui(player);
        } else if (raw >=0 && raw < 53 && raw != 45) {
            int index = raw; // direct mapping
            plugin.promptChatInput((Player) e.getWhoClicked(), plugin.color("&eEdit mob or type 'remove' to delete. Format: type,name,health,damage,speed,size,mythicId?"), s -> {
                if (s.equalsIgnoreCase("remove")) {
                    plugin.removeMobFromDungeon(dungeonKey, index);
                    e.getWhoClicked().sendMessage(plugin.color("&cMob removed."));
                } else {
                    String[] parts = s.split(",");
                    if (parts.length < 5) {
                        e.getWhoClicked().sendMessage(plugin.color("&cInvalid format."));
                    } else {
                        try {
                            String type = parts[0].trim().toUpperCase();
                            String name = parts[1].trim();
                            double health = Double.parseDouble(parts[2].trim()) * 2.0; // hearts to hp
                            double damage = Double.parseDouble(parts[3].trim());
                            double speed = Double.parseDouble(parts[4].trim());
                            int size = parts.length > 5 ? Integer.parseInt(parts[5].trim()) : 0;
                            String mythic = parts.length > 6 ? parts[6].trim() : null;
                            plugin.updateMobInDungeon(dungeonKey, index, new MobDefinition(type, name, health, damage, speed, size, mythic));
                            e.getWhoClicked().sendMessage(plugin.color("&aMob updated."));
                        } catch (Exception ex) {
                            e.getWhoClicked().sendMessage(plugin.color("&cParse error: " + ex.getMessage()));
                        }
                    }
                }
                build();
                ((Player)e.getWhoClicked()).openInventory(inv);
            });
        }
    }
}
