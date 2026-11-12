package pl.szafa;

import java.util.HashMap;
import java.util.Map;

/** Dane jednego gracza: aktywne zwierzatko/kostium (data wygasniecia w ms) i cooldowny umiejetnosci. */
public final class PlayerData {
    public String petId;
    public long petExpiry;
    public String costumeId;
    public long costumeExpiry;
    /** id kostiumu -> czas (ms), do kiedy trwa cooldown umiejetnosci. */
    public final Map<String, Long> cooldowns = new HashMap<>();
}
