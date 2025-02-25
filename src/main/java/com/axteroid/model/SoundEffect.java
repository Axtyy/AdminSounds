package com.axteroid.model;

import com.axteroid.AdminSound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Objects;

public class SoundEffect {
    private final String name;
    private final List<SoundStep> steps;
    private final AdminSound plugin;

    public SoundEffect(String name, List<SoundStep> steps, AdminSound plugin) {
        this.name = name;
        this.steps = steps;
        this.plugin = plugin;
    }

    public String getName() {
        return name;
    }

    public List<SoundStep> getSteps() {
        return steps;
    }

    public void play(final Player player) {
        int totalDelay = 0;
        for (final SoundStep step : steps) {
            totalDelay += step.getDelay();
            final int delay = totalDelay;
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        plugin.getCustomSoundManager().playCustomSound(
                            player,
                            step.getSound(),
                            step.getVolume(),
                            step.getPitch()
                        );
                    }
                }
            }.runTaskLater(plugin, delay);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SoundEffect)) return false;
        SoundEffect that = (SoundEffect) o;
        return Objects.equals(name, that.name) &&
               Objects.equals(steps, that.steps);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, steps);
    }

    @Override
    public String toString() {
        return "SoundEffect(name=" + name + ", steps=" + steps + ")";
    }
} 