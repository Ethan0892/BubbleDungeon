# 🏆 BubbleDungeon Advancement System Guide

## ✨ Overview
The BubbleDungeon plugin now features a comprehensive advancement system that works both with UltimateAdvancementAPI and as a standalone system with custom notifications. The system tracks player progress and provides rewarding feedback for dungeon achievements.

## 🎯 Advancement Types

### 1. **Dungeon Completion Advancements**
- **Trigger**: Complete any dungeon successfully
- **Title**: Customizable per dungeon (configured in dungeon section)
- **Description**: Customizable per dungeon
- **Icon**: Diamond Sword (UltimateAdvancementAPI)
- **Multiplayer**: All participants receive advancement (configurable)

### 2. **Participation Advancement** 
- **Title**: "Dungeon Explorer"
- **Description**: "Enter your first dungeon!"
- **Trigger**: First time entering any dungeon
- **Icon**: Wooden Sword (UltimateAdvancementAPI)

### 3. **First Boss Defeat**
- **Title**: "Boss Slayer" 
- **Description**: "Defeat your first dungeon boss!"
- **Trigger**: Defeating your first boss in any dungeon
- **Icon**: Iron Sword (UltimateAdvancementAPI)

### 4. **All Bosses Defeated**
- **Title**: "Dungeon Master"
- **Description**: "Defeat all 3 dungeon bosses!"
- **Trigger**: Defeating bosses in all configured dungeons
- **Icon**: Netherite Sword (UltimateAdvancementAPI)

## ⚙️ Configuration Options

### Feature Control
```yaml
features:
  advancement-system: true        # Enable/disable entire advancement system
  advancement-notifications: true # Enable custom notifications when UltimateAdvancementAPI unavailable
  advancement-sounds: true        # Enable advancement sounds
  advancement-multiplayer: true   # Grant advancements to all dungeon participants
```

### Per-Dungeon Advancement Settings
```yaml
dungeons:
  sample:
    # ... other dungeon settings ...
    advancement:
      title: "&6Dungeon Conqueror"
      description: "&7You conquered the sample dungeon!"
```

## 🔧 System Architecture

### Dual System Support
1. **Primary**: UltimateAdvancementAPI Integration
   - Uses UltimateAdvancementAPI when available
   - Creates proper advancement entries
   - Integrates with advancement GUI
   - Full advancement tree support

2. **Fallback**: Custom Notification System
   - Used when UltimateAdvancementAPI is not installed
   - Custom title/chat/actionbar notifications
   - Achievement-style visual presentation
   - Sound effects and visual feedback

### Multiplayer Support
- **Full Multiplayer**: All dungeon participants receive advancements
- **Single Player**: Only first player receives advancement (configurable)
- **Progress Tracking**: Individual player progress saved across sessions
- **Session Persistence**: Progress maintained through server restarts

## 🎮 Commands

### Player Commands
```
/dungeon progress          # View your advancement progress
```

### Admin Commands  
```
/dungeon testadv <type>    # Test advancement system (debug)
```

**Test Types:**
- `completion` - Test dungeon completion advancement
- `participation` - Test participation advancement  
- `firstboss` - Test first boss defeat advancement
- `allbosses` - Test all bosses defeated advancement

## 🎨 Visual Presentation

### UltimateAdvancementAPI Mode
- Standard Minecraft advancement toast notification
- Advancement tab integration
- Progress tracking in advancement GUI
- Achievement tree structure

### Custom Notification Mode
```
§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬
§6§l                           ACHIEVEMENT UNLOCKED
                    
                    §6✦ Dungeon Conqueror §6✦
                         You conquered the sample dungeon!
                         
§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬
```

- **Title Display**: Large title with achievement name and description
- **Chat Notification**: Formatted achievement announcement
- **Action Bar**: Scrolling achievement notification
- **Sound Effect**: Challenge completion sound (configurable)

## 🔄 Progress Tracking

