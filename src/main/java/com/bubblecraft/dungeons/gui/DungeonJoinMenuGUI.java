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
import java.util.Locale;

public class DungeonJoinMenuGUI implements Listener {
    private final BubbleDungeonsPlugin plugin;
    private Inventory inv;
    public DungeonJoinMenuGUI(BubbleDungeonsPlugin plugin){
        this.plugin = plugin;
        rebuild();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    private void rebuild(){
        inv = Bukkit.createInventory(null, 54, plugin.color("&6&lBubble&bCraft&7 » &fJoin Dungeon"));
        int slot=0;
        for(String key: plugin.getDungeonKeysSorted()){
            if(slot>=inv.getSize()) break;
            inv.setItem(slot++, dungeonItem(key));
        }
        
        // Add back button
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(plugin.color("&cBack"));
            List<String> backLore = new ArrayList<>();
            backLore.add(plugin.color("&7Return to main menu"));
            backMeta.setLore(backLore);
            backButton.setItemMeta(backMeta);
        }
        inv.setItem(45, backButton);
    }
    private ItemStack dungeonItem(String key){
        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = it.getItemMeta();
        if(meta!=null){
            meta.setDisplayName(plugin.color("&e"+key));
            List<String> lore = new ArrayList<>();
            lore.add(plugin.color("&7Click to join"));
            meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }
    public void open(Player p){ p.openInventory(inv); }
    @EventHandler public void onClick(InventoryClickEvent e){
        if(!e.getView().getTitle().equals(plugin.color("&6&lBubble&bCraft&7 » &fJoin Dungeon"))) return;
        if(e.getClickedInventory()==null) return;
        e.setCancelled(true);
        if(e.getCurrentItem()==null || !e.getCurrentItem().hasItemMeta()) return;
        
        int slot = e.getSlot();
        Player p = (Player)e.getWhoClicked();
        
        // Handle back button
        if (slot == 45) {
            plugin.getParticleEffects().playGUIEffect(p);
            p.closeInventory();
            return;
        }
        
        String name = org.bukkit.ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).toLowerCase(Locale.ROOT);
        p.closeInventory();
        p.performCommand("dungeon join "+name);
    }
}
