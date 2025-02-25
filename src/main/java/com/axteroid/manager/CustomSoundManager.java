package com.axteroid.manager;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import com.axteroid.AdminSound;

public class CustomSoundManager {
    private final AdminSound plugin;
    private boolean hasItemsAdder;

    public CustomSoundManager(AdminSound plugin) {
        this.plugin = plugin;
        this.hasItemsAdder = plugin.getServer().getPluginManager().getPlugin("ItemsAdder") != null;
    }

    public boolean playCustomSound(Player player, String soundName, float volume, float pitch) {
        Location loc = player.getLocation();

        // Try custom resource pack sounds
        if (soundName.startsWith("custom:")) {
            try {
                player.playSound(loc, soundName.substring(7), volume, pitch);
                return true;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to play custom sound: " + soundName);
            }
        }

        // Try to play as vanilla sound
        try {
            Sound vanillaSound = Sound.valueOf(soundName);
            player.playSound(loc, vanillaSound, volume, pitch);
            return true;
        } catch (IllegalArgumentException ignored) {
            // Not a vanilla sound
        }

        // Try ItemsAdder sounds
        if (hasItemsAdder && tryItemsAdderSound(player, soundName, volume, pitch)) {
            return true;
        }
        
        return false;
    }

    // Stop a custom sound
    public void stopSound(Player player, String soundName) {
        if (soundName.startsWith("custom:")) {
            String soundPath = soundName.substring(7);
            String namespace = plugin.getSoundsConfig().getString("sounds." + soundPath + ".namespace", "minecraft");
            String key = namespace + ":" + soundPath;
            
            player.stopSound(key, SoundCategory.MASTER);
        }
    }

    private boolean tryItemsAdderSound(Player player, String soundName, float volume, float pitch) {
        try {
            // ItemsAdder sound format: namespace:sound_name
            if (soundName.contains(":")) {
                // Use the standard Bukkit sound API since ItemsAdder registers its sounds
                player.playSound(player.getLocation(), soundName, volume, pitch);
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to play ItemsAdder sound: " + soundName);
        }
        return false;
    }
} 