### Automatic Tracking
- **Dungeon Participation**: Tracks first-time dungeon entries
- **Boss Defeats**: Records which bosses each player has defeated
- **Completion History**: Maintains record of completed dungeons
- **Achievement Status**: Prevents duplicate advancement grants

### Data Persistence
```yaml
advancement-progress:
  boss-defeats:
    player-uuid-here:
      - "sample"
      - "crypt"
      - "volcano"
  participation:
    player-uuid-here:
      - "sample"
      - "crypt"
  all-bosses-defeated:
    - "player-uuid-who-defeated-all"
```

## 🚀 Getting Started

### 1. Installation Options

**Option A: With UltimateAdvancementAPI (Recommended)**
1. Install UltimateAdvancementAPI plugin
2. Install BubbleDungeon plugin
3. Advancements will appear in advancement GUI

**Option B: Standalone Mode**
1. Install BubbleDungeon plugin only
2. Custom notifications will be used automatically
3. Full advancement functionality available

### 2. Configuration
```yaml
# Enable all advancement features
features:
  advancement-system: true
  advancement-notifications: true
  advancement-sounds: true
  advancement-multiplayer: true

# Configure per-dungeon advancements
dungeons:
  your-dungeon:
    advancement:
      title: "&6Your Custom Title"
      description: "&7Your custom description!"
```

### 3. Testing
```
/dungeon testadv participation   # Test participation advancement
/dungeon testadv completion      # Test completion advancement
/dungeon testadv firstboss       # Test first boss advancement
/dungeon testadv allbosses       # Test all bosses advancement
```

## 🎯 Best Practices

### Achievement Design
- **Clear Titles**: Use descriptive, exciting titles
- **Motivating Descriptions**: Explain what the player accomplished
- **Progressive Difficulty**: Start easy, increase challenge
- **Meaningful Rewards**: Make achievements feel worthwhile

### Multiplayer Considerations
- **Team Recognition**: Enable multiplayer mode for team achievements
- **Individual Progress**: Maintain personal achievement records
- **Fair Distribution**: Ensure all contributors receive credit
- **Clear Communication**: Use announcements for major achievements

### Performance Optimization
- **Efficient Tracking**: Minimal database writes
- **Smart Caching**: In-memory progress tracking
- **Cleanup Management**: Automatic advancement data cleanup
- **Scalable Design**: Supports large player bases

## 🛠️ Troubleshooting

### Common Issues

**Advancements Not Appearing**
1. Check if advancement system is enabled in config
2. Verify UltimateAdvancementAPI installation (if using)
3. Test with `/dungeon testadv` commands
4. Check server console for advancement errors

**Duplicate Advancements**
1. Progress tracking prevents duplicates automatically
2. Clear player data if needed: delete advancement-progress section
3. Restart server to refresh advancement state

**Custom Notifications Not Showing**
1. Verify `advancement-notifications: true` in config
2. Check if `advancement-sounds: true` for audio feedback
3. Test with `/dungeon testadv` commands

**Multiplayer Issues**
1. Ensure `advancement-multiplayer: true` in config
2. All participants must be online during completion
3. Check if players have required permissions

### Debug Commands
```
/dungeon testadv <type>          # Test specific advancement type
/dungeon progress                # View current player progress
/dungeon reload                  # Reload configuration
```

## 🏆 Achievement Ideas

### Custom Dungeon Achievements
```yaml
dungeons:
  underwater-temple:
    advancement:
      title: "&b&lDeep Sea Explorer"
      description: "&7Conquered the depths of the ocean temple!"
      
  volcano-fortress:
    advancement:
      title: "&c&lLava Walker"
      description: "&7Survived the molten fortress!"
      
  sky-castle:
    advancement:
      title: "&e&lCloud Breaker"
      description: "&7Reached the castle in the clouds!"
```

This advancement system provides a complete achievement experience that enhances player engagement and provides meaningful progression tracking in your dungeon server!
