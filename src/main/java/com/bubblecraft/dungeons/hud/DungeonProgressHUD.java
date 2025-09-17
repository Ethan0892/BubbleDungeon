package com.bubblecraft.dungeons.hud;

import com.bubblecraft.dungeons.BubbleDungeonsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.*;

public class DungeonProgressHUD {
    private final BubbleDungeonsPlugin plugin;
    private final Map<String, BossBar> bars = new HashMap<>();
    public DungeonProgressHUD(BubbleDungeonsPlugin plugin) { this.plugin = plugin; }

    public void update(String dungeonKey, int remaining, int total, Collection<UUID> players) {
        if (!plugin.hudBossBarEnabled()) return;
        double progress = total == 0 ? 1.0 : Math.max(0.0, Math.min(1.0, (double)(total-remaining)/total));
        BossBar bar = bars.computeIfAbsent(dungeonKey, k -> Bukkit.createBossBar(plugin.color("&e"+k+" &7Progress"), BarColor.BLUE, BarStyle.SEGMENTED_10));
        bar.setProgress(progress);
        bar.setTitle(plugin.color("&e"+dungeonKey+" &7" + (total-remaining) + "/" + total));
        
        // Only show boss bar to players actually in the dungeon and in the dungeon world
        Set<UUID> currentPlayers = new HashSet<>(players);
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (currentPlayers.contains(p.getUniqueId()) && 
                plugin.isDungeonWorld(p.getWorld())) {
                // Player is in this dungeon and in the dungeon world - add them to boss bar
                if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
            } else {
                // Player is not in this dungeon or not in dungeon world - remove them from boss bar
                if (bar.getPlayers().contains(p)) bar.removePlayer(p);
            }
        }
        
        // Clean up empty bars
        if (players.isEmpty()) { 
            bar.removeAll(); 
            bars.remove(dungeonKey); 
        }
    }
    public void clear(String dungeonKey) {
        BossBar bar = bars.remove(dungeonKey);
        if (bar != null) {
            bar.removeAll();
        }
    }
    
    /**
     * Reset all boss bars for a specific dungeon (useful when dungeon starts/restarts)
     */
    public void reset(String dungeonKey) {
        clear(dungeonKey);
    }
    
    /**
     * Clear all boss bars (useful for plugin shutdown or global reset)
     */
    public void clearAll() {
        int cleared = 0;
        for (BossBar bar : bars.values()) {
            bar.removeAll();
            cleared++;
        }
        bars.clear();
    }
}
