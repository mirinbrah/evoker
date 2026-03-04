package player.evoker;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class EvokerWand extends JavaPlugin implements Listener {

    private NamespacedKey wandKey;
    private final Map<UUID, Long> lkmCooldowns = new HashMap<>();
    private final Map<UUID, Long> pkmCooldowns = new HashMap<>();
    private final Map<UUID, Long> totemCooldowns = new HashMap<>();
    private final Map<UUID, Long> activeBarriers = new HashMap<>();

    @Override
    public void onEnable() {
        wandKey = new NamespacedKey(this, "evoker_wand");
        getServer().getPluginManager().registerEvents(this, this);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            long now = System.currentTimeMillis();
            for (Player player : Bukkit.getOnlinePlayers()) {
                // Логика барьера
                if (activeBarriers.containsKey(player.getUniqueId()) && activeBarriers.get(player.getUniqueId()) > now) {
                    player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1, 0), 20, 1.5, 1, 1.5, 0.1);
                    for (Entity entity : player.getNearbyEntities(4, 4, 4)) {
                        if (entity instanceof LivingEntity && !entity.equals(player)) {
                            Vector push = entity.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(1.5);
                            entity.setVelocity(push);
                        } else if (entity instanceof Projectile proj && proj.getShooter() != player) {
                            proj.setVelocity(proj.getVelocity().multiply(-1));
                        }
                    }
                }

                // Отображение кулдаунов на хотбаре
                if (isWand(player.getInventory().getItemInMainHand())) {
                    sendCooldownActionBar(player, now);
                }
            }
        }, 0L, 5L); // Обновление каждые 5 тиков для плавности
    }

    private void sendCooldownActionBar(Player p, long now) {
        long lkmTime = (lkmCooldowns.getOrDefault(p.getUniqueId(), 0L) - now) / 1000;
        long pkmTime = (pkmCooldowns.getOrDefault(p.getUniqueId(), 0L) - now) / 1000;

        Component lkmPart = lkmTime > 0
                ? Component.text("ЛКМ: " + lkmTime + "с", NamedTextColor.RED)
                : Component.text("ЛКМ: Готов", NamedTextColor.GREEN);

        Component pkmPart = pkmTime > 0
                ? Component.text("ПКМ: " + pkmTime + "с", NamedTextColor.RED)
                : Component.text("ПКМ: Готов", NamedTextColor.GREEN);

        p.sendActionBar(Component.text("[ ", NamedTextColor.GRAY)
                .append(lkmPart)
                .append(Component.text(" | ", NamedTextColor.GRAY))
                .append(pkmPart)
                .append(Component.text(" ]", NamedTextColor.GRAY)));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p) || !p.hasPermission("evoker.give")) return true;
        ItemStack wand = new ItemStack(Material.STICK);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Палочка вызывателя", NamedTextColor.DARK_GRAY));
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
            wand.setItemMeta(meta);
        }
        p.getInventory().addItem(wand);
        return true;
    }

    private boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    private boolean hasWand(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isWand(item)) return true;
        }
        return false;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!isWand(p.getInventory().getItemInMainHand())) return;

        Action action = e.getAction();
        long now = System.currentTimeMillis();

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            if (lkmCooldowns.getOrDefault(p.getUniqueId(), 0L) > now) return;
            lkmCooldowns.put(p.getUniqueId(), now + 20000L);

            Location loc = p.getLocation();
            Vector dir = loc.getDirection().setY(0).normalize();

            for (int angle = -30; angle <= 30; angle += 15) {
                Vector rot = dir.clone().rotateAroundY(Math.toRadians(angle));
                for (int d = 1; d <= 12; d++) {
                    Location spawnLoc = loc.clone().add(rot.clone().multiply(d));
                    spawnLoc.setY(p.getWorld().getHighestBlockYAt(spawnLoc));
                    p.getWorld().spawn(spawnLoc, EvokerFangs.class);
                }
            }

            for (int i = 0; i < 7; i++) {
                Vex vex = p.getWorld().spawn(p.getLocation().add(0, 2, 0), Vex.class);
                vex.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 99999, 0));
                vex.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 99999, 0));
                vex.getPersistentDataContainer().set(new NamespacedKey(this, "owner"), PersistentDataType.STRING, p.getUniqueId().toString());
            }
            p.playSound(p.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1, 1);
        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            if (pkmCooldowns.getOrDefault(p.getUniqueId(), 0L) > now) return;
            pkmCooldowns.put(p.getUniqueId(), now + 30000L);
            activeBarriers.put(p.getUniqueId(), now + 10000L);
            p.playSound(p.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1, 0.5f);
        }
    }

    @EventHandler
    public void onTarget(EntityTargetEvent e) {
        if (e.getEntity() instanceof Vex vex && e.getTarget() instanceof Player p) {
            String ownerStr = vex.getPersistentDataContainer().get(new NamespacedKey(this, "owner"), PersistentDataType.STRING);
            if (ownerStr != null && ownerStr.equals(p.getUniqueId().toString())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && e.getFinalDamage() >= p.getHealth()) {
            if (hasWand(p)) {
                long now = System.currentTimeMillis();
                if (totemCooldowns.getOrDefault(p.getUniqueId(), 0L) > now) return;
                totemCooldowns.put(p.getUniqueId(), now + 60000L);

                e.setCancelled(true);
                p.setHealth(4.0);
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 900, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 800, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1));
                p.playEffect(EntityEffect.TOTEM_RESURRECT);
                p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1, 1);
            }
        }
    }
}