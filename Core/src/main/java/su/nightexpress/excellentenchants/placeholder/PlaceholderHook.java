package su.nightexpress.excellentenchants.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import su.nightexpress.excellentenchants.EnchantsPlugin;
import su.nightexpress.excellentenchants.api.enchantment.CustomEnchantment;
import su.nightexpress.excellentenchants.enchantment.EnchantRegistry;
import su.nightexpress.nightcore.util.Enums;
import su.nightexpress.nightcore.util.LowerCase;
import su.nightexpress.nightcore.util.Numbers;

@NullMarked
public class PlaceholderHook {

    private static EnchantsExpansion expansion;

    public static void setup(EnchantsPlugin plugin) {
        if (expansion == null) {
            expansion = new EnchantsExpansion(plugin);
            expansion.register();
        }
    }

    public static void shutdown() {
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
    }

    static class EnchantsExpansion extends PlaceholderExpansion {

        private final EnchantsPlugin plugin;

        public EnchantsExpansion(EnchantsPlugin plugin) {
            this.plugin = plugin;
        }

        @Override

        public String getIdentifier() {
            return LowerCase.INTERNAL.apply(this.plugin.getName());
        }

        @Override

        public String getAuthor() {
            return String.join(", ", this.plugin.getDescription().getAuthors());
        }

        @Override

        public String getVersion() {
            return this.plugin.getDescription().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        @Nullable
        public String onPlaceholderRequest(Player player, String params) {
            if (params.startsWith("charges_remaining_")) {
                String[] chargesSplit = params.substring("charges_remaining_".length()).split(":");
                if (chargesSplit.length < 2) return null;

                EquipmentSlot slot = Enums.get(chargesSplit[0], EquipmentSlot.class);
                if (slot == null) return null;
                if (!this.plugin.schedulerUtil().isOwned(player)) return null;

                ItemStack itemStack = player.getInventory().getItem(slot);
                if (itemStack == null || itemStack.getType().isAir()) return "-";

                CustomEnchantment enchant = EnchantRegistry.getById(chargesSplit[1]);
                if (enchant == null) return null;

                return String.valueOf(enchant.getCharges(itemStack));
            }

            if (params.startsWith("charges_maximum_")) {
                String[] chargesSplit = params.substring("charges_maximum_".length()).split(":");
                if (chargesSplit.length < 2) return null;

                CustomEnchantment enchant = EnchantRegistry.getById(chargesSplit[0]);
                if (enchant == null) return null;

                int level = Numbers.getIntegerAbs(chargesSplit[1], 1);

                return String.valueOf(enchant.getCharges().getMaxAmount(level));
            }

            return super.onPlaceholderRequest(player, params);
        }
    }
}
