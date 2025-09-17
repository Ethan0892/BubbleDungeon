package com.bubblecraft.dungeons.hook;

import com.bubblecraft.dungeons.BubbleDungeonsPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public class PlaceholderHook extends PlaceholderExpansion {
    private final BubbleDungeonsPlugin plugin;
    public PlaceholderHook(BubbleDungeonsPlugin plugin) { this.plugin = plugin; }
    @Override public String getIdentifier() { return "bubbledungeons"; }
    @Override public String getAuthor() { return "BubbleCraft"; }
    @Override public String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) return "";
        switch (params.toLowerCase()) {
            case "active":
                return plugin.isInAnyDungeon(player.getUniqueId()) ? "yes" : "no";
            case "dungeon":
                return plugin.getPlayerDungeon(player.getUniqueId());
            case "timer":
                return plugin.getActiveTimerDisplay(player.getUniqueId());
            default:
                return "";
        }
    }
}
