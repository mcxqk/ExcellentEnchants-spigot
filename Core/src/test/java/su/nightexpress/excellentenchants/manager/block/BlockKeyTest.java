package su.nightexpress.excellentenchants.manager.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class BlockKeyTest {

    @Test
    void equalityUsesWorldAndBlockCoordinates() {
        UUID worldId = UUID.randomUUID();
        World world = mock(World.class);
        when(world.getUID()).thenReturn(worldId);
        Location firstLocation = new Location(world, 10.75D, 64D, -4.25D);
        Location secondLocation = new Location(world, 10.1D, 64.9D, -4.9D);

        BlockKey first = BlockKey.from(firstLocation);
        BlockKey second = BlockKey.from(secondLocation);

        assertEquals(first, second);
        assertEquals(new BlockKey(worldId, 10, 64, -5), first);
    }

    @Test
    void locationMutationDoesNotChangeExistingKey() {
        UUID worldId = UUID.randomUUID();
        World world = mock(World.class);
        when(world.getUID()).thenReturn(worldId);
        Location location = new Location(world, 2D, 70D, 3D);
        BlockKey key = BlockKey.from(location);

        location.setX(200D);
        location.setY(5D);
        location.setZ(-30D);

        assertEquals(new BlockKey(worldId, 2, 70, 3), key);
    }
}
