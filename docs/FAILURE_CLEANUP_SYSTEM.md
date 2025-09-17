# Dungeon Failure Cleanup System

## Overview
The plugin now automatically handles dungeon failures when all players in a dungeon die, properly cleaning up bosses and mobs to prevent them from lingering.

## How It Works

### Player Death Detection
When a player dies in a dungeon, the system:
1. **Checks all players** in that specific dungeon
2. **Determines if all are dead** by checking if they're offline, dead, or no longer in the dungeon
3. **Triggers cleanup** if no living players remain

### Automatic Cleanup Process
When all players die in a dungeon:

1. **Visual Effects**: Plays failure particle effects at the dungeon center
2. **Entity Removal**: Removes ALL mobs and bosses from the dungeon world
3. **State Cleanup**: Clears all tracking data for that dungeon instance
4. **Player Teleportation**: Teleports any remaining players back to the main world
5. **Reset Timers**: Clears dungeon start times and wave states

### Technical Implementation

#### Entity Removal Method
```java
private void removeAllDungeonEntities(String dungeonKey) {
    // Searches all worlds for entities belonging to this dungeon
    for (World world : Bukkit.getWorlds()) {
        world.getLivingEntities().removeIf(entity -> {
            UUID entityId = entity.getUniqueId();
            // Check if entity belongs to this dungeon (mob or boss)
            if (mobToDungeon.get(entityId).equals(dungeonKey) || 
                bossToDungeon.get(entityId).equals(dungeonKey)) {
                entity.remove(); // Remove from world
                return true;
            }
            return false;
        });
    }
}
```

#### Data Cleanup
- **Mob Tracking**: Clears `mobToDungeon` entries
- **Boss Tracking**: Clears `bossToDungeon` entries  
- **Player Lists**: Removes `activeDungeonPlayers` for the dungeon
- **Mob Counts**: Resets `dungeonMobCounts`
- **Wave States**: Clears any wave progression data
- **Test Mode**: Removes test run flags

## Player Experience

### Death Sequence
1. Player dies in dungeon
2. System checks if other players are alive
3. If all dead: **"All players have died! Dungeon failed."** message
4. Dramatic failure effects play
5. All dungeon entities disappear
6. Players are teleported to main world spawn

### Benefits
- **No Lingering Mobs**: Prevents bosses/mobs from staying in failed dungeons
- **Clean Reset**: Each new dungeon attempt starts fresh
- **Resource Efficiency**: Removes unused entities to prevent server lag
- **Clear Feedback**: Players know exactly when a dungeon has failed

## Commands Enhanced

### New Command Added
- **`/dungeon edit <name>`**: Direct access to dungeon property editor
  - Added to help text: `/dungeon edit <name> - Edit dungeon properties`
  - Requires `bubbledungeons.admin` permission
  - Opens the comprehensive dungeon editing GUI

## Configuration Impact

No additional configuration required - the cleanup system works automatically based on:
- `main-world` setting (for player teleportation)
- Existing particle effect settings
- Standard dungeon tracking mechanisms

## Logging

The system provides clear logging:
- `"Dungeon <name> failed - all players died"`
- `"Removed all entities from failed dungeon: <name>"`

This ensures administrators can monitor dungeon failures and cleanup operations.

## Compatibility

Works seamlessly with:
- ✅ Existing dungeon mechanics
- ✅ Boss speed reduction system  
- ✅ GUI editing system
- ✅ World import fixes
- ✅ All advancement tracking
- ✅ Multiverse integration
