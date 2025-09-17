package com.bubblecraft.dungeons.effects;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.Plugin;

import java.util.Collection;

/**
 * Cool particle effects system for BubbleDungeons
 * Provides visual feedback for various dungeon events
 */
public class ParticleEffects {
    
    private final Plugin plugin;
    
    public ParticleEffects(Plugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Play mob spawn effect with mystical particles
     */
    public void playMobSpawnEffect(Location location) {
        World world = location.getWorld();
        if (world == null) return;
        
        // Purple mystical explosion
        world.spawnParticle(Particle.WITCH, location.clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
        world.spawnParticle(Particle.PORTAL, location.clone().add(0, 0.5, 0), 50, 1.0, 1.0, 1.0, 0.5);
        world.spawnParticle(Particle.DRAGON_BREATH, location.clone().add(0, 1.5, 0), 20, 0.3, 0.3, 0.3, 0.05);
        
        // Sound effect
        world.playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.7f);
        world.playSound(location, Sound.BLOCK_PORTAL_AMBIENT, 0.5f, 1.2f);
    }
    
    /**
     * Play boss spawn effect with dramatic particles
     */
    public void playBossSpawnEffect(Location location) {
        World world = location.getWorld();
        if (world == null) return;
        
        // Dramatic boss entrance with expanding circles
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 40) {
                    cancel();
                    return;
                }
                
                double radius = ticks * 0.3;
                int particleCount = 8 + (ticks / 2);
                
                // Red flame circle expanding outward
                for (int i = 0; i < particleCount; i++) {
                    double angle = (2 * Math.PI * i) / particleCount;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    
                    Location particleLoc = location.clone().add(x, 0.1, z);
                    world.spawnParticle(Particle.FLAME, particleLoc, 1, 0, 0, 0, 0.05);
                    world.spawnParticle(Particle.DRIPPING_LAVA, particleLoc.clone().add(0, 2, 0), 2, 0.1, 0.1, 0.1, 0);
                }
                
                // Central explosion effect
                if (ticks % 10 == 0) {
                    world.spawnParticle(Particle.EXPLOSION, location.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
                    world.spawnParticle(Particle.LAVA, location.clone().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.1);
                }
                
                ticks++;
            }
        }.runTaskTimer(plugin, 0, 2);
        
