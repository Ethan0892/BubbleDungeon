# BubbleDungeons - Shared World Configuration

## Overview
BubbleDungeons now supports multiple dungeons in a single world for improved server performance and easier management.

## Configuration
All 3 sample dungeons are now configured to use the same world (`dungeon_world`) but with different coordinates:

### Dungeon Locations
- **Sample Dungeon**: `x: 0, y: 64, z: 0` 
- **Crypt Dungeon**: `x: 200, y: 64, z: 0`
- **Volcano Dungeon**: `x: 0, y: 64, z: 200`

### Benefits
1. **Performance**: Only 1 world loaded instead of 3
2. **Memory**: Reduced server memory usage
3. **Management**: Easier world backups and maintenance
4. **Resource**: Lower disk space usage

### Spacing
Dungeons are spaced 200 blocks apart to prevent interference:
- Mob spawning range: 6 blocks (configurable)
- Buffer zone: ~190 blocks between dungeons
- Safe distance: No overlap or interference

### Customization
To add more dungeons to the same world:
1. Choose coordinates at least 50+ blocks from existing dungeons
2. Use the same `world: dungeon_world` setting
3. Ensure adequate spacing for your spawn radius

### World Settings
The shared `dungeon_world` will have these optimizations:
- Type: FLAT (configurable)
- Natural spawning: DISABLED
- Time: Fixed at noon
- Weather: DISABLED
- PvP: DISABLED
- Block protection: ENABLED

### Performance Optimizations
- World names cached for O(1) lookup performance
- Unique world creation (no duplicates)
- Efficient world detection for block protection
- Optimized mob tracking per dungeon area

## Migration from Separate Worlds
If upgrading from separate worlds:
1. Backup your server
2. Update the config.yml with new coordinates  
3. Delete old individual world folders (optional)
4. Restart server - plugin will create the single shared world

The plugin automatically handles the transition seamlessly!
