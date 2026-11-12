package pl.szafa;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import pl.szafa.Cosmetic.Kind;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Glowa kostiumu jako osobny model (ItemDisplay) "zamontowany" na graczu jako pasazer.
 * Pasazer porusza sie razem z graczem po stronie klienta (zero opoznienia przy bieganiu),
 * a obrot glowy odwzorowujemy skosem i przesunieciem w transformacji (obrot wokol szyi).
 * Nie zajmuje slotu helmu - gracz moze normalnie nosic zbroje.
 */
public final class HeadManager {

    /** Odleglosc srodka glowy od szyi (bloki). */
    private static final double HEAD_HALF = 0.25;
    /** Srodek glowy lezy odrobine ponizej oczu. */
    private static final double EYE_TO_CENTER = 0.07;

    private static final class Entry {
        final String costumeId;
        final ItemDisplay display;
        float lastYaw = Float.NaN;
        float lastPitch = Float.NaN;
        double lastNeck = Double.NaN;

        Entry(String costumeId, ItemDisplay display) {
            this.costumeId = costumeId;
            this.display = display;
        }
    }

    private final SzafaPlugin plugin;
    private final Map<UUID, Entry> entries = new HashMap<>();

    public HeadManager(SzafaPlugin plugin) {
        this.plugin = plugin;
    }

    public void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Cosmetic c = plugin.activeOf(p, Kind.COSTUME);
            Entry en = entries.get(p.getUniqueId());

            boolean show = c != null && c.headModel != null && !p.isDead()
                    && p.getGameMode() != GameMode.SPECTATOR;
            if (!show) {
                if (en != null) {
                    remove(p);
                }
                continue;
            }
            if (en != null && (!en.costumeId.equals(c.id) || !en.display.isValid())) {
                remove(p);
                en = null;
            }
            if (en == null) {
                en = new Entry(c.id, spawn(c, p.getLocation()));
                entries.put(p.getUniqueId(), en);
            }
            ensureMounted(p, en.display);
            update(p, en);
        }
        entries.entrySet().removeIf(e -> {
            if (Bukkit.getPlayer(e.getKey()) == null) {
                e.getValue().display.remove();
                return true;
            }
            return false;
        });
    }

    /** Pasazer znika po teleportach / smierci / zmianie swiata - montujemy ponownie. */
    private void ensureMounted(Player p, ItemDisplay d) {
        Entity vehicle = d.getVehicle();
        if (vehicle != null && vehicle.equals(p)) {
            return;
        }
        if (!d.getWorld().equals(p.getWorld()) || d.getLocation().distanceSquared(p.getLocation()) > 4.0) {
            d.teleport(p.getLocation());
        }
        if (!p.addPassenger(d)) {
            // tryb awaryjny (np. inny plugin ma pasazera na graczu): przyklejamy recznie w punkcie
            // takim samym jak punkt mocowania pasazera (nad glowa), zeby transformacja byla ta sama
            d.teleport(p.getLocation().add(0, p.getHeight(), 0));
        }
    }

    private void update(Player p, Entry en) {
        Location eye = p.getEyeLocation();
        float yaw = eye.getYaw() + plugin.costumeHeadYawOffset();
        float pitch = eye.getPitch();
        // punkt mocowania pasazera = gora obrysu gracza (wysokosc zalezy od pozy); szyja jest nizej
        double neckOff = (p.getEyeHeight() - EYE_TO_CENTER - HEAD_HALF) - p.getHeight();

        if (Float.isNaN(en.lastYaw) || Math.abs(wrap(yaw - en.lastYaw)) > 0.3f) {
            en.display.setRotation(yaw, 0f);
            en.lastYaw = yaw;
        }
        if (Float.isNaN(en.lastPitch) || Math.abs(pitch - en.lastPitch) > 0.4f
                || Double.isNaN(en.lastNeck) || Math.abs(neckOff - en.lastNeck) > 0.01) {
            double th = Math.toRadians(pitch);
            Vector3f translation = new Vector3f(
                    0f,
                    (float) (neckOff + HEAD_HALF * Math.cos(th)),
                    (float) (HEAD_HALF * Math.sin(th)));
            Quaternionf rotation = new Quaternionf().rotationX((float) th);
            en.display.setInterpolationDelay(0);
            en.display.setInterpolationDuration(2);
            en.display.setTransformation(new Transformation(
                    translation, rotation, new Vector3f(0.5f, 0.5f, 0.5f), new Quaternionf()));
            en.lastPitch = pitch;
            en.lastNeck = neckOff;
        }
    }

    private static float wrap(float a) {
        a %= 360f;
        if (a > 180f) {
            a -= 360f;
        } else if (a < -180f) {
            a += 360f;
        }
        return a;
    }

    private ItemDisplay spawn(Cosmetic c, Location loc) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(c.headModel);
        stack.setItemMeta(meta);

        return loc.getWorld().spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            // model glowy ma 18 jednostek (9 px): skala 0.5 => 0.5625 bloku, tyle co glowa gracza z nakladka
            d.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f), new Quaternionf(),
                    new Vector3f(0.5f, 0.5f, 0.5f), new Quaternionf()));
            d.setBillboard(Display.Billboard.FIXED);
            d.setTeleportDuration(2);
            d.setPersistent(false);
            d.setInvulnerable(true);
        });
    }

    public void remove(Player p) {
        Entry en = entries.remove(p.getUniqueId());
        if (en != null) {
            en.display.remove();
        }
    }

    public void removeAll() {
        for (Entry en : entries.values()) {
            en.display.remove();
        }
        entries.clear();
    }
}
