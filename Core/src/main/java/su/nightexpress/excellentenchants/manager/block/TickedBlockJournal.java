package su.nightexpress.excellentenchants.manager.block;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;

public final class TickedBlockJournal {

    private static final Comparator<Map.Entry<BlockKey, Material>> ENTRY_ORDER = Comparator
        .comparing((Map.Entry<BlockKey, Material> entry) -> entry.getKey().worldId())
        .thenComparingInt(entry -> entry.getKey().x())
        .thenComparingInt(entry -> entry.getKey().y())
        .thenComparingInt(entry -> entry.getKey().z());

    private final Path file;
    private final Map<BlockKey, Material> entries;

    public TickedBlockJournal(Path file) {
        this.file = file;
        this.entries = new ConcurrentHashMap<>();
    }

    public void put(BlockKey key, Material material) {
        this.entries.put(key, material);
    }

    public void remove(BlockKey key) {
        this.entries.remove(key);
    }

    public Map<BlockKey, Material> snapshot() {
        return Map.copyOf(this.entries);
    }

    public synchronized void load() throws IOException {
        if (!Files.exists(this.file)) return;

        Map<BlockKey, Material> loaded = new ConcurrentHashMap<>();
        for (String line : Files.readAllLines(this.file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;

            String[] values = line.split(";", -1);
            if (values.length != 5) throw new IOException("Invalid ticked block entry: " + line);

            try {
                BlockKey key = new BlockKey(UUID.fromString(values[0]), Integer.parseInt(values[1]),
                    Integer.parseInt(values[2]), Integer.parseInt(values[3]));
                loaded.put(key, Material.valueOf(values[4]));
            }
            catch (IllegalArgumentException exception) {
                throw new IOException("Invalid ticked block entry: " + line, exception);
            }
        }

        loaded.forEach(this.entries::putIfAbsent);
    }

    public synchronized void save() throws IOException {
        Map<BlockKey, Material> snapshot = this.snapshot();
        Path temporary = this.file.resolveSibling(this.file.getFileName() + ".tmp");
        if (snapshot.isEmpty()) {
            Files.deleteIfExists(this.file);
            Files.deleteIfExists(temporary);
            return;
        }

        Path parent = this.file.getParent();
        if (parent != null) Files.createDirectories(parent);

        List<String> lines = snapshot.entrySet().stream()
            .sorted(ENTRY_ORDER)
            .map(entry -> this.serialize(entry.getKey(), entry.getValue()))
            .toList();
        Files.write(temporary, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.move(temporary, this.file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, this.file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String serialize(BlockKey key, Material material) {
        return key.worldId() + ";" + key.x() + ";" + key.y() + ";" + key.z() + ";" + material.name();
    }
}
