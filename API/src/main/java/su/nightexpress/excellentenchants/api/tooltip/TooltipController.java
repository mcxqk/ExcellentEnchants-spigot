package su.nightexpress.excellentenchants.api.tooltip;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.UUID;


@NullMarked
public interface TooltipController {

    boolean hasHandler();

    ItemStack addDescription(ItemStack itemStack);

    boolean isReadyForTooltipUpdate(UUID playerId);

    default boolean isReadyForTooltipUpdate(Player player) {
        return this.isReadyForTooltipUpdate(player.getUniqueId());
    }

    boolean isEnchantTooltipAllowed(ItemStack item);

    void addToUpdateStopList(Player player);

    void removeFromUpdateStopList(Player player);

    void runInStopList(Player player, Runnable runnable);
}
