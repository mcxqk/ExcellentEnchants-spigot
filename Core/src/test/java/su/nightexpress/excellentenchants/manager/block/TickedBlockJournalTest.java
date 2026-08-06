package su.nightexpress.excellentenchants.manager.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TickedBlockJournalTest {

    @Test
    void roundTripsPendingBlocks(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("ticked-blocks.dat");
        UUID worldId = UUID.randomUUID();
        BlockKey first = new BlockKey(worldId, 10, 64, -4);
        BlockKey second = new BlockKey(worldId, -2, 80, 15);
        TickedBlockJournal journal = new TickedBlockJournal(file);
        journal.put(first, Material.LAVA);
        journal.put(second, Material.WATER);

        journal.save();
        TickedBlockJournal loaded = new TickedBlockJournal(file);
        loaded.load();

        assertEquals(Map.of(first, Material.LAVA, second, Material.WATER), loaded.snapshot());
    }

    @Test
    void removingLastEntryDeletesJournal(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("ticked-blocks.dat");
        BlockKey key = new BlockKey(UUID.randomUUID(), 1, 2, 3);
        TickedBlockJournal journal = new TickedBlockJournal(file);
        journal.put(key, Material.LAVA);
        journal.save();

        journal.remove(key);
        journal.save();

        assertFalse(Files.exists(file));
    }

    @Test
    void loadMergesEntriesAddedBeforeAsyncRecoveryCompletes(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("ticked-blocks.dat");
        BlockKey recovered = new BlockKey(UUID.randomUUID(), 1, 64, 1);
        BlockKey current = new BlockKey(UUID.randomUUID(), 2, 70, 2);
        TickedBlockJournal stored = new TickedBlockJournal(file);
        stored.put(recovered, Material.LAVA);
        stored.save();
        TickedBlockJournal loading = new TickedBlockJournal(file);
        loading.put(current, Material.WATER);

        loading.load();

        assertEquals(Map.of(recovered, Material.LAVA, current, Material.WATER), loading.snapshot());
    }
}
