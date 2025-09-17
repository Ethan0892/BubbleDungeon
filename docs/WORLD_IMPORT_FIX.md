# World Import Fix

## Problem Fixed
The plugin was attempting to create worlds with Multiverse even when they already existed in the server's world folder, causing the error:
```
World 'dungeon_world' already exists in server folders! Type '/mv import dungeon_world <environment>' if you wish to import it.
```

## Solution Implemented
Updated the `tryCreateWorld()` method to:

1. **Check if world folder exists first** - Before attempting to create a world, check if a folder with that name already exists in the server's worlds directory
2. **Import existing worlds** - If the world folder exists, use `mv import <worldname> NORMAL` instead of `mv create`
3. **Create new worlds** - Only use `mv create` when the world doesn't exist yet
4. **Proper logging** - Added clear logging messages to distinguish between importing existing worlds and creating new ones

## Technical Details

### Before (causing errors):
```java
Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
    "mv create " + name + " NORMAL -t " + worldType);
```

### After (smart handling):
```java
File worldFolder = new File(Bukkit.getWorldContainer(), name);
if (worldFolder.exists() && worldFolder.isDirectory()) {
    // World exists in filesystem, import it instead of creating
    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
        "mv import " + name + " NORMAL");
    getLogger().info("Imported existing world '" + name + "' using Multiverse-Core");
} else {
    // Create new world with specified environment type
    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
        "mv create " + name + " NORMAL -t " + worldType);
    getLogger().info("Created new world '" + name + "' using Multiverse-Core");
}
```

## Result
- No more "world already exists" errors
- Existing dungeon worlds are properly imported and configured
- New worlds are created when needed
- Clear logging shows whether worlds were imported or created
- All world configuration (PvP, weather, spawning, etc.) still applies correctly

This fix resolves the issue where players couldn't join dungeons because the world creation was failing due to existing world folders.
