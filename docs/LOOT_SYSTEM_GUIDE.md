# 🎁 Enhanced Loot System Guide

## 📋 Overview
The BubbleDungeon plugin now features a comprehensive loot system with:
- **6 Rarity Tiers**: Common, Uncommon, Rare, Epic, Legendary, Mythic
- **Custom Item Names & Lore**: Fully customizable item appearance
- **BubbleCoins Integration**: Direct BubbleCoins rewards
- **Glowing Effects**: Visual enhancement for special items
- **Guarantee System**: Ensure rare drops when configured

## 🎯 Configuration Format

### Basic Loot Table Structure
```yaml
dungeons:
  example_dungeon:
    # ... other dungeon settings ...
    loot:
      enabled: true
      rolls: 3                    # Number of items to roll per completion
      guarantee-rare: true        # Guarantee at least one RARE+ item
      items:
        - item: "DIAMOND_SWORD"
          name: "&bFrozen Blade"
          lore:
            - "&7A sword forged in eternal ice"
            - "&7Deals extra damage to fire creatures"
          rarity: "EPIC"
          min: 1
          max: 1
          glowing: true
          
        - item: "GOLD_INGOT"
          name: "&6Treasure Gold"
          rarity: "UNCOMMON"
          min: 5
          max: 15
          
        - bubblecoins: 100
          rarity: "RARE"
          
        - command: "give %player% minecraft:enchanted_golden_apple 1"
          rarity: "LEGENDARY"
```

## 🌟 Rarity System

### Rarity Tiers & Default Weights
| Rarity | Color | Default Weight | Drop Chance |
|--------|-------|----------------|-------------|
| COMMON | Gray | 100 | ~40% |
| UNCOMMON | Green | 75 | ~30% |
| RARE | Blue | 50 | ~20% |
| EPIC | Purple | 25 | ~7% |
| LEGENDARY | Gold | 10 | ~2.5% |
| MYTHIC | Red | 5 | ~0.5% |

### Custom Weights
Override default rarity weights:
```yaml
- item: "NETHERITE_SWORD"
  rarity: "MYTHIC"
  weight: 1              # Make even rarer than default
```

## 💰 BubbleCoins Integration

### Direct BubbleCoins Drops
```yaml
- bubblecoins: 50        # Gives 50 BubbleCoins
  rarity: "COMMON"

- bubblecoins: 500       # Gives 500 BubbleCoins  
  rarity: "LEGENDARY"
```

### Command-Based Economy
```yaml
- command: "eco give %player% 100"
  rarity: "UNCOMMON"
```

## 🎨 Item Customization

### Custom Names & Lore
```yaml
- item: "IRON_SWORD"
  name: "&4Bloodthirsty Blade"
  lore:
    - "&7Forged in the depths of hell"
    - "&cDeals &4+2 &cattack damage"
    - ""
    - "&8Right-click for special ability"
  rarity: "RARE"
  glowing: true
```

### Glowing Effects
Add enchanted glow to items:
```yaml
- item: "DIAMOND"
  name: "&bMagical Crystal"
  glowing: true          # Adds enchanted glow effect
  rarity: "EPIC"
```

## 📊 Advanced Configuration Examples

### Balanced PvE Dungeon
```yaml
loot:
  enabled: true
  rolls: 2
  guarantee-rare: false
  items:
    # Common drops (bread & arrows)
    - item: "BREAD"
      min: 3
      max: 8
      rarity: "COMMON"
    - item: "ARROW"
      min: 16
      max: 32
      rarity: "COMMON"
      
    # Uncommon gear
    - item: "IRON_CHESTPLATE"
      name: "&aGuardian's Plate"
      rarity: "UNCOMMON"
      
    # Rare weapons
    - item: "DIAMOND_SWORD"
      name: "&9Hero's Blade"
      lore:
        - "&7Wielded by ancient heroes"
      rarity: "RARE"
      glowing: true
      
    # Epic rewards
    - bubblecoins: 250
      rarity: "EPIC"
      
    # Legendary prizes
    - item: "NETHERITE_INGOT"
      name: "&6Ancient Netherite"
      rarity: "LEGENDARY"
      min: 1
      max: 3
```

### Boss-Specific Loot
```yaml
loot:
  enabled: true
  rolls: 5
  guarantee-rare: true    # Boss always drops something good
  items:
    # Boss-themed weapon
    - item: "NETHERITE_AXE"
      name: "&4Demon Lord's Cleaver"
      lore:
        - "&7Torn from the hands of evil"
        - "&cDeals massive damage"
        - ""
        - "&5BOSS DROP"
      rarity: "MYTHIC"
      glowing: true
      weight: 3           # Slightly higher chance
      
    # Big BubbleCoins reward
    - bubblecoins: 1000
      rarity: "LEGENDARY"
      
    # Special commands
    - command: "advancement grant %player% minecraft:nether/summon_wither"
      rarity: "EPIC"
```

## 🔧 Migration from Old Format

The system supports backward compatibility with the old `table` format:

### Old Format (Still Works)
```yaml
loot:
  enabled: true
  rolls: 2
  table:
    - item: "DIAMOND"
      min: 1
      max: 3
      weight: 10
    - command: "give %player% bread 5"
      weight: 50
```

### New Enhanced Format
```yaml
loot:
  enabled: true
  rolls: 2
  items:
    - item: "DIAMOND"
      name: "&bPrecious Crystal"
      rarity: "RARE"      # Auto-calculates weight
      min: 1
      max: 3
    - bubblecoins: 25
      rarity: "COMMON"
```

## 🎮 In-Game Features

### Automatic Notifications
- Players receive special messages for RARE+ drops
- BubbleCoins awards show custom notifications
- Rarity colors are applied automatically

### Visual Enhancements
- Custom item names with rarity colors
- Rich lore with formatting
- Glowing effects for special items
- Rarity information added to item tooltips

## 💡 Best Practices

1. **Balance Drop Rates**: Use `guarantee-rare: true` sparingly for boss dungeons only
2. **Meaningful Rewards**: Scale BubbleCoins amounts with dungeon difficulty  
3. **Visual Appeal**: Use glowing effects and custom names for special items
4. **Clear Progression**: Make higher dungeons give better loot
5. **Mix Rewards**: Combine items, commands, and BubbleCoins for variety

## 🐛 Troubleshooting

### Common Issues
- **No drops**: Check `enabled: true` and valid item materials
- **Wrong rarity colors**: Verify rarity spelling (case-insensitive)
- **BubbleCoins not working**: Ensure economy plugin is installed
- **Items don't glow**: Verify `glowing: true` is set

### Debug Mode
Enable debug logging in config:
```yaml
debug:
  log-loot-rolls: true
```

This comprehensive loot system makes dungeon rewards exciting and configurable while maintaining backward compatibility!
