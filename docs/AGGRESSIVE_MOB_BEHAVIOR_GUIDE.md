# ⚔️ Aggressive Mob Behavior Guide

## 📋 Overview
The BubbleDungeon plugin now features aggressive mob behavior that makes dungeon encounters more challenging and engaging. Mobs will actively pursue players regardless of distance, and the Ender Dragon will stay focused on combat instead of flying away.

## 🎯 Key Features

### **Enhanced Mob Targeting**
- **Extended Follow Range**: Mobs can detect players up to 128 blocks away (configurable)
- **Persistent Targeting**: Mobs automatically retarget the nearest player if they lose focus
- **No Despawning**: Dungeon mobs never despawn, ensuring consistent encounters
- **Smart Detection**: Mobs always know where players are in the dungeon

### **Ender Dragon Improvements**
- **No Flying Away**: Dragon stays in the combat area instead of flying to distant locations
- **Aggressive Phases**: Forces dragon into combat phases instead of passive hovering
- **Player Focused**: Dragon always targets the nearest player
- **Distance Control**: Teleports dragon back if it gets too far from players

### **Monster Enhancements**
- **Increased Aggression**: All monsters become more hostile and focused
- **Improved AI**: Better targeting and pursuit behavior
- **Persistent Pursuit**: Mobs won't give up chasing players

## ⚙️ Configuration

### **Master Toggle**
```yaml
features:
  aggressive-mobs: true  # Enable/disable aggressive mob behavior
```

### **Detailed Settings**
```yaml
aggressive-mobs:
  enabled: true                   # Enable aggressive mob behavior
  follow-range: 128               # How far mobs can detect and follow players (blocks)
  targeting-interval: 40          # How often mobs retarget players (ticks, 20 = 1 second)
  dragon-behavior:
    prevent-flying-away: true     # Prevent Ender Dragons from flying away
    max-distance: 50              # Max distance dragon can be from players before teleporting back
    aggressive-phases: true       # Force dragon into aggressive combat phases
    targeting-interval: 20        # How often dragon retargets (ticks)
```

## 🐲 Ender Dragon Specific Behavior

### **Anti-Escape System**
- **Phase Control**: Dragon is forced into `CIRCLING` phase instead of flying away
- **Distance Monitoring**: Tracks dragon distance from players every second
- **Auto-Teleport**: If dragon exceeds max distance (50 blocks), it teleports back above nearest player
- **Continuous Targeting**: Dragon retargets nearest player every second

### **Combat Phases**
**Aggressive Phases (Enabled):**
- `CIRCLING` - Dragon flies around players aggressively
- Prevents `HOVER` - Stops passive floating
- Prevents `FLY_TO_PORTAL` - Stops escaping to end portal

**Benefits:**
- Dragon stays engaged in combat
- No more chasing the dragon across the map
- Consistent encounter experience
- Fair and challenging fights

## 🗡️ Regular Mob Behavior

### **Enhanced Targeting System**
- **Extended Detection**: 128-block follow range (vs normal 16-32 blocks)
- **Active Retargeting**: Checks for new targets every 2 seconds
- **Nearest Player Priority**: Always targets closest player in dungeon
- **Cross-Room Tracking**: Can pursue players through walls and obstacles

### **Mob-Specific Improvements**
**Zombies:**
- Forced to adult form (more aggressive than babies)
- Enhanced targeting behavior

**PigZombies (Zombie Pigmen):**
- Automatically set to angry state
- Immediate hostility toward players

**Wolves:**
- Set to angry state for consistent aggression

**All Monsters:**
- Persistent (won't despawn)
- Extended follow range
- Improved targeting AI

## 🎮 Gameplay Impact

### **For Players**
- **Challenging Encounters**: Mobs won't lose interest or wander away
- **No Safe Hiding**: Extended follow range means fewer safe spots
- **Consistent Difficulty**: Predictable mob behavior across all encounters
- **Fair Dragon Fights**: Dragon can't escape, ensuring proper boss battles

### **For Dungeon Design**
- **Reliable Encounters**: Mobs behave predictably for encounter design
- **Extended Combat Areas**: Larger dungeons remain challenging
- **Boss Battle Integrity**: Dragons and bosses stay engaged
- **Tactical Positioning**: Players must use strategy, not exploits

### **For Multiplayer Teams**
- **Team Focus**: Mobs target nearest player, encouraging teamwork
- **Dynamic Combat**: Aggro switches between team members naturally
- **Coordinated Fights**: Teams can plan around predictable mob behavior
- **Shared Challenges**: All players face equal mob attention

## 🔧 Technical Implementation

### **Follow Range Enhancement**
```java
// Applied to all dungeon mobs
applyAttribute(entity, "GENERIC_FOLLOW_RANGE", 128.0);
```

### **Persistent Targeting Loop**
- Runs every 40 ticks (2 seconds) by default
- Finds nearest player in same dungeon
- Updates mob target automatically
- Handles different entity types appropriately

### **Dragon Behavior Loop**
- Runs every 20 ticks (1 second) for dragons
- Monitors dragon phase and distance
- Forces aggressive phases
- Teleports dragon if too far away

### **Smart Mob Detection**
- Uses `instanceof` checks for proper targeting
- Handles `Mob` interface for most creatures
- Special handling for `EnderDragon` entities
- Graceful fallback for unsupported mobs

## 📊 Performance Considerations

### **Optimized Scheduling**
- **Efficient Intervals**: Configurable timing to balance performance
- **Smart Checks**: Only processes valid, living entities
- **Dungeon-Specific**: Only affects mobs in active dungeons
- **Player Presence**: Skips processing when no players in dungeon

### **Resource Usage**
- **Minimal CPU**: Lightweight distance calculations
- **Memory Friendly**: No additional data storage
- **Network Efficient**: Reduces mob wandering and pathfinding
- **Scalable**: Performance scales with number of active dungeons

## 🛠️ Troubleshooting

### **Common Issues**

**Mobs Not Targeting Players:**
- Check `features.aggressive-mobs: true` is enabled
- Verify `aggressive-mobs.enabled: true`
- Ensure players are in the same world as mobs
- Check if follow range is sufficient

**Dragon Flying Away:**
- Verify `dragon-behavior.prevent-flying-away: true`
- Check `dragon-behavior.max-distance` setting
- Ensure targeting interval isn't too long

**Performance Issues:**
- Increase `targeting-interval` to reduce checks
- Increase `dragon-behavior.targeting-interval`
- Reduce `follow-range` if needed

### **Debug Configuration**
```yaml
# Add for troubleshooting
debug:
  log-mob-targeting: true        # Log targeting events
  log-dragon-behavior: true     # Log dragon phase changes
  log-aggressive-actions: true  # Detailed behavior logging
```

## 🎯 Benefits Summary

### **🏹 Enhanced Combat**
- No more mobs losing interest mid-fight
- Dragons can't escape to unreachable areas
- Consistent challenge across all encounters
- Improved AI makes fights more engaging

### **⚔️ Strategic Gameplay**
- Players must use tactics instead of exploits
- Team coordination becomes more important
- Positioning and movement matter more
- Fair and balanced boss encounters

### **🎪 Encounter Design**
- Reliable mob behavior for dungeon creators
- Predictable encounters for balanced difficulty
- Extended combat areas remain challenging
- Professional-quality boss fights

### **🔄 Multiplayer Balance**
- Fair aggro distribution among team members
- Natural target switching for dynamic combat
- Coordinated team strategies become viable
- Equal challenge for all party members

This aggressive mob behavior system transforms dungeon encounters from potentially exploitable encounters into engaging, challenging battles that require skill and strategy! ⚔️🐲💀
