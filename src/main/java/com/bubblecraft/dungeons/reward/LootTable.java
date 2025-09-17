package com.bubblecraft.dungeons.reward;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class LootTable {
    
    public enum Rarity {
        COMMON("&7Common", 100, "&7"),
        UNCOMMON("&aUncommon", 75, "&a"),
        RARE("&9Rare", 50, "&9"),
        EPIC("&5Epic", 25, "&5"),
        LEGENDARY("&6Legendary", 10, "&6"),
        MYTHIC("&4Mythic", 5, "&4");
        
        public final String displayName;
        public final int defaultWeight;
        public final String color;
        
        Rarity(String displayName, int defaultWeight, String color) {
            this.displayName = displayName;
            this.defaultWeight = defaultWeight;
            this.color = color;
        }
        
        public static Rarity fromString(String str) {
            if (str == null) return COMMON;
            try {
                return valueOf(str.toUpperCase());
            } catch (IllegalArgumentException e) {
                return COMMON;
            }
        }
    }
    
    public static class Entry {
        public final ItemStack item; // nullable if command
        public final String command; // nullable if item
        public final String displayName; // custom display name
        public final List<String> lore; // custom lore
        public final int min;
        public final int max;
        public final int weight;
        public final Rarity rarity;
        public final boolean glowing;
        public final int bubbleCoins; // amount of bubblecoins to give (0 = none)
        
        public Entry(ItemStack item, String command, String displayName, List<String> lore, 
                    int min, int max, int weight, Rarity rarity, boolean glowing, int bubbleCoins) {
            this.item = item; 
            this.command = command; 
            this.displayName = displayName;
            this.lore = lore;
            this.min = min; 
            this.max = max; 
            this.weight = weight;
            this.rarity = rarity;
            this.glowing = glowing;
            this.bubbleCoins = bubbleCoins;
        }
    }
    
    private final List<Entry> entries;
    private final int totalWeight;
    private final int rolls;
    private final boolean guaranteeRare; // guarantee at least one rare+ item per roll set
    
    public LootTable(List<Entry> entries, int rolls, boolean guaranteeRare) {
        this.entries = entries; 
        this.rolls = rolls;
        this.guaranteeRare = guaranteeRare;
        this.totalWeight = entries.stream().mapToInt(e -> e.weight).sum();
    }
    
    public List<Entry> roll(Random random) {
        List<Entry> out = new ArrayList<>();
        boolean hasRare = false;
        
        for (int i = 0; i < rolls; i++) {
            if (entries.isEmpty() || totalWeight <= 0) break;
            
            Entry selected = null;
            
            // On last roll, guarantee rare if needed and none found yet
            if (guaranteeRare && i == rolls - 1 && !hasRare) {
                List<Entry> rareEntries = entries.stream()
                    .filter(e -> e.rarity.ordinal() >= Rarity.RARE.ordinal())
                    .toList();
                if (!rareEntries.isEmpty()) {
                    int rareWeight = rareEntries.stream().mapToInt(e -> e.weight).sum();
                    if (rareWeight > 0) {
                        int r = random.nextInt(rareWeight) + 1;
                        int acc = 0;
                        for (Entry e : rareEntries) {
                            acc += e.weight;
                            if (r <= acc) {
                                selected = e;
                                break;
                            }
                        }
                    }
                }
            }
            
            // Normal roll if no rare guarantee needed
            if (selected == null) {
                int r = random.nextInt(totalWeight) + 1;
                int acc = 0;
                for (Entry e : entries) {
                    acc += e.weight;
                    if (r <= acc) {
                        selected = e;
                        break;
                    }
                }
            }
            
            if (selected != null) {
                out.add(selected);
                if (selected.rarity.ordinal() >= Rarity.RARE.ordinal()) {
                    hasRare = true;
                }
            }
        }
        return out;
    }
    
    public ItemStack createCustomItem(Entry entry, int amount) {
        if (entry.item == null) return null;
        
        ItemStack item = entry.item.clone();
        item.setAmount(Math.min(64, Math.max(1, amount)));
        
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Set custom display name with rarity color
            if (entry.displayName != null && !entry.displayName.isEmpty()) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', 
                    entry.rarity.color + entry.displayName));
            }
            
            // Set custom lore with rarity info
            List<String> finalLore = new ArrayList<>();
            if (entry.lore != null && !entry.lore.isEmpty()) {
                for (String line : entry.lore) {
                    finalLore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                finalLore.add(""); // Empty line
            }
            
            // Add rarity line
            finalLore.add(ChatColor.translateAlternateColorCodes('&', entry.rarity.displayName));
            
            // Add bubblecoin info if applicable
            if (entry.bubbleCoins > 0) {
                finalLore.add(ChatColor.translateAlternateColorCodes('&', 
                    "&6+" + entry.bubbleCoins + " BubbleCoins"));
            }
            
            meta.setLore(finalLore);
            
            // Add glowing effect
            if (entry.glowing) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    public static LootTable fromConfig(ConfigurationSection sec) {
        if (sec == null) return new LootTable(Collections.emptyList(), 0, false);
        
        int rolls = sec.getInt("rolls", 1);
        boolean guaranteeRare = sec.getBoolean("guarantee-rare", false);
        List<Entry> list = new ArrayList<>();
        
        // Load from simplified format
        if (sec.contains("items")) {
            List<Map<?, ?>> raw = sec.getMapList("items");
            for (Map<?, ?> m : raw) {
                Entry entry = parseEntry(m);
                if (entry != null) list.add(entry);
            }
        }
        
        // Load from legacy format for backward compatibility
        else if (sec.contains("table")) {
            List<Map<?, ?>> raw = sec.getMapList("table");
            for (Map<?, ?> m : raw) {
                Entry entry = parseLegacyEntry(m);
                if (entry != null) list.add(entry);
            }
        }
        
        return new LootTable(list, rolls, guaranteeRare);
    }
    
    private static Entry parseEntry(Map<?, ?> m) {
        try {
            // Parse basic properties
            String itemName = m.containsKey("item") ? Objects.toString(m.get("item")) : null;
            String command = m.containsKey("command") ? Objects.toString(m.get("command")) : null;
            String displayName = m.containsKey("name") ? Objects.toString(m.get("name")) : null;
            
            // Parse lore
            List<String> lore = new ArrayList<>();
            if (m.containsKey("lore")) {
                Object loreObj = m.get("lore");
                if (loreObj instanceof List<?> loreList) {
                    for (Object line : loreList) {
                        lore.add(Objects.toString(line));
                    }
                } else {
                    lore.add(Objects.toString(loreObj));
                }
            }
            
            // Parse amounts and weights
            int min = m.containsKey("min") ? ((Number)m.get("min")).intValue() : 1;
            int max = m.containsKey("max") ? ((Number)m.get("max")).intValue() : min;
            
            // Parse rarity and calculate weight
            Rarity rarity = Rarity.fromString(Objects.toString(m.get("rarity")));
            int weight = m.containsKey("weight") ? ((Number)m.get("weight")).intValue() : rarity.defaultWeight;
            
            // Parse special properties
            boolean glowing = m.containsKey("glowing") ? Boolean.parseBoolean(Objects.toString(m.get("glowing"))) : false;
            int bubbleCoins = m.containsKey("bubblecoins") ? ((Number)m.get("bubblecoins")).intValue() : 0;
            
            // Create item if specified
            ItemStack item = null;
            if (itemName != null) {
                Material mat = Material.matchMaterial(itemName.toUpperCase(Locale.ROOT));
                if (mat != null) item = new ItemStack(mat);
            }
            
            // Must have either item or command
            if (item == null && command == null && bubbleCoins <= 0) return null;
            
            return new Entry(item, command, displayName, lore, min, max, weight, rarity, glowing, bubbleCoins);
            
        } catch (Exception e) {
            return null;
        }
    }
    
    private static Entry parseLegacyEntry(Map<?, ?> m) {
        try {
            String itemName = m.containsKey("item") ? Objects.toString(m.get("item")) : null;
            String command = m.containsKey("command") ? Objects.toString(m.get("command")) : null;
            int min = m.containsKey("min") ? ((Number)m.get("min")).intValue() : 1;
            int max = m.containsKey("max") ? ((Number)m.get("max")).intValue() : min;
            int weight = m.containsKey("weight") ? ((Number)m.get("weight")).intValue() : 1;
            
            ItemStack item = null;
            if (itemName != null) {
                Material mat = Material.matchMaterial(itemName.toUpperCase(Locale.ROOT));
                if (mat != null) item = new ItemStack(mat);
            }
            if (item == null && command == null) return null;
            
            return new Entry(item, command, null, null, min, max, weight, Rarity.COMMON, false, 0);
        } catch (Exception ignored) {
            return null;
        }
    }
}

