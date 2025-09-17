# Flat World & Natural Mob Spawning Prevention

## Overview
The plugin now ensures all dungeon worlds are created as flat worlds and completely prevents natural mob spawning, so only intentional dungeon mobs appear.

## Flat World Creation

### Enhanced Multiverse Integration
The plugin now creates dungeon worlds with specific flat world settings:

```java
String flatWorldSettings = getConfig().getString("world-creation.flat-settings", "2;7,2x3,2;1;");
Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
    "mv create " + name + " NORMAL -t " + worldType + " -g " + flatWorldSettings);
```

### Default Flat World Configuration
- **Layer Structure**: `2;7,2x3,2;1;`
  - `2` layers of bedrock at bottom
  - `7` layers of stone 
  - `2x3` (6 layers) of dirt
  - `2` layers of grass blocks
  - `1;` = spawn structures enabled
- **World Type**: FLAT (enforced)
- **Environment**: NORMAL (overworld-like)

### Configuration Options
```yaml
world-creation:
  world-type: "FLAT"
  flat-settings: "2;7,2x3,2;1;"  # Customizable layer structure
  auto-create-worlds: true
  prefer-multiverse: true
  disable-natural-spawning: true
  disable-weather: true
  disable-pvp: true
  set-time-noon: true
```

## Natural Mob Spawning Prevention

### Multi-Layer Protection System

#### 1. Multiverse Configuration
When creating worlds, the plugin automatically sets:
```
mv modify <world> set animals false
mv modify <world> set monsters false
```

#### 2. Event-Based Protection
Added `CreatureSpawnEvent` handler that:
- **Detects dungeon worlds** using efficient O(1) lookup
- **Blocks natural spawning** for all natural spawn reasons
- **Allows controlled spawning** for dungeon mobs

#### 3. Allowed Spawn Reasons
The plugin permits spawning only for:
- `CUSTOM` - Plugin-controlled spawning (our dungeon mobs)
- `SPAWNER_EGG` - Admin placed spawner eggs
- `DISPENSE_EGG` - Dispensed spawn eggs
- `COMMAND` - Command-based spawning

#### 4. Blocked Spawn Reasons
All natural spawning is prevented:
- `NATURAL` - Natural world spawning
- `CHUNK_GEN` - Chunk generation spawning  
- `SPAWNER` - Natural spawners
- `EGG` - Thrown spawn eggs
- `LIGHTNING` - Lightning-based spawning
- `BREEDING` - Animal breeding
- And all other natural causes

### Technical Implementation

```java
@EventHandler
public void onCreatureSpawn(CreatureSpawnEvent event) {
    // Only affect dungeon worlds
    if (!isDungeonWorld(event.getLocation().getWorld())) {
        return; // Allow normal spawning in other worlds
    }
    
    // Allow plugin-controlled spawning only
    CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
    if (reason == CreatureSpawnEvent.SpawnReason.CUSTOM || 
        reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG ||
        reason == CreatureSpawnEvent.SpawnReason.DISPENSE_EGG ||
        reason == CreatureSpawnEvent.SpawnReason.COMMAND) {
        return; // Allow controlled spawning
    }
    
    // Block all natural spawning
    event.setCancelled(true);
}
```

## Debugging & Monitoring

### Debug Logging
Enable detailed spawn blocking logs:
```yaml
debug:
  log-blocked-spawns: true  # Logs all blocked spawn attempts
```

When enabled, logs show:
```
[INFO] Blocked natural ZOMBIE spawn in dungeon world dungeon_world (reason: NATURAL)
[INFO] Blocked natural SKELETON spawn in dungeon world dungeon_world (reason: CHUNK_GEN)
```

### Performance Optimization
- **Efficient World Detection**: Uses cached Set lookup instead of stream operations
- **Early Return**: Exits immediately for non-dungeon worlds
- **Minimal Processing**: Only processes events in dungeon worlds

## World Import Compatibility

### Existing Worlds
When importing existing dungeon worlds:
1. **Detection**: Checks if world folder exists before creation
2. **Import**: Uses `mv import <world> NORMAL` instead of creation
3. **Configuration**: Applies all spawn/weather/PvP settings to imported worlds
4. **Mob Cleanup**: Event handler still prevents natural spawning

### Logging
Clear distinction between operations:
- `"Imported existing world 'dungeon_world' using Multiverse-Core"`
- `"Created new flat world 'dungeon_world' using Multiverse-Core with settings: 2;7,2x3,2;1;"`

## Benefits

### For Server Performance
- ✅ **Reduced Entity Count**: No unwanted mobs spawning
- ✅ **Predictable Lag**: Only intended dungeon entities load
- ✅ **Memory Efficient**: Flat worlds generate faster

### For Gameplay Experience
- ✅ **Pure Dungeon Experience**: Only intended mobs appear
- ✅ **Consistent Difficulty**: No random spawns affecting balance
- ✅ **Clean Environment**: Flat terrain perfect for custom dungeons
- ✅ **Admin Control**: Admins can still spawn mobs via commands/eggs

### For Development
- ✅ **Reliable Testing**: Consistent environment for dungeon testing
- ✅ **Controlled Spawning**: Only plugin-managed entities appear
- ✅ **Easy Building**: Flat worlds ideal for dungeon construction

## Configuration Example

```yaml
world-creation:
  world-type: "FLAT"
  flat-settings: "2;7,2x3,2;1;"  # Custom: bedrock, stone, dirt, grass
  auto-create-worlds: true
  prefer-multiverse: true
  disable-natural-spawning: true
  disable-weather: true
  disable-pvp: true
  set-time-noon: true

debug:
  log-blocked-spawns: false  # Set to true for debugging

# Alternative flat settings:
# "3;minecraft:bedrock,59*minecraft:stone,3*minecraft:dirt,minecraft:grass_block;minecraft:plains;"
# (More modern format for newer Minecraft versions)
```

This ensures completely controlled dungeon environments with flat terrain and no unwanted mob interference!
