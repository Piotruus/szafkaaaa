package pl.szafa;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.logging.Logger;

/** Umiejetnosc kostiumu uruchamiana klawiszem SHIFT (konfiguracja w config.yml -> ability). */
public final class Ability {

    public enum Type {FREEZE, WEB, CHARGE}

    public final Type type;
    /** Ile razy trzeba szybko nacisnac SHIFT, zeby uzyc umiejetnosci. */
    public final int sneaks;
    public final int cooldownSec;
    public final double radius;
    public final double durationSec;
    public final int amplifier;
    public final double damage;
    public final double power;

    private Ability(Type type, ConfigurationSection s) {
        this.type = type;
        this.sneaks = Math.max(1, s.getInt("sneaks", 1));
        this.cooldownSec = Math.max(1, s.getInt("cooldown", 60));
        this.radius = Math.max(1.0, s.getDouble("radius", 8.0));
        this.durationSec = Math.max(0.5, s.getDouble("duration", 4.0));
        this.amplifier = Math.max(0, s.getInt("amplifier", 2));
        this.damage = Math.max(0.0, s.getDouble("damage", 4.0));
        this.power = Math.max(0.2, s.getDouble("power", 1.4));
    }

    public static Ability parse(ConfigurationSection s, Logger log) {
        if (s == null) {
            return null;
        }
        try {
            Type t = Type.valueOf(s.getString("type", "").toUpperCase(Locale.ROOT));
            return new Ability(t, s);
        } catch (IllegalArgumentException ex) {
            log.warning("Nieznany typ umiejetnosci: " + s.getString("type"));
            return null;
        }
    }
}
