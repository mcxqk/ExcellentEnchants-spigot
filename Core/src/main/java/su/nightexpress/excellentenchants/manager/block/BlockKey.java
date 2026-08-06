package su.nightexpress.excellentenchants.manager.block;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;

public record BlockKey(UUID worldId, int x, int y, int z) {

    public static BlockKey from(Location location) {
        World world = Objects.requireNonNull(location.getWorld());
        return new BlockKey(world.getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
