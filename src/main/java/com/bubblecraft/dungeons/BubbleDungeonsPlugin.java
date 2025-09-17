package com.bubblecraft.dungeons;

import com.bubblecraft.dungeons.effects.ParticleEffects;
import com.bubblecraft.dungeons.hook.PlaceholderHook;
import com.bubblecraft.dungeons.hud.DungeonProgressHUD;
import com.bubblecraft.dungeons.reward.LootTable;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Flying;
import org.bukkit.entity.Mob;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;

// UltimateAdvancementAPI imports
import com.fren_gor.ultimateAdvancementAPI.UltimateAdvancementAPI;
import com.fren_gor.ultimateAdvancementAPI.advancement.Advancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.BaseAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.RootAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementDisplay;
import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementFrameType;
import com.fren_gor.ultimateAdvancementAPI.events.PlayerLoadingCompletedEvent;
import com.fren_gor.ultimateAdvancementAPI.AdvancementTab;

import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;

// Adventure for clickable messages
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

public class BubbleDungeonsPlugin extends JavaPlugin implements Listener, TabCompleter {

    // Core data
    private final Map<String, DungeonDefinition> dungeons = new HashMap<>();
    private final Map<String, Set<UUID>> activeDungeonPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, String> mobToDungeon = new ConcurrentHashMap<>();
    private final Map<UUID, String> bossToDungeon = new ConcurrentHashMap<>();
    private final Map<String, UUID> dungeonToBoss = new ConcurrentHashMap<>();
    // Per-dungeon main boss BossBar and updater task tracking
    private final Map<String, BossBar> bossHealthBars = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> bossHealthTasks = new ConcurrentHashMap<>();
    // Milestone announcement tracking per dungeon (e.g., 75,50,25,10)
    private final Map<String, Set<Integer>> announcedBossMilestones = new ConcurrentHashMap<>();
    // Cooldown for the "boss protected by minions" message per player
    private final Map<UUID, Long> bossProtectedMsgCooldown = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private int spawnRadius;

    // Flags
    private boolean mythicMobsPresent;
    private boolean advancementApiPresent;
    private boolean placeholderPresent;
    private boolean vaultPresent;
    private boolean multiversePresent;
    private boolean startupPhase = true;
    private boolean safeMode = false;

    // Hooks / helpers
    private Economy economy;
    private DungeonProgressHUD hud;
    private ParticleEffects particleEffects;

    // Runtime tracking
    private final Map<String, Long> dungeonStartTime = new HashMap<>();
    private final Map<String, LootTable> lootTables = new HashMap<>();
    private final Map<String, Integer> dungeonRuns = new HashMap<>();
    private final Map<String, Long> dungeonTotalTime = new HashMap<>();
    private final Deque<String> undoSnapshots = new ArrayDeque<>();
    private final Map<String, Integer> waveState = new HashMap<>();
    private final Set<String> testRunMode = new HashSet<>();
    
    // Performance optimization: Cache mob counts per dungeon
    private final Map<String, Integer> dungeonMobCounts = new ConcurrentHashMap<>();
    
    // Performance optimization: Cache dungeon world names for fast lookup
    private final Set<String> dungeonWorldNames = new HashSet<>();
    
    // Security: Rate limiting and validation
    private final Map<UUID, Long> lastJoinAttempt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCommandUse = new ConcurrentHashMap<>();
    private static final long JOIN_COOLDOWN = 3000; // 3 seconds
    private static final long COMMAND_COOLDOWN = 500; // 0.5 seconds
    private static final int MAX_DUNGEON_NAME_LENGTH = 32;
    private static final int MAX_CONCURRENT_DUNGEONS = 10;
    // Extreme mode constants & pacing
    private static final double EXTREME_BOSS_HEALTH_MULTIPLIER = 10.0; // Boss health x10
    private static final double EXTREME_BOSS_DAMAGE_MULTIPLIER = 3.0;  // Boss damage x3
    private static final int GLOBAL_EXTRA_MINIONS = 10;                // +10 minions per dungeon spawn
    private static final long DUNGEON_REOPEN_DELAY_MS = 3600_000L;     // 1 hour delay before next dungeon opens
    // Competitive tracking
    private final Map<String, Map<UUID, Double>> dungeonDamageTracking = new ConcurrentHashMap<>();

    // Chat capture
    private final Map<UUID, java.util.function.Consumer<String>> chatInputs = new HashMap<>();
    
    // Player advancement tracking
    private final Map<UUID, Set<String>> playerBossDefeats = new ConcurrentHashMap<>(); // Track which bosses each player has defeated
    private final Map<UUID, Set<String>> playerDungeonParticipation = new ConcurrentHashMap<>(); // Track dungeon participation
    private final Set<UUID> allBossesDefeatedPlayers = new HashSet<>(); // Players who defeated all 3 bosses

    // UltimateAdvancementAPI fields
    private Object ultimateAdvancementApi; // UltimateAdvancementAPI instance
    private Object advancementTab; // AdvancementTab instance
    private Object rootAdvancement; // RootAdvancement instance
    private final Map<String, Object> dungeonAdvancements = new HashMap<>(); // Dynamic dungeon advancements
    
    // Dungeon Queue System
    private String currentOpenDungeon = null; // Currently open dungeon
    private int currentDungeonIndex = 0; // Current position in dungeon queue
    private List<String> dungeonQueue = new ArrayList<>(); // Queue order from config
    private long lastDungeonCompletion = 0; // Timestamp of last completion
    private boolean queueSystemEnabled = false; // Is queue system active
    private int queueTaskId = -1; // Task ID for queue management
    private final Map<String, Set<UUID>> pendingReturnPlayers = new ConcurrentHashMap<>(); // Players to return after wait

    private String prefix() { return color("&6&lBubble&bCraft&7 » &f"); }
    public String color(String s) { return s == null ? null : s.replace('&', '§'); }
    
    // Public getters for enhancement systems
    public Map<String, Set<UUID>> getActiveDungeonPlayers() { return activeDungeonPlayers; }

    @Override public void onEnable() {
        saveDefaultConfig();
        reloadLocal(false);
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("dungeon"), "Command dungeon not defined").setExecutor(this);
        getCommand("dungeon").setTabCompleter(this);
        mythicMobsPresent = Bukkit.getPluginManager().getPlugin("MythicMobs") != null;
        advancementApiPresent = Bukkit.getPluginManager().getPlugin("UltimateAdvancementAPI") != null;
        placeholderPresent = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        vaultPresent = Bukkit.getPluginManager().getPlugin("Vault") != null;
        multiversePresent = Bukkit.getPluginManager().getPlugin("Multiverse-Core") != null;
        if (placeholderPresent) try { new PlaceholderHook(this).register(); } catch (Throwable ignored) {}
        if (vaultPresent) setupEconomy();
        hud = new DungeonProgressHUD(this);
        particleEffects = new ParticleEffects(this); // Initialize particle effects system
        
        // Load advancement progress from config
        if (advancementApiPresent) {
            // Delay the setup to ensure the API is fully loaded
            getServer().getScheduler().runTaskLater(this, () -> {
                setupUltimateAdvancementAPI();
                loadAdvancementProgress();
            }, 20L); // Wait 1 second
        }
        
