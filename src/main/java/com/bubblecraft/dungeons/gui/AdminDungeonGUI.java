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
import java.util.function.Consumer;

public class AdminDungeonGUI implements Listener {

    private final BubbleDungeonsPlugin plugin;
    private final Inventory inv;
    private final Consumer<String> dungeonCreateCallback;

    public AdminDungeonGUI(BubbleDungeonsPlugin plugin, Consumer<String> dungeonCreateCallback) {
        this.plugin = plugin;
        this.dungeonCreateCallback = dungeonCreateCallback;
        this.inv = Bukkit.createInventory(null, 27, plugin.color("&6&lBubble&bCraft&7 » &fDungeons"));
        build();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void build() {
        inv.clear();
        // Slot 0: Create new dungeon
        inv.setItem(0, item(Material.ANVIL, "&aCreate New Dungeon", List.of("&7Click to create")));
        // Slot 1: Config GUI
        inv.setItem(1, item(Material.REDSTONE, "&6Config Settings", List.of("&7Toggle plugin settings", "&7in a convenient GUI")));
        
        // Back button (bottom left)
        inv.setItem(18, item(Material.ARROW, "&cBack", List.of("&7Return to previous menu")));
        
        // Existing dungeons list
        int slot = 9;
        for (String key : plugin.getDungeonKeysSorted()) {
            inv.setItem(slot++, item(Material.SPAWNER, "&e" + key, List.of("&7Left-click to edit mobs", "&7Right-click to edit dungeon")));
            if (slot >= inv.getSize()) break;
        }
    }

    private ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.color(name));
            if (lore != null) {
                List<String> c = new ArrayList<>();
                for (String l : lore) c.add(plugin.color(l));
                meta.setLore(c);
            }
            it.setItemMeta(meta);
        }
        return it;
    }

    public void open(Player p) {
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTitle().equals(plugin.color("&6&lBubble&bCraft&7 » &fDungeons"))) {
            e.setCancelled(true);
            if (e.getClickedInventory() == null) return;
            if (e.getRawSlot() == 0) {
                // Create
                plugin.promptChatInput((Player) e.getWhoClicked(), plugin.color("&aEnter new dungeon key:"), dungeonCreateCallback);
            } else if (e.getRawSlot() == 1) {
                // Config GUI
                plugin.openConfigGui((Player) e.getWhoClicked());
            } else if (e.getRawSlot() == 18) {
                // Back button - close this GUI
                Player player = (Player) e.getWhoClicked();
                plugin.getParticleEffects().playGUIEffect(player);
                player.closeInventory();
            } else if (e.getCurrentItem() != null && e.getCurrentItem().hasItemMeta()) {
                String dn = org.bukkit.ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
                Player player = (Player) e.getWhoClicked();
                plugin.getParticleEffects().playGUIEffect(player);
                
                if (e.isLeftClick()) {
                    // Open mobs editor
                    new DungeonMobsGUI(plugin, dn).open(player);
                } else if (e.isRightClick()) {
                    // Open dungeon editor
                    new DungeonEditGUI(plugin, dn).open(player);
                }
            }
        }
    }
}
