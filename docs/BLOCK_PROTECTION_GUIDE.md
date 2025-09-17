# 🛡️ Dungeon Block Protection Guide

## 📋 Overview
The BubbleDungeon plugin now features comprehensive block protection that prevents mobs (including destructive ones like Ender Dragons, Creepers, and Ghasts) from destroying or modifying dungeon structures. This ensures that dungeon environments remain intact regardless of the types of mobs spawned.

## 🚫 Protection Features

### **Mob Griefing Prevention**
- **Ender Dragon**: Cannot destroy blocks with its attacks or body
- **Creepers**: Explosions deal damage but don't destroy blocks
- **Ghasts**: Fireballs damage entities but don't destroy blocks
- **TNT/Minecart TNT**: Explosions contained to prevent block damage
- **Endermen**: Cannot pick up or place blocks in dungeons
- **Withers**: Explosions and projectiles don't destroy blocks

### **Environmental Protection**
- **All Explosions**: Entity and block explosions prevented
- **Projectile Damage**: Fireballs, arrows, etc. don't damage blocks
- **Block Changes**: Prevents all entity-caused block modifications
- **Natural Events**: Beds exploding in Nether/End dimensions contained

## ⚙️ Configuration

### **Master Toggle**
```yaml
features:
  block-protection: true  # Enable/disable all block protection
```

### **Detailed Settings**
```yaml
block-protection:
  prevent-explosions: true        # Prevent entity explosions (creepers, ghasts, ender dragons)
  prevent-block-changes: true     # Prevent entities from changing blocks (endermen, ender dragons)
  prevent-projectile-damage: true # Prevent projectiles from damaging blocks
  allow-player-building: false    # Allow players to build in dungeons (not recommended)
```

## 🔧 Protection Types

### **1. Entity Explosions (`prevent-explosions`)**
**Protected Against:**
- Creeper explosions
- Ghast fireballs
- Ender Dragon attacks
- TNT explosions
- Wither explosions
- Fireball impacts

**How it Works:**
- Cancels explosion events that would destroy blocks
- Preserves entity damage from explosions
- Clears block list to prevent destruction

### **2. Entity Block Changes (`prevent-block-changes`)**
**Protected Against:**
- Endermen picking up/placing blocks
- Ender Dragon destroying blocks by touching them
- Silverfish hiding in blocks
- Sheep eating grass
- Zombies breaking doors

**How it Works:**
- Intercepts `EntityChangeBlockEvent`
- Prevents all entity-initiated block modifications
- Maintains dungeon structure integrity

### **3. Projectile Block Damage (`prevent-projectile-damage`)**
**Protected Against:**
- Ghast fireballs hitting blocks
- Arrow impacts on blocks
- Thrown projectiles
- Splash potions affecting blocks

**How it Works:**
- Monitors `ProjectileHitEvent` for block hits
- Cancels events that would damage blocks
- Allows projectile damage to entities

### **4. Block Explosions (`prevent-explosions`)**
**Protected Against:**
- TNT block explosions
- Bed explosions in Nether/End
- Respawn anchor explosions
- End crystal explosions

**How it Works:**
- Intercepts `BlockExplodeEvent`
- Prevents block-initiated explosions
- Clears destruction list

## 👨‍💼 Admin Features

### **Admin Override**
- Admins with `bubbledungeons.admin` permission can build in dungeons
- Useful for dungeon maintenance and construction
- Automatic detection of admin permissions

### **Player Building Control**
```yaml
block-protection:
  allow-player-building: false  # Toggle player building permissions
```

**When Enabled (`true`):**
- Players can place and break blocks in dungeons
- Useful for construction-based dungeon challenges
- Mob protection still applies

**When Disabled (`false`) - Recommended:**
- Players cannot modify dungeon structures
- Maintains intended dungeon design
- Prevents griefing and accidental damage

## 🎮 Gameplay Impact

### **For Players**
- **Consistent Environment**: Dungeons remain unchanged between runs
- **Fair Challenges**: Terrain doesn't become easier/harder due to destruction
- **Visual Appeal**: Dungeons maintain their designed appearance
- **No Fall Damage**: Destroyed floors won't cause unexpected deaths

