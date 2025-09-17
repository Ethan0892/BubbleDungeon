# BubbleDungeons

Lightweight configurable dungeon system for Purpur 1.21.4.

## Core Features
* Multiple dungeons defined in `config.yml` (world, spawn location, mobs, difficulty).
* Auto world creation (deferred) per dungeon world name.
* Custom mobs (vanilla or MythicMobs via `mythicId`) with: name, hearts, damage, speed, slime size.
* Scaling & (optional) wave system (split mob list into two waves) with player-count scaling.
* Completion flow: broadcast, optional advancement (UltimateAdvancementAPI), rewards, loot, teleport back.
* HUD boss bar + placeholders (PlaceholderAPI) for active status/timer.
* Economy (Vault) money & command rewards (optional toggle).
* Weighted loot tables with roll count.
* Admin GUI to create dungeons & edit mobs; chat prompts for values.
* Undo stack (last 10 config snapshots) for safe edits.
* Test run mode (spawns mobs, no rewards) & Safe Mode (no spawning).
* Join menu GUI listing dungeons.
* Stats tracking (runs & average time) - in-memory.

## Commands
`/dungeon join <dungeon>` Join/start a dungeon.
`/dungeon menu` Open join GUI.
`/dungeon leave` Leave and return to main world.
`/dungeon admin` Open admin GUI (create/edit).
`/dungeon reload` Reload config & worlds.
`/dungeon safemode` Toggle Safe Mode (admin).
`/dungeon testrun <dungeon>` Spawn mobs without rewards.
`/dungeon undo` Revert last config snapshot.
`/dungeon stats [dungeon]` View run stats.
`/dungeon forcejoin <player> <dungeon>` Force send a player (portal / automation integration). Alias: `send`.

## Permissions
`bubbledungeons.use` Basic usage (default true)
`bubbledungeons.admin` Admin actions
`bubbledungeons.editor` GUI editing
`bubbledungeons.reward` Receive rewards (default true)
`bubbledungeons.forcejoin` Use /dungeon forcejoin

## Config Notes
* Health values in config are in hearts (2 health points per heart).
* Feature toggles section controls: boss-bar, scale-with-players, actionbar-progress, show-timer, economy-rewards, loot-rewards, wave-system, placeholderapi.
* Loot tables allow item stacks or commands; amounts roll between min/max.

## Optional Integrations
| Plugin | Purpose |
| ------ | ------- |
| MythicMobs | Use custom Mythic mobs (`mythicId`) |
| UltimateAdvancementAPI | Custom advancement on completion |
| PlaceholderAPI | Dynamic placeholders (timer, dungeon, active) |
| Vault | Economy payouts |

All are soft depends—plugin functions without them.

## Building
```
./gradlew build
```
Jar output: `build/libs/BubbleDungeons-<version>.jar`

## Deploying to a Server
1. Provide your server directory path (must contain a `plugins` folder or one will be created).
2. Run:
```
./gradlew deployPlugin -PserverDir="C:/Full/Path/To/YourServer"
```
3. Restart / reload your server.

If you prefer manual: copy the jar from `build/libs/` into `server/plugins/`.

## Advanced Portals Integration
Configure your portal to run a console command (Advanced Portals supports command execution). Use:
```
dungeon forcejoin <player> <dungeon>
```
The `<player>` placeholder is replaced by the portal's player variable (e.g., `%p%`). Example portal command:
```
dungeon forcejoin %p% crypt
```

## Roadmap Ideas
* Persist stats to file.
* More granular progress bar (remaining mobs vs total).
* Difficulty-based reward scaling.
* Per-dungeon custom join menu icons.

## License
Proprietary / All rights reserved (adjust to your needs).

---
Reflection is used for optional hooks to avoid hard dependencies.
