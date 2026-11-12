package pl.szafa;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import pl.szafa.Cosmetic.Kind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Umiejetnosci kostiumow na SHIFT: zamrozenie (Lodowy Straznik), pajeczyna (Spiderman), szarza (Koziolek). */
public final class AbilityManager implements Listener {

    private static final long SNEAK_WINDOW_MS = 1200L;

    private final SzafaPlugin plugin;
    private final Map<UUID, Long> frozenUntil = new HashMap<>();
    private final Map<UUID, Integer> sneakCount = new HashMap<>();
    private final Map<UUID, Long> lastSneak = new HashMap<>();
    private final Map<UUID, Long> lastWarn = new HashMap<>();

    public AbilityManager(SzafaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickFrozen, 5L, 5L);
    }

    // ------------------------------------------------------------------ SHIFT

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (!e.isSneaking()) {
            return;
        }
        Player p = e.getPlayer();
        Cosmetic c = plugin.activeOf(p, Kind.COSTUME);
        if (c == null || c.ability == null) {
            return;
        }
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        long last = lastSneak.getOrDefault(id, 0L);
        int n = (now - last > SNEAK_WINDOW_MS) ? 1 : sneakCount.getOrDefault(id, 0) + 1;
        lastSneak.put(id, now);
        if (n < c.ability.sneaks) {
            sneakCount.put(id, n);
            return;
        }
        sneakCount.put(id, 0);
        lastSneak.put(id, 0L);
        trigger(p, c);
    }

    private void trigger(Player p, Cosmetic c) {
        Ability a = c.ability;
        PlayerData d = plugin.data().get(p.getUniqueId());
        long now = System.currentTimeMillis();
        Long until = d.cooldowns.get(c.id);
        if (until != null && until > now) {
            warn(p, "&cUmiejętność dostępna za &f" + cooldownText(until - now));
            return;
        }
        boolean used = switch (a.type) {
            case FREEZE -> freeze(p, a);
            case WEB -> web(p, a);
            case CHARGE -> charge(p, a);
        };
        if (used) {
            d.cooldowns.put(c.id, now + a.cooldownSec * 1000L);
            plugin.data().markDirty();
        }
    }

    private static String cooldownText(long ms) {
        long totalSec = Math.max(1, (ms + 999) / 1000);
        long m = totalSec / 60;
        long s = totalSec % 60;
        return m > 0 ? m + "m " + s + "s" : s + "s";
    }

    /** Komunikat na action barze, ograniczony do jednego na 1.5 s (zeby nie spamowac). */
    private void warn(Player p, String text) {
        long now = System.currentTimeMillis();
        if (now - lastWarn.getOrDefault(p.getUniqueId(), 0L) < 1500L) {
            return;
        }
        lastWarn.put(p.getUniqueId(), now);
        p.sendActionBar(Text.c(text));
    }

    private List<Player> nearbyEnemies(Player p, double r) {
        List<Player> out = new ArrayList<>();
        Location here = p.getLocation();
        for (Entity e : p.getNearbyEntities(r, r, r)) {
            if (e instanceof Player t && !t.equals(p)
                    && t.getGameMode() != GameMode.SPECTATOR && t.getGameMode() != GameMode.CREATIVE
                    && t.getWorld().equals(p.getWorld())
                    && t.getLocation().distanceSquared(here) <= r * r) {
                out.add(t);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ Lodowy Straznik: zamrozenie

    private boolean freeze(Player p, Ability a) {
        List<Player> targets = nearbyEnemies(p, a.radius);
        if (targets.isEmpty()) {
            warn(p, "&7Brak przeciwników w promieniu &f" + (int) a.radius + " &7bloków.");
            return false;
        }
        long until = System.currentTimeMillis() + (long) (a.durationSec * 1000);
        for (Player t : targets) {
            frozenUntil.put(t.getUniqueId(), until);
            t.setFreezeTicks(t.getMaxFreezeTicks());
            t.getWorld().playSound(t.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 1f);
            plugin.msg(t, "&bZostałeś zamrożony przez &f" + p.getName() + " &bna &f" + (int) a.durationSec + "s&b!");
        }
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.6f);
        for (int i = 0; i < 48; i++) {
            double ang = Math.PI * 2 * i / 48;
            Location ring = p.getLocation().add(Math.cos(ang) * 2.5, 0.3, Math.sin(ang) * 2.5);
            p.getWorld().spawnParticle(Particle.SNOWFLAKE, ring, 2, 0.1, 0.2, 0.1, 0.01);
        }
        plugin.msg(p, "&bZamrożono &f" + targets.size() + " &bprzeciwników na &f" + (int) a.durationSec + "s&b.");
        return true;
    }

    private void tickFrozen() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = frozenUntil.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            Player t = Bukkit.getPlayer(e.getKey());
            if (t == null || t.isDead()) {
                it.remove();
                continue;
            }
            if (e.getValue() <= now) {
                it.remove();
                t.setFreezeTicks(0);
                plugin.msg(t, "&bRozmrożono!");
                continue;
            }
            t.setFreezeTicks(t.getMaxFreezeTicks());
            t.getWorld().spawnParticle(Particle.SNOWFLAKE, t.getLocation().add(0, 1, 0), 6, 0.4, 0.7, 0.4, 0.01);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Long until = frozenUntil.get(e.getPlayer().getUniqueId());
        if (until == null) {
            return;
        }
        if (until <= System.currentTimeMillis()) {
            frozenUntil.remove(e.getPlayer().getUniqueId());
            return;
        }
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) {
            return;
        }
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location fixed = from.clone();
            fixed.setYaw(to.getYaw());
            fixed.setPitch(to.getPitch());
            e.setTo(fixed);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (!frozenUntil.containsKey(e.getPlayer().getUniqueId())) {
            return;
        }
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || e.getCause() == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ Spiderman: pajeczyna

    private boolean web(Player p, Ability a) {
        List<Player> targets = nearbyEnemies(p, a.radius);
        if (targets.isEmpty()) {
            warn(p, "&7Brak przeciwników w promieniu &f" + (int) a.radius + " &7bloków.");
            return false;
        }
        int ticks = (int) (a.durationSec * 20);
        Location from = p.getEyeLocation();
        for (Player t : targets) {
            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, a.amplifier, false, true, true));
            Location to = t.getLocation().add(0, 1, 0);
            Vector dir = to.toVector().subtract(from.toVector());
            double len = dir.length();
            if (len > 0.01) {
                dir.normalize();
                for (double d = 0; d < len; d += 0.6) {
                    p.getWorld().spawnParticle(Particle.BLOCK, from.clone().add(dir.clone().multiply(d)),
                            1, 0, 0, 0, 0, Material.COBWEB.createBlockData());
                }
            }
            t.getWorld().spawnParticle(Particle.BLOCK, to, 40, 0.4, 0.6, 0.4, 0, Material.COBWEB.createBlockData());
            t.getWorld().playSound(t.getLocation(), Sound.BLOCK_COBWEB_PLACE, 1f, 1f);
            plugin.msg(t, "&fOplątała Cię pajęcza sieć gracza &7" + p.getName() + "&f!");
        }
        plugin.msg(p, "&fOplątano siecią &7" + targets.size() + " &fprzeciwników na &7" + (int) a.durationSec + "s&f.");
        return true;
    }

    // ------------------------------------------------------------------ Koziolek: szarza

    private boolean charge(Player p, Ability a) {
        Vector dir = p.getLocation().getDirection();
        dir.setY(0);
        if (dir.lengthSquared() < 1.0E-4) {
            return false;
        }
        dir.normalize();
        p.setVelocity(dir.clone().multiply(a.power).setY(0.25));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GOAT_PREPARE_RAM, 1f, 1f);
        Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || t++ > 10) {
                    cancel();
                    return;
                }
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.3, 0), 3, 0.2, 0.1, 0.2, 0.01);
                for (Player target : nearbyEnemies(p, a.radius)) {
                    if (!hit.add(target.getUniqueId())) {
                        continue;
                    }
                    target.setVelocity(dir.clone().multiply(a.power).setY(0.5));
                    target.damage(a.damage, p);
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GOAT_RAM_IMPACT, 1f, 1f);
                    plugin.msg(target, "&fKoziołek &7" + p.getName() + " &fstaranował Cię!");
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    // ------------------------------------------------------------------ sprzatanie

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        frozenUntil.remove(id);
        sneakCount.remove(id);
        lastSneak.remove(id);
        lastWarn.remove(id);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        frozenUntil.remove(e.getEntity().getUniqueId());
        e.getEntity().setFreezeTicks(0);
    }
}
