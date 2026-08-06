package su.nightexpress.excellentenchants.enchantment.bow;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.projectiles.ProjectileSource;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import su.nightexpress.excellentenchants.EnchantsPlugin;
import su.nightexpress.excellentenchants.api.EnchantPriority;
import su.nightexpress.excellentenchants.api.enchantment.component.EnchantComponent;
import su.nightexpress.excellentenchants.api.enchantment.meta.Probability;
import su.nightexpress.excellentenchants.api.enchantment.type.ArrowEnchant;
import su.nightexpress.excellentenchants.enchantment.EnchantContext;
import su.nightexpress.excellentenchants.enchantment.GameEnchantment;
import su.nightexpress.excellentenchants.manager.EnchantManager;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.util.ItemUtil;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

@NullMarked
public class LingeringEnchant extends GameEnchantment implements ArrowEnchant {

    public LingeringEnchant(EnchantsPlugin plugin, EnchantManager manager, Path file, EnchantContext context) {
        super(plugin, manager, file, context);
        this.addComponent(EnchantComponent.PROBABILITY, Probability.addictive(5, 5));
    }

    @Override
    protected void loadAdditional(FileConfig config) {

    }

    @Override

    public EnchantPriority getShootPriority() {
        return EnchantPriority.NORMAL;
    }

    @Override
    public boolean onShoot(EntityShootBowEvent event, LivingEntity shooter, ItemStack bow, int level) {
        return true;
    }

    @Override
    public void onHit(ProjectileHitEvent event, LivingEntity shooter, Arrow arrow, int level) {
        if (event.getHitEntity() != null) return;

        this.createCloud(arrow, shooter, arrow.getLocation(), event.getHitEntity(), event.getHitBlock(), event
            .getHitBlockFace());
    }

    @Override
    public void onDamage(EntityDamageByEntityEvent event, LivingEntity shooter, LivingEntity victim, Arrow arrow,
                         int level) {

    }

    private void createCloud(Arrow arrow,
                             ProjectileSource shooter,
                             Location location,
                             @Nullable Entity hitEntity,
                             @Nullable Block hitBlock,
                             @Nullable BlockFace hitFace) {

        Set<PotionEffect> effects = new HashSet<>();
        if (arrow.hasCustomEffects()) {
            effects.addAll(arrow.getCustomEffects());
        }
        if (arrow.getBasePotionType() != null) {
            effects.addAll(arrow.getBasePotionType().getPotionEffects());
        }
        if (effects.isEmpty()) return;

        // There are some tweaks to respect protection plugins by using event call.
        ItemStack item = new ItemStack(Material.LINGERING_POTION);
        ItemUtil.editMeta(item, meta -> {
            if (meta instanceof PotionMeta potionMeta) {
                effects.forEach(potionEffect -> potionMeta.addCustomEffect(potionEffect, true));
            }
        });

        ThrownPotion potion = location.getWorld().spawn(location, ThrownPotion.class);
        potion.setItem(item);
        if (shooter instanceof Entity entity && this.plugin.schedulerUtil().isOwned(entity)) {
            potion.setShooter(shooter);
        }

        AreaEffectCloud cloud = potion.getWorld().spawn(location, AreaEffectCloud.class);
        cloud.clearCustomEffects();
        if (shooter instanceof Entity entity && this.plugin.schedulerUtil().isOwned(entity)) {
            cloud.setSource(shooter);
        }
        cloud.setWaitTime(10);
        cloud.setRadius(3F); // 3.0
        cloud.setRadiusOnUse(-0.5F);
        cloud.setDuration(600); // 600
        cloud.setRadiusPerTick(-cloud.getRadius() / (float) cloud.getDuration());
        cloud.setBasePotionType(arrow.getBasePotionType());
        effects.forEach(potionEffect -> cloud.addCustomEffect(potionEffect, false));

        LingeringPotionSplashEvent splashEvent = new LingeringPotionSplashEvent(potion, hitEntity, hitBlock, hitFace, cloud);
        plugin.getPluginManager().callEvent(splashEvent);
        if (splashEvent.isCancelled()) {
            cloud.remove();
        }
        potion.remove();
    }
}
