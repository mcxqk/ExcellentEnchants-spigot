package su.nightexpress.excellentenchants.manager.listener;

import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

import su.nightexpress.excellentenchants.EnchantsPlugin;
import su.nightexpress.excellentenchants.EnchantsUtils;
import su.nightexpress.excellentenchants.config.Config;
import su.nightexpress.excellentenchants.manager.EnchantManager;
import su.nightexpress.nightcore.manager.AbstractListener;

import java.util.Map;

@NullMarked
public class GenericListener extends AbstractListener<EnchantsPlugin> {

    private final EnchantManager manager;

    public GenericListener(EnchantsPlugin plugin, EnchantManager manager) {
        super(plugin);
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        this.manager.setSpawnReason(event.getEntity(), event.getSpawnReason());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChargesFillOnEnchant(EnchantItemEvent event) {
        if (!Config.isChargesEnabled()) return;

        Player player = event.getEnchanter();
        Inventory inventory = event.getInventory();
        Map<Enchantment, Integer> enchants = Map.copyOf(event.getEnchantsToAdd());
        this.plugin.schedulerUtil().runAtEntityDelayed(player, () -> {
            if (!player.getOpenInventory().getTopInventory().equals(inventory)) return;

            ItemStack result = inventory.getItem(0);
            if (result == null) return;

            enchants.forEach((enchantment, level) -> {
                EnchantsUtils.restoreCharges(result, enchantment, level);
            });

            inventory.setItem(0, result);
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTickedBlockBreak(BlockBreakEvent event) {
        if (this.manager.removeTickedBlock(event.getBlock())) {
            event.setDropItems(false);
            event.setExpToDrop(0);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTickedBlockTNTExplode(BlockExplodeEvent event) {
        event.blockList().forEach(this.manager::removeTickedBlock);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTickedBlockEntityExplode(EntityExplodeEvent event) {
        event.blockList().forEach(this.manager::removeTickedBlock);

        if (event.getEntity() instanceof LivingEntity entity) {
            this.manager.handleEnchantExplosion(event, entity);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onExplosionDamage(EntityDamageByEntityEvent event) {
        DamageSource source = event.getDamageSource();
        DamageType type = source.getDamageType();

        if (type != DamageType.PLAYER_EXPLOSION && type != DamageType.EXPLOSION) return;
        if (!(source.getCausingEntity() instanceof LivingEntity entity)) return;

        this.manager.handleEnchantExplosionDamage(event, entity);
    }
}