        getLogger().info("Loaded " + dungeons.size() + " dungeons" + (multiversePresent ? " (Multiverse-Core integration enabled)" : "") + ".");
        Bukkit.getScheduler().runTask(this, () -> { 
            ensureDungeonWorlds(); 
            initializeDungeonQueue(); // Initialize queue system after worlds are ready
            startupPhase = false; 
        });
    }

    @Override public void onDisable() { 
        // Save advancement progress before shutdown
        if (advancementApiPresent) {
            saveAdvancementProgress();
        }
        
        chatInputs.clear(); 
        mobToDungeon.clear(); 
        bossToDungeon.clear(); 
        dungeonToBoss.clear(); 
        activeDungeonPlayers.clear(); 
        // Clear all boss bars and tasks
        for (String key : new HashSet<>(bossHealthBars.keySet())) {
            removeBossHealthBar(key);
        }
    }

    /* ---------------- Config Load ---------------- */
    public void reloadLocal(boolean createWorlds) {
        reloadConfig();
        dungeons.clear(); lootTables.clear();
        dungeonWorldNames.clear(); // Clear world names cache
        spawnRadius = getConfig().getInt("spawn-radius", 6);
        ConfigurationSection ds = getConfig().getConfigurationSection("dungeons");
        if (ds != null) for (String key : ds.getKeys(false)) {
            ConfigurationSection sec = ds.getConfigurationSection(key); if (sec == null) continue;
            String world = sec.getString("world", "dungeon_" + key); // Use dungeon-specific world name
            if (createWorlds && Bukkit.getWorld(world) == null) tryCreateWorld(world);
            dungeonWorldNames.add(world); // Cache world name for fast lookup
            int x = sec.getInt("location.x"), y = sec.getInt("location.y"), z = sec.getInt("location.z");
            List<MobDefinition> mobs = new ArrayList<>();
            for (Map<?,?> m : sec.getMapList("mobs")) mobs.add(MobDefinition.fromMap(m));
            String advTitle = sec.getString("advancement.title", "&6Dungeon Conqueror");
            String advDesc = sec.getString("advancement.description", "&7You conquered the dungeon!");
            String difficulty = sec.getString("difficulty", "NORMAL").toUpperCase(Locale.ROOT);
            
            // Parse boss definition
            BossDefinition boss = null;
            ConfigurationSection bossSec = sec.getConfigurationSection("boss");
            if (bossSec != null && bossSec.getBoolean("enabled", false)) {
                String bossType = bossSec.getString("type", "PHANTOM");
                String bossName = bossSec.getString("name", "&c&lBoss");
                double bossHealth = bossSec.getDouble("health", 100) * 2.0;
                double bossDamage = bossSec.getDouble("damage", 10);
                double bossSpeed = bossSec.getDouble("speed", 0.3);
                int bossSize = bossSec.getInt("size", 4);
                String bossMythicId = bossSec.getString("mythicId", null);
                boss = new BossDefinition(bossType, bossName, bossHealth, bossDamage, bossSpeed, bossSize, bossMythicId, true);
            }
            
            // Get spawn locations from config (with defaults)
            int bossX = sec.getInt("boss-spawn.x", x);
            int bossY = sec.getInt("boss-spawn.y", y);
            int bossZ = sec.getInt("boss-spawn.z", z);
            int playerX = sec.getInt("player-spawn.x", x);
            int playerY = sec.getInt("player-spawn.y", y);
            int playerZ = sec.getInt("player-spawn.z", z);
            
            DungeonDefinition dd = new DungeonDefinition(key, world, x, y, z, mobs, advTitle, advDesc, difficulty, boss, bossX, bossY, bossZ, playerX, playerY, playerZ);
            dungeons.put(key.toLowerCase(Locale.ROOT), dd);
            ConfigurationSection lootSec = sec.getConfigurationSection("loot");
            if (lootSec != null && lootSec.getBoolean("enabled", false)) lootTables.put(key.toLowerCase(Locale.ROOT), LootTable.fromConfig(lootSec));
        }
    }

    /* ---------------- Commands ---------------- */
    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("dungeon")) return false;
        
        // Security: Rate limiting for players
        if (sender instanceof Player player) {
            UUID playerId = player.getUniqueId();
            long now = System.currentTimeMillis();
            Long lastUse = lastCommandUse.get(playerId);
            if (lastUse != null && (now - lastUse) < COMMAND_COOLDOWN) {
                sender.sendMessage(prefix() + "§cPlease wait before using commands again.");
                return true;
            }
            lastCommandUse.put(playerId, now);
        }
        
        if (args.length==0) {
            // If player used /dungeon with no args, auto-join currently open dungeon if available
            if (queueSystemEnabled && currentOpenDungeon != null && sender instanceof Player) {
                doJoin(sender, new String[]{"join"});
            } else {
                help(sender);
            }
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "admin" -> { if (needPlayer(sender)) { Player p=(Player)sender; if (perm(sender,"bubbledungeons.admin")) openAdminGui(p);} }
            case "config" -> { if (needPlayer(sender)) { Player p=(Player)sender; if (perm(sender,"bubbledungeons.admin")) openConfigGui(p);} }
            case "edit" -> { if (needPlayer(sender) && perm(sender,"bubbledungeons.admin")) { Player p=(Player)sender; if(args.length > 1) { new com.bubblecraft.dungeons.gui.DungeonEditGUI(this, args[1]).open(p); } else { p.sendMessage(prefix()+"§cUsage: /bd edit <dungeon>"); } } }
            case "reload" -> { if (perm(sender,"bubbledungeons.admin")) { reloadLocal(true); ensureDungeonWorlds(); sender.sendMessage(prefix()+"§aConfig reloaded."); } }
            case "list" -> {
                sender.sendMessage(prefix()+"§eAvailable dungeons (§f"+dungeons.size()+"§e):");
                for (String key : getDungeonKeysSorted()) {
                    String display = getDungeonDisplayName(key);
                    boolean isOpen = queueSystemEnabled && key.equalsIgnoreCase(currentOpenDungeon);
                    sender.sendMessage(" §7- " + (isOpen ? "§a[OPEN] " : "") + "§e" + key + " §7» " + display);
                }
                if (queueSystemEnabled) {
                    if (currentOpenDungeon != null) sender.sendMessage(prefix()+"§7Use §a/dungeon join §7to join the currently open dungeon.");
                    else sender.sendMessage(prefix()+"§7No dungeon is open right now.");
                }
            }
            case "start" -> {
                if (!perm(sender, "bubbledungeons.admin")) return true;
                if (args.length < 2) {
                    // No name provided: if queue is enabled, open next; else show usage
                    if (queueSystemEnabled) {
                        openNextDungeon();
                        sender.sendMessage(prefix()+"§aOpened next dungeon in the queue: §e" + (currentOpenDungeon==null?"none":currentOpenDungeon));
                    } else {
                        sender.sendMessage(prefix()+"§cUsage: /dungeon start <dungeon>");
                    }
                } else {
                    String dn = args[1].toLowerCase(java.util.Locale.ROOT);
                    if (!dungeons.containsKey(dn)) {
                        sender.sendMessage(prefix()+"§cDungeon not found: §e"+dn);
                        return true;
                    }
                    openSpecificDungeon(dn);
                    sender.sendMessage(prefix()+"§aOpened dungeon: §e" + dn);
                }
            }
            case "join" -> doJoin(sender,args);
            case "leave" -> { if (needPlayer(sender)) leaveDungeon(((Player)sender).getUniqueId(), true); }
            case "menu" -> { if (needPlayer(sender)) new com.bubblecraft.dungeons.gui.DungeonJoinMenuGUI(this).open((Player)sender); }
            case "safemode" -> { if (perm(sender,"bubbledungeons.admin")) { safeMode=!safeMode; sender.sendMessage(prefix()+"§eSafeMode: "+(safeMode?"§aON":"§cOFF")); } }
            case "testrun" -> testRun(sender,args);
            case "undo" -> undo(sender);
            case "stats" -> stats(sender,args);
            case "progress" -> showAdvancementProgress(sender);
            case "testadv" -> testAdvancement(sender, args);
            case "forcejoin", "send" -> forceJoin(sender, args);
            default -> sender.sendMessage(prefix()+"§cUnknown subcommand.");
        }
        return true;
    }

    private void help(CommandSender s){
        s.sendMessage(prefix()+"§e/dungeon join <name>");
        s.sendMessage(prefix()+"§e/dungeon list §7- List available dungeons");
        s.sendMessage(prefix()+"§e/dungeon menu");
        s.sendMessage(prefix()+"§e/dungeon leave");
        s.sendMessage(prefix()+"§e/dungeon progress §7- View advancement progress");
        if (s.hasPermission("bubbledungeons.admin")) {
            s.sendMessage(prefix()+"§e/dungeon edit <dungeon> §7- Edit dungeon spawn locations");
            s.sendMessage(prefix()+"§e/dungeon admin §7- Open admin GUI");
            s.sendMessage(prefix()+"§e/dungeon config §7- Open config GUI");
            s.sendMessage(prefix()+"§e/dungeon reload §7- Reload configuration");
            s.sendMessage(prefix()+"§e/dungeon start <dungeon> §7- Open a specific dungeon now");
            s.sendMessage(prefix()+"§e/dungeon testadv <type> §7- Test advancement system");
        }
    }
    private boolean perm(CommandSender s,String p){ if(!s.hasPermission(p)){s.sendMessage(prefix()+"§cNo permission."); return false;} return true; }
    private boolean needPlayer(CommandSender s){ if(!(s instanceof Player)){ s.sendMessage(prefix()+"Players only."); return false;} return true; }

    private void doJoin(CommandSender sender,String[] args){
        if(!needPlayer(sender)) return; 
        
        Player p = (Player)sender;
        UUID playerId = p.getUniqueId();
        boolean isAdmin = p.hasPermission("bubbledungeons.admin");
        // Non-admin players may never "start" a dungeon implicitly; they can only join the currently open one
        if (!isAdmin) {
            if (!queueSystemEnabled) {
                sender.sendMessage(prefix()+"§cYou can only join dungeons when one is opened by staff.");
                return;
            }
            if (queueSystemEnabled && currentOpenDungeon == null) {
                sender.sendMessage(prefix()+"§cNo dungeon is open right now. Please wait for the next dungeon to open.");
                return;
            }
        }
        
        // Security: Join cooldown to prevent spam
        long now = System.currentTimeMillis();
        Long lastJoin = lastJoinAttempt.get(playerId);
        if (lastJoin != null && (now - lastJoin) < JOIN_COOLDOWN) {
            sender.sendMessage(prefix()+"§cPlease wait before joining another dungeon.");
            return;
        }
        
        String name;
        if (args.length < 2) {
            // No dungeon specified - join the currently open dungeon if queue system is enabled
            if (queueSystemEnabled && currentOpenDungeon != null) {
                name = currentOpenDungeon;
                sender.sendMessage(prefix()+"§aJoining currently open dungeon: §e" + name);
            } else {
                sender.sendMessage(prefix()+"§cUsage: /dungeon join <name>" + 
                    (queueSystemEnabled ? " §7or §a/dungeon join §7to join the current open dungeon" : ""));
                return;
            }
        } else {
            // Specific dungeon requested
            name = args[1].toLowerCase(Locale.ROOT);
            // If player is not admin they may only join the currently open dungeon (no direct starts)
            if (!isAdmin) {
                if (!name.equalsIgnoreCase(currentOpenDungeon)) {
                    sender.sendMessage(prefix()+"§cOnly the current open dungeon §e" + currentOpenDungeon + " §cis available! Use §a/dungeon join§c.");
                    return;
                }
            }
            
            // If queue system is enabled, only allow joining the current open dungeon
            if (queueSystemEnabled && currentOpenDungeon != null && !name.equals(currentOpenDungeon)) {
                sender.sendMessage(prefix()+"§cOnly the current open dungeon §e" + currentOpenDungeon + " §cis available! Use §a/dungeon join §cto join.");
                return;
            }
        }
        
        // Security: Validate dungeon name length and characters
        if (name.length() > MAX_DUNGEON_NAME_LENGTH || !name.matches("[a-z0-9_-]+")) {
            sender.sendMessage(prefix()+"§cInvalid dungeon name.");
            return;
        }
        
        DungeonDefinition dd = dungeons.get(name);
        if(dd==null){ sender.sendMessage(prefix()+"§cDungeon not found."); return; }
        
        // Security: Limit concurrent dungeons to prevent server overload
        if (activeDungeonPlayers.size() >= MAX_CONCURRENT_DUNGEONS) {
            sender.sendMessage(prefix()+"§cToo many active dungeons. Please try again later.");
            return;
        }
        
        lastJoinAttempt.put(playerId, now);
        
        World w=Bukkit.getWorld(dd.worldName()); 
        if(w==null){ 
            tryCreateWorld(dd.worldName()); 
            w=Bukkit.getWorld(dd.worldName()); 
            if(w==null){
                sender.sendMessage(prefix()+"§cWorld loading; retry."); 
                return;
            }
        }
        
    // Force global player spawn location and facing (for all dungeons)
    Location teleportLoc = new Location(w, 65.5, -41, 0.5);
    Location safeLoc = findSafeSpawnLocation(w, teleportLoc.getX(), teleportLoc.getY(), teleportLoc.getZ());
    // Face West (towards negative X)
    safeLoc.setYaw(90.0f);
    safeLoc.setPitch(0.0f);
    p.teleport(safeLoc);
        
        // Play cool teleport effect
        particleEffects.playTeleportEffect(safeLoc);
        
        // Show big entrance title for 2 seconds
        p.sendTitle(
            color("&6&l" + dd.key().toUpperCase()), // Main title
            color("&7&oPrepare for adventure!"), // Subtitle
            10, // Fade in (0.5 seconds)
            40, // Stay (2 seconds) 
            10  // Fade out (0.5 seconds)
        );
        
        activeDungeonPlayers.computeIfAbsent(dd.key(),k->new HashSet<>()).add(playerId);
        dungeonStartTime.putIfAbsent(dd.key(), System.currentTimeMillis());
        
        // Track participation for advancement system
        playerDungeonParticipation.computeIfAbsent(playerId, k -> new HashSet<>()).add(dd.key());
        
        // Grant participation advancement on first dungeon entry
        if (!testRunMode.contains(dd.key()) && advancementApiPresent) {
            Set<String> participatedDungeons = playerDungeonParticipation.get(playerId);
            if (participatedDungeons != null && participatedDungeons.size() == 1) {
                // First time participating in any dungeon
                grantParticipationAdvancement(p);
            }
        }
        
        // Performance: Use cached mob count instead of stream operation
        Integer cachedMobCount = dungeonMobCounts.get(dd.key());
        boolean already = (cachedMobCount != null && cachedMobCount > 0) || safeMode;
        
        if(!already){ 
            if(safeMode) {
                sender.sendMessage(prefix()+"§eSafeMode: mobs not spawned."); 
            } else { 
                spawnDungeonMobs(dd); 
                sender.sendMessage(prefix()+"§aSpawned mobs."); 
            } 
        } else {
            sender.sendMessage(prefix()+"§aJoined active dungeon.");
        }
        updateHud(dd.key());
    }

    private void testRun(CommandSender s,String[] a){
        if(!needPlayer(s)) return; if(a.length<2){ s.sendMessage(prefix()+"§cUsage: /dungeon testrun <name>"); return; }
        DungeonDefinition dd = dungeons.get(a[1].toLowerCase(Locale.ROOT)); if(dd==null){ s.sendMessage(prefix()+"§cNot found."); return; }
        Player p=(Player)s; activeDungeonPlayers.computeIfAbsent(dd.key(),k->new HashSet<>()).add(p.getUniqueId());
        dungeonStartTime.put(dd.key(), System.currentTimeMillis()); testRunMode.add(dd.key()); spawnDungeonMobs(dd); p.sendMessage(prefix()+"§eTest mobs spawned (no rewards).");
    }

    private void undo(CommandSender s){ if(!perm(s,"bubbledungeons.admin")) return; if(undoSnapshots.isEmpty()){ s.sendMessage(prefix()+"§cNothing to undo."); return; } String yaml=undoSnapshots.pop(); try{ Files.writeString(getDataFolder().toPath().resolve("config.yml"), yaml); reloadLocal(true); s.sendMessage(prefix()+"§aReverted last change."); }catch(IOException ex){ s.sendMessage(prefix()+"§cUndo failed: "+ex.getMessage()); }}
    private void stats(CommandSender s,String[] a){ if(a.length==1){ s.sendMessage(prefix()+"§eDungeon Stats:"); for(String k:dungeons.keySet()){ int runs=dungeonRuns.getOrDefault(k,0); long tot=dungeonTotalTime.getOrDefault(k,0L); long avg=runs==0?0: (tot/runs/1000L); s.sendMessage(prefix()+"§7- §e"+k+" §7avg(s): §f"+avg); } } else { String k=a[1].toLowerCase(Locale.ROOT); int runs=dungeonRuns.getOrDefault(k,0); long tot=dungeonTotalTime.getOrDefault(k,0L); long avg=runs==0?0:(tot/runs/1000L); s.sendMessage(prefix()+"§e"+k+" §7runs: §f"+runs+" §7avg(s): §f"+avg); } }

    /**
     * Test advancement system - admin command for debugging
     */
    private void testAdvancement(CommandSender s, String[] args) {
        if (!perm(s, "bubbledungeons.admin")) return;
        if (!(s instanceof Player)) {
            s.sendMessage(prefix() + "§cThis command can only be used by players.");
            return;
        }
        
        Player player = (Player) s;
        
        if (args.length < 2) {
            s.sendMessage(prefix() + "§cUsage: /bd testadv <type>");
            s.sendMessage(prefix() + "§cTypes: completion, participation, firstboss, allbosses");
            return;
        }
        
        String type = args[1].toLowerCase();
        
        switch (type) {
            case "completion" -> {
                // Test dungeon completion advancement
                DungeonDefinition testDungeon = dungeons.get("sample");
                if (testDungeon != null) {
                    grantAdvancement(testDungeon, Set.of(player.getUniqueId()));
                    s.sendMessage(prefix() + "§aTest dungeon completion advancement granted!");
                } else {
                    s.sendMessage(prefix() + "§cNo sample dungeon found for testing.");
                }
            }
            case "participation" -> {
                grantParticipationAdvancement(player);
                s.sendMessage(prefix() + "§aTest participation advancement granted!");
            }
            case "firstboss" -> {
                grantFirstBossAdvancement(player);
                s.sendMessage(prefix() + "§aTest first boss advancement granted!");
            }
            case "allbosses" -> {
                grantAllBossesAdvancement(player);
                s.sendMessage(prefix() + "§aTest all bosses advancement granted!");
            }
            default -> {
                s.sendMessage(prefix() + "§cInvalid type. Use: completion, participation, firstboss, allbosses");
            }
        }
    }

    private void showAdvancementProgress(CommandSender s) {
        if (!(s instanceof Player)) {
            s.sendMessage(prefix() + "§cThis command can only be used by players.");
            return;
        }
        
        Player player = (Player) s;
        UUID playerId = player.getUniqueId();
        
        s.sendMessage(prefix() + "§e§lDungeon Advancement Progress:");
        s.sendMessage("§7§m                                    ");
        
        // Participation advancement
        boolean hasParticipated = playerDungeonParticipation.containsKey(playerId);
        String participationStatus = hasParticipated ? "§a✓ Completed" : "§c✗ Not Completed";
        s.sendMessage("§6Dungeon Explorer: " + participationStatus);
        s.sendMessage("§7  └ Participate in any dungeon");
        
        // First boss advancement
        Set<String> defeatedBosses = playerBossDefeats.getOrDefault(playerId, new HashSet<>());
        int bossesDefeated = defeatedBosses.size();
        boolean hasFirstBoss = bossesDefeated >= 1;
        String firstBossStatus = hasFirstBoss ? "§a✓ Completed" : "§c✗ Not Completed";
        s.sendMessage("§6Boss Slayer: " + firstBossStatus);
        s.sendMessage("§7  └ Defeat your first dungeon boss (" + bossesDefeated + "/1)");
        
        // All bosses advancement
        boolean hasAllBosses = allBossesDefeatedPlayers.contains(playerId);
        String allBossesStatus = hasAllBosses ? "§a✓ Completed" : "§c✗ Not Completed";
        s.sendMessage("§6Dungeon Master: " + allBossesStatus);
        s.sendMessage("§7  └ Defeat all 3 unique dungeon bosses (" + Math.min(bossesDefeated, 3) + "/3)");
        
        s.sendMessage("§7§m                                    ");
        s.sendMessage("§7Total Bosses Defeated: §f" + bossesDefeated);
        if (hasParticipated) {
            s.sendMessage("§7Dungeons Participated: §a✓");
        }
    }

    /* ---------------- World / Spawn ---------------- */
    private void ensureDungeonWorlds(){ 
        // Get unique world names to avoid creating duplicates
        Set<String> uniqueWorlds = new HashSet<>();
        for (DungeonDefinition dd : dungeons.values()) {
            uniqueWorlds.add(dd.worldName());
        }
        // Create only unique worlds that don't exist
        for (String worldName : uniqueWorlds) {
            if (Bukkit.getWorld(worldName) == null) {
                tryCreateWorld(worldName);
            }
        }
    }
    
    // Cache to prevent repeated world creation attempts
    private final Set<String> worldCreationAttempts = ConcurrentHashMap.newKeySet();
    
    private void tryCreateWorld(String name){ 
        if(startupPhase){ 
            Bukkit.getScheduler().runTask(this,()->tryCreateWorld(name)); 
            return;
        } 
        if(Bukkit.getWorld(name)!=null) return; 
        
        // Prevent repeated creation attempts for the same world
        if (worldCreationAttempts.contains(name)) {
            return; // Already attempting to create this world
        }
        worldCreationAttempts.add(name);
        
        // Check if auto-creation is enabled
        if (!getConfig().getBoolean("world-creation.auto-create-worlds", true)) {
            getLogger().warning("World '" + name + "' does not exist and auto-creation is disabled");
            worldCreationAttempts.remove(name);
            return;
        }
        
        try{
            if (multiversePresent && getConfig().getBoolean("world-creation.prefer-multiverse", true)) {
                // Use Multiverse-Core to create the world
                if (createWorldWithMultiverse(name)) {
                    getLogger().info("Created dungeon world '" + name + "' using Multiverse-Core");
                    worldCreationAttempts.remove(name); // Clear cache on success
                    return;
                }
            }
            
            // Fallback to Bukkit world creation
            org.bukkit.WorldCreator creator = org.bukkit.WorldCreator.name(name);
            String worldType = getConfig().getString("world-creation.world-type", "FLAT");
            
            // Set world type if configured
            try {
                org.bukkit.WorldType type = org.bukkit.WorldType.valueOf(worldType.toUpperCase());
                creator.type(type);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid world type '" + worldType + "', using NORMAL");
                creator.type(org.bukkit.WorldType.NORMAL);
            }
            
            World w = Bukkit.createWorld(creator); 
            if(w!=null) {
                try{ 
                    w.getSpawnLocation().getChunk().load(); 
                }catch(Exception ignored){} 
                
                // Apply world settings if using Bukkit creation
                if (getConfig().getBoolean("world-creation.disable-natural-spawning", true)) {
                    w.setSpawnFlags(false, false); // No monsters, no animals
                }
                if (getConfig().getBoolean("world-creation.set-time-noon", true)) {
                    w.setTime(6000); // Noon
                }
                if (getConfig().getBoolean("world-creation.disable-weather", true)) {
                    w.setStorm(false);
                    w.setThundering(false);
                    w.setWeatherDuration(0);
                }
                if (getConfig().getBoolean("world-creation.disable-pvp", true)) {
                    w.setPVP(false);
                }
                
                getLogger().info("Created dungeon world '" + name + "' using Bukkit");
                worldCreationAttempts.remove(name); // Clear cache on success
            }
        }catch(IllegalStateException ex){ 
            getLogger().warning("Could not create world '"+name+"': "+ex.getMessage()); 
            worldCreationAttempts.remove(name); // Clear cache on failure
            Bukkit.getScheduler().runTask(this,()->tryCreateWorld(name)); 
        }
    }
    
    private boolean createWorldWithMultiverse(String name) {
        try {
            // Check if world already exists first
            if (Bukkit.getWorld(name) != null) {
                return true; // World already exists
            }
            
            // Get world creation settings from config
            String worldType = getConfig().getString("world-creation.world-type", "FLAT");
            boolean disableNaturalSpawning = getConfig().getBoolean("world-creation.disable-natural-spawning", true);
            boolean setTimeNoon = getConfig().getBoolean("world-creation.set-time-noon", true);
            boolean disableWeather = getConfig().getBoolean("world-creation.disable-weather", true);
            boolean disablePvp = getConfig().getBoolean("world-creation.disable-pvp", true);
            
            // Use Bukkit console to run Multiverse commands
            Bukkit.getScheduler().runTask(this, () -> {
                // Check if world folder exists first
                File worldFolder = new File(Bukkit.getWorldContainer(), name);
                if (worldFolder.exists() && worldFolder.isDirectory()) {
                    // World exists in filesystem, import it instead of creating
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
                        "mv import " + name + " NORMAL");
                    getLogger().info("Imported existing world '" + name + "' using Multiverse-Core");
                } else {
                    // Create new world with specified environment type and flat generator
                    String flatWorldSettings = getConfig().getString("world-creation.flat-settings", "2;7,2x3,2;1;");
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
                        "mv create " + name + " NORMAL -t " + worldType + " -g " + flatWorldSettings);
                    getLogger().info("Created new flat world '" + name + "' using Multiverse-Core with settings: " + flatWorldSettings);
                }
                
                // Configure world settings for dungeons with proper command syntax
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    // Verify world was created before modifying and clear cache
                    if (Bukkit.getWorld(name) != null) {
                        worldCreationAttempts.remove(name); // Clear cache on success
                        if (disableNaturalSpawning) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
                                "mv modify " + name + " set animals false");
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
                                "mv modify " + name + " set monsters false");
                        }
                        if (disablePvp) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
                                "mv modify " + name + " set pvp false");
                        }
                        if (disableWeather) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
                                "mv modify " + name + " set weather false");
                        }
                        if (setTimeNoon) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
                                "mv modify " + name + " set time 6000"); // Noon
                        }
                    } else {
                        worldCreationAttempts.remove(name); // Clear cache on failure
                    }
                }, 20L); // Wait 1 second for world creation
            });
            return true;
        } catch (Exception e) {
            getLogger().warning("Failed to create world with Multiverse-Core: " + e.getMessage());
            return false;
        }
    }

    /* ---------------- Mob Spawning ---------------- */
    private void spawnDungeonMobs(DungeonDefinition dd){ 
        World w=Bukkit.getWorld(dd.worldName()); 
        if(w==null) return; 
        Location base=new Location(w, dd.x()+0.5, dd.y(), dd.z()+0.5); 
        
        // Determine wave context
    boolean waveMode=getConfig().getBoolean("features.wave-system", false);
    int currentWave = waveState.getOrDefault(dd.key(), 1);
    // Per-dungeon override for waves.count, fallback to global
    int totalWaves = Math.max(1, getConfig().getInt("dungeons."+dd.key()+".waves.count",
        getConfig().getInt("waves.count", 2)));

        // Reset boss bar only when starting the first wave
        if (hud != null && (!waveMode || currentWave == 1)) {
            hud.reset(dd.key());
        }
        
        // Initialize mob count cache for this dungeon
        dungeonMobCounts.put(dd.key(), 0);
        
        // Apply atmospheric effects based on dungeon mobs and boss
        applyDungeonAtmosphere(dd, w);
        
        // Get player-based scaling configuration
        int playerCount = activeDungeonPlayers.getOrDefault(dd.key(), Collections.emptySet()).size();
        PlayerScalingData scalingData = calculatePlayerScaling(playerCount);
        
        // Spawn regular mobs around boss location (minions)
        List<MobDefinition> mobs=dd.mobs(); 
        int wave=currentWave; 
        int start=0,end=mobs.size(); 
        if(waveMode){ 
            // Evenly split mob list across total waves
            start = (mobs.size() * (wave - 1)) / totalWaves; 
            end   = (mobs.size() * wave) / totalWaves; 
        }
        
    // Use global boss location as the center for minion spawning (for all dungeons)
    Location bossSpawnCenter = new Location(w, 0.5, -52, 0.5);
        int minionRadius = getConfig().getInt("minion-spawn-radius", 3);
        String minionFacing = getConfig().getString("minion-facing", "EAST");
        
        // Spawn base mobs
        for(int i=start;i<end;i++){ 
            MobDefinition md=mobs.get(i); 
            spawnMinionAroundBoss(md, bossSpawnCenter, dd.key(), scalingData.difficultyMultiplier, minionRadius, minionFacing);
        }
        // Always spawn additional global minions per request (independent of wave system)
        if (!mobs.isEmpty()) {
            for (int extra = 0; extra < GLOBAL_EXTRA_MINIONS; extra++) {
                MobDefinition md = mobs.get(random.nextInt(mobs.size()));
                spawnMinionAroundBoss(md, bossSpawnCenter, dd.key(), scalingData.difficultyMultiplier, minionRadius, minionFacing);
            }
        }

        // Extra minions per wave (configurable)
        if (waveMode) {
            // Per-dungeon override for extra-minions-per-wave (list or single int)
            List<Integer> extraPerWave = getConfig().getIntegerList("dungeons."+dd.key()+".waves.extra-minions-per-wave");
            if (extraPerWave == null || extraPerWave.isEmpty()) {
                extraPerWave = getConfig().getIntegerList("waves.extra-minions-per-wave");
            }
            int extra = 0;
            if (extraPerWave != null && !extraPerWave.isEmpty()) {
                int idx = Math.min(Math.max(0, wave - 1), extraPerWave.size() - 1);
                extra = Math.max(0, extraPerWave.get(idx));
            } else {
                extra = Math.max(0, getConfig().getInt("dungeons."+dd.key()+".waves.extra-minions-per-wave",
                        getConfig().getInt("waves.extra-minions-per-wave", 0)));
            }
            if (extra > 0 && !mobs.isEmpty()) {
                for (int i = 0; i < extra; i++) {
                    MobDefinition md = mobs.get(random.nextInt(mobs.size()));
                    spawnMinionAroundBoss(md, bossSpawnCenter, dd.key(), scalingData.difficultyMultiplier, minionRadius, minionFacing);
                }
            }
        }
        
        // Spawn additional mobs based on player scaling
        if (scalingData.additionalMobs > 0) {
            spawnAdditionalMobs(dd, mobs, bossSpawnCenter, scalingData, minionRadius, minionFacing);
        }
        
    // Spawn boss if enabled and not already spawned
        if(dd.boss() != null && dd.boss().enabled() && !bossToDungeon.values().contains(dd.key())){
            // Use global boss spawn location with safe spawning (for all dungeons)
            Location initialBossLoc = new Location(w, 0.5, -52, 0.5);
            Location bossLoc = findSafeSpawnLocation(w, initialBossLoc.getX(), initialBossLoc.getY(), initialBossLoc.getZ());
            spawnBoss(dd.boss(), bossLoc, dd.key(), scalingData.difficultyMultiplier);
        }
        
        updateHud(dd.key()); 
    }
    
    /**
     * Data class for player scaling calculations
     */
    private static class PlayerScalingData {
        final int additionalMobs;
        final double difficultyMultiplier;
        
        PlayerScalingData(int additionalMobs, double difficultyMultiplier) {
            this.additionalMobs = additionalMobs;
            this.difficultyMultiplier = difficultyMultiplier;
        }
    }
    
    /**
     * Calculates mob scaling based on player count
     */
    private PlayerScalingData calculatePlayerScaling(int playerCount) {
        if (!getConfig().getBoolean("player-scaling.enabled", true) || playerCount <= 1) {
            if (getConfig().getBoolean("player-scaling.debug-mode", false)) {
                getLogger().info("[Player Scaling] No scaling needed - Player count: " + playerCount + 
                    ", Scaling enabled: " + getConfig().getBoolean("player-scaling.enabled", true));
            }
            return new PlayerScalingData(0, 1.0);
        }
        
        int scalingInterval = getConfig().getInt("player-scaling.scaling-interval", 2);
        int additionalMobsMin = getConfig().getInt("player-scaling.additional-mobs-min", 3);
        int additionalMobsMax = getConfig().getInt("player-scaling.additional-mobs-max", 5);
        double difficultyMultiplier = getConfig().getDouble("player-scaling.difficulty-multiplier", 1.2);
        int maxScalingIntervals = getConfig().getInt("player-scaling.max-scaling-intervals", 3);
        
        // Calculate number of scaling intervals (every 2 players by default)
        int scalingIntervals = Math.min((playerCount - 1) / scalingInterval, maxScalingIntervals);
        
        if (scalingIntervals <= 0) {
            if (getConfig().getBoolean("player-scaling.debug-mode", false)) {
                getLogger().info("[Player Scaling] No scaling intervals - Player count: " + playerCount + 
                    ", Interval: " + scalingInterval);
            }
            return new PlayerScalingData(0, 1.0);
        }
        
        // Calculate additional mobs (3-5 per interval)
        int additionalMobs = 0;
        for (int i = 0; i < scalingIntervals; i++) {
            additionalMobs += additionalMobsMin + random.nextInt(additionalMobsMax - additionalMobsMin + 1);
        }
        
        // Calculate difficulty multiplier (1.2x per interval)
        double finalDifficultyMultiplier = Math.pow(difficultyMultiplier, scalingIntervals);
        
        if (getConfig().getBoolean("player-scaling.debug-mode", false)) {
            getLogger().info("[Player Scaling] Calculated scaling - Players: " + playerCount + 
                ", Intervals: " + scalingIntervals + ", Additional mobs: " + additionalMobs + 
                ", Difficulty multiplier: " + String.format("%.2f", finalDifficultyMultiplier));
        }
        
        return new PlayerScalingData(additionalMobs, finalDifficultyMultiplier);
    }
    
    /**
     * Spawns additional mobs based on player scaling
     */
    private void spawnAdditionalMobs(DungeonDefinition dd, List<MobDefinition> baseMobs, Location bossCenter, 
                                   PlayerScalingData scalingData, int minionRadius, String minionFacing) {
        if (baseMobs.isEmpty()) return;
        
        for (int i = 0; i < scalingData.additionalMobs; i++) {
            // Randomly select a mob type from the base mobs
            MobDefinition selectedMob = baseMobs.get(random.nextInt(baseMobs.size()));
            
            // Spawn the additional mob around the boss
            spawnMinionAroundBoss(selectedMob, bossCenter, dd.key(), scalingData.difficultyMultiplier, minionRadius, minionFacing);
        }
        
        // Notify players about additional mobs
        Set<UUID> players = activeDungeonPlayers.get(dd.key());
        if (players != null && !players.isEmpty()) {
            String message = getConfig().getString("player-scaling.scaling-message", 
                "§c⚔ §e{additional_mobs} additional mobs spawned due to {player_count} players! §c⚔")
                .replace("{additional_mobs}", String.valueOf(scalingData.additionalMobs))
                .replace("{player_count}", String.valueOf(players.size()));
            
            for (UUID playerId : players) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.sendMessage(color(message));
                }
            }
        }
    }
    
    /**
     * Spawns a minion around the boss location within the specified radius and facing direction
     */
    private void spawnMinionAroundBoss(MobDefinition md, Location bossCenter, String dungeonKey, double scale, int radius, String facing) {
        World w = bossCenter.getWorld();
        if (w == null) return;
        
        // Random position within radius around boss
        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = 2 + random.nextDouble() * (radius - 2); // 2 to radius blocks from boss
        
        double offsetX = Math.cos(angle) * distance;
        double offsetZ = Math.sin(angle) * distance;
        
        double x = bossCenter.getX() + offsetX;
        double y = bossCenter.getY();
        double z = bossCenter.getZ() + offsetZ;
        
        Location spawnLoc = findSafeSpawnLocation(w, x, y, z);
        
        // Spawn the mob
        LivingEntity mob = spawnMob(md, spawnLoc, dungeonKey, scale, false);
        
        // Set facing direction if mob was spawned successfully
        if (mob != null) {
            try {
                org.bukkit.block.BlockFace face = org.bukkit.block.BlockFace.valueOf(facing.toUpperCase());
                float yaw = getYawFromBlockFace(face);
                Location facingLoc = mob.getLocation();
                facingLoc.setYaw(yaw);
                mob.teleport(facingLoc);
            } catch (IllegalArgumentException e) {
                // Invalid facing direction, default to EAST (yaw = -90)
                Location facingLoc = mob.getLocation();
                facingLoc.setYaw(-90.0f);
                mob.teleport(facingLoc);
            }
        }
    }
    
    /**
     * Converts BlockFace to yaw value for entity facing
     */
    private float getYawFromBlockFace(org.bukkit.block.BlockFace face) {
        switch (face) {
            case NORTH: return 180.0f;
            case SOUTH: return 0.0f;
            case WEST: return 90.0f;
            case EAST: return -90.0f;
            case NORTH_EAST: return -135.0f;
            case NORTH_WEST: return 135.0f;
            case SOUTH_EAST: return -45.0f;
            case SOUTH_WEST: return 45.0f;
            default: return -90.0f; // Default to EAST
        }
    }
    
    private LivingEntity spawnMob(MobDefinition md, Location base, String dungeonKey, double scale, boolean isBoss){
        try { 
            LivingEntity le; 
            Vector off = isBoss ? new Vector(0,0,0) : new Vector(random.nextInt(spawnRadius*2+1)-spawnRadius,0,random.nextInt(spawnRadius*2+1)-spawnRadius); 
            Location initialLoc = base.clone().add(off); 
            // Find safe spawn location to prevent mobs from taking fall damage
            Location loc = findSafeSpawnLocation(initialLoc.getWorld(), initialLoc.getX(), initialLoc.getY(), initialLoc.getZ()); 
            
            if(mythicMobsPresent && md.mythicId()!=null){ 
                try { 
                    Class<?> mythic=Class.forName("io.lumine.mythic.bukkit.MythicBukkit"); 
                    Object inst=mythic.getMethod("inst").invoke(null); 
                    Object mgr=mythic.getMethod("inst").invoke(null).getClass().getMethod("getMobManager").invoke(inst); 
                    Object opt=mgr.getClass().getMethod("getMythicMob",String.class).invoke(mgr, md.mythicId()); 
                    if(opt!=null){ 
                        Object active=mgr.getClass().getMethod("spawnMob",String.class,Location.class).invoke(mgr, md.mythicId(), loc); 
                        if(active!=null){ 
                            Object be=active.getClass().getMethod("getBukkitEntity").invoke(active); 
                            if(be instanceof LivingEntity living) le=living; 
                            else return null; 
                        } else return null; 
                    } else { 
                        org.bukkit.entity.EntityType t= org.bukkit.entity.EntityType.valueOf(md.type().toUpperCase(Locale.ROOT)); 
                        le=(LivingEntity)loc.getWorld().spawnEntity(loc,t); 
                    } 
                } catch (Exception ignored){ 
                    org.bukkit.entity.EntityType t= org.bukkit.entity.EntityType.valueOf(md.type().toUpperCase(Locale.ROOT)); 
                    le=(LivingEntity)loc.getWorld().spawnEntity(loc,t); 
                } 
            } else { 
                org.bukkit.entity.EntityType t= org.bukkit.entity.EntityType.valueOf(md.type().toUpperCase(Locale.ROOT)); 
                le=(LivingEntity)loc.getWorld().spawnEntity(loc,t);
            } 
            
            if(md.name()!=null){ 
                le.setCustomName(color(md.name())); 
                le.setCustomNameVisible(true);
            } 
            
            applyAttribute(le,"GENERIC_MAX_HEALTH", md.health()*scale); 
            applyAttribute(le,"GENERIC_ATTACK_DAMAGE", md.damage()*scale); 
            applyAttribute(le,"GENERIC_MOVEMENT_SPEED", md.speed()); 
            
            // Apply size scaling for bosses if they have size > 0
            if (isBoss && md.size() > 0) {
                // Apply GENERIC_SCALE attribute for overall size scaling
                applyAttribute(le, "GENERIC_SCALE", md.size() * scale);
            }
            
            try{ 
                Attribute maxH=Attribute.valueOf("GENERIC_MAX_HEALTH"); 
                if(le.getAttribute(maxH)!=null){ 
                    double max=le.getAttribute(maxH).getBaseValue(); 
                    le.setHealth(Math.min(max, md.health()*scale)); 
                }
            }catch(Exception ignored){} 
            
            // Apply entity-specific size scaling
            if(le instanceof org.bukkit.entity.Slime slime && md.size()>0) {
                int scaledSize = Math.max(1, Math.min(127, (int)(md.size() * (isBoss ? scale : 1.0))));
                slime.setSize(scaledSize);
            }
            if(le instanceof org.bukkit.entity.Phantom phantom && md.size()>0) {
                int scaledSize = Math.max(1, Math.min(64, (int)(md.size() * (isBoss ? scale : 1.0))));
                phantom.setSize(scaledSize);
            }
            if(le instanceof org.bukkit.entity.MagmaCube magmaCube && md.size()>0) {
                int scaledSize = Math.max(1, Math.min(127, (int)(md.size() * (isBoss ? scale : 1.0))));
                magmaCube.setSize(scaledSize);
            } 
            
            // Apply aggressive dungeon mob behavior
            applyAggressiveBehavior(le, dungeonKey, isBoss);
            
            if(isBoss){
                bossToDungeon.put(le.getUniqueId(), dungeonKey);
                dungeonToBoss.put(dungeonKey, le.getUniqueId());
                // Play dramatic boss spawn effect
                particleEffects.playBossSpawnEffect(loc);
            } else {
                mobToDungeon.put(le.getUniqueId(), dungeonKey);
                // Update mob count cache for performance
                dungeonMobCounts.merge(dungeonKey, 1, Integer::sum);
                // Play mystical mob spawn effect
                particleEffects.playMobSpawnEffect(loc);
            }
            
            return le;
        } catch (IllegalArgumentException ex){ 
            getLogger().warning("Unknown entity type: "+md.type()); 
            return null;
        } 
    }
    
    /**
     * Applies aggressive behavior to dungeon mobs to make them more challenging
     */
    private void applyAggressiveBehavior(LivingEntity entity, String dungeonKey, boolean isBoss) {
        if (!getConfig().getBoolean("features.aggressive-mobs", true) ||
            !getConfig().getBoolean("aggressive-mobs.enabled", true)) {
            return; // Aggressive behavior disabled
        }
        
        // Increase mob awareness and detection range
        try {
            // Set follow range to configured value so mobs can see players from far away
            double followRange = getConfig().getDouble("aggressive-mobs.follow-range", 128.0);
            applyAttribute(entity, "GENERIC_FOLLOW_RANGE", followRange);
            
            // Make mobs persistent so they don't despawn
            entity.setRemoveWhenFarAway(false);
            
            // Specific behavior modifications based on mob type
            if (entity instanceof org.bukkit.entity.EnderDragon dragon) {
                applyDragonAggressiveBehavior(dragon, dungeonKey);
            } else if (entity instanceof org.bukkit.entity.Monster monster) {
                applyMonsterAggressiveBehavior(monster, dungeonKey);
            } else if (entity instanceof org.bukkit.entity.Flying flying) {
                applyFlyingMobBehavior(flying, dungeonKey);
            }
            
            // Schedule aggressive targeting task
            scheduleAggressiveTargeting(entity, dungeonKey);
            
        } catch (Exception e) {
            getLogger().warning("Failed to apply aggressive behavior to " + entity.getType() + ": " + e.getMessage());
        }
    }
    
    /**
     * Special aggressive behavior for Ender Dragons
     */
    private void applyDragonAggressiveBehavior(org.bukkit.entity.EnderDragon dragon, String dungeonKey) {
        if (!getConfig().getBoolean("aggressive-mobs.dragon-behavior.prevent-flying-away", true)) {
            return; // Dragon behavior modification disabled
        }
        
        // Prevent dragon from flying away by setting it to attack mode
        try {
            dragon.setPhase(EnderDragon.Phase.CIRCLING);
        } catch (Exception e) {
            // Fallback if phase doesn't exist
            getLogger().warning("Could not set dragon phase, using alternative method");
        }
        
        // Schedule a task to keep the dragon aggressive and in the area
        int interval = getConfig().getInt("aggressive-mobs.dragon-behavior.targeting-interval", 20);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (dragon.isDead() || !dragon.isValid()) return;
            
            // Find nearest player in the dungeon
            Set<UUID> players = activeDungeonPlayers.get(dungeonKey);
            if (players == null || players.isEmpty()) return;
            
            Player nearestPlayer = null;
            double minDistance = Double.MAX_VALUE;
            
            for (UUID playerId : players) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.getWorld().equals(dragon.getWorld())) {
                    double distance = player.getLocation().distanceSquared(dragon.getLocation());
                    if (distance < minDistance) {
                        minDistance = distance;
                        nearestPlayer = player;
                    }
                }
            }
            
            if (nearestPlayer != null) {
                // Force dragon to target the nearest player
                dragon.setTarget(nearestPlayer);
                
                // Keep dragon in aggressive phases if enabled
                if (getConfig().getBoolean("aggressive-mobs.dragon-behavior.aggressive-phases", true)) {
                    try {
                        if (dragon.getPhase() == EnderDragon.Phase.HOVER || 
                            dragon.getPhase() == EnderDragon.Phase.FLY_TO_PORTAL) {
                            dragon.setPhase(EnderDragon.Phase.CIRCLING);
                        }
                    } catch (Exception e) {
                        // Phase management failed, continue without it
                    }
                }
                
                // If dragon is too far from players, teleport it closer
                double maxDistance = getConfig().getDouble("aggressive-mobs.dragon-behavior.max-distance", 50.0);
                if (minDistance > maxDistance * maxDistance) {
                    Location playerLoc = nearestPlayer.getLocation();
                    Location newDragonLoc = playerLoc.clone().add(0, 10, 0); // 10 blocks above player
                    dragon.teleport(newDragonLoc);
                }
            }
        }, interval, interval);
    }
    
    /**
     * Aggressive behavior for monster mobs
     */
    private void applyMonsterAggressiveBehavior(org.bukkit.entity.Monster monster, String dungeonKey) {
        // Make monsters more aggressive by setting them to attack mode
        if (monster instanceof org.bukkit.entity.Zombie zombie) {
            zombie.setBaby(false); // Ensure adult zombies are aggressive
        }
        
        // Try to make them angry if they support it
        try {
            if (monster instanceof org.bukkit.entity.PigZombie pigZombie) {
                pigZombie.setAngry(true);
            } else if (monster instanceof org.bukkit.entity.Wolf wolf) {
                wolf.setAngry(true);
            }
        } catch (Exception e) {
            // Some mobs may not support setAngry, continue without it
        }
    }
    
    /**
     * Behavior modifications for flying mobs
     */
    private void applyFlyingMobBehavior(org.bukkit.entity.Flying flying, String dungeonKey) {
        // Prevent flying mobs from flying too far away
        flying.setRemoveWhenFarAway(false);
    }
    
    /**
     * Schedules aggressive targeting behavior for dungeon mobs
     */
    private void scheduleAggressiveTargeting(LivingEntity entity, String dungeonKey) {
        // Schedule a repeating task to ensure mobs always target players
        int interval = getConfig().getInt("aggressive-mobs.targeting-interval", 40);
        
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (entity.isDead() || !entity.isValid()) return;
            
            // Skip if entity already has a valid player target
            try {
                if (entity instanceof org.bukkit.entity.Mob mob && 
                    mob.getTarget() != null && 
                    mob.getTarget() instanceof Player) {
                    return;
                }
            } catch (Exception e) {
                // Entity doesn't support targeting, continue
            }
            
            // Find nearest player in this dungeon
            Set<UUID> players = activeDungeonPlayers.get(dungeonKey);
            if (players == null || players.isEmpty()) return;
            
            Player nearestPlayer = null;
            double minDistance = Double.MAX_VALUE;
            
            for (UUID playerId : players) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.getWorld().equals(entity.getWorld())) {
                    double distance = player.getLocation().distanceSquared(entity.getLocation());
                    if (distance < minDistance) {
                        minDistance = distance;
                        nearestPlayer = player;
                    }
                }
            }
            
            if (nearestPlayer != null) {
                // Set target based on mob type
                try {
                    if (entity instanceof org.bukkit.entity.Mob mob) {
                        mob.setTarget(nearestPlayer);
                    }
                } catch (Exception e) {
                    // Mob doesn't support targeting
                }
            }
        }, interval, interval);
    }
    
    /**
     * Applies atmospheric effects (time and weather) based on dungeon mobs and boss
     */
    private void applyDungeonAtmosphere(DungeonDefinition dd, World world) {
        if (!getConfig().getBoolean("atmospheric-effects.enabled", true)) {
            return; // Atmospheric effects disabled
        }
        
        // Only apply atmospheric effects in dungeon worlds
        if (!isDungeonWorld(world)) {
            return; // Not a dungeon world, skip atmospheric effects
        }
        
        try {
            // Analyze dungeon composition to determine appropriate atmosphere
            AtmosphereType atmosphereType = determineDungeonAtmosphere(dd);
            
            // Apply time and weather based on atmosphere type
            switch (atmosphereType) {
                case UNDEAD_CRYPT -> {
                    // Dark, stormy atmosphere for undead dungeons
                    world.setTime(18000); // Midnight
                    if (getConfig().getBoolean("atmospheric-effects.weather-control", true)) {
                        world.setStorm(true);
                        world.setThundering(true);
                    }
                    getLogger().info("Applied UNDEAD_CRYPT atmosphere to " + dd.key() + ": midnight storm");
                }
                case FIRE_REALM -> {
                    // Sunset/dusk for fire-based dungeons
                    world.setTime(13000); // Sunset
                    if (getConfig().getBoolean("atmospheric-effects.weather-control", true)) {
                        world.setStorm(false);
                        world.setThundering(false);
                    }
                    getLogger().info("Applied FIRE_REALM atmosphere to " + dd.key() + ": sunset clear");
                }
                case SHADOW_DOMAIN -> {
                    // Deep night for shadow/phantom dungeons
                    world.setTime(20000); // Late night
                    if (getConfig().getBoolean("atmospheric-effects.weather-control", true)) {
                        world.setStorm(false);
                        world.setThundering(false);
                    }
                    getLogger().info("Applied SHADOW_DOMAIN atmosphere to " + dd.key() + ": late night");
                }
                case NATURE_CORRUPTED -> {
                    // Overcast day for spider/nature-corrupted dungeons
                    world.setTime(8000); // Afternoon
                    if (getConfig().getBoolean("atmospheric-effects.weather-control", true)) {
                        world.setStorm(true);
                        world.setThundering(false);
                    }
                    getLogger().info("Applied NATURE_CORRUPTED atmosphere to " + dd.key() + ": overcast afternoon");
                }
                case DRAGON_LAIR -> {
                    // Dynamic atmosphere for dragon lairs
                    world.setTime(16000); // Dusk
                    if (getConfig().getBoolean("atmospheric-effects.weather-control", true)) {
                        world.setStorm(true);
                        world.setThundering(true);
                    }
                    getLogger().info("Applied DRAGON_LAIR atmosphere to " + dd.key() + ": stormy dusk");
                }
                case NORMAL -> {
                    // Default atmosphere - noon, clear
                    world.setTime(6000); // Noon
                    if (getConfig().getBoolean("atmospheric-effects.weather-control", true)) {
                        world.setStorm(false);
                        world.setThundering(false);
                    }
                    getLogger().info("Applied NORMAL atmosphere to " + dd.key() + ": clear noon");
                }
            }
            
            // Send atmospheric message to players if enabled
            if (getConfig().getBoolean("atmospheric-effects.announce-changes", true)) {
                Set<UUID> players = activeDungeonPlayers.get(dd.key());
                if (players != null) {
                    String atmosphereMessage = getAtmosphereMessage(atmosphereType);
                    for (UUID playerId : players) {
                        Player player = Bukkit.getPlayer(playerId);
                        if (player != null) {
                            player.sendMessage(atmosphereMessage);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            getLogger().warning("Failed to apply atmospheric effects to " + dd.key() + ": " + e.getMessage());
        }
    }
    
    /**
     * Determines the atmosphere type based on dungeon mobs and boss
     */
    private AtmosphereType determineDungeonAtmosphere(DungeonDefinition dd) {
        int undeadCount = 0;
        int fireCount = 0;
        int shadowCount = 0;
        int natureCount = 0;
        boolean hasDragon = false;
        
        // Analyze regular mobs
        for (MobDefinition mob : dd.mobs()) {
            String mobType = mob.type().toUpperCase();
            switch (mobType) {
                case "ZOMBIE", "SKELETON", "WITHER_SKELETON", "ZOMBIE_PIGMAN", "STRAY", "HUSK" -> undeadCount++;
                case "BLAZE", "MAGMA_CUBE", "GHAST" -> fireCount++;
                case "PHANTOM", "VEX", "EVOKER", "VINDICATOR" -> shadowCount++;
                case "SPIDER", "CAVE_SPIDER", "WITCH", "CREEPER" -> natureCount++;
                case "ENDER_DRAGON" -> hasDragon = true;
            }
        }
        
        // Analyze boss
        if (dd.boss() != null && dd.boss().enabled()) {
            String bossType = dd.boss().type().toUpperCase();
            switch (bossType) {
                case "ZOMBIE", "SKELETON", "WITHER_SKELETON", "WITHER" -> undeadCount += 3; // Boss counts as 3
                case "BLAZE", "MAGMA_CUBE", "GHAST", "GIANT" -> fireCount += 3; // Giant is volcanic/fire-themed
                case "PHANTOM", "VEX", "EVOKER", "VINDICATOR" -> shadowCount += 3;
                case "SPIDER", "CAVE_SPIDER", "WITCH" -> natureCount += 3;
                case "ENDER_DRAGON" -> hasDragon = true;
            }
        }
        
        // Determine atmosphere based on mob composition
        if (hasDragon) {
            return AtmosphereType.DRAGON_LAIR;
        } else if (undeadCount >= fireCount && undeadCount >= shadowCount && undeadCount >= natureCount) {
            return AtmosphereType.UNDEAD_CRYPT;
        } else if (fireCount >= shadowCount && fireCount >= natureCount) {
            return AtmosphereType.FIRE_REALM;
        } else if (shadowCount >= natureCount) {
            return AtmosphereType.SHADOW_DOMAIN;
        } else if (natureCount > 0) {
            return AtmosphereType.NATURE_CORRUPTED;
        } else {
            return AtmosphereType.NORMAL;
        }
    }
    
    /**
     * Gets the atmosphere announcement message for players
     */
    private String getAtmosphereMessage(AtmosphereType atmosphereType) {
        return switch (atmosphereType) {
            case UNDEAD_CRYPT -> "§8§l⚡ §7The air grows cold as undead forces stir... §8§l⚡";
            case FIRE_REALM -> "§c§l🔥 §6The sky burns with an ominous glow... §c§l🔥";
            case SHADOW_DOMAIN -> "§5§l🌙 §8Shadows deepen as dark entities awaken... §5§l🌙";
            case NATURE_CORRUPTED -> "§2§l🕷 §aThe very nature seems corrupted and hostile... §2§l🕷";
            case DRAGON_LAIR -> "§4§l🐲 §eThe heavens tremble before an ancient power... §4§l🐲";
            case NORMAL -> "§e§l☀ §7The atmosphere feels unusually calm... §e§l☀";
        };
    }
    
    /**
     * Atmosphere types for different dungeon themes
     */
    private enum AtmosphereType {
        UNDEAD_CRYPT,    // Midnight + Storm (Zombies, Skeletons, Wither)
        FIRE_REALM,      // Sunset + Clear (Blazes, Magma Cubes, Ghasts)
        SHADOW_DOMAIN,   // Late Night + Clear (Phantoms, Vexes, Evokers)
        NATURE_CORRUPTED,// Afternoon + Rain (Spiders, Witches, Creepers)
        DRAGON_LAIR,     // Dusk + Thunderstorm (Ender Dragons)
        NORMAL           // Noon + Clear (Default)
    }
    
    /**
     * Sets up boss health display for all players in the dungeon
     */
    private void setupBossHealthDisplay(String dungeonKey, LivingEntity boss) {
        if (!getConfig().getBoolean("boss-health-display.enabled", true)) {
            return; // Boss health display disabled
        }
        
        try {
            Set<UUID> players = activeDungeonPlayers.get(dungeonKey);
            if (players == null || players.isEmpty()) {
                return;
            }
            
            String bossName = getBossDisplayName(boss, dungeonKey);
            
            // Create boss bar for visual health tracking
            if (getConfig().getBoolean("boss-health-display.use-boss-bar", true)) {
                boolean witherStyle = getConfig().getString("boss-health-display.style", "wither").equalsIgnoreCase("wither");
                String confColor = getConfig().getString("boss-health-display.color", "AUTO");
                BarColor barColor = getConfiguredOrTypeColor(confColor, boss);
                // Create or reuse an existing BossBar for this dungeon
                BossBar bar = bossHealthBars.computeIfAbsent(dungeonKey, k -> {
                    BossBar b = Bukkit.createBossBar(bossName, barColor, witherStyle ? BarStyle.SOLID : BarStyle.SEGMENTED_20);
                    b.setVisible(true);
                    return b;
                });
                bar.setTitle(bossName);
                bar.setColor(barColor);
                double maxHealth = boss.getAttribute(Attribute.valueOf("GENERIC_MAX_HEALTH")).getValue();
                double progress = Math.max(0.0, Math.min(1.0, boss.getHealth() / Math.max(1.0, maxHealth)));
                bar.setProgress(progress);
                // Add only players actually in the dungeon world
                updateBossBarPlayers(dungeonKey);
                getLogger().info("Boss bar initialized for " + bossName + " in dungeon " + dungeonKey);
            }
            
            // Send initial health message to all players
            double maxHealth = boss.getAttribute(Attribute.valueOf("GENERIC_MAX_HEALTH")).getValue();
            String healthMessage = formatBossHealthMessage(bossName, boss.getHealth(), maxHealth);
            
            for (UUID playerId : players) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    player.sendMessage(healthMessage);
                }
            }
            
            // Reset milestone announcements for this dungeon
            announcedBossMilestones.put(dungeonKey, new HashSet<>());

            // Schedule health updates
            scheduleBossHealthUpdates(dungeonKey, boss, bossName);
            
        } catch (Exception e) {
            getLogger().warning("Failed to setup boss health display for " + dungeonKey + ": " + e.getMessage());
        }
    }
    
    /**
     * Schedules periodic boss health updates for players
     */
    private void scheduleBossHealthUpdates(String dungeonKey, LivingEntity boss, String bossName) {
        int updateInterval = getConfig().getInt("boss-health-display.update-interval", 20); // Default 1 second

        BukkitRunnable updater = new BukkitRunnable() {
            @Override
            public void run() {
                // If boss died or dungeon no longer active, cleanup and stop
                if (boss.isDead() || !activeDungeonPlayers.containsKey(dungeonKey)) {
                    removeBossHealthBar(dungeonKey);
                    cancel();
                    return;
                }

                Set<UUID> players = activeDungeonPlayers.get(dungeonKey);
                if (players == null || players.isEmpty()) {
                    // No players currently; still keep tracking but hide from all
                    BossBar bar = bossHealthBars.get(dungeonKey);
                    if (bar != null) bar.removeAll();
                    return;
                }

                try {
                    double currentHealth = Math.max(0.0, boss.getHealth());
                    double maxHealth = boss.getAttribute(Attribute.valueOf("GENERIC_MAX_HEALTH")).getValue();
                    double healthPercent = Math.max(0.0, Math.min(100.0, (currentHealth / Math.max(1.0, maxHealth)) * 100.0));
                    // No per-tick critical chat; rely on milestone cues only

                    // Update BossBar progress and color if enabled
                    if (getConfig().getBoolean("boss-health-display.use-boss-bar", true)) {
                        boolean witherStyle = getConfig().getString("boss-health-display.style", "wither").equalsIgnoreCase("wither");
                        String confColor = getConfig().getString("boss-health-display.color", "AUTO");
                        BarColor barColor = getConfiguredOrTypeColor(confColor, boss);
                        BossBar bar = bossHealthBars.computeIfAbsent(dungeonKey, k -> Bukkit.createBossBar(bossName, barColor, witherStyle ? BarStyle.SOLID : BarStyle.SEGMENTED_20));
                        bar.setTitle(bossName);
                        bar.setProgress(Math.max(0.0, Math.min(1.0, currentHealth / Math.max(1.0, maxHealth))));
                        bar.setColor(barColor); // Adapt color to boss type (or config override)
                        // Ensure correct viewers
                        updateBossBarPlayers(dungeonKey);
                    }

                    // Suppress action bar/chat spam during updates

                    // Health milestone cues (fire once per configured threshold)
                    List<Integer> milestones = getConfig().getIntegerList("boss-health-display.health-percentage-milestones");
                    if (milestones == null || milestones.isEmpty()) milestones = java.util.Arrays.asList(75, 50, 25, 10);
                    // Send at most one milestone per update (highest not-yet-announced)
                    milestones.sort(java.util.Collections.reverseOrder());
                    Set<Integer> announced = announcedBossMilestones.computeIfAbsent(dungeonKey, k -> new HashSet<>());
                    for (int m : milestones) {
                        if (healthPercent <= m && !announced.contains(m)) {
                            announced.add(m);
                            String msg;
                            if (m >= 75) msg = "§e⚔ " + bossName + " §eis weakening... §e⚔";
                            else if (m >= 50) msg = "§6⚔ " + bossName + " §6is half defeated! §6⚔";
                            else if (m >= 25) msg = "§c⚔ " + bossName + " §cis near death! §c⚔";
                            else msg = "§4⚔ " + bossName + " §4is on the brink of defeat! §4⚔";
                            sendBossHealthMilestone(players, bossName, m + "%", msg);
                            break;
                        }
                    }
                } catch (Exception e) {
                    getLogger().warning("Error updating boss health display: " + e.getMessage());
                }
            }
        };

        BukkitTask task = updater.runTaskTimer(this, updateInterval, updateInterval);
        // Cancel any previous task for this dungeon and replace
        BukkitTask old = bossHealthTasks.put(dungeonKey, task);
        if (old != null) old.cancel();
    }

    // Update BossBar viewers to only players in the dungeon and in the dungeon world
    private void updateBossBarPlayers(String dungeonKey) {
        BossBar bar = bossHealthBars.get(dungeonKey);
        if (bar == null) return;
        Set<UUID> players = activeDungeonPlayers.getOrDefault(dungeonKey, Collections.emptySet());
        // Remove all then add correct viewers (small N, simple and correct)
        for (Player p : new ArrayList<>(bar.getPlayers())) {
            if (p == null || !players.contains(p.getUniqueId()) || !isDungeonWorld(p.getWorld())) {
                bar.removePlayer(p);
            }
        }
        for (UUID id : players) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && isDungeonWorld(p.getWorld()) && !bar.getPlayers().contains(p)) {
                bar.addPlayer(p);
            }
        }
    }

    // Remove and cleanup the BossBar and scheduled task for a dungeon
    private void removeBossHealthBar(String dungeonKey) {
        BukkitTask t = bossHealthTasks.remove(dungeonKey);
        if (t != null) {
            try { t.cancel(); } catch (Exception ignored) {}
        }
        BossBar bar = bossHealthBars.remove(dungeonKey);
        if (bar != null) {
            try { bar.removeAll(); } catch (Exception ignored) {}
        }
    announcedBossMilestones.remove(dungeonKey);
    }
    
    /**
     * Gets display name for boss based on type and dungeon
     */
    private String getBossDisplayName(LivingEntity boss, String dungeonKey) {
        String customName = boss.getCustomName();
        if (customName != null && !customName.isEmpty()) {
            return customName;
        }
        
        String bossType = boss.getType().name();
        DungeonDefinition dd = dungeons.get(dungeonKey);
        
        return switch (bossType.toUpperCase()) {
            case "ENDER_DRAGON" -> "§5§l🐲 Ancient Dragon";
            case "WITHER" -> "§8§l💀 Wither Lord";
            case "ZOMBIE" -> "§2§l🧟 Undead Champion";
            case "SKELETON" -> "§f§l💀 Skeleton King";
            case "BLAZE" -> "§c§l🔥 Inferno Lord";
            case "GIANT" -> "§4§l🔥 Volcanic Titan";
            case "SPIDER" -> "§8§l🕷 Spider Queen";
            case "WITCH" -> "§5§l🔮 Dark Witch";
            case "PHANTOM" -> "§9§l👻 Shadow Phantom";
            case "EVOKER" -> "§4§l✨ Master Evoker";
            default -> "§6§l⚔ " + dungeonKey + " Boss";
        };
    }
    
    /**
     * Formats boss health message for chat
     */
    private String formatBossHealthMessage(String bossName, double currentHealth, double maxHealth) {
        double healthPercent = (currentHealth / maxHealth) * 100;
        String healthBar = createHealthBar(healthPercent);
        
        return String.format("§6§l═══ %s §6§lHEALTH ═══\n%s §f%.0f§7/§f%.0f §7(§a%.1f%%§7)",
                bossName, healthBar, currentHealth, maxHealth, healthPercent);
    }
    
    /**
     * Formats boss health for action bar display
     */
    private String formatBossHealthActionBar(String bossName, double currentHealth, double maxHealth) {
        double healthPercent = (currentHealth / maxHealth) * 100;
        String healthBar = createHealthBar(healthPercent);
        
        return String.format("%s %s §f%.0f§7/§f%.0f",
                bossName, healthBar, currentHealth, maxHealth);
    }
    
    /**
     * Creates a visual health bar based on health percentage
     */
    private String createHealthBar(double healthPercent) {
        int barLength = 20;
        int filledBars = (int) Math.round((healthPercent / 100.0) * barLength);
        
        StringBuilder healthBar = new StringBuilder();
        
        // Choose color based on health percentage
        String healthColor;
        if (healthPercent > 75) {
            healthColor = "§a"; // Green
        } else if (healthPercent > 50) {
            healthColor = "§e"; // Yellow
        } else if (healthPercent > 25) {
            healthColor = "§6"; // Orange
        } else {
            healthColor = "§c"; // Red
        }
        
        // Build the health bar
        healthBar.append("[");
        for (int i = 0; i < barLength; i++) {
            if (i < filledBars) {
                healthBar.append(healthColor).append("█");
            } else {
                healthBar.append("§8█");
            }
        }
        healthBar.append("§f]");
        
        return healthBar.toString();
    }
    
    /**
     * Sends boss health milestone message to players
     */
    private void sendBossHealthMilestone(Set<UUID> players, String bossName, String milestone, String message) {
        for (UUID playerId : players) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(message);
                // Add sound effect if enabled
                if (getConfig().getBoolean("boss-health-display.milestone-sounds", true)) {
                    try {
                        player.playSound(player.getLocation(), "entity.ender_dragon.growl", 0.7f, 1.2f);
                    } catch (Exception soundError) {
                        // Sound not available, skip
                    }
                }
            }
        }
    }

    // Resolve BossBar color either from config override or adapt based on boss type
    private BarColor getConfiguredOrTypeColor(String confColor, LivingEntity boss) {
        if (confColor != null && !confColor.equalsIgnoreCase("AUTO")) {
            try {
                return BarColor.valueOf(confColor.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Fall through to auto color
            }
        }
        EntityType type = boss.getType();
        switch (type) {
            case ENDER_DRAGON:
            case PHANTOM:
            case ENDERMAN:
                return BarColor.PURPLE;
            case WITHER:
            case WITHER_SKELETON:
            case SKELETON:
                return BarColor.WHITE;
            case ZOMBIE:
            case HUSK:
            case DROWNED:
            case ZOGLIN:
                return BarColor.GREEN;
            case BLAZE:
            case MAGMA_CUBE:
            case GHAST:
            case PIGLIN_BRUTE:
                return BarColor.RED;
            case SPIDER:
            case CAVE_SPIDER:
                return BarColor.BLUE;
            case WITCH:
            case EVOKER:
            case VEX:
            case ILLUSIONER:
                return BarColor.PINK;
            case GUARDIAN:
            case ELDER_GUARDIAN:
            case WARDEN:
                return BarColor.BLUE;
            case RAVAGER:
            case IRON_GOLEM:
                return BarColor.YELLOW;
            default:
                return BarColor.RED;
        }
    }

    private void spawnBoss(BossDefinition boss, Location loc, String dungeonKey, double scale){
        // Make boss larger than regular mobs - scale up the size by at least 1, max of 1.5x
        int bossSize = Math.max((int)(boss.size() * 1.5), boss.size() + 1);
        
        // Check if this dungeon has minions (regular mobs)
        DungeonDefinition dd = dungeons.get(dungeonKey);
        boolean hasMinions = dd != null && dd.mobs() != null && !dd.mobs().isEmpty();
        
        // If boss has minions, make boss slower than minions
        double bossSpeed = boss.speed();
        if (hasMinions) {
            // Calculate average minion speed
            double avgMinionSpeed = dd.mobs().stream()
                .mapToDouble(MobDefinition::speed)
                .average()
                .orElse(0.25);
            
            // Make boss 30% slower than average minion speed (but not less than 0.1)
            bossSpeed = Math.max(avgMinionSpeed * 0.7, 0.1);
        }
        
        MobDefinition bossMob = new MobDefinition(boss.type(), boss.name(), boss.health(), boss.damage(), bossSpeed, bossSize, boss.mythicId());
        LivingEntity bossEntity = spawnMob(bossMob, loc, dungeonKey, scale, true);
        
        // Set boss facing direction globally (ignore per-dungeon config)
        if (bossEntity != null) {
            // Always face EAST (towards +X): yaw = -90.0f
            Location facingLoc = bossEntity.getLocation();
            facingLoc.setYaw(-90.0f);
            facingLoc.setPitch(0.0f);
            bossEntity.teleport(facingLoc);
            
            // Setup boss health display system
            setupBossHealthDisplay(dungeonKey, bossEntity);
        }
    }

    /* ---------------- Events ---------------- */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        
        // Get the boss entity UUID for this damage event
        UUID entityId = entity.getUniqueId();
        String bossDungeon = bossToDungeon.get(entityId);
        
        // If this isn't a boss being damaged, ignore
        if (bossDungeon == null) return;
        
        // Check if there are still regular minions alive for this dungeon (optimized)
        Integer minionCount = dungeonMobCounts.get(bossDungeon);
        boolean minionsAlive = (minionCount != null && minionCount > 0);
        
        if (minionsAlive) {
            // Minions still alive - boss is immune to damage
            event.setCancelled(true);
            
            // Send message to damage source if it's a player (throttled)
            if (event.getDamageSource().getCausingEntity() instanceof Player player) {
                long now = System.currentTimeMillis();
                Long last = bossProtectedMsgCooldown.get(player.getUniqueId());
                if (last == null || now - last > 3000) { // 3s cooldown
                    bossProtectedMsgCooldown.put(player.getUniqueId(), now);
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                        "&6&lBubble&bCraft &8» &cThe boss is protected by its minions! Defeat all minions first."));
                }
            }
        }
    }

    @EventHandler public void onMobDeath(EntityDeathEvent e){ 
        UUID id=e.getEntity().getUniqueId(); 
        String dungeon=mobToDungeon.remove(id); 
        String bossDungeon=bossToDungeon.remove(id);
        
        Location deathLocation = e.getEntity().getLocation();
        
        // Handle boss death
        if(bossDungeon!=null){
            // Play enhanced dramatic boss death sequence
            playEnhancedBossDeathSequence(deathLocation, bossDungeon);
            // Reward boss coins to all participants
            rewardBossCoins(bossDungeon);
            // Remove boss health bar immediately on boss death
            removeBossHealthBar(bossDungeon);
            
            // Check if all regular mobs are also dead before completing
            Integer remainingMobs = dungeonMobCounts.get(bossDungeon);
            boolean allMobsDead = (remainingMobs == null || remainingMobs <= 0);
            
            if (allMobsDead) {
                // Both boss and all mobs are dead - start dramatic completion sequence
                scheduleDelayedDungeonCompletion(bossDungeon, deathLocation);
            } else {
                // Boss died but mobs remain - notify players
                Set<UUID> players = activeDungeonPlayers.getOrDefault(bossDungeon, Collections.emptySet());
                for (UUID playerId : players) {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                            "&6&lBubble&bCraft &8» &eThe boss has fallen, but minions remain! Defeat all enemies to complete the dungeon."));
                    }
                }
            }
            return;
        }
        
        // Regular mob death - update mob count cache and check completion
        if(dungeon==null) return; 
        
        // Play mob death effect
        particleEffects.playMobDeathEffect(deathLocation);
        
        // Update mob count cache for performance and get accurate count
        dungeonMobCounts.merge(dungeon, -1, (current, decrement) -> Math.max(0, current + decrement));
        
        // Get accurate count by checking actual living mobs (more reliable than cache)
        int actualMobCount = getRemainingMobCount(dungeon);
        
        // Update cache with accurate count to keep it synchronized
        dungeonMobCounts.put(dungeon, actualMobCount);
        
        if (getConfig().getBoolean("player-scaling.debug-mode", false)) {
            getLogger().info("[Mob Death] Dungeon: " + dungeon + ", Remaining mobs: " + actualMobCount);
        }
        
        DungeonDefinition dd=dungeons.get(dungeon); 
        if(dd==null) return;
        
        // If dungeon has a boss, don't complete on regular mob death but check if all minions dead
        if(dd.boss() != null && dd.boss().enabled()) {
            // Check if any other minions remain using accurate count
            boolean anyMinionsLeft = (actualMobCount > 0);
            
            if (!anyMinionsLeft) {
                // If wave mode is enabled and there are more waves, start next wave instead of making boss vulnerable
                boolean waveMode = getConfig().getBoolean("features.wave-system", false);
                if (waveMode && !testRunMode.contains(dungeon)) {
                    int totalWaves = Math.max(1, getConfig().getInt("waves.count", 2));
                    int curWave = waveState.getOrDefault(dungeon, 1);
                    if (curWave < totalWaves) {
                        scheduleNextWave(dd, curWave + 1);
                        updateHud(dungeon);
                        return;
                    }
                }

                // All minions dead and no more waves - boss becomes vulnerable!
                Set<UUID> players = activeDungeonPlayers.getOrDefault(dungeon, Collections.emptySet());
                // Find boss location for particle effect
                UUID bossId = dungeonToBoss.get(dungeon);
                if (bossId != null) {
                    for (World world : Bukkit.getWorlds()) {
                        for (LivingEntity entity : world.getLivingEntities()) {
                            if (entity.getUniqueId().equals(bossId)) {
                                particleEffects.playBossVulnerableEffect(entity.getLocation());
                                break;
                            }
                        }
                    }
                }
                for (UUID playerId : players) {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                            "&6&lBubble&bCraft &8» &aAll minions defeated! The boss is now vulnerable to damage!"));
                    }
                }
                getLogger().info("All minions defeated in dungeon " + dungeon + " - boss is now vulnerable!");
            }
            
            updateHud(dungeon); 
            return;
        }
        
        // Logic for dungeons without bosses OR when all mobs are dead
        Integer remainingMobs = dungeonMobCounts.get(dungeon);
        boolean anyLeft = (remainingMobs != null && remainingMobs > 0); 
        if(anyLeft){ updateHud(dungeon); return; } 
        
        // Check if boss also exists and is dead for boss dungeons
        if(dd.boss() != null && dd.boss().enabled()) {
            // For boss dungeons, only complete when both mobs AND boss are dead
            boolean bossStillAlive = bossToDungeon.values().contains(dungeon);
            if (bossStillAlive) {
                // All mobs dead but boss still alive - just update HUD
                updateHud(dungeon);
                return;
            }
            // Both mobs and boss are dead - proceed to completion
        }
        
        boolean waveMode=getConfig().getBoolean("features.wave-system", false); 
        if(waveMode && !testRunMode.contains(dungeon)){ 
            int totalWaves = Math.max(1, getConfig().getInt("waves.count", 2));
            int cur=waveState.getOrDefault(dungeon,1); 
            if(cur < totalWaves){ 
                scheduleNextWave(dd, cur + 1);
                return; 
            } else { 
                waveState.remove(dungeon); 
            }
        } 
        
        completeDungeon(dungeon);
    }

    /**
     * Schedule the next wave after a configurable delay and announce to players
     */
    private void scheduleNextWave(DungeonDefinition dd, int nextWave) {
        final String dungeonKey = dd.key();
        int totalWaves = Math.max(1, getConfig().getInt("waves.count", 2));

        // Determine delay for this wave
        int delaySeconds = 0;
        List<Integer> delays = getConfig().getIntegerList("waves.delays-seconds");
        if (delays != null && !delays.isEmpty()) {
            int idx = Math.min(Math.max(0, nextWave - 1), delays.size() - 1);
            delaySeconds = Math.max(0, delays.get(idx));
        } else {
            delaySeconds = Math.max(0, getConfig().getInt("waves.delay-seconds", 5));
        }

        // Announce upcoming wave
        announceWave(dungeonKey, nextWave, totalWaves, delaySeconds);

        // Schedule the wave start
        int ticks = delaySeconds * 20;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            // If dungeon was cleaned up or wave progressed, skip
            int cur = waveState.getOrDefault(dungeonKey, 1);
            if (nextWave <= cur) return;
            waveState.put(dungeonKey, nextWave);
            spawnDungeonMobs(dd);
        }, Math.max(1, ticks));
    }

    /**
     * Sends a wave announcement message and sound to all players in the dungeon
     */
    private void announceWave(String dungeonKey, int wave, int totalWaves, int delaySeconds) {
        String messageTemplate;
        if (wave == totalWaves) {
            messageTemplate = getConfig().getString("waves.final-wave-message", "§6§l⚔ FINAL WAVE! Prepare for the ultimate challenge!");
        } else {
            messageTemplate = getConfig().getString("waves.next-wave-message", "§e§lWave {wave}/{total} starting in {delay}s!");
        }
        String msg = messageTemplate
                .replace("{wave}", String.valueOf(wave))
                .replace("{total}", String.valueOf(totalWaves))
                .replace("{delay}", String.valueOf(delaySeconds))
                .replace("{dungeon}", dungeonKey);

        Set<UUID> players = activeDungeonPlayers.getOrDefault(dungeonKey, Collections.emptySet());
        for (UUID pid : players) {
            Player p = Bukkit.getPlayer(pid);
            if (p != null) {
                p.sendMessage(msg);
                String soundKey = getConfig().getString("waves.wave-start-sound", "entity.wither.spawn");
                try { p.playSound(p.getLocation(), soundKey, 0.8f, 1.0f); } catch (Exception ignored) {}
            }
        }
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player player = e.getEntity();
        UUID playerId = player.getUniqueId();
        
        // Only handle deaths in dungeon worlds
        if (!isDungeonWorld(player.getWorld())) {
            return;
        }
        
        // Find which dungeon this player is in
        String playerDungeon = getPlayerDungeon(playerId);
        if (playerDungeon == null) return;
        
        // Check if all players in this dungeon are now dead
        Set<UUID> dungeonPlayers = activeDungeonPlayers.get(playerDungeon);
        if (dungeonPlayers == null || dungeonPlayers.isEmpty()) return;
        
        boolean allPlayersDead = true;
        for (UUID dungeonPlayerId : dungeonPlayers) {
            Player dungeonPlayer = Bukkit.getPlayer(dungeonPlayerId);
            if (dungeonPlayer != null && dungeonPlayer.isOnline() && !dungeonPlayer.isDead()) {
                allPlayersDead = false;
                break;
            }
        }
        
        if (allPlayersDead) {
            // All players are dead - fail the dungeon
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&6&lBubble&bCraft &8» &cAll players have died! Dungeon failed."));
            
            // Clean up dungeon state
            cleanupFailedDungeon(playerDungeon);
        }
    }
    
    private void cleanupFailedDungeon(String dungeonKey) {
        // Find dungeon definition for failure effect location
        DungeonDefinition dd = dungeons.get(dungeonKey);
        if (dd != null) {
            World dungeonWorld = Bukkit.getWorld(dd.worldName());
            if (dungeonWorld != null) {
                Location dungeonCenter = new Location(dungeonWorld, dd.x() + 0.5, dd.y(), dd.z() + 0.5);
                // Play dramatic failure effect
                particleEffects.playFailureEffect(dungeonCenter);
            }
        }
        
        // Remove all mobs and bosses from the dungeon
        removeAllDungeonEntities(dungeonKey);
        
        // Clear tracking data
        mobToDungeon.entrySet().removeIf(entry -> entry.getValue().equals(dungeonKey));
        bossToDungeon.entrySet().removeIf(entry -> entry.getValue().equals(dungeonKey));
        
        // Clear mob count cache
        dungeonMobCounts.remove(dungeonKey);
        
        // Clear player tracking
        Set<UUID> players = activeDungeonPlayers.remove(dungeonKey);
        if (players != null) {
            for (UUID playerId : players) {
                // Safely return dead/surviving players to main world
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    returnPlayerToMainWorld(player, dungeonKey);
                }
            }
        }
        
        // Clear other state
        dungeonStartTime.remove(dungeonKey);
        waveState.remove(dungeonKey);
        testRunMode.remove(dungeonKey);
        
        getLogger().info("Dungeon " + dungeonKey + " failed - all players died");
    }
    
    private void removeAllDungeonEntities(String dungeonKey) {
        // Find all worlds to search for entities
        for (World world : Bukkit.getWorlds()) {
            // Remove all living entities that belong to this dungeon
            world.getLivingEntities().removeIf(entity -> {
                UUID entityId = entity.getUniqueId();
                String entityDungeon = mobToDungeon.get(entityId);
                if (entityDungeon != null && entityDungeon.equals(dungeonKey)) {
                    entity.remove();
                    return true;
                }
                
                String bossDungeon = bossToDungeon.get(entityId);
                if (bossDungeon != null && bossDungeon.equals(dungeonKey)) {
                    entity.remove();
                    return true;
                }
                
                return false;
            });
        }
        
        getLogger().info("Removed all entities from failed dungeon: " + dungeonKey);
    }
    
    /* ---------------- Enhanced Boss Death & Completion System ---------------- */
    
    /**
     * Plays an enhanced dramatic boss death sequence with multiple effects
     */
    private void playEnhancedBossDeathSequence(Location deathLocation, String dungeonKey) {
        World world = deathLocation.getWorld();
        if (world == null) return;
        
        // Get all players in the dungeon for targeted effects
        Set<UUID> players = activeDungeonPlayers.getOrDefault(dungeonKey, Collections.emptySet());
        Collection<Player> onlinePlayers = players.stream()
            .map(Bukkit::getPlayer)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toList());
        
        // Immediate dramatic screen effect for all players
        for (Player player : onlinePlayers) {
            player.sendTitle(
                ChatColor.translateAlternateColorCodes('&', "&4&l⚔ BOSS DEFEATED! ⚔"), 
                ChatColor.translateAlternateColorCodes('&', "&6Collecting your rewards..."), 
                10, 60, 20
            );
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
        }
        
        // Enhanced particle sequence
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 100) { // Extended to 5 seconds
                    cancel();
                    return;
                }
                
                // Multiple overlapping effects for maximum drama
                
                // 1. Expanding shockwave rings
                if (ticks % 8 == 0) {
                    double radius = (ticks / 8) * 2.0;
                    for (int i = 0; i < 32; i++) {
                        double angle = (2 * Math.PI * i) / 32;
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;
                        
                        Location ringLoc = deathLocation.clone().add(x, 0.2, z);
                        world.spawnParticle(org.bukkit.Particle.EXPLOSION, ringLoc, 1, 0, 0, 0, 0);
                        world.spawnParticle(org.bukkit.Particle.FLAME, ringLoc.clone().add(0, 1, 0), 2, 0.1, 0.1, 0.1, 0.05);
                        world.spawnParticle(org.bukkit.Particle.LAVA, ringLoc.clone().add(0, 0.5, 0), 1, 0, 0, 0, 0);
                    }
                    world.playSound(deathLocation, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.5f + (ticks * 0.01f));
                }
                
                // 2. Spiraling energy ascension
                if (ticks % 3 == 0) {
                    for (int spiral = 0; spiral < 3; spiral++) {
                        double height = (ticks % 60) * 0.1;
                        double spiralAngle = (ticks * 0.3) + (spiral * 2.094); // 120 degrees apart
                        double spiralRadius = 1.5 - (height * 0.05);
                        
                        double x = Math.cos(spiralAngle) * spiralRadius;
                        double z = Math.sin(spiralAngle) * spiralRadius;
                        
                        Location spiralLoc = deathLocation.clone().add(x, height, z);
                        world.spawnParticle(org.bukkit.Particle.END_ROD, spiralLoc, 1, 0, 0, 0, 0);
                        world.spawnParticle(org.bukkit.Particle.DRAGON_BREATH, spiralLoc, 2, 0.1, 0.1, 0.1, 0.02);
                    }
                }
                
                // 3. Central pillar of light
                if (ticks % 5 == 0) {
                    for (int y = 0; y < 10; y++) {
                        Location pillarLoc = deathLocation.clone().add(0, y * 0.5, 0);
                        world.spawnParticle(org.bukkit.Particle.GLOW, pillarLoc, 3, 0.2, 0.1, 0.2, 0);
                        world.spawnParticle(org.bukkit.Particle.ENCHANT, pillarLoc, 5, 0.3, 0.1, 0.3, 0.5);
                    }
                }
                
                // 4. Ground impact cracks
                if (ticks % 10 == 0) {
                    for (int crack = 0; crack < 8; crack++) {
                        double crackAngle = (crack * Math.PI) / 4;
                        for (int dist = 1; dist <= 5; dist++) {
                            double x = Math.cos(crackAngle) * dist;
                            double z = Math.sin(crackAngle) * dist;
                            
                            Location crackLoc = deathLocation.clone().add(x, -0.1, z);
                            world.spawnParticle(org.bukkit.Particle.BLOCK, crackLoc, 3, 0.1, 0.1, 0.1, 0,
                                org.bukkit.Material.BLACKSTONE.createBlockData());
                        }
                    }
                }
                
                // 5. Periodic dramatic moments
                if (ticks == 20) {
                    world.playSound(deathLocation, org.bukkit.Sound.ENTITY_ENDER_DRAGON_DEATH, 0.8f, 1.2f);
                    world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, deathLocation.clone().add(0, 2, 0), 5, 1, 1, 1, 0);
                }
                
                if (ticks == 50) {
                    world.playSound(deathLocation, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.8f);
                    for (Player player : onlinePlayers) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                            "&6&lBubble&bCraft &8» &d✦ &5The boss's power fades... &d✦"));
                    }
                }
                
                if (ticks == 80) {
                    world.playSound(deathLocation, org.bukkit.Sound.BLOCK_END_PORTAL_SPAWN, 0.7f, 1.1f);
                    world.spawnParticle(org.bukkit.Particle.PORTAL, deathLocation.clone().add(0, 1, 0), 100, 2, 2, 2, 1);
                }
                
                ticks++;
            }
        }.runTaskTimer(this, 0L, 1L);
    }
    
    /**
     * Schedules delayed dungeon completion to give players time to appreciate the victory
     */
    private void scheduleDelayedDungeonCompletion(String dungeonKey, Location bossDeathLocation) {
        Set<UUID> players = activeDungeonPlayers.getOrDefault(dungeonKey, Collections.emptySet());
        
        // Immediate notification that victory is achieved
        for (UUID playerId : players) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    "&6&lBubble&bCraft &8» &a&l✦ VICTORY! ✦ &aPreparing your rewards..."));
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
            }
        }
        
        // Schedule the actual completion after dramatic effects
        org.bukkit.scheduler.BukkitScheduler scheduler = Bukkit.getScheduler();
        
        // Phase 1: Victory celebration (2 seconds in)
        scheduler.runTaskLater(this, () -> {
            for (UUID playerId : players) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    player.sendTitle(
                        ChatColor.translateAlternateColorCodes('&', "&6&l★ VICTORY! ★"), 
                        ChatColor.translateAlternateColorCodes('&', "&eYou have conquered the dungeon!"), 
                        10, 40, 10
                    );
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.5f);
                }
            }
        }, 40L); // 2 seconds
        
        // Phase 2: Loot distribution (4 seconds in)
        scheduler.runTaskLater(this, () -> {
            DungeonDefinition dd = dungeons.get(dungeonKey);
            if (dd != null) {
                for (UUID playerId : players) {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                            "&6&lBubble&bCraft &8» &e&l⚡ LOOT ACQUIRED! ⚡"));
                    }
                }
                
                // Give rewards during the dramatic sequence
                if (!testRunMode.contains(dungeonKey)) {
                    rewardPlayers(dd.key(), players);
                    giveLoot(dd.key(), players);
                }
            }
        }, 80L); // 4 seconds
        
        // Phase 3: Final completion (6 seconds total)
        scheduler.runTaskLater(this, () -> {
            completeDungeonImmediate(dungeonKey);
        }, 120L); // 6 seconds
    }
    
    /**
     * Immediate completion without rewards (rewards already given in delayed sequence)
     */
    private void completeDungeonImmediate(String dungeon) {
        DungeonDefinition dd = dungeons.get(dungeon);
        if (dd == null) return;
        
        Set<UUID> players = activeDungeonPlayers.getOrDefault(dungeon, Collections.emptySet());
        
        // Handle advancements and broadcasts (no rewards - already given)
        if (!testRunMode.contains(dungeon)) {
            Bukkit.broadcastMessage(prefix() + "§6Dungeon §e" + dd.key() + " §6completed!");
            
            if (advancementApiPresent) {
                grantAdvancement(dd, players);
                
                // Grant dungeon completion advancement for each player
                for (UUID playerId : players) {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null) {
                        grantDungeonCompletionAdvancement(player, dd.key());
                    }
                }
                
                // Track boss defeats for advanced advancements
                if (dd.boss() != null && dd.boss().enabled()) {
                    for (UUID playerId : players) {
                        playerBossDefeats.computeIfAbsent(playerId, k -> new HashSet<>()).add(dd.key());
                        
                        Player player = Bukkit.getPlayer(playerId);
                        if (player != null) {
                            Set<String> defeatedBosses = playerBossDefeats.get(playerId);
                            
                            if (defeatedBosses.size() == 1) {
                                grantFirstBossAdvancement(player);
                            }
                            
                            Set<String> allBossDungeons = Set.of("sample", "crypt", "volcano");
                            if (defeatedBosses.containsAll(allBossDungeons) && !allBossesDefeatedPlayers.contains(playerId)) {
                                allBossesDefeatedPlayers.add(playerId);
                                grantAllBossesAdvancement(player);
                            }
                        }
                    }
                }
            }
        } else {
            players.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).findFirst()
                    .ifPresent(p -> p.sendMessage(prefix() + "§eTest run finished."));
        }
        
    // Save players to return after queue wait time (teleport later to configured world)
    pendingReturnPlayers.put(dungeon, new HashSet<>(players));
        
        // Clean up dungeon state
        activeDungeonPlayers.remove(dungeon);
        long start = dungeonStartTime.getOrDefault(dd.key(), System.currentTimeMillis());
        long dur = System.currentTimeMillis() - start;
        dungeonStartTime.remove(dungeon);
    hud.clear(dungeon);
    // Clear boss health bar for this dungeon
    removeBossHealthBar(dungeon);
        
        if (!testRunMode.remove(dungeon)) {
            dungeonRuns.put(dd.key(), dungeonRuns.getOrDefault(dd.key(), 0) + 1);
            long avgTime = dungeonTotalTime.getOrDefault(dd.key(), 0L) + dur;
            dungeonTotalTime.put(dd.key(), avgTime);
        }
        
        // Handle dungeon queue system integration
        onDungeonCompletedInQueue(dungeon);
    }
    
    private void completeDungeon(String dungeon){
        // NOTE: This method handles immediate completions (non-boss or legacy calls)
        // For boss deaths, scheduleDelayedDungeonCompletion provides enhanced experience
        DungeonDefinition dd=dungeons.get(dungeon);
        if(dd==null) return;
        
        Set<UUID> players=activeDungeonPlayers.getOrDefault(dungeon,Collections.emptySet()); 
        
        // Get dungeon location for celebration effects
        World dungeonWorld = Bukkit.getWorld(dd.worldName());
        if (dungeonWorld != null) {
            Location dungeonCenter = new Location(dungeonWorld, dd.x() + 0.5, dd.y(), dd.z() + 0.5);
            // Play epic completion celebration
            Collection<Player> onlinePlayers = players.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());
            particleEffects.playCompletionEffect(dungeonCenter, onlinePlayers);
        }
        
        if(!testRunMode.contains(dungeon)){ 
            Bukkit.broadcastMessage(prefix()+"§6Dungeon §e"+dd.key()+" §6completed!"); 
            if(advancementApiPresent) {
                grantAdvancement(dd, players); 
                
                // Track boss defeats for advanced advancements
                if (dd.boss() != null && dd.boss().enabled()) {
                    for (UUID playerId : players) {
                        // Track this boss defeat for each player
                        playerBossDefeats.computeIfAbsent(playerId, k -> new HashSet<>()).add(dd.key());
                        
                        Player player = Bukkit.getPlayer(playerId);
                        if (player != null) {
                            Set<String> defeatedBosses = playerBossDefeats.get(playerId);
                            
                            // Check for first boss defeat advancement
                            if (defeatedBosses.size() == 1) {
                                grantFirstBossAdvancement(player);
                            }
                            
                            // Check for all bosses defeated advancement
                            // Assuming we have 3 dungeons with bosses: sample, crypt, volcano
                            Set<String> allBossDungeons = Set.of("sample", "crypt", "volcano");
                            if (defeatedBosses.containsAll(allBossDungeons) && !allBossesDefeatedPlayers.contains(playerId)) {
                                allBossesDefeatedPlayers.add(playerId);
                                grantAllBossesAdvancement(player);
                            }
                        }
                    }
                }
            }
            rewardPlayers(dd.key(), players); 
            giveLoot(dd.key(), players); 
        } else { 
            players.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).findFirst().ifPresent(p->p.sendMessage(prefix()+"§eTest run finished.")); 
        }
        
        // Safely return players to main world
        for(UUID pu:players){ 
            Player p=Bukkit.getPlayer(pu); 
            if(p!=null && p.isOnline()) {
                returnPlayerToMainWorld(p, dd.key());
            }
        } 
        
        activeDungeonPlayers.remove(dungeon); 
        long start=dungeonStartTime.getOrDefault(dd.key(),System.currentTimeMillis()); 
        long dur=System.currentTimeMillis()-start; 
        dungeonStartTime.remove(dungeon); 
        hud.clear(dungeon); 
        
        if(!testRunMode.remove(dungeon)){ 
            dungeonRuns.put(dd.key(), dungeonRuns.getOrDefault(dd.key(),0)+1); 
            dungeonTotalTime.put(dd.key(), dungeonTotalTime.getOrDefault(dd.key(),0L)+dur);
        }
        
        // Handle dungeon queue system integration
        onDungeonCompletedInQueue(dungeon);
    }

    @EventHandler public void onChat(AsyncPlayerChatEvent e){ var cb=chatInputs.remove(e.getPlayer().getUniqueId()); if(cb!=null){ e.setCancelled(true); String msg=e.getMessage(); Bukkit.getScheduler().runTask(this, ()->cb.accept(msg)); }}

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!getConfig().getBoolean("features.block-protection", true)) return;
        
        if (isDungeonWorld(event.getBlock().getWorld())) {
            // If general building is allowed, let it pass
            if (getConfig().getBoolean("block-protection.allow-player-building", false)) {
                return;
            }

            // Admin override toggle (default true) controls whether admins can bypass
            boolean adminOverride = getConfig().getBoolean("block-protection.admin-override", true);
            if (adminOverride && event.getPlayer().hasPermission("bubbledungeons.admin")) {
                return;
            }

            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&6&lBubble&bCraft &8» &cYou cannot break blocks in dungeon worlds!"));
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!getConfig().getBoolean("features.block-protection", true)) return;
        
        if (isDungeonWorld(event.getBlock().getWorld())) {
            if (getConfig().getBoolean("block-protection.allow-player-building", false)) {
                return;
            }

            boolean adminOverride = getConfig().getBoolean("block-protection.admin-override", true);
            if (adminOverride && event.getPlayer().hasPermission("bubbledungeons.admin")) {
                return;
            }

            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&6&lBubble&bCraft &8» &cYou cannot place blocks in dungeon worlds!"));
        }
    }
    
    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        // Prevent all entity explosions in dungeon worlds (Creepers, TNT, Ghasts, etc.)
        if (isDungeonWorld(event.getLocation().getWorld()) && 
            getConfig().getBoolean("block-protection.prevent-explosions", true)) {
            event.setCancelled(true);
            // Still allow damage to entities but prevent block destruction
            event.blockList().clear();
        }
    }
    
    @EventHandler 
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        // Prevent all entity block changes in dungeon worlds (Endermen, Ender Dragons, etc.)
        if (isDungeonWorld(event.getBlock().getWorld()) && 
            getConfig().getBoolean("block-protection.prevent-block-changes", true)) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        // Prevent all block explosions in dungeon worlds (TNT, Beds in Nether, etc.)
        if (isDungeonWorld(event.getBlock().getWorld()) && 
            getConfig().getBoolean("block-protection.prevent-explosions", true)) {
            event.setCancelled(true);
            event.blockList().clear();
        }
    }
    
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        // Prevent projectiles from damaging blocks in dungeon worlds (Fireballs, etc.)
        if (event.getHitBlock() != null && 
            isDungeonWorld(event.getHitBlock().getWorld()) && 
            getConfig().getBoolean("block-protection.prevent-projectile-damage", true)) {
            event.setCancelled(true);
        }
    }

    public boolean isDungeonWorld(World world) {
        String worldName = world.getName();
        // Efficient O(1) lookup using cached world names instead of stream operation
        return dungeonWorldNames.contains(worldName);
    }
    
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Only block natural spawning in dungeon worlds
        if (!isDungeonWorld(event.getLocation().getWorld())) {
            return; // Allow spawning in non-dungeon worlds
        }
        
        // Allow spawning for plugin-controlled reasons (our dungeon mobs)
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.CUSTOM || 
            reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG ||
            reason == CreatureSpawnEvent.SpawnReason.DISPENSE_EGG ||
            reason == CreatureSpawnEvent.SpawnReason.COMMAND) {
            return; // Allow controlled spawning
        }
        
        // Block all natural spawning reasons
        event.setCancelled(true);
        
        // Debug logging for admins if enabled
        if (getConfig().getBoolean("debug.log-blocked-spawns", false)) {
            getLogger().info("Blocked natural " + event.getEntityType() + " spawn in dungeon world " + 
                event.getLocation().getWorld().getName() + " (reason: " + reason + ")");
        }
    }
    
    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Check if player was in a dungeon and has left a dungeon world
        String currentDungeon = getPlayerDungeon(playerId);
        if (currentDungeon != null && !isDungeonWorld(player.getWorld())) {
            // Player left a dungeon world, remove them from dungeon tracking
            leaveDungeon(playerId, true);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&6&lBubble&bCraft &8» &eYou have left the dungeon world and been removed from the dungeon."));
        }
    }

    /* ---------------- Advancement ---------------- */
    private void grantAdvancement(DungeonDefinition dd, Set<UUID> players){ 
        // Check if advancement system is enabled
        if (!getConfig().getBoolean("features.advancement-system", true)) return;
        
        // Try UltimateAdvancementAPI first
        if (advancementApiPresent) {
            try { 
                Class<?> apiClass=Class.forName("com.fren_gor.ultimateAdvancementAPI.UltimateAdvancementAPI"); 
                Object instance=apiClass.getMethod("getInstance", JavaPlugin.class).invoke(null,this); 
                String key="bubbledungeons_"+dd.key().replaceAll("[^a-z0-9_]", "_"); // Fix key format to prevent spam
                Class<?> advClass=Class.forName("com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementDisplay"); 
                Class<?> frameClass=Class.forName("com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementFrameType"); 
                Object frame=Enum.valueOf((Class<Enum>)frameClass,"TASK"); 
                Class<?> itemBuilder=Class.forName("com.fren_gor.ultimateAdvancementAPI.util.ItemBuilder"); 
                Object icon=itemBuilder.getConstructor(Material.class).newInstance(Material.DIAMOND_SWORD); 
                Object display=advClass.getConstructor(org.bukkit.inventory.ItemStack.class,String.class,String.class,frameClass,boolean.class,boolean.class,boolean.class).newInstance(icon.getClass().getMethod("build").invoke(icon), color(dd.advTitle()), color(dd.advDescription()), frame,true,true,false); 
                Class<?> aClass=Class.forName("com.fren_gor.ultimateAdvancementAPI.advancement.Advancement"); 
                Object root=apiClass.getMethod("getRootAdvancement").invoke(instance); 
                Object adv=aClass.getConstructor(String.class,aClass,advClass,int.class).newInstance(key,root,display,1); 
                
                // Grant to all players if multiplayer mode enabled
                if (getConfig().getBoolean("features.advancement-multiplayer", true)) {
                    for(UUID u:players){ 
                        Player p=Bukkit.getPlayer(u); 
                        if(p!=null) aClass.getMethod("grant",Player.class).invoke(adv,p);
                    }
                } else {
                    // Grant to first player only
                    Player firstPlayer = players.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).findFirst().orElse(null);
                    if (firstPlayer != null) aClass.getMethod("grant",Player.class).invoke(adv,firstPlayer);
                }
                return; // Success with UltimateAdvancementAPI
            } catch (Exception ex){ 
                // UltimateAdvancementAPI failed, fall back to built-in system
                getLogger().info("UltimateAdvancementAPI failed, using built-in advancement system: " + ex.getMessage());
            }
        }
        
        // Fallback to built-in advancement system
        grantBuiltInAdvancement(dd, players);
    }

    /**
     * Grant built-in advancement using Minecraft's advancement system
     */
    private void grantBuiltInAdvancement(DungeonDefinition dd, Set<UUID> players) {
        // Check if notifications are enabled
        if (!getConfig().getBoolean("features.advancement-notifications", true)) return;
        
        try {
            // Create custom advancement ID
            String advancementKey = "bubbledungeons:" + dd.key().replaceAll("[^a-z0-9_]", "_");
            
            // Determine which players to grant advancement to
            Set<UUID> targetPlayers = players;
            if (!getConfig().getBoolean("features.advancement-multiplayer", true)) {
                // Only grant to first player
                Player firstPlayer = players.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).findFirst().orElse(null);
                if (firstPlayer != null) {
                    targetPlayers = Set.of(firstPlayer.getUniqueId());
                } else {
                    return; // No online players
                }
            }
            
            for (UUID playerId : targetPlayers) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    // Grant using built-in advancement system
                    org.bukkit.advancement.Advancement advancement = Bukkit.getAdvancement(org.bukkit.NamespacedKey.fromString(advancementKey));
                    if (advancement != null) {
                        org.bukkit.advancement.AdvancementProgress progress = player.getAdvancementProgress(advancement);
                        if (!progress.isDone()) {
                            for (String criteria : progress.getRemainingCriteria()) {
                                progress.awardCriteria(criteria);
                            }
                        }
                    } else {
                        // Custom advancement notification since we can't create advancements at runtime
                        sendAdvancementNotification(player, dd.advTitle(), dd.advDescription());
                    }
                }
            }
        } catch (Exception ex) {
            getLogger().warning("Built-in advancement system failed: " + ex.getMessage());
            // Fallback to simple notification
            Set<UUID> targetPlayers = players;
            if (!getConfig().getBoolean("features.advancement-multiplayer", true)) {
                Player firstPlayer = players.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).findFirst().orElse(null);
                if (firstPlayer != null) {
                    targetPlayers = Set.of(firstPlayer.getUniqueId());
                } else {
                    return;
                }
            }
            
            for (UUID playerId : targetPlayers) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    sendAdvancementNotification(player, dd.advTitle(), dd.advDescription());
                }
            }
        }
    }
    
    /**
     * Send custom advancement-style notification to player
     */
    private void sendAdvancementNotification(Player player, String title, String description) {
        if (!getConfig().getBoolean("features.advancement-notifications", false)) {
            return; // Chat/title/actionbar notifications disabled
        }
        // Send title message
        player.sendTitle("§6✦ " + color(title) + " §6✦", color(description), 10, 60, 20);
        
        // Send chat message  
        player.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§6§l                           ACHIEVEMENT UNLOCKED");
        player.sendMessage("");
        player.sendMessage("                    §6✦ " + color(title) + " §6✦");
        player.sendMessage("                         " + color(description));
        player.sendMessage("");
        player.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        
        // Play achievement sound if enabled
        if (getConfig().getBoolean("features.advancement-sounds", false)) {
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
        
        // Send actionbar message for additional feedback
        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, 
            new net.md_5.bungee.api.chat.TextComponent("§6★ Achievement Unlocked: " + color(title) + " §6★"));
    }

    /**
     * Setup UltimateAdvancementAPI system
     */
    private void setupUltimateAdvancementAPI() {
        if (!advancementApiPresent) {
            return;
        }
        
        try {
            getLogger().info("Setting up UltimateAdvancementAPI integration...");
            
            // Try multiple approaches to get the API instance
            Class<?> apiClass = Class.forName("com.fren_gor.ultimateAdvancementAPI.UltimateAdvancementAPI");
            
            // Debug: List all available methods
            getLogger().info("Available UltimateAdvancementAPI methods:");
            for (java.lang.reflect.Method method : apiClass.getMethods()) {
                if (method.getName().equals("getInstance")) {
                    getLogger().info("  - " + method.getName() + "(" + java.util.Arrays.toString(method.getParameterTypes()) + ")");
                }
            }
            
            // First try: getInstance() without parameters
            try {
                ultimateAdvancementApi = apiClass.getMethod("getInstance").invoke(null);
                getLogger().info("UltimateAdvancementAPI instance obtained via getInstance()");
            } catch (Exception e1) {
                getLogger().info("getInstance() failed: " + e1.getMessage());
                // Second try: getInstance(Plugin)
                try {
                    ultimateAdvancementApi = apiClass.getMethod("getInstance", org.bukkit.plugin.Plugin.class).invoke(null, this);
                    getLogger().info("UltimateAdvancementAPI instance obtained via getInstance(Plugin)");
                } catch (Exception e2) {
                    getLogger().info("getInstance(Plugin) failed: " + e2.getMessage());
                    // Third try: getInstance(JavaPlugin)
                    ultimateAdvancementApi = apiClass.getMethod("getInstance", JavaPlugin.class).invoke(null, this);
                    getLogger().info("UltimateAdvancementAPI instance obtained via getInstance(JavaPlugin)");
                }
            }
            
            // Create advancement tab
            advancementTab = apiClass.getMethod("createAdvancementTab", String.class).invoke(ultimateAdvancementApi, "bubble_dungeons");
            
            // Create root advancement
            setupRootAdvancement();
            
            // Create dungeon-specific advancements
            setupDungeonAdvancements();
            
            // Register all advancements
            registerAllAdvancements();
            
            getLogger().info("UltimateAdvancementAPI integration setup successfully!");
            
        } catch (Exception e) {
            getLogger().severe("Failed to setup UltimateAdvancementAPI: " + e.getMessage());
            if (e.getCause() != null) {
                getLogger().severe("Caused by: " + e.getCause().getMessage());
            }
            e.printStackTrace();
            advancementApiPresent = false; // Disable if setup fails
        }
    }
    
    /**
     * Setup root advancement for the dungeon tab
     */
    private void setupRootAdvancement() {
        try {
            ClassLoader uaapiCl = Class.forName("com.fren_gor.ultimateAdvancementAPI.UltimateAdvancementAPI").getClassLoader();
            Class<?> displayClass = uaapiCl.loadClass("com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementDisplay");
            Class<?> frameClass = uaapiCl.loadClass("com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementFrameType");
            Class<?> rootClass = uaapiCl.loadClass("com.fren_gor.ultimateAdvancementAPI.advancement.RootAdvancement");
            
            Object frameType = Enum.valueOf((Class<Enum>) frameClass, "TASK");
            
            // Create display for root advancement using helper for broader compatibility
            Object rootDisplay = createDisplayReflectively(uaapiCl, Material.DIAMOND_SWORD,
                "§6§lBubble Dungeons", "§7Enter the world of epic dungeon adventures!", frameType, 0.0f, 0.0f);
            
            // Create root advancement
            rootAdvancement = rootClass.getConstructor(
                advancementTab.getClass(), String.class, displayClass, String.class
            ).newInstance(
                advancementTab, "bubble_dungeons_root", rootDisplay, "textures/block/diamond_block.png"
            );
            
            getLogger().info("Root advancement created successfully");
            
        } catch (Exception e) {
            getLogger().warning("Failed to create root advancement: " + (e.getCause()!=null?e.getCause().getMessage():e.getMessage()));
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Setup dungeon-specific advancements
     */
    private void setupDungeonAdvancements() {
        try {
            // Always resolve UAAPI classes from the same classloader as the created root advancement
            ClassLoader uaapiCl = rootAdvancement.getClass().getClassLoader();
            Class<?> displayClass = uaapiCl.loadClass("com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementDisplay");
            Class<?> frameClass = uaapiCl.loadClass("com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementFrameType");
            Class<?> advancementClass = uaapiCl.loadClass("com.fren_gor.ultimateAdvancementAPI.advancement.Advancement");
            Class<?> baseAdvClass = uaapiCl.loadClass("com.fren_gor.ultimateAdvancementAPI.advancement.BaseAdvancement");
            
            Object taskFrame = Enum.valueOf((Class<Enum>) frameClass, "TASK");
            Object goalFrame = Enum.valueOf((Class<Enum>) frameClass, "GOAL");
            Object challengeFrame = Enum.valueOf((Class<Enum>) frameClass, "CHALLENGE");

            // Debug available constructors once to align reflection if needed
            debugLogConstructors(advancementClass, "UAAPI Advancement constructors");
            debugLogDeclaredConstructors(advancementClass, "UAAPI Advancement declared ctors");
            debugLogFactoryMethods(rootAdvancement, "UAAPI RootAdvancement factory-like methods");
            debugLogFactoryMethods(advancementTab, "UAAPI AdvancementTab factory-like methods");
            
            // Create participation advancement
            try {
                Object participationDisplay = createDisplayReflectively(uaapiCl, Material.WOODEN_SWORD,
                    "§6Dungeon Explorer", "§7Enter your first dungeon adventure", taskFrame, 1.0f, 1.0f);
                
                Object participationAdv = createAdvancementReflectively(
                    advancementClass, baseAdvClass, displayClass,
                    advancementTab, rootAdvancement, "bubble_dungeons_participation", participationDisplay, 1
                );
                linkAdvancementParent(participationAdv, rootAdvancement, baseAdvClass);
                dungeonAdvancements.put("participation", participationAdv);
            } catch (Exception ex) {
                String cause = (ex instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause()!=null) ? ite.getCause().getMessage() : ex.getMessage();
                getLogger().warning("Advancement 'participation' failed: " + ex.getClass().getName() + ": " + cause);
            }
            
            // Create first boss defeat advancement
            try {
                Object firstBossDisplay = createDisplayReflectively(uaapiCl, Material.IRON_SWORD,
                    "§6Boss Slayer", "§7Defeat your first dungeon boss", goalFrame, 2.0f, 2.0f);
                
                Object firstBossAdv = createAdvancementReflectively(
                    advancementClass, baseAdvClass, displayClass,
                    advancementTab, rootAdvancement, "bubble_dungeons_first_boss", firstBossDisplay, 1
                );
                linkAdvancementParent(firstBossAdv, rootAdvancement, baseAdvClass);
                dungeonAdvancements.put("first_boss", firstBossAdv);
            } catch (Exception ex) {
                String cause = (ex instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause()!=null) ? ite.getCause().getMessage() : ex.getMessage();
                getLogger().warning("Advancement 'first_boss' failed: " + ex.getClass().getName() + ": " + cause);
            }
            
            // Create dungeon master advancement (all bosses)
            try {
                Object masterDisplay = createDisplayReflectively(uaapiCl, Material.DIAMOND_SWORD,
                    "§6§lDungeon Master", "§7Defeat all dungeon bosses", challengeFrame, 3.0f, 2.0f);
                
                Object masterAdv = createAdvancementReflectively(
                    advancementClass, baseAdvClass, displayClass,
                    advancementTab, rootAdvancement, "bubble_dungeons_master", masterDisplay, 1
                );
                linkAdvancementParent(masterAdv, rootAdvancement, baseAdvClass);
                dungeonAdvancements.put("master", masterAdv);
            } catch (Exception ex) {
                String cause = (ex instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause()!=null) ? ite.getCause().getMessage() : ex.getMessage();
                getLogger().warning("Advancement 'master' failed: " + ex.getClass().getName() + ": " + cause);
            }
            
            // Create individual dungeon completion advancements
            float xOffset = 1.0f;
            for (String dungeonKey : dungeons.keySet()) {
                try {
                    DungeonDefinition dd = dungeons.get(dungeonKey);
                    String advTitle = dd.advancement() != null && dd.advancement().title() != null ? 
                        dd.advancement().title() : "§6" + capitalizeFirst(dungeonKey) + " Conqueror";
                    String advDesc = dd.advancement() != null && dd.advancement().description() != null ? 
                        dd.advancement().description() : "§7Complete the " + dungeonKey + " dungeon";
                    
                    Object dungeonDisplay = createDisplayReflectively(uaapiCl, Material.GOLDEN_SWORD,
                        color(advTitle), color(advDesc), goalFrame, xOffset, 4.0f);
                    
                    Object dungeonAdv = createAdvancementReflectively(
                        advancementClass, baseAdvClass, displayClass,
                        advancementTab, rootAdvancement, "bubble_dungeons_" + dungeonKey.toLowerCase(), dungeonDisplay, 1
                    );
                    linkAdvancementParent(dungeonAdv, rootAdvancement, baseAdvClass);
                    
                    dungeonAdvancements.put(dungeonKey, dungeonAdv);
                    xOffset += 1.0f;
                } catch (Exception ex) {
                    String cause = (ex instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause()!=null) ? ite.getCause().getMessage() : ex.getMessage();
                    getLogger().warning("Advancement for dungeon '" + dungeonKey + "' failed: " + ex.getClass().getName() + ": " + cause);
                }
            }
            
            getLogger().info("Created " + dungeonAdvancements.size() + " dungeon advancements");
            
        } catch (Exception e) {
            getLogger().warning("Failed to create one or more dungeon advancements: " + e.getMessage());
            // Do not throw; continue so we can at least register the root and any successes
        }
    }
    
    /**
     * Register all advancements with UltimateAdvancementAPI
     */
    private void registerAllAdvancements() {
    try {
            // Build typed BaseAdvancement[] for varargs registration using the same classloader as rootAdvancement
            Class<?> baseAdvClass;
            try {
                baseAdvClass = rootAdvancement.getClass().getClassLoader()
                    .loadClass("com.fren_gor.ultimateAdvancementAPI.advancement.BaseAdvancement");
            } catch (ClassNotFoundException e) {
                baseAdvClass = Class.forName("com.fren_gor.ultimateAdvancementAPI.advancement.BaseAdvancement");
            }
            java.util.List<Object> toRegister = new java.util.ArrayList<>();
            for (Object adv : dungeonAdvancements.values()) if (adv != null) toRegister.add(adv);
            Object typedArray = java.lang.reflect.Array.newInstance(baseAdvClass, toRegister.size());
            for (int i = 0; i < toRegister.size(); i++) java.lang.reflect.Array.set(typedArray, i, toRegister.get(i));
            
            // Register all advancements using the correct two-parameter method: (RootAdvancement, BaseAdvancement[]|Set)
            Class<?> tabClass = advancementTab.getClass();
            java.lang.reflect.Method registerMethod = null;
            for (var m : tabClass.getMethods()) {
                if (!m.getName().equals("registerAdvancements")) continue;
                if (m.getParameterCount() != 2) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (!pts[1].isArray() && !java.util.Set.class.isAssignableFrom(pts[1])) continue;
                registerMethod = m; break;
            }
            if (registerMethod == null) {
                throw new NoSuchMethodException("registerAdvancements(RootAdvancement, BaseAdvancement[]|Set) not found on tab class");
            }
            Object arg1;
            Class<?>[] regPts = registerMethod.getParameterTypes();
            if (regPts[1].isArray()) {
                Class<?> component = regPts[1].getComponentType();
                if (!regPts[1].isAssignableFrom(typedArray.getClass())) {
                    Object exactArray = java.lang.reflect.Array.newInstance(component, java.lang.reflect.Array.getLength(typedArray));
                    for (int i = 0; i < java.lang.reflect.Array.getLength(typedArray); i++) {
                        java.lang.reflect.Array.set(exactArray, i, java.lang.reflect.Array.get(typedArray, i));
                    }
                    typedArray = exactArray;
                }
                arg1 = typedArray;
            } else {
                java.util.Set<Object> set = new java.util.HashSet<>();
                int len = java.lang.reflect.Array.getLength(typedArray);
                for (int i = 0; i < len; i++) set.add(java.lang.reflect.Array.get(typedArray, i));
                arg1 = set;
            }
            registerMethod.invoke(advancementTab, rootAdvancement, arg1);
            
            getLogger().info("Registered " + java.lang.reflect.Array.getLength(typedArray) + " advancements with UltimateAdvancementAPI");
            
    } catch (Exception e) {
            getLogger().warning("Failed to register some advancements: " + e.getMessage());
            // Attempt to register only the root to ensure the tab is initialised
            try {
                Class<?> baseAdvClass = rootAdvancement.getClass().getClassLoader()
                    .loadClass("com.fren_gor.ultimateAdvancementAPI.advancement.BaseAdvancement");
                Class<?> tabClass = advancementTab.getClass();
                Class<?> paramArrayType = java.lang.reflect.Array.newInstance(baseAdvClass, 0).getClass();
                Object one = java.lang.reflect.Array.newInstance(baseAdvClass, 1);
                java.lang.reflect.Array.set(one, 0, rootAdvancement);
                tabClass.getMethod("registerAdvancements", paramArrayType).invoke(advancementTab, one);
                getLogger().info("Registered only root advancement to initialise tab.");
            } catch (Exception ignored) {
                // Leave as-is; API will remain without our tab
            }
        }
    }

    /**
     * Creates an Advancement instance reflectively, trying BaseAdvancement and RootAdvancement parent signatures.
     */
    private Object createAdvancementReflectively(Class<?> advancementClass, Class<?> baseAdvClass, Class<?> displayClass,
                                                 Object tabObject, Object parentAdvancement, String key, Object display, int points) throws Exception {
        Class<?> parentClass = parentAdvancement.getClass();
        Class<?> tabClass = tabObject.getClass();

    // Note: direct subclassing is not viable due to non-public ctors/abstract methods in UAAPI; stick to reflection-only

        // Try factory-style creation first (some UAAPI versions expose builders/factories, not public ctors)
        Object viaFactory = tryCreateAdvancementViaFactories(advancementClass, baseAdvClass, displayClass,
                parentAdvancement, key, display, points);
    if (viaFactory != null) return viaFactory;

        // UAAPI 2.6.0: Advancement has only declared ctors: (AdvancementTab, String, AdvancementDisplay[, int]).
        // Try those exact signatures directly via getDeclaredConstructor with types loaded from the same classloader as Advancement.
        try {
            // Resolve the AdvancementTab class from the same loader to avoid classloader mismatches
            Class<?> uaapiTabClass = advancementClass.getClassLoader().loadClass("com.fren_gor.ultimateAdvancementAPI.AdvancementTab");
            Class<?> uaapiDisplayClass = advancementClass.getClassLoader().loadClass("com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementDisplay");
            // Prefer 4-arg (with points), then 3-arg
            try {
                var ctor = advancementClass.getDeclaredConstructor(uaapiTabClass, String.class, uaapiDisplayClass, int.class);
                try { ctor.setAccessible(true); } catch (Throwable ignored) {}
                return ctor.newInstance(tabObject, key, display, points);
            } catch (NoSuchMethodException ignorePrimitiveInt) {
                // Try with boxed Integer
                try {
                    var ctor = advancementClass.getDeclaredConstructor(uaapiTabClass, String.class, uaapiDisplayClass, Integer.class);
                    try { ctor.setAccessible(true); } catch (Throwable ignored) {}
                    return ctor.newInstance(tabObject, key, display, points);
                } catch (NoSuchMethodException ignore3ArgFallback) {}
                var ctor = advancementClass.getDeclaredConstructor(uaapiTabClass, String.class, uaapiDisplayClass);
                try { ctor.setAccessible(true); } catch (Throwable ignored) {}
                return ctor.newInstance(tabObject, key, display);
            }
        } catch (Throwable directCtorMiss) {
            // Continue with broader matching below
        }

        // Try all public first
        for (var ctor : advancementClass.getConstructors()) {
            Object created = tryBuildAdvancementWithConstructor(ctor, parentClass, baseAdvClass, displayClass, parentAdvancement, key, display, points);
            if (created != null) return created;
        }
        // Then declared (non-public) as a fallback
        for (var ctor : advancementClass.getDeclaredConstructors()) {
            try { ctor.setAccessible(true); } catch (Throwable ignored) {}
            Object created = tryBuildAdvancementWithConstructor(ctor, parentClass, baseAdvClass, displayClass, parentAdvancement, key, display, points);
            if (created != null) return created;
            // Also try tab-based signature hinted by logs: (AdvancementTab, String, AdvancementDisplay[,int])
            Class<?>[] pts = ctor.getParameterTypes();
            if (pts.length == 3 && pts[0].isAssignableFrom(tabClass) && pts[1] == String.class && pts[2].isInstance(display)) {
                try { return ctor.newInstance(tabObject, key, display); } catch (Throwable ignored) {}
            }
            if (pts.length == 4 && pts[0].isAssignableFrom(tabClass) && pts[1] == String.class && pts[2].isInstance(display) && (pts[3]==int.class||pts[3]==Integer.class)) {
                try { return ctor.newInstance(tabObject, key, display, points); } catch (Throwable ignored) {}
            }
            if (created != null) return created;
        }
        throw new NoSuchMethodException("No compatible Advancement constructor found");
    }

    private static java.lang.reflect.Constructor<?> getCtor(Class<?> cls, Class<?>... params) {
        try { return cls.getConstructor(params); } catch (NoSuchMethodException e) { return null; }
    }

    private Object tryCreateAdvancementViaFactories(Class<?> advancementClass, Class<?> baseAdvClass, Class<?> displayClass,
                                                    Object parentAdvancement, String key, Object display, int points) {
        Object[] hosts = new Object[] { parentAdvancement, this.advancementTab, this.ultimateAdvancementApi, advancementClass };
        for (Object host : hosts) {
            if (host == null) continue;
            final boolean isStaticHost = (host instanceof Class<?>);
            final Class<?> hostClass = isStaticHost ? (Class<?>) host : host.getClass();
            for (var m : hostClass.getMethods()) {
                try {
                    // Only static methods if host is a Class (e.g., Advancement class itself)
                    if (isStaticHost && !java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                    if (m.getReturnType() == void.class) continue;
                    if (!(advancementClass.isAssignableFrom(m.getReturnType()) || baseAdvClass.isAssignableFrom(m.getReturnType()))) continue;
                    String name = m.getName().toLowerCase(java.util.Locale.ROOT);
                    if (!(name.contains("create") || name.contains("add") || name.contains("register") || name.contains("build") || name.contains("of") || name.contains("new"))) continue;
                    Class<?>[] pts = m.getParameterTypes();
                    if (pts.length < 1 || pts.length > 6) continue;
                    Object[] args = new Object[pts.length];
                    boolean ok = true;
                    for (int i = 0; i < pts.length; i++) {
                        Class<?> p = pts[i];
                        if (p == String.class) { args[i] = key; continue; }
                        if (p.getName().equals("org.bukkit.NamespacedKey")) { args[i] = new org.bukkit.NamespacedKey(this, key); continue; }
                        if (p.isInstance(display) || p.isAssignableFrom(displayClass)) { args[i] = display; continue; }
                        if (p.isInstance(parentAdvancement) || p.isAssignableFrom(baseAdvClass)) { args[i] = parentAdvancement; continue; }
                        if (p == int.class || p == Integer.class) { args[i] = points; continue; }
                        if (p.getName().equals("com.fren_gor.ultimateAdvancementAPI.AdvancementTab")) { args[i] = this.advancementTab; continue; }
                        ok = false; break;
                    }
                    if (!ok) continue;
                    return m.invoke(isStaticHost ? null : host, args);
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    private Object tryBuildAdvancementWithConstructor(java.lang.reflect.Constructor<?> ctor,
                                                      Class<?> parentClass, Class<?> baseAdvClass, Class<?> displayClass,
                                                      Object parentAdvancement, String key, Object display, int points) {
        try {
            Class<?>[] pts = ctor.getParameterTypes();
            if (pts.length < 3 || pts.length > 4) return null;
            Object[] args = new Object[pts.length];
            boolean hasKey = false, hasParent = false, hasDisplay = false, hasInt = pts.length == 3; // int optional

            for (int i = 0; i < pts.length; i++) {
                Class<?> p = pts[i];
                // Key mapping: String or NamespacedKey (Bukkit)
                if (!hasKey) {
                    if (p == String.class) {
                        args[i] = key; hasKey = true; continue;
                    }
                    if (p.getName().equals("org.bukkit.NamespacedKey")) {
                        args[i] = new org.bukkit.NamespacedKey(this, key); hasKey = true; continue;
                    }
                }
                // Parent mapping (BaseAdvancement/RootAdvancement/etc.)
                if (!hasParent && (p.isAssignableFrom(parentClass) || p.isAssignableFrom(baseAdvClass))) {
                    args[i] = parentAdvancement; hasParent = true; continue;
                }
                // Display mapping
                if (!hasDisplay && p.isAssignableFrom(displayClass)) {
                    args[i] = display; hasDisplay = true; continue;
                }
                // Points mapping
                if (!hasInt && (p == int.class || p == Integer.class)) { args[i] = points; hasInt = true; continue; }
            }
            if (hasKey && hasParent && hasDisplay && (pts.length == 3 || hasInt)) {
                return ctor.newInstance(args);
            }
        } catch (java.lang.reflect.InvocationTargetException ite) {
            // Bubble up to capture cause in caller logs
            throw new RuntimeException(ite);
        } catch (Throwable ignored) {}
        return null;
    }

    private void linkAdvancementParent(Object child, Object parent, Class<?> baseAdvClass) {
        try {
            // Try setParent on child
            for (var m : child.getClass().getMethods()) {
                if (m.getName().equals("setParent") && m.getParameterCount()==1 && m.getParameterTypes()[0].isAssignableFrom(baseAdvClass)) {
                    m.invoke(child, parent); return;
                }
            }
            // Try addChild on parent
            for (var m : parent.getClass().getMethods()) {
                if (m.getName().equals("addChild") && m.getParameterCount()==1 && m.getParameterTypes()[0].isAssignableFrom(baseAdvClass)) {
                    m.invoke(parent, child); return;
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Creates an AdvancementDisplay instance reflectively. Prefers the UAAPI 2.6.0 signature:
     * (ItemStack, String title, String description, FrameType, boolean showToast, boolean announceChat, boolean hidden)
     * Falls back to older Material-based constructors with offsets and lore/description variants.
     */
    private Object createDisplayReflectively(ClassLoader uaapiCl, Material icon, String title, String description,
                                             Object frameType, float x, float y) throws Exception {
        Class<?> displayClass = uaapiCl.loadClass("com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementDisplay");
        Class<?> frameClass = frameType.getClass();
    // Try ItemBuilder signature first (UAAPI 2.6.0 style). If used, set x/y via setters if available.
        try {
            Class<?> itemBuilder = uaapiCl.loadClass("com.fren_gor.ultimateAdvancementAPI.util.ItemBuilder");
            Object builder = itemBuilder.getConstructor(Material.class).newInstance(icon);
            Object itemStack = builder.getClass().getMethod("build").invoke(builder);
            try {
                // (ItemStack, String, String, FrameType, boolean, boolean, boolean)
        Object disp = displayClass.getConstructor(org.bukkit.inventory.ItemStack.class, String.class, String.class,
            frameClass, boolean.class, boolean.class, boolean.class)
            .newInstance(itemStack, title, description, frameType, true, true, false);
        // Try to set x/y if setters exist
        try { displayClass.getMethod("setX", float.class).invoke(disp, x); } catch (Throwable ignored) {}
        try { displayClass.getMethod("setY", float.class).invoke(disp, y); } catch (Throwable ignored) {}
        return disp;
            } catch (NoSuchMethodException ignore) {}
        } catch (ClassNotFoundException ignore) {}
        
        // Fallbacks: Material-based with offsets and description variants
        try {
            Object disp = displayClass.getConstructor(Material.class, String.class, frameClass, boolean.class, boolean.class, float.class, float.class, String[].class)
                .newInstance(icon, title, frameType, true, true, x, y, new String[]{description});
            return disp;
        } catch (NoSuchMethodException e1) {
            try {
                Object disp = displayClass.getConstructor(Material.class, String.class, frameClass, boolean.class, boolean.class, float.class, float.class, String.class)
                    .newInstance(icon, title, frameType, true, true, x, y, description);
                return disp;
            } catch (NoSuchMethodException e2) {
                Object disp = displayClass.getConstructor(Material.class, String.class, frameClass, boolean.class, boolean.class, float.class, float.class, java.util.List.class)
                    .newInstance(icon, title, frameType, true, true, x, y, java.util.Arrays.asList(description));
                return disp;
            }
        }
    }
    
    /**
     * Show advancement tab shortly after a player joins, avoiding hard dependency
     * on UAAPI's PlayerLoadingCompletedEvent (varies across versions).
     */
    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        if (!advancementApiPresent || advancementTab == null) return;
        // Delay to ensure UAAPI has had time to load player state
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                advancementTab.getClass().getMethod("showTab", Player.class).invoke(advancementTab, event.getPlayer());
            } catch (Exception e) {
                // Keep it quiet to avoid noise if UAAPI isn't fully ready yet
                getLogger().fine("Advancement tab show deferred failed once: " + e.getMessage());
            }
        }, 20L);
    }
    
    /**
     * Grant advancement using UltimateAdvancementAPI
     */
    private void grantUltimateAdvancement(Player player, String advancementKey) {
        if (!advancementApiPresent || !dungeonAdvancements.containsKey(advancementKey)) {
            return;
        }
        
        try {
            Object advancement = dungeonAdvancements.get(advancementKey);
            advancement.getClass().getMethod("grant", Player.class).invoke(advancement, player);
            
            getLogger().info("Granted advancement '" + advancementKey + "' to player " + player.getName());
            
        } catch (Exception e) {
            getLogger().warning("Failed to grant advancement '" + advancementKey + "' to " + player.getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Helper method to capitalize first letter
     */
    private String capitalizeFirst(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
    }

    private void debugLogConstructors(Class<?> cls, String header) {
        try {
            var ctors = cls.getConstructors();
            getLogger().info(header + ": " + ctors.length + " found");
            for (var c : ctors) {
                StringBuilder sb = new StringBuilder("  - (");
                Class<?>[] pts = c.getParameterTypes();
                for (int i = 0; i < pts.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(pts[i].getName());
                }
                sb.append(")");
                getLogger().info(sb.toString());
            }
        } catch (Throwable ignored) {}
    }

    private void debugLogDeclaredConstructors(Class<?> cls, String header) {
        try {
            var ctors = cls.getDeclaredConstructors();
            getLogger().info(header + ": " + ctors.length + " found");
            for (var c : ctors) {
                StringBuilder sb = new StringBuilder("  - (");
                Class<?>[] pts = c.getParameterTypes();
                for (int i = 0; i < pts.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(pts[i].getName());
                }
                sb.append(")");
                getLogger().info(sb.toString());
            }
        } catch (Throwable ignored) {}
    }

    private void debugLogFactoryMethods(Object host, String header) {
        try {
            var methods = host.getClass().getMethods();
            getLogger().info(header + ": scanning " + methods.length + " methods");
            for (var m : methods) {
                String n = m.getName().toLowerCase(java.util.Locale.ROOT);
                if (n.contains("create") || n.contains("add") || n.contains("register") || n.contains("builder") || n.contains("build")) {
                    StringBuilder sb = new StringBuilder("  - ").append(m.getName()).append("(");
                    Class<?>[] pts = m.getParameterTypes();
                    for (int i = 0; i < pts.length; i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(pts[i].getName());
                    }
                    sb.append(") -> ").append(m.getReturnType().getName());
                    getLogger().info(sb.toString());
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Grant participation advancement for first dungeon entry
     */
    private void grantParticipationAdvancement(Player player) {
        // Try UltimateAdvancementAPI first
        if (advancementApiPresent && dungeonAdvancements.containsKey("participation")) {
            grantUltimateAdvancement(player, "participation");
            return;
        }
        
        // Fallback to reflection method for compatibility
        if (advancementApiPresent) {
            try {
                Class<?> apiClass=Class.forName("com.fren_gor.ultimateAdvancementAPI.UltimateAdvancementAPI");
                Object instance=apiClass.getMethod("getInstance", JavaPlugin.class).invoke(null,this);
                String key="bubbledungeons_participation"; // Fix key format
                Class<?> advClass=Class.forName("com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementDisplay");
                Class<?> frameClass=Class.forName("com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementFrameType");
                Object frame=Enum.valueOf((Class<Enum>)frameClass,"TASK");
                Class<?> itemBuilder=Class.forName("com.fren_gor.ultimateAdvancementAPI.util.ItemBuilder");
                Object icon=itemBuilder.getConstructor(Material.class).newInstance(Material.WOODEN_SWORD);
                Object display=advClass.getConstructor(org.bukkit.inventory.ItemStack.class,String.class,String.class,frameClass,boolean.class,boolean.class,boolean.class)
                    .newInstance(icon.getClass().getMethod("build").invoke(icon), 
                        color("&6Dungeon Explorer"), 
                        color("&7Enter your first dungeon!"), 
                        frame,true,true,false);
                Class<?> aClass=Class.forName("com.fren_gor.ultimateAdvancementAPI.advancement.Advancement");
                Object root=apiClass.getMethod("getRootAdvancement").invoke(instance);
                Object adv=aClass.getConstructor(String.class,aClass,advClass,int.class).newInstance(key,root,display,1);
                aClass.getMethod("grant",Player.class).invoke(adv,player);
                return; // Success with UltimateAdvancementAPI
            } catch (Exception ex) {
                // UltimateAdvancementAPI failed, fall back to built-in system
                getLogger().info("UltimateAdvancementAPI participation failed, using notification: " + ex.getMessage());
            }
        }
        
        // Fallback to custom notification
        sendAdvancementNotification(player, "&6Dungeon Explorer", "&7Enter your first dungeon!");
    }

    /**
     * Grant first boss defeat advancement
     */
    private void grantFirstBossAdvancement(Player player) {
        // Try UltimateAdvancementAPI first
        if (advancementApiPresent && dungeonAdvancements.containsKey("first_boss")) {
            grantUltimateAdvancement(player, "first_boss");
            return;
        }
        
        // Fallback to custom notification
        sendAdvancementNotification(player, "&6Boss Slayer", "&7Defeat your first dungeon boss!");
    }

    /**
     * Grant all bosses defeated advancement
     */
    private void grantAllBossesAdvancement(Player player) {
        // Try UltimateAdvancementAPI first
        if (advancementApiPresent && dungeonAdvancements.containsKey("master")) {
            grantUltimateAdvancement(player, "master");
            return;
        }
        
        // Fallback to custom notification
        sendAdvancementNotification(player, "&6&lDungeon Master", "&7Defeat all dungeon bosses!");
    }

    /**
     * Grant dungeon completion advancement
     */
    private void grantDungeonCompletionAdvancement(Player player, String dungeonKey) {
        // Try UltimateAdvancementAPI first
        if (advancementApiPresent && dungeonAdvancements.containsKey(dungeonKey)) {
            grantUltimateAdvancement(player, dungeonKey);
            return;
        }
        
        // Fallback to custom notification
        DungeonDefinition dd = dungeons.get(dungeonKey);
        if (dd != null && dd.advancement() != null) {
            String title = dd.advancement().title() != null ? dd.advancement().title() : "&6" + capitalizeFirst(dungeonKey) + " Conqueror";
            String desc = dd.advancement().description() != null ? dd.advancement().description() : "&7Complete the " + dungeonKey + " dungeon";
            sendAdvancementNotification(player, title, desc);
        }
    }

    /* ---------------- Records & Models ---------------- */
    public record AdvancementDefinition(String title, String description) {}
    public record DungeonDefinition(String key,String worldName,int x,int y,int z,List<MobDefinition> mobs,String advTitle,String advDescription,String difficulty,BossDefinition boss,int bossX,int bossY,int bossZ,int playerX,int playerY,int playerZ) {
        public AdvancementDefinition advancement() {
            return new AdvancementDefinition(advTitle, advDescription);
        }
    }
    public record BossDefinition(String type,String name,double health,double damage,double speed,int size,String mythicId,boolean enabled){}
    public record MobDefinition(String type,String name,double health,double damage,double speed,int size,String mythicId){
        static MobDefinition fromMap(Map<?,?> map){
            String type = map.containsKey("type") ? Objects.toString(map.get("type")) : "ZOMBIE";
            String name = map.containsKey("name") ? Objects.toString(map.get("name")) : null;
            Object hObj = map.containsKey("health") ? map.get("health") : 20;
            double hearts = toDouble(hObj);
            double health = hearts * 2.0;
            Object dObj = map.containsKey("damage") ? map.get("damage") : 2;
            double damage = toDouble(dObj);
            Object sObj = map.containsKey("speed") ? map.get("speed") : 0.25;
            double speed = toDouble(sObj);
            Object sizeObj = map.containsKey("size") ? map.get("size") : 0;
            int size = (int) toDouble(sizeObj);
            String mythicId = map.containsKey("mythicId") ? Objects.toString(map.get("mythicId")) : null;
            return new MobDefinition(type,name,health,damage,speed,size,mythicId);
        }
        private static double toDouble(Object o){ if(o instanceof Number n) return n.doubleValue(); try{return Double.parseDouble(String.valueOf(o));}catch(Exception e){return 0;}} }

    private void applyAttribute(LivingEntity le,String attr,double val){ try { Attribute a=Attribute.valueOf(attr); if(le.getAttribute(a)!=null) le.getAttribute(a).setBaseValue(val);}catch(Exception ignored){} }
    
    /**
     * Get accurate count of remaining mobs for a dungeon by checking actual living entities
     * This is more reliable than the cache when there are scaling issues
     */
    private int getRemainingMobCount(String dungeonKey) {
        int count = 0;
        for (UUID mobId : mobToDungeon.keySet()) {
            if (dungeonKey.equals(mobToDungeon.get(mobId))) {
                // Check if this mob is still alive and valid
                for (World world : Bukkit.getWorlds()) {
                    for (LivingEntity entity : world.getLivingEntities()) {
                        if (entity.getUniqueId().equals(mobId) && !entity.isDead()) {
                            count++;
                            break; // Found this mob, move to next
                        }
                    }
                }
            }
        }
        return count;
    }
    
    /* ---------------- Dungeon Queue System ---------------- */
    
    /**
     * Initialize the dungeon queue system
     */
    private void initializeDungeonQueue() {
        queueSystemEnabled = getConfig().getBoolean("dungeon-queue.enabled", false);
        
        if (!queueSystemEnabled) {
            getLogger().info("Dungeon queue system is disabled.");
            return;
        }
        
        // Load dungeon queue order from config
        dungeonQueue = getConfig().getStringList("dungeon-queue.dungeon-order");
        if (dungeonQueue.isEmpty()) {
            getLogger().warning("No dungeons configured in dungeon-queue.dungeon-order - disabling queue system.");
            queueSystemEnabled = false;
            return;
        }
        
        // Validate all dungeons in queue exist
        dungeonQueue.removeIf(dungeonName -> {
            if (!dungeons.containsKey(dungeonName)) {
                getLogger().warning("Dungeon '" + dungeonName + "' in queue order does not exist - removing from queue.");
                return true;
            }
            return false;
        });
        
        if (dungeonQueue.isEmpty()) {
            getLogger().warning("No valid dungeons in queue after validation - disabling queue system.");
            queueSystemEnabled = false;
            return;
        }
        
        // Clear any existing mobs from all dungeons before starting
        clearAllDungeonMobs();
        
        // Start with the first dungeon in the queue
        currentDungeonIndex = 0;
        openNextDungeon();
        
        // Start watchdog to ensure a dungeon is open if none is active and no scheduled opening
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!queueSystemEnabled) return;
            if (currentOpenDungeon == null && queueTaskId == -1) {
                openNextDungeon();
            }
        }, 1200L, 1200L); // every 60s

        getLogger().info("Dungeon queue system enabled with " + dungeonQueue.size() + " dungeons.");
    }
    
    /**
     * Clear all mobs from all dungeon worlds
     */
    private void clearAllDungeonMobs() {
        for (String dungeonKey : dungeons.keySet()) {
            clearDungeonMobs(dungeonKey);
        }
        
        // Also clear tracking maps
        mobToDungeon.clear();
        bossToDungeon.clear();
        dungeonToBoss.clear();
        dungeonMobCounts.clear();
        
        getLogger().info("Cleared all dungeon mobs before starting queue system.");
    }
    
    /**
     * Clear mobs from a specific dungeon
     */
    private void clearDungeonMobs(String dungeonKey) {
        DungeonDefinition dd = dungeons.get(dungeonKey);
        if (dd == null) return;
        
        World world = Bukkit.getWorld(dd.worldName());
        if (world == null) return;
        
        int cleanupRadius = getConfig().getInt("dungeon-queue.cleanup-radius", 50);
        Location center = new Location(world, dd.x(), dd.y(), dd.z());
        
        int cleared = 0;
        for (LivingEntity entity : world.getLivingEntities()) {
            if (entity instanceof Player) continue; // Don't clear players
            
            if (entity.getLocation().distance(center) <= cleanupRadius) {
                entity.remove();
                cleared++;
            }
        }
        
        if (cleared > 0 && getConfig().getBoolean("player-scaling.debug-mode", false)) {
            getLogger().info("Cleared " + cleared + " mobs from dungeon " + dungeonKey);
        }
    // Also ensure boss health bar is cleared for this dungeon
    removeBossHealthBar(dungeonKey);
    }
    
    /**
     * Open the next dungeon in the queue
     */
    private void openNextDungeon() {
        if (!queueSystemEnabled || dungeonQueue.isEmpty()) return;
        // Enforce global reopen delay after last completion (1 hour)
        if (lastDungeonCompletion > 0) {
            long elapsed = System.currentTimeMillis() - lastDungeonCompletion;
            if (elapsed < DUNGEON_REOPEN_DELAY_MS) {
                long remaining = (DUNGEON_REOPEN_DELAY_MS - elapsed) / 1000L;
                if (getConfig().getBoolean("dungeon-queue.debug", false)) {
                    getLogger().info("Dungeon reopening delayed. Remaining seconds: " + remaining);
                }
                // Reschedule to check again in 60s
                if (queueTaskId == -1) {
                    queueTaskId = Bukkit.getScheduler().runTaskLater(this, this::openNextDungeon, 20L * 60).getTaskId();
                }
                return;
            }
        }
        queueTaskId = -1; // clear pending schedule
        
        // Clear previous dungeon if any
        if (currentOpenDungeon != null) {
            clearDungeonMobs(currentOpenDungeon);
            removeBossHealthBar(currentOpenDungeon);
        }
        
        // Get next dungeon in queue
        currentOpenDungeon = dungeonQueue.get(currentDungeonIndex);
        currentDungeonIndex = (currentDungeonIndex + 1) % dungeonQueue.size(); // Cycle through queue
        
        // Announce the new open dungeon
        String displayName = getDungeonDisplayName(currentOpenDungeon);
        String announcement = getConfig().getString("dungeon-queue.queue-announcement", 
            "§6§l🏰 DUNGEON QUEUE §6§l🏰 §e{dungeon} is now OPEN! Use §a/dungeon join §eto participate!")
            .replace("{dungeon}", displayName);
        
    Bukkit.broadcastMessage(color(announcement));
    // Send interactive join message
    broadcastJoinClick(currentOpenDungeon);
        
        if (getConfig().getBoolean("player-scaling.debug-mode", false)) {
            getLogger().info("Opened dungeon: " + currentOpenDungeon + " (index " + (currentDungeonIndex - 1) + ")");
        }
    }

    /**
     * Open a specific dungeon immediately (admin start)
     */
    private void openSpecificDungeon(String key) {
        // Enable queue system temporarily if disabled to reuse announcer/flow
        if (!queueSystemEnabled) {
            currentOpenDungeon = key;
        } else {
            queueTaskId = -1;
            if (currentOpenDungeon != null) clearDungeonMobs(currentOpenDungeon);
            currentOpenDungeon = key;
            // keep currentDungeonIndex as-is for next openNextDungeon()
        }
        String displayName = getDungeonDisplayName(currentOpenDungeon);
        String announcement = getConfig().getString("dungeon-queue.queue-announcement",
            "§6§l🏰 DUNGEON QUEUE §6§l🏰 §e{dungeon} is now OPEN! Use §a/dungeon join §eto participate!")
            .replace("{dungeon}", displayName);
        Bukkit.broadcastMessage(color(announcement));
        // Send interactive join message
        broadcastJoinClick(currentOpenDungeon);
    }

    /**
     * Broadcast a clickable message that lets players join the currently open dungeon.
     */
    private void broadcastJoinClick(String dungeonKey) {
        if (dungeonKey == null) return;
        String displayName = getDungeonDisplayName(dungeonKey);
        String plain = org.bukkit.ChatColor.stripColor(displayName);
        String cmd = queueSystemEnabled ? "/dungeon join" : "/dungeon join " + dungeonKey;
        Component msg = Component.text()
            .append(Component.text("» ", NamedTextColor.GOLD))
            .append(Component.text("Click to join ", NamedTextColor.YELLOW))
            .append(Component.text(plain, NamedTextColor.AQUA))
            .clickEvent(ClickEvent.runCommand(cmd))
            .hoverEvent(HoverEvent.showText(Component.text("Join " + plain + " now", NamedTextColor.GREEN)))
            .build();
        Bukkit.broadcast(msg);
    }
    
    /**
     * Handle dungeon completion in queue system
     */
    private void onDungeonCompletedInQueue(String dungeonKey) {
        if (!queueSystemEnabled || !dungeonKey.equals(currentOpenDungeon)) return;
        
        lastDungeonCompletion = System.currentTimeMillis();
        
        // Announce completion
        int waitTime = getConfig().getInt("dungeon-queue.wait-time", 300);
    String completionMsg = getConfig().getString("dungeon-queue.completion-announcement",
            "§a§l✓ DUNGEON COMPLETED! §7Next dungeon opens in §e{wait_time} seconds...")
            .replace("{wait_time}", String.valueOf(waitTime));
        
        Bukkit.broadcastMessage(color(completionMsg));
        
        // Clear current dungeon
        currentOpenDungeon = null;
        
        // Schedule next dungeon opening and return players to configured world just before
        if (getConfig().getBoolean("dungeon-queue.auto-start", true)) {
            queueTaskId = Bukkit.getScheduler().runTaskLater(this, () -> {
                // Teleport previous dungeon players to return world (e.g., sspawn)
                Set<UUID> toReturn = pendingReturnPlayers.remove(dungeonKey);
                if (toReturn != null && !toReturn.isEmpty()) {
                    String returnWorld = getConfig().getString("queue-return.world", "sspawn");
                    for (UUID pid : toReturn) {
                        Player p = Bukkit.getPlayer(pid);
                        if (p != null && p.isOnline()) {
                            teleportToWorldSpawn(p, returnWorld);
                        }
                    }
                }

                String cleanupMsg = getConfig().getString("dungeon-queue.cleanup-announcement",
                    "§c§l🧹 Clearing dungeon area... §7Preparing for next challenge!");
                Bukkit.broadcastMessage(color(cleanupMsg));
                
                // Small delay for cleanup message
                Bukkit.getScheduler().runTaskLater(this, this::openNextDungeon, 20L); // 1 second delay
            }, waitTime * 20L).getTaskId(); // Convert seconds to ticks
        }
    }

    private void teleportToWorldSpawn(Player p, String worldName) {
        World w = Bukkit.getWorld(worldName);
        if (w == null) return; // world missing
        Location spawn = w.getSpawnLocation();
        Location safe = findSafeSpawnLocation(w, spawn.getX(), spawn.getY(), spawn.getZ());
        p.sendMessage(ChatColor.translateAlternateColorCodes('&',
            getConfig().getString("messages.returned-to-world",
                "&6&lBubble&bCraft &8» &eReturned to the main world!")));
        p.teleport(safe);
    }

    private String getDungeonDisplayName(String key) {
        String path = "dungeons." + key + ".display-name";
        String name = getConfig().getString(path, null);
        if (name != null) return color(name);
        return key.toUpperCase(Locale.ROOT);
    }

    private void rewardBossCoins(String dungeonKey) {
        if (!getConfig().getBoolean("features.boss-coins", true)) return;
        Set<UUID> players = activeDungeonPlayers.getOrDefault(dungeonKey, Collections.emptySet());
        String template = getConfig().getString("boss-coins.command", "coins give %player% 1");
        for (UUID pid : players) {
            Player p = Bukkit.getPlayer(pid);
            if (p != null && p.isOnline()) {
                String cmd = template.replace("%player%", p.getName());
                if (cmd.startsWith("/")) cmd = cmd.substring(1);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
        }
    }
    
    /* ---------------- Safe Spawning & Teleportation ---------------- */
    
    /**
     * Finds a safe location at or near the given coordinates to prevent fall damage
     * If no ground exists below, searches in expanding radius for nearest solid block
     */
    private Location findSafeSpawnLocation(World world, double x, double y, double z) {
        // Check if safe spawning is disabled
        if (!getConfig().getBoolean("safe-spawning.enabled", true)) {
            return new Location(world, x, y, z);
        }
        
        int maxY = Math.max((int) y, world.getMaxHeight() - 1);
        int minY = Math.max(world.getMinHeight(), -64);
        
        Location safeLoc = new Location(world, x, y, z);
        
        // Check if current location is already safe (has solid block below and air above)
        if (y > minY && world.getBlockAt((int)x, (int)y - 1, (int)z).getType().isSolid() && 
            world.getBlockAt((int)x, (int)y, (int)z).getType().isAir() &&
            world.getBlockAt((int)x, (int)y + 1, (int)z).getType().isAir()) {
            return safeLoc;
        }
        
        // Search directly downward first
        for (int searchY = maxY; searchY >= minY; searchY--) {
            if (world.getBlockAt((int)x, searchY, (int)z).getType().isSolid() && 
                world.getBlockAt((int)x, searchY + 1, (int)z).getType().isAir() &&
                (searchY + 2 >= world.getMaxHeight() || world.getBlockAt((int)x, searchY + 2, (int)z).getType().isAir())) {
                safeLoc.setY(searchY + 1);
                return safeLoc;
            }
        }
        
        // If no ground directly below, search in expanding radius for nearest solid block
        int maxRadius = getConfig().getInt("safe-spawning.search-radius", 16);
        for (int radius = 1; radius <= maxRadius; radius++) {
            Location nearestSafe = findNearestSafeLocation(world, (int)x, (int)y, (int)z, radius, minY, maxY);
            if (nearestSafe != null) {
                return nearestSafe;
            }
        }
        
        // Last resort: create platform at original location if needed
        return createSafePlatform(world, (int)x, (int)y, (int)z, minY);
    }
    
    /**
     * Searches for the nearest safe location within given radius
     */
    private Location findNearestSafeLocation(World world, int centerX, int centerY, int centerZ, int radius, int minY, int maxY) {
        Location closestSafe = null;
        double closestDistance = Double.MAX_VALUE;
        
        // Search in a square radius around the center point
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                // Search from maxY down to minY for solid ground
                for (int y = maxY; y >= minY; y--) {
                    if (world.getBlockAt(x, y, z).getType().isSolid() && 
                        world.getBlockAt(x, y + 1, z).getType().isAir() &&
                        (y + 2 >= world.getMaxHeight() || world.getBlockAt(x, y + 2, z).getType().isAir())) {
                        
                        // Calculate distance to original point
                        double distance = Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(z - centerZ, 2) + Math.pow(y + 1 - centerY, 2));
                        
                        if (distance < closestDistance) {
                            closestDistance = distance;
                            closestSafe = new Location(world, x + 0.5, y + 1, z + 0.5);
                        }
                        break; // Found ground at this x,z - no need to search lower
                    }
                }
            }
        }
        
        return closestSafe;
    }
    
    /**
     * Creates a safe platform at the specified location as last resort
     */
    private Location createSafePlatform(World world, int x, int y, int z, int minY) {
        // Check if platform creation is enabled
        if (!getConfig().getBoolean("safe-spawning.create-platforms", true)) {
            // Just return the location without creating platform
            return new Location(world, x + 0.5, Math.max(y, minY + 2), z + 0.5);
        }
        
        // Ensure we don't create platform below world limit
        int platformY = Math.max(y - 1, minY + 1);
        
        // Only create platform in dungeon worlds to avoid griefing other worlds
        if (isDungeonWorld(world)) {
            // Get platform material from config
            String materialName = getConfig().getString("safe-spawning.platform-material", "STONE");
            org.bukkit.Material platformMaterial;
            try {
                platformMaterial = org.bukkit.Material.valueOf(materialName.toUpperCase());
            } catch (IllegalArgumentException e) {
                platformMaterial = org.bukkit.Material.STONE;
                getLogger().warning("Invalid platform material '" + materialName + "', using STONE instead");
            }
            
            // Create a small 3x3 platform 
            for (int px = x - 1; px <= x + 1; px++) {
                for (int pz = z - 1; pz <= z + 1; pz++) {
                    Location platformLoc = new Location(world, px, platformY, pz);
                    if (world.getBlockAt(platformLoc).getType().isAir()) {
                        world.getBlockAt(platformLoc).setType(platformMaterial);
                    }
                }
            }
            getLogger().info("Created emergency " + platformMaterial.name() + " platform at " + x + "," + platformY + "," + z + " in world " + world.getName());
        }
        
        return new Location(world, x + 0.5, platformY + 1, z + 0.5);
    }
    
    /**
     * Safely teleports a player to prevent fall damage
     */
    private void safeTeleport(Player player, Location location) {
        Location safeLoc = findSafeSpawnLocation(location.getWorld(), 
            location.getX(), location.getY(), location.getZ());
        player.teleport(safeLoc);
    }
    
    /**
     * Returns players to their original world or configured return location
     */
    private void returnPlayerToMainWorld(Player player, String dungeonKey) {
        if (!getConfig().getBoolean("features.auto-return", true)) {
            return; // Auto-return disabled
        }
        
        // Check for custom return location for this dungeon
        String customReturn = getConfig().getString("dungeons." + dungeonKey + ".return-location.world");
        if (customReturn != null) {
            World returnWorld = Bukkit.getWorld(customReturn);
            if (returnWorld != null) {
                double x = getConfig().getDouble("dungeons." + dungeonKey + ".return-location.x", 0);
                double y = getConfig().getDouble("dungeons." + dungeonKey + ".return-location.y", 70);
                double z = getConfig().getDouble("dungeons." + dungeonKey + ".return-location.z", 0);
                Location customLoc = findSafeSpawnLocation(returnWorld, x, y, z);
                player.teleport(customLoc);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    getConfig().getString("messages.returned-to-world", 
                    "&6&lBubble&bCraft &8» &eReturned to the main world!")));
                return;
            }
        }
        
        // Default return to main world spawn
        String mainWorldName = getConfig().getString("main-world", "world");
        World mainWorld = Bukkit.getWorld(mainWorldName);
        if (mainWorld != null) {
            Location spawnLoc = mainWorld.getSpawnLocation();
            Location safeLoc = findSafeSpawnLocation(mainWorld, 
                spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ());
            player.teleport(safeLoc);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                getConfig().getString("messages.returned-to-world", 
                "&6&lBubble&bCraft &8» &eReturned to the main world!")));
        }
    }
    
    /* ---------------- Security & Validation ---------------- */
    
    private boolean isValidDungeonName(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        if (name.length() > MAX_DUNGEON_NAME_LENGTH) return false;
        return name.matches("[a-zA-Z0-9_-]+"); // Only alphanumeric, underscore, hyphen
    }
    
    private boolean isValidPlayerName(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        if (name.length() > 16) return false; // Minecraft username limit
        return name.matches("[a-zA-Z0-9_]+"); // Only alphanumeric and underscore
    }
    
    private boolean isValidWorldName(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        if (name.length() > 64) return false; // Reasonable world name limit
        return name.matches("[a-zA-Z0-9_-]+"); // Only safe characters
    }
    
    private String sanitizeInput(String input) {
        if (input == null) return "";
        return input.trim().replaceAll("[^a-zA-Z0-9_\\-\\s]", "");
    }

    /* ---------------- Tab Complete ---------------- */
    @Override public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args){
        if(!c.getName().equalsIgnoreCase("dungeon")) return List.of();
        if(args.length==1){
            return List.of("join","list","start","menu","leave","reload","admin","config","edit","safemode","testrun","undo","stats","forcejoin")
                    .stream().filter(x->x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if(args.length==2){
            if(args[0].equalsIgnoreCase("join") || args[0].equalsIgnoreCase("edit") || args[0].equalsIgnoreCase("start"))
                return dungeons.keySet().stream().filter(k->k.startsWith(args[1].toLowerCase(Locale.ROOT))).sorted().toList();
            if(args[0].equalsIgnoreCase("forcejoin") || args[0].equalsIgnoreCase("send"))
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n->n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).sorted().toList();
        }
        if(args.length==3 && (args[0].equalsIgnoreCase("forcejoin") || args[0].equalsIgnoreCase("send"))){
            return dungeons.keySet().stream().filter(k->k.startsWith(args[2].toLowerCase(Locale.ROOT))).sorted().toList();
        }
        return List.of();
    }

    /* ---------------- Force Join (Advanced Portals integration) ---------------- */
    private void forceJoin(CommandSender sender, String[] args){
        if(!perm(sender, "bubbledungeons.forcejoin")) return;
        if(args.length < 3){ sender.sendMessage(prefix()+"§cUsage: /dungeon forcejoin <player> <dungeon>"); return; }
        
        // Enhanced input validation for security
        String playerName = sanitizeInput(args[1]);
        String dungeonKey = sanitizeInput(args[2]).toLowerCase(Locale.ROOT);
        
        if(!isValidPlayerName(playerName)) {
            sender.sendMessage(prefix()+"§cInvalid player name format");
            return;
        }
        
        if(!isValidDungeonName(dungeonKey)) {
            sender.sendMessage(prefix()+"§cInvalid dungeon name format");
            return;
        }
        
        Player target = Bukkit.getPlayerExact(playerName);
        if(target == null){ sender.sendMessage(prefix()+"§cPlayer offline: §f"+playerName); return; }
        DungeonDefinition dd = dungeons.get(dungeonKey);
        if(dd == null){ sender.sendMessage(prefix()+"§cDungeon not found: §f"+dungeonKey); return; }
        // Remove from any current dungeon first
        leaveDungeon(target.getUniqueId(), false);
        World w = Bukkit.getWorld(dd.worldName());
        if(w == null){ tryCreateWorld(dd.worldName()); w = Bukkit.getWorld(dd.worldName()); if(w==null){ sender.sendMessage(prefix()+"§cWorld loading; retry soon."); return; } }
    // Force global player spawn location and facing (for all dungeons)
    Location teleportLoc = new Location(w, 65.5, -41, 0.5);
    Location safeLoc = findSafeSpawnLocation(w, teleportLoc.getX(), teleportLoc.getY(), teleportLoc.getZ());
    // Face West (towards negative X)
    safeLoc.setYaw(90.0f);
    safeLoc.setPitch(0.0f);
    target.teleport(safeLoc);
        activeDungeonPlayers.computeIfAbsent(dd.key(),k->new HashSet<>()).add(target.getUniqueId());
        dungeonStartTime.putIfAbsent(dd.key(), System.currentTimeMillis());
        // Check if mobs already spawned using efficient cache lookup
        Integer existingMobs = dungeonMobCounts.get(dd.key());
        boolean already = (existingMobs != null && existingMobs > 0) || safeMode;
        if(!already){ if(safeMode) sender.sendMessage(prefix()+"§eSafeMode: mobs not spawned."); else { spawnDungeonMobs(dd); sender.sendMessage(prefix()+"§aSpawned mobs."); } }
        target.sendMessage(prefix()+"§eYou were sent to dungeon §f"+dd.key());
        sender.sendMessage(prefix()+"§aSent §f"+target.getName()+" §ato dungeon §f"+dd.key());
        updateHud(dd.key());
    }

    /* ---------------- GUI Support API ---------------- */
    public List<String> getDungeonKeysSorted(){ return dungeons.keySet().stream().sorted().toList(); }
    public List<MobDefinition> getMobsForDungeon(String key){ DungeonDefinition dd=dungeons.get(key.toLowerCase(Locale.ROOT)); return dd==null?List.of(): new ArrayList<>(dd.mobs()); }
    public void promptChatInput(Player p,String prompt, java.util.function.Consumer<String> cb){ p.sendMessage(prompt); chatInputs.put(p.getUniqueId(), cb); }
    public void addDungeon(String key){ 
        String clean=key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+",""); 
        if(clean.isBlank()||dungeons.containsKey(clean)) return; 
        DungeonDefinition dd=new DungeonDefinition(clean, clean+"_world",0,80,0,new ArrayList<>(),"&6Dungeon Conqueror","&7You conquered the dungeon!","NORMAL",null,0,80,0,0,80,0); 
        dungeons.put(clean,dd); 
        saveDungeonsToConfig(); 
        Bukkit.getScheduler().runTask(this,this::ensureDungeonWorlds); 
    }
    public void addMobToDungeon(String dKey,String type,String name,double health,double damage,double speed,int size,String mythic){ 
        dKey=dKey.toLowerCase(Locale.ROOT); 
        DungeonDefinition dd=dungeons.get(dKey); 
        if(dd==null) return; 
        List<MobDefinition> list=new ArrayList<>(dd.mobs()); 
        list.add(new MobDefinition(type, blankNull(name), health, damage, speed, size, blankNull(mythic))); 
        dungeons.put(dKey, new DungeonDefinition(dd.key(),dd.worldName(),dd.x(),dd.y(),dd.z(), list, dd.advTitle(), dd.advDescription(), dd.difficulty(), dd.boss(), dd.bossX(), dd.bossY(), dd.bossZ(), dd.playerX(), dd.playerY(), dd.playerZ())); 
        saveDungeonsToConfig(); 
    }
    public void updateMobInDungeon(String dKey,int index,MobDefinition def){ 
        dKey=dKey.toLowerCase(Locale.ROOT); 
        DungeonDefinition dd=dungeons.get(dKey); 
        if(dd==null) return; 
        List<MobDefinition> list=new ArrayList<>(dd.mobs()); 
        if(index<0||index>=list.size()) return; 
        list.set(index,def); 
        dungeons.put(dKey,new DungeonDefinition(dd.key(),dd.worldName(),dd.x(),dd.y(),dd.z(),list,dd.advTitle(),dd.advDescription(),dd.difficulty(),dd.boss(),dd.bossX(),dd.bossY(),dd.bossZ(),dd.playerX(),dd.playerY(),dd.playerZ())); 
        saveDungeonsToConfig(); 
    }
    public void removeMobFromDungeon(String dKey,int index){ 
        dKey=dKey.toLowerCase(Locale.ROOT); 
        DungeonDefinition dd=dungeons.get(dKey); 
        if(dd==null) return; 
        List<MobDefinition> list=new ArrayList<>(dd.mobs()); 
        if(index<0||index>=list.size()) return; 
        list.remove(index); 
        dungeons.put(dKey,new DungeonDefinition(dd.key(),dd.worldName(),dd.x(),dd.y(),dd.z(),list,dd.advTitle(),dd.advDescription(),dd.difficulty(),dd.boss(),dd.bossX(),dd.bossY(),dd.bossZ(),dd.playerX(),dd.playerY(),dd.playerZ())); 
        saveDungeonsToConfig(); 
    }
    private String blankNull(String s){ return (s==null||s.isBlank())?null:s; }

    public void openAdminGui(Player p){ new com.bubblecraft.dungeons.gui.AdminDungeonGUI(this, key -> { String cleaned=key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+",""); if(cleaned.isBlank()){ p.sendMessage(prefix()+"§cInvalid key."); return;} if(dungeons.containsKey(cleaned)){ p.sendMessage(prefix()+"§cExists."); } else { snapshotConfig(); addDungeon(cleaned); p.sendMessage(prefix()+"§aCreated §e"+cleaned); Bukkit.getScheduler().runTask(this,()-> new com.bubblecraft.dungeons.gui.DungeonMobsGUI(this, cleaned).open(p)); } }).open(p); }

    public void openConfigGui(Player p) {
        new com.bubblecraft.dungeons.gui.ConfigGUI(this).open(p);
    }

    private void updateHud(String key){ 
        if(hud==null) return; 
        // Use efficient cache instead of expensive stream operation
        Integer total = dungeonMobCounts.getOrDefault(key, 0);
        Set<UUID> players=activeDungeonPlayers.getOrDefault(key,Set.of()); 
        hud.update(key,0,Math.max(1,total),players); 
    }
    public boolean hudBossBarEnabled(){ return getConfig().getBoolean("features.boss-bar",true); }
    public String getActiveTimerDisplay(UUID id){ String d=getPlayerDungeon(id); if(d==null) return ""; Long st=dungeonStartTime.get(d); if(st==null) return ""; long secs=(System.currentTimeMillis()-st)/1000L; return (secs/60)+":"+String.format("%02d",secs%60); }
    public boolean isInAnyDungeon(UUID id){ return getPlayerDungeon(id)!=null; }
    public String getPlayerDungeon(UUID id){ for(var e:activeDungeonPlayers.entrySet()) if(e.getValue().contains(id)) return e.getKey(); return null; }
    
    // Getter for particle effects system
    public ParticleEffects getParticleEffects() { return particleEffects; }
    
    /* ---------------- Advancement Persistence ---------------- */
    /**
     * Load advancement progress from config
     */
    private void loadAdvancementProgress() {
        var config = getConfig();
        if (config.contains("advancement-progress.boss-defeats")) {
            var bossSection = config.getConfigurationSection("advancement-progress.boss-defeats");
            if (bossSection != null) {
                for (String uuidStr : bossSection.getKeys(false)) {
                    try {
                        UUID playerId = UUID.fromString(uuidStr);
                        List<String> defeats = bossSection.getStringList(uuidStr);
                        playerBossDefeats.put(playerId, new HashSet<>(defeats));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }
        
        if (config.contains("advancement-progress.participation")) {
            var participationSection = config.getConfigurationSection("advancement-progress.participation");
            if (participationSection != null) {
                for (String uuidStr : participationSection.getKeys(false)) {
                    try {
                        UUID playerId = UUID.fromString(uuidStr);
                        List<String> dungeons = participationSection.getStringList(uuidStr);
                        playerDungeonParticipation.put(playerId, new HashSet<>(dungeons));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }
        
        if (config.contains("advancement-progress.all-bosses-defeated")) {
            List<String> allBossesPlayers = config.getStringList("advancement-progress.all-bosses-defeated");
            for (String uuidStr : allBossesPlayers) {
                try {
                    allBossesDefeatedPlayers.add(UUID.fromString(uuidStr));
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }
    
    /**
     * Save advancement progress to config
     */
    private void saveAdvancementProgress() {
        var config = getConfig();
        
        // Save boss defeats
        config.set("advancement-progress.boss-defeats", null);
        for (var entry : playerBossDefeats.entrySet()) {
            config.set("advancement-progress.boss-defeats." + entry.getKey().toString(), 
                      new ArrayList<>(entry.getValue()));
        }
        
        // Save participation
        config.set("advancement-progress.participation", null);
        for (var entry : playerDungeonParticipation.entrySet()) {
            config.set("advancement-progress.participation." + entry.getKey().toString(), 
                      new ArrayList<>(entry.getValue()));
        }
        
        // Save all bosses defeated players
        List<String> allBossesPlayerStrings = allBossesDefeatedPlayers.stream()
            .map(UUID::toString)
            .collect(java.util.stream.Collectors.toList());
        config.set("advancement-progress.all-bosses-defeated", allBossesPlayerStrings);
        
        saveConfig();
    }

    /* ---------------- Saving / Undo ---------------- */
    private void saveDungeonsToConfig(){ 
        var cfg=getConfig(); 
        snapshotConfig(); 
        cfg.set("dungeons", null); 
        for(DungeonDefinition dd:dungeons.values()){ 
            String base="dungeons."+dd.key(); 
            cfg.set(base+".world",dd.worldName()); 
            cfg.set(base+".location.x",dd.x()); 
            cfg.set(base+".location.y",dd.y()); 
            cfg.set(base+".location.z",dd.z()); 
            cfg.set(base+".boss-spawn.x",dd.bossX()); 
            cfg.set(base+".boss-spawn.y",dd.bossY()); 
            cfg.set(base+".boss-spawn.z",dd.bossZ()); 
            cfg.set(base+".player-spawn.x",dd.playerX()); 
            cfg.set(base+".player-spawn.y",dd.playerY()); 
            cfg.set(base+".player-spawn.z",dd.playerZ()); 
            cfg.set(base+".difficulty",dd.difficulty()); 
            cfg.set(base+".advancement.title",dd.advTitle()); 
            cfg.set(base+".advancement.description",dd.advDescription()); 
            List<Map<String,Object>> mobList=new ArrayList<>(); 
            for(MobDefinition md:dd.mobs()){ 
                Map<String,Object> map=new LinkedHashMap<>(); 
                map.put("type",md.type()); 
                if(md.name()!=null) map.put("name",md.name()); 
                map.put("health", md.health()/2.0); 
                map.put("damage", md.damage()); 
                map.put("speed", md.speed()); 
                if(md.size()>0) map.put("size", md.size()); 
                if(md.mythicId()!=null) map.put("mythicId", md.mythicId()); 
                mobList.add(map);
            } 
            cfg.set(base+".mobs", mobList); 
            // Save boss information if exists
            if(dd.boss() != null) {
                cfg.set(base+".boss.type", dd.boss().type());
                if(dd.boss().name() != null) cfg.set(base+".boss.name", dd.boss().name());
                cfg.set(base+".boss.health", dd.boss().health()/2.0);
                cfg.set(base+".boss.damage", dd.boss().damage());
                cfg.set(base+".boss.speed", dd.boss().speed());
                if(dd.boss().size() > 0) cfg.set(base+".boss.size", dd.boss().size());
                if(dd.boss().mythicId() != null) cfg.set(base+".boss.mythicId", dd.boss().mythicId());
                cfg.set(base+".boss.enabled", dd.boss().enabled());
            }
        } 
        saveConfig(); 
    }
    private void snapshotConfig(){ try { Path p=getDataFolder().toPath().resolve("config.yml"); if(Files.exists(p)){ String y=Files.readString(p); undoSnapshots.push(y); while(undoSnapshots.size()>10) undoSnapshots.removeLast(); } }catch(Exception ignored){} }
    private void leaveDungeon(UUID id, boolean notify){ 
        String d=getPlayerDungeon(id); 
        if(d==null){ 
            if(notify){ 
                Player p=Bukkit.getPlayer(id); 
                if(p!=null) p.sendMessage(prefix()+"§cNot in a dungeon."); 
            } 
            return;
        } 
        activeDungeonPlayers.getOrDefault(d,Set.of()).remove(id); 
        Player p=Bukkit.getPlayer(id); 
        if(p!=null){ 
            returnPlayerToMainWorld(p, d);
            if(notify) p.sendMessage(prefix()+"§eLeft dungeon §f"+d); 
        } 
    updateHud(d);
    // Remove this player from the boss bar viewers if present
    updateBossBarPlayers(d);
    }

    /* ---------------- Economy / Loot ---------------- */
    private void rewardPlayers(String key, Set<UUID> players){ if(!getConfig().getBoolean("features.economy-rewards",true)) return; if(!vaultPresent||economy==null) return; double money=getConfig().getDouble("dungeons."+key+".rewards.money",0); List<String> cmds=getConfig().getStringList("dungeons."+key+".rewards.commands"); if(money<=0 && cmds.isEmpty()) return; for(UUID u:players){ Player p=Bukkit.getPlayer(u); if(p==null) continue; if(money>0) economy.depositPlayer(p,money); for(String c:cmds) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("%player%", p.getName())); } }
    private void giveLoot(String key, Set<UUID> players) {
        if (!getConfig().getBoolean("features.loot-rewards", true)) return;
        LootTable table = lootTables.get(key);
        if (table == null) return;
        
        List<LootTable.Entry> entries = table.roll(random);
        for (UUID u : players) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) continue;
            
            for (LootTable.Entry e : entries) {
                // Handle item drops with custom styling
                if (e.item != null) {
                    int amt = e.min == e.max ? e.min : random.nextInt(e.max - e.min + 1) + e.min;
                    ItemStack finalItem = table.createCustomItem(e, amt);
                    if (finalItem != null) {
                        var left = p.getInventory().addItem(finalItem);
                        left.values().forEach(rem -> p.getWorld().dropItemNaturally(p.getLocation(), rem));
                        
                        // Send rarity notification
                        if (e.rarity.ordinal() >= LootTable.Rarity.RARE.ordinal()) {
                            p.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                                "&6&lBubble&bCraft &8» " + e.rarity.color + "You found " + e.rarity.displayName + 
                                " loot: " + (e.displayName != null ? e.displayName : finalItem.getType().name()) + "!"));
                        }
                    }
                }
                
                // Handle command execution
                if (e.command != null) {
                    String processedCommand = e.command.replace("%player%", p.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
                }
                
                // Handle BubbleCoins directly
                if (e.bubbleCoins > 0) {
                    String bubbleCoinsCommand = "eco give " + p.getName() + " " + e.bubbleCoins;
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), bubbleCoinsCommand);
                    p.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                        "&6&lBubble&bCraft &8» &6+" + e.bubbleCoins + " BubbleCoins!"));
                }
            }
        }
    }
    private void setupEconomy(){ try { var rsp=getServer().getServicesManager().getRegistration(Economy.class); if(rsp!=null) economy=rsp.getProvider(); } catch (Exception ignored) {} }

    /* ---------------- Dungeon Editing Methods ---------------- */
    public DungeonDefinition getDungeon(String key) {
        return dungeons.get(key.toLowerCase());
    }
    
    public void updateDungeonWorldName(String key, String worldName) {
        DungeonDefinition dd = dungeons.get(key.toLowerCase());
        if (dd == null) return;
        dungeons.put(key.toLowerCase(), new DungeonDefinition(dd.key(), worldName, dd.x(), dd.y(), dd.z(), 
            dd.mobs(), dd.advTitle(), dd.advDescription(), dd.difficulty(), dd.boss(), 
            dd.bossX(), dd.bossY(), dd.bossZ(), dd.playerX(), dd.playerY(), dd.playerZ()));
        saveDungeonsToConfig();
    }
    
    public void updateDungeonBaseLocation(String key, Location location) {
        DungeonDefinition dd = dungeons.get(key.toLowerCase());
        if (dd == null) return;
        dungeons.put(key.toLowerCase(), new DungeonDefinition(dd.key(), dd.worldName(), 
            (int)location.getX(), (int)location.getY(), (int)location.getZ(), dd.mobs(), dd.advTitle(), 
            dd.advDescription(), dd.difficulty(), dd.boss(), dd.bossX(), dd.bossY(), dd.bossZ(), 
            dd.playerX(), dd.playerY(), dd.playerZ()));
        saveDungeonsToConfig();
    }
    
    public void updateDungeonDifficulty(String key, int difficulty) {
        DungeonDefinition dd = dungeons.get(key.toLowerCase());
        if (dd == null) return;
        dungeons.put(key.toLowerCase(), new DungeonDefinition(dd.key(), dd.worldName(), dd.x(), dd.y(), dd.z(), 
            dd.mobs(), dd.advTitle(), dd.advDescription(), String.valueOf(difficulty), dd.boss(), 
            dd.bossX(), dd.bossY(), dd.bossZ(), dd.playerX(), dd.playerY(), dd.playerZ()));
        saveDungeonsToConfig();
    }
    
    public void updateDungeonPlayerSpawn(String key, Location location) {
        updateDungeonPlayerSpawn(key, location.getX(), location.getY(), location.getZ());
    }
    
    public void updateDungeonPlayerSpawn(String key, double x, double y, double z) {
        DungeonDefinition dd = dungeons.get(key.toLowerCase());
        if (dd == null) return;
        dungeons.put(key.toLowerCase(), new DungeonDefinition(dd.key(), dd.worldName(), dd.x(), dd.y(), dd.z(), 
            dd.mobs(), dd.advTitle(), dd.advDescription(), dd.difficulty(), dd.boss(), 
            dd.bossX(), dd.bossY(), dd.bossZ(), (int)x, (int)y, (int)z));
        saveDungeonsToConfig();
    }
    
    public void updateDungeonBossSpawn(String key, Location location) {
        updateDungeonBossSpawn(key, location.getX(), location.getY(), location.getZ());
    }
    
    public void updateDungeonBossSpawn(String key, double x, double y, double z) {
        DungeonDefinition dd = dungeons.get(key.toLowerCase());
        if (dd == null) return;
        dungeons.put(key.toLowerCase(), new DungeonDefinition(dd.key(), dd.worldName(), dd.x(), dd.y(), dd.z(), 
            dd.mobs(), dd.advTitle(), dd.advDescription(), dd.difficulty(), dd.boss(), 
            (int)x, (int)y, (int)z, dd.playerX(), dd.playerY(), dd.playerZ()));
        saveDungeonsToConfig();
    }
    
    public void updateDungeonAdvancementTitle(String key, String title) {
        DungeonDefinition dd = dungeons.get(key.toLowerCase());
        if (dd == null) return;
        dungeons.put(key.toLowerCase(), new DungeonDefinition(dd.key(), dd.worldName(), dd.x(), dd.y(), dd.z(), 
            dd.mobs(), title, dd.advDescription(), dd.difficulty(), dd.boss(), 
            dd.bossX(), dd.bossY(), dd.bossZ(), dd.playerX(), dd.playerY(), dd.playerZ()));
        saveDungeonsToConfig();
    }
    
    public void updateDungeonAdvancementDescription(String key, String description) {
        DungeonDefinition dd = dungeons.get(key.toLowerCase());
        if (dd == null) return;
        dungeons.put(key.toLowerCase(), new DungeonDefinition(dd.key(), dd.worldName(), dd.x(), dd.y(), dd.z(), 
            dd.mobs(), dd.advTitle(), description, dd.difficulty(), dd.boss(), 
            dd.bossX(), dd.bossY(), dd.bossZ(), dd.playerX(), dd.playerY(), dd.playerZ()));
        saveDungeonsToConfig();
    }
    
    public void updateDungeonBoss(String key, String type, String name, double health, double damage, double speed, int size, String mythicId) {
        DungeonDefinition dd = dungeons.get(key.toLowerCase());
        if (dd == null) return;
        BossDefinition newBoss = new BossDefinition(type, name, health, damage, speed, size, mythicId, true);
        dungeons.put(key.toLowerCase(), new DungeonDefinition(dd.key(), dd.worldName(), dd.x(), dd.y(), dd.z(), 
            dd.mobs(), dd.advTitle(), dd.advDescription(), dd.difficulty(), newBoss, 
            dd.bossX(), dd.bossY(), dd.bossZ(), dd.playerX(), dd.playerY(), dd.playerZ()));
        saveDungeonsToConfig();
    }
    
    public void deleteDungeon(String key) {
        dungeons.remove(key.toLowerCase());
        saveDungeonsToConfig();
    }
    
    public void teleportToDungeon(Player player, String key) {
        DungeonDefinition dd = dungeons.get(key.toLowerCase());
        if (dd == null) {
            player.sendMessage(prefix() + "&cDungeon not found!");
            return;
        }
        World world = Bukkit.getWorld(dd.worldName());
        if (world == null) {
            player.sendMessage(prefix() + "&cDungeon world not found!");
            return;
        }
        
    // Force global player spawn location and facing (for all dungeons)
    Location initialLoc = new Location(world, 65.5, -41, 0.5);
    Location safeLoc = findSafeSpawnLocation(world, initialLoc.getX(), initialLoc.getY(), initialLoc.getZ());
    // Face West (towards negative X)
    safeLoc.setYaw(90.0f);
    safeLoc.setPitch(0.0f);
    player.teleport(safeLoc);
    }
}
