# 🛡️ Safe Spawning & Return System Guide

## 📋 Overview
The BubbleDungeon plugin now features advanced safe spawning and return mechanics to enhance the player experience:

- **Safe Spawning**: Players and mobs spawn on solid ground to prevent fall damage
- **Configurable Returns**: Customizable return locations when dungeons are completed or failed
- **Automatic Ground Detection**: Intelligent detection of safe spawn points
- **Custom Return Messages**: Personalized messages for different return scenarios

## 🎯 Key Features

### 🛬 Safe Spawning System
- **Player Spawning**: Players are teleported to safe ground when joining dungeons
- **Mob Spawning**: All mobs (including bosses) spawn on solid blocks to prevent fall damage
- **Ground Detection**: Automatically finds the nearest solid block below the configured spawn point
- **Air Space Validation**: Ensures 2 blocks of air space above the spawn point for entities

### 🏠 Configurable Return System
- **Auto-Return Toggle**: Enable/disable automatic return to main world
- **Custom Return Locations**: Set specific return coordinates for each dungeon
- **Multiple Return Options**: Support for different return locations per dungeon
- **Safe Return Teleportation**: Return locations also use safe spawning logic

## ⚙️ Configuration

### Basic Settings
```yaml
# Enable/disable automatic return to main world after dungeon completion/failure
features:
  auto-return: true        # Default: true

# Main world name for default returns
main-world: "world"        # Default: "world"

# Custom messages
messages:
  returned-to-world: "&6&lBubble&bCraft &8» &eReturned to the main world!"
```

### Per-Dungeon Return Locations
```yaml
dungeons:
  forest_ruins:
    world: "dungeon_forest"
    location:
      x: 0
      y: 70
      z: 0
    # Custom return location for this dungeon
    return-location:
      world: "spawn_world"     # World to return to
      x: 100                   # X coordinate
      y: 80                    # Y coordinate (will find safe ground below this)
      z: -50                   # Z coordinate
    # ... other dungeon settings ...
    
  crystal_caves:
    world: "dungeon_caves"
    location:
      x: 0
      y: 60
      z: 0
    # Uses default main world return (no custom return-location)
    # ... other dungeon settings ...
    
  shadow_fortress:
    world: "dungeon_shadow"
    location:
      x: 0
      y: 80
      z: 0
    # Custom return to a special VIP area
    return-location:
      world: "vip_world"
      x: 0
      y: 100
      z: 0
    # ... other dungeon settings ...
```

## 🔧 How It Works

### Safe Spawning Algorithm
1. **Target Location**: Plugin receives the configured spawn coordinates
2. **Ground Search**: Searches downward from the Y coordinate to find solid ground
3. **Air Space Check**: Verifies 2 blocks of air space above the ground for entity spawning
4. **Fallback Safety**: If no safe spot found, spawns above bedrock level with safety margin
5. **Final Teleport**: Teleports entity to the safe location

### Return Location Priority
1. **Custom Return Location**: If `return-location` is configured for the dungeon
2. **Main World Spawn**: Falls back to the main world's spawn location
3. **Safe Ground Search**: All return locations use safe spawning to prevent fall damage

### When Players Are Returned
- **Dungeon Completion**: Boss defeated, all objectives complete
- **Dungeon Failure**: All players die in the dungeon
- **Manual Leave**: Player uses `/dungeon leave` command
- **World Change**: Player leaves the dungeon world through other means

## 🏗️ Advanced Configuration Examples

### Adventure Map Integration
```yaml
dungeons:
  tutorial_dungeon:
    world: "dungeon_tutorial"
    return-location:
      world: "adventure_hub"    # Return to adventure map hub
      x: 0
      y: 75
      z: 0
      
  boss_dungeon:
    world: "dungeon_boss"
    return-location:
      world: "victory_hall"     # Return to special victory area
      x: 250
      y: 90
      z: -100
```

### PvP Server Setup
```yaml
dungeons:
  arena_battle:
    world: "dungeon_arena"
    return-location:
      world: "pvp_lobby"        # Return to PvP lobby
      x: 0
      y: 80
      z: 0
      
  team_dungeon:
    world: "dungeon_team"
    # No custom return - uses main world spawn
```

### Roleplay Server Configuration
```yaml
dungeons:
  goblin_caves:
    world: "dungeon_goblins"
    return-location:
      world: "town_square"      # Return to town
      x: 150
      y: 70
      z: 200
      
  dragon_lair:
    world: "dungeon_dragon"
    return-location:
      world: "royal_castle"     # Return to castle for rewards
      x: 0
      y: 95
      z: 0
```

## 💡 Best Practices

### Safe Spawning Tips
1. **Y Coordinate Choice**: Set spawn Y coordinates slightly above expected ground level
2. **Flat Areas**: Configure spawns in relatively flat areas for best results
3. **Avoid Void**: Don't set spawn coordinates in void areas of custom worlds
4. **Test Spawns**: Use `/dungeon edit` to test and adjust spawn locations in-game

### Return Location Tips
1. **Thematic Returns**: Match return locations to your server's theme
2. **Progression**: Use different return locations for different difficulty dungeons
3. **Player Convenience**: Choose return locations near important server areas
4. **Safety First**: Ensure return locations are safe and well-lit

### Performance Considerations
- Safe spawning adds minimal overhead (single-pass ground search)
- Return location lookups are cached for performance
- Custom return locations are only calculated when needed

## 🔍 Troubleshooting

### Common Issues
- **Still Taking Fall Damage**: Check if spawn coordinates are set properly in config
- **Wrong Return Location**: Verify `return-location` world name exists
- **Not Returning**: Check `auto-return: true` is set in features section
- **Spawning in Walls**: Increase Y coordinate in spawn configuration

### Debug Tips
- Enable debug logging to see safe spawn calculations
- Use `/dungeon edit` to visually test spawn locations
- Check server console for world loading errors
- Verify world names match exactly (case-sensitive)

## 🎮 Player Experience

### What Players Notice
- **Smooth Entry**: No fall damage when joining dungeons
- **Professional Feel**: Polished teleportation experience
- **Thematic Returns**: Immersive return to appropriate locations
- **Consistent Safety**: Reliable safe spawning across all dungeons

### Enhanced Gameplay
- **Focus on Fun**: Less distraction from technical issues
- **Improved Flow**: Seamless transitions between worlds
- **Reduced Frustration**: No unexpected fall damage or unsafe spawns
- **Better Immersion**: Thematically appropriate return locations

This safe spawning and return system ensures a professional, polished dungeon experience that keeps players focused on the adventure rather than technical issues!
