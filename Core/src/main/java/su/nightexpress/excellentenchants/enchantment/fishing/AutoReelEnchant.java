package su.nightexpress.excellentenchants.enchantment.fishing;

import org.bukkit.Material;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

import su.nightexpress.excellentenchants.EnchantsPlugin;
import su.nightexpress.excellentenchants.EnchantsUtils;
import su.nightexpress.excellentenchants.api.EnchantPriority;
import su.nightexpress.excellentenchants.api.enchantment.type.FishingEnchant;
import su.nightexpress.excellentenchants.enchantment.EnchantContext;
import su.nightexpress.excellentenchants.enchantment.GameEnchantment;
import su.nightexpress.excellentenchants.manager.EnchantManager;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.util.EntityUtil;

import java.nio.file.Path;

@NullMarked
public class AutoReelEnchant extends GameEnchantment implements FishingEnchant {

    public AutoReelEnchant(EnchantsPlugin plugin, EnchantManager manager, Path file, EnchantContext context) {
        super(plugin, manager, file, context);
    }

    @Override
    protected void loadAdditional(FileConfig config) {

    }

    @Override

    public EnchantPriority getFishingPriority() {
        return EnchantPriority.MONITOR;
    }

    @Override
    public boolean onFishing(PlayerFishEvent event, ItemStack itemStack, int level) {
        if (event.getState() != PlayerFishEvent.State.BITE) return false;

        Player player = event.getPlayer();
        EquipmentSlot slot = EnchantsUtils.getItemHand(player, Material.FISHING_ROD);
        if (slot == null) return false;

        FishHook hook = event.getHook();
        ItemStack rodSnapshot = itemStack.clone();
        this.plugin.schedulerUtil().runAtEntityDelayed(hook, () -> {
            if (!hook.isValid()) return;
            if (!this.plugin.schedulerUtil().isOwned(player)) return;

            ItemStack rod = EntityUtil.getItemInSlot(player, slot);
            if (rod == null || rod.getType() != Material.FISHING_ROD) return;
            if (!rod.isSimilar(rodSnapshot)) return;

            player.swingHand(slot);
            hook.retrieve(slot);
            player.damageItemStack(rod, 1);
        }, 1L);
        return true;
    }
}
