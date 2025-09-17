# Dungeon Editing GUI System

## Overview
The BubbleDungeon plugin now includes a comprehensive in-game GUI system for editing dungeon properties without needing to manually edit config files.

## How to Access

### Method 1: Admin GUI
1. Use `/dungeon admin` to open the main admin interface
2. **Left-click** on any dungeon to edit mobs
3. **Right-click** on any dungeon to open the dungeon editor

### Method 2: Direct Command
- Use `/dungeon edit <dungeon_name>` to directly open the editor for a specific dungeon

## Dungeon Editor Features

The new DungeonEditGUI provides the following editing capabilities:

### Basic Settings
- **World Name**: Change which world the dungeon uses
- **Base Location**: Set the main dungeon coordinates to your current position
- **Difficulty**: Adjust the dungeon difficulty level (1-10)

### Spawn Locations
- **Player Spawn**: Configure where players teleport when joining the dungeon
  - Left-click to set to your current location
  - Right-click to manually enter coordinates (x,y,z format)
- **Boss Spawn**: Configure where the boss spawns in the dungeon
  - Left-click to set to your current location  
  - Right-click to manually enter coordinates (x,y,z format)

### Content Management
- **Mobs**: Opens the existing mob editor interface
- **Boss**: Edit boss properties (type, name, health, damage, speed, size, mythic ID)

### Advancement Settings
- **Title**: Edit the advancement title that players receive
- **Description**: Edit the advancement description

### Additional Actions
- **Save & Test**: Teleports you to the dungeon to test your changes
- **Delete**: Permanently remove the dungeon (requires shift-click to confirm)
- **Back**: Return to the main admin menu

## New Config Format

The plugin now saves additional spawn coordinate information:

```yaml
dungeons:
  example-dungeon:
    world: "dungeon_world"
    location:
      x: 100
      y: 64
      z: 200
    boss-spawn:
      x: 150
      y: 64
      z: 200
    player-spawn:
      x: 50
      y: 64
      z: 180
    difficulty: "5"
    advancement:
      title: "Dungeon Master"
      description: "Complete the example dungeon"
    # ... mobs and boss configuration
```

## Dynamic Boss Speed System

When editing bosses, the system automatically:
- Reduces boss speed to 70% of average minion speed when minions are present
- Maintains minimum speed of 0.1 for boss mobility
- Applies normal speed when no minions are configured

## Permissions

All editing features require the `bubbledungeons.admin` permission.

## Tips for Use

1. **Set spawn locations in-game**: Stand where you want players or bosses to spawn, then left-click the corresponding option
2. **Test frequently**: Use the "Save & Test" button to immediately see your changes
3. **Coordinate format**: When manually entering coordinates, use the format: `100,64,200`
4. **Boss configuration**: Format boss data as: `type,name,health,damage,speed,size,mythicId`
5. **Backup configs**: The system automatically creates config snapshots for undo functionality

This GUI system makes dungeon creation and modification much more intuitive and accessible for server administrators.