### **For Dungeon Designers**
- **Design Integrity**: Structures remain as intended
- **Creative Freedom**: Can use destructive mobs without worry
- **Balanced Encounters**: Consistent terrain for fair gameplay
- **Reusability**: Dungeons don't require rebuilding after boss fights

### **For Server Admins**
- **Low Maintenance**: No need to constantly repair dungeons
- **Performance**: Reduced block updates and regeneration
- **Flexibility**: Per-feature configuration options
- **Compatibility**: Works with existing dungeon designs

## 🐲 Ender Dragon Specific Protection

### **Attack Prevention**
- **Body Damage**: Dragon touching blocks doesn't destroy them
- **Explosion Attacks**: Dragon explosions deal entity damage only
- **Charge Attacks**: Physical contact with blocks prevented
- **Death Explosion**: Final explosion contained to prevent block damage

### **Behavior Preservation**
- **Movement**: Dragon can still fly and move normally
- **Combat**: Attacks against players/mobs work normally
- **AI**: Pathfinding and behavior unchanged
- **Effects**: Particle effects and sounds preserved

## 📊 Performance Considerations

### **Optimized Event Handling**
- **Efficient Checks**: Quick world type detection
- **Minimal Overhead**: Only processes events in dungeon worlds
- **Event Cancellation**: Prevents unnecessary block updates
- **Memory Friendly**: No additional data storage required

### **Resource Usage**
- **CPU Impact**: Minimal - only event listeners
- **Memory Usage**: No additional memory requirements
- **Network Traffic**: Reduced due to fewer block updates
- **Storage**: No additional disk usage

## 🔄 Compatibility

### **Mob Compatibility**
- ✅ **Vanilla Mobs**: All vanilla destructive mobs supported
- ✅ **MythicMobs**: Works with custom MythicMobs creatures
- ✅ **Plugin Mobs**: Compatible with most mob plugins
- ✅ **Spawner Mobs**: Handles spawner-generated mobs

### **Plugin Compatibility**
- ✅ **WorldGuard**: Complements region protection
- ✅ **GriefPrevention**: Works alongside land protection
- ✅ **CoreProtect**: Block logging still functions
- ✅ **Economy Plugins**: No conflict with reward systems

### **World Compatibility**
- ✅ **Multiverse**: Works with Multiverse world management
- ✅ **Custom Worlds**: Supports any world type
- ✅ **Dimension**: Works in Nether, End, and custom dimensions
- ✅ **Terrain**: Compatible with all terrain types

## 🛠️ Troubleshooting

### **Common Issues**

**Mobs Still Destroying Blocks:**
- Check `features.block-protection: true` is enabled
- Verify specific protection type is enabled
- Ensure world is recognized as dungeon world

**Players Can't Build:**
- Check `allow-player-building` setting
- Verify admin permissions for admins
- Confirm `features.block-protection` is enabled

**Explosions Not Working:**
- Entity damage still occurs with protection
- Visual/sound effects preserved
- Only block destruction is prevented

### **Debug Configuration**
```yaml
# Add to config.yml for troubleshooting
debug:
  log-blocked-explosions: true   # Log prevented explosions
  log-blocked-changes: true      # Log prevented block changes
  log-protection-events: true    # Detailed protection logging
```

## 📈 Benefits Summary

### **🏗️ Structural Integrity**
- Dungeons maintain their designed layout
- No unexpected terrain changes during combat
- Consistent challenge difficulty

### **🎯 Balanced Gameplay**
- Fair encounters for all players
- Predictable dungeon layouts
- No advantage/disadvantage from destroyed terrain

### **🔧 Administrative Ease**
- Reduced maintenance requirements
- No need for constant dungeon repairs
- Set-and-forget protection system

### **⚡ Performance Optimization**
- Fewer block updates reduce server load
- Reduced network traffic from block changes
- Minimal CPU overhead from event handling

This comprehensive block protection system ensures that your dungeons remain pristine and challenging, regardless of the destructive capabilities of the mobs within them! 🛡️⚔️🐲
