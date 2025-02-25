package com.axteroid.model;

import java.util.Objects;

public class SoundStep {
    private final String sound;
    private final int delay;
    private final float volume;
    private final float pitch;
    private final String namespace;
    private final String key;

    public SoundStep(String sound, int delay, float volume, float pitch) {
        this(sound, delay, volume, pitch, null, null);
    }

    public SoundStep(String sound, int delay, float volume, float pitch, String namespace, String key) {
        this.sound = sound;
        this.delay = delay;
        this.volume = volume;
        this.pitch = pitch;
        this.namespace = namespace;
        this.key = key;
    }

    public String getSound() {
        return sound;
    }

    public int getDelay() {
        return delay;
    }

    public float getVolume() {
        return volume;
    }

    public float getPitch() {
        return pitch;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getKey() {
        return key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SoundStep)) return false;
        SoundStep other = (SoundStep) o;
        return delay == other.delay &&
               Float.compare(other.volume, volume) == 0 &&
               Float.compare(other.pitch, pitch) == 0 &&
               Objects.equals(sound, other.sound) &&
               Objects.equals(namespace, other.namespace) &&
               Objects.equals(key, other.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sound, delay, volume, pitch, namespace, key);
    }

    @Override
    public String toString() {
        return "SoundStep(sound=" + sound + 
               ", delay=" + delay + 
               ", volume=" + volume + 
               ", pitch=" + pitch +
               (namespace != null ? ", namespace=" + namespace : "") +
               (key != null ? ", key=" + key : "") + ")";
    }
} 