        // Dramatic sound sequence
        world.playSound(location, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);
        world.playSound(location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 0.5f);
    }
    
    /**
     * Play player teleport effect
     */
    public void playTeleportEffect(Location location) {
        World world = location.getWorld();
        if (world == null) return;
        
        // Blue mystical teleport effect
        world.spawnParticle(Particle.ENCHANT, location.clone().add(0, 1, 0), 50, 0.5, 1.0, 0.5, 1.0);
        world.spawnParticle(Particle.END_ROD, location.clone().add(0, 0.5, 0), 20, 0.3, 0.5, 0.3, 0.1);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, location, 30, 0.4, 0.4, 0.4, 0.2);
        
        // Teleport sound
        world.playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        world.playSound(location, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.5f);
    }
    
    /**
     * Play dungeon completion effect
     */
    public void playCompletionEffect(Location location, Collection<Player> players) {
        World world = location.getWorld();
        if (world == null) return;
        
        // Victory celebration with fireworks-like effect
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 60) {
                    cancel();
                    return;
                }
                
                // Firework-like bursts
                if (ticks % 15 == 0) {
                    for (int i = 0; i < 5; i++) {
                        Location burstLoc = location.clone().add(
                            (Math.random() - 0.5) * 6,
                            Math.random() * 4 + 2,
                            (Math.random() - 0.5) * 6
                        );
                        
                        world.spawnParticle(Particle.FIREWORK, burstLoc, 20, 0.3, 0.3, 0.3, 0.2);
                        world.spawnParticle(Particle.TOTEM_OF_UNDYING, burstLoc, 10, 0.2, 0.2, 0.2, 0.1);
                    }
                    
                    world.playSound(location, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 1.0f + (float)(Math.random() * 0.4));
                }
                
                // Continuous sparkle effect around players
                for (Player player : players) {
                    if (player.isOnline()) {
                        Location playerLoc = player.getLocation();
                        world.spawnParticle(Particle.HAPPY_VILLAGER, playerLoc.clone().add(0, 2.5, 0), 3, 0.5, 0.5, 0.5, 0);
                        world.spawnParticle(Particle.ENCHANT, playerLoc.clone().add(0, 1, 0), 5, 0.8, 0.5, 0.8, 0.5);
                    }
                }
                
                ticks++;
            }
        }.runTaskTimer(plugin, 0, 3);
        
        // Victory sounds
        world.playSound(location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        world.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
    }
    
    /**
     * Play boss vulnerable effect (when minions are defeated)
     */
    public void playBossVulnerableEffect(Location location) {
        World world = location.getWorld();
        if (world == null) return;
        
        // Dramatic shield-breaking effect
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 30) {
                    cancel();
                    return;
                }
                
                // Expanding ring of breaking particles
                double radius = ticks * 0.4;
                for (int i = 0; i < 16; i++) {
                    double angle = (2 * Math.PI * i) / 16;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    
                    Location particleLoc = location.clone().add(x, 1 + (ticks * 0.1), z);
                    world.spawnParticle(Particle.CRIT, particleLoc, 2, 0.1, 0.1, 0.1, 0.2);
                    world.spawnParticle(Particle.ENCHANTED_HIT, particleLoc, 1, 0, 0, 0, 0);
                }
                
                // Central shattering effect
                if (ticks % 5 == 0) {
                    world.spawnParticle(Particle.BLOCK, location.clone().add(0, 2, 0), 20, 0.8, 0.8, 0.8, 0.1,
                        Material.STONE.createBlockData());
                    world.spawnParticle(Particle.SWEEP_ATTACK, location.clone().add(0, 1.5, 0), 3, 0.5, 0.5, 0.5, 0);
                }
                
                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
        
        // Shield breaking sound
        world.playSound(location, Sound.ITEM_SHIELD_BREAK, 1.0f, 0.8f);
        world.playSound(location, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.6f);
        world.playSound(location, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 1.5f);
    }
    
    /**
     * Play mob death effect
     */
    public void playMobDeathEffect(Location location) {
        World world = location.getWorld();
        if (world == null) return;
        
        // Dark dissipation effect
        world.spawnParticle(Particle.SMOKE, location.clone().add(0, 1, 0), 15, 0.4, 0.5, 0.4, 0.1);
        world.spawnParticle(Particle.ASH, location.clone().add(0, 0.5, 0), 10, 0.3, 0.3, 0.3, 0.05);
        world.spawnParticle(Particle.SOUL, location.clone().add(0, 1.5, 0), 5, 0.2, 0.2, 0.2, 0.1);
        
        // Subtle death sound
        world.playSound(location, Sound.ENTITY_VEX_DEATH, 0.6f, 0.8f);
    }
    
    /**
     * Play boss death effect
     */
    public void playBossDeathEffect(Location location) {
        World world = location.getWorld();
        if (world == null) return;
        
        // Epic boss death sequence
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 50) {
                    cancel();
                    return;
                }
                
                // Expanding explosion rings
                if (ticks % 5 == 0) {
                    double radius = (ticks / 5) * 1.5;
                    for (int i = 0; i < 20; i++) {
                        double angle = (2 * Math.PI * i) / 20;
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;
                        
                        Location particleLoc = location.clone().add(x, 0.5, z);
                        world.spawnParticle(Particle.EXPLOSION, particleLoc, 1, 0, 0, 0, 0);
                        world.spawnParticle(Particle.FLAME, particleLoc.clone().add(0, 1, 0), 3, 0.2, 0.2, 0.2, 0.1);
                    }
                    
                    world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.6f + (ticks * 0.02f));
                }
                
                // Central dramatic effect
                world.spawnParticle(Particle.LAVA, location.clone().add(0, 2, 0), 8, 1.0, 1.0, 1.0, 0.1);
                world.spawnParticle(Particle.LARGE_SMOKE, location.clone().add(0, 3, 0), 5, 0.8, 0.8, 0.8, 0.2);
                
                if (ticks == 25) {
                    // Final dramatic explosion
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, location.clone().add(0, 1, 0), 3, 0.5, 0.5, 0.5, 0);
                    world.playSound(location, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.0f, 0.7f);
                }
                
                ticks++;
            }
        }.runTaskTimer(plugin, 0, 2);
    }
    
    /**
     * Play dungeon failure effect
     */
    public void playFailureEffect(Location location) {
        World world = location.getWorld();
        if (world == null) return;
        
        // Dark failure effect
        world.spawnParticle(Particle.LARGE_SMOKE, location.clone().add(0, 1, 0), 30, 1.0, 1.0, 1.0, 0.1);
        world.spawnParticle(Particle.ASH, location.clone().add(0, 2, 0), 50, 1.5, 1.0, 1.5, 0.2);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, location.clone().add(0, 0.5, 0), 20, 0.8, 0.5, 0.8, 0.1);
        
        // Ominous sounds
        world.playSound(location, Sound.ENTITY_WITHER_DEATH, 0.8f, 0.6f);
        world.playSound(location, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.5f);
    }
    
    /**
     * Play GUI interaction effect for player
     */
    public void playGUIEffect(Player player) {
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) return;
        
        // Subtle magical interaction effect
        world.spawnParticle(Particle.ENCHANT, location.clone().add(0, 1.5, 0), 10, 0.3, 0.3, 0.3, 0.5);
        world.spawnParticle(Particle.END_ROD, location.clone().add(0, 1, 0), 5, 0.2, 0.2, 0.2, 0.05);
        
        // Soft UI sound
        player.playSound(location, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 1.8f);
    }
}
