package su.nightexpress.excellentenchants.enchantment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import su.nightexpress.excellentenchants.api.EnchantPriority;
import su.nightexpress.excellentenchants.api.enchantment.CustomEnchantment;

class EnchantHolderTest {

    private EnchantHolder<CustomEnchantment> holder;
    private CustomEnchantment enchantment;

    @BeforeEach
    void setUp() {
        this.holder = EnchantHolder.cached(CustomEnchantment.class, enchant -> EnchantPriority.NORMAL);
        this.enchantment = mock(CustomEnchantment.class);
        when(this.enchantment.getId()).thenReturn("test");
        assertTrue(this.holder.accept(this.enchantment));
    }

    @Test
    void updatesEntitySlotsConcurrentlyWithoutLosingEntries() throws Exception {
        LivingEntity entity = this.entity(UUID.randomUUID());
        EquipmentSlot[] slots = Arrays.stream(EquipmentSlot.values())
            .filter(slot -> slot != EquipmentSlot.BODY && slot != EquipmentSlot.SADDLE)
            .toArray(EquipmentSlot[]::new);
        Map<EquipmentSlot, ItemStack> items = Arrays.stream(slots)
            .collect(java.util.stream.Collectors.toUnmodifiableMap(slot -> slot, slot -> mock(ItemStack.class)));
        ExecutorService executor = Executors.newFixedThreadPool(slots.length);
        List<Callable<Void>> writes = IntStream.range(0, 500)
            .mapToObj(index -> (Callable<Void>) () -> {
                EquipmentSlot slot = slots[index % slots.length];
                this.holder.updateCache(entity, slot, items.get(slot), Map.of(this.enchantment, 1));
                return null;
            })
            .toList();

        executor.invokeAll(writes);
        executor.shutdown();

        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(slots.length, this.holder.getCached(entity).size());
        Arrays.stream(slots).forEach(slot -> assertNotNull(this.holder.getCached(entity, slot)));
    }

    @Test
    void returnedSlotMapIsImmutableSnapshot() {
        LivingEntity entity = this.entity(UUID.randomUUID());
        this.holder.updateCache(entity, EquipmentSlot.HEAD, mock(ItemStack.class), Map.of(this.enchantment, 1));

        Map<EquipmentSlot, EnchantedItem<CustomEnchantment>> snapshot = this.holder.getCached(entity);

        assertThrows(UnsupportedOperationException.class, snapshot::clear);
    }

    @Test
    void returnedEnchantCollectionsAreImmutableSnapshots() {
        LivingEntity entity = this.entity(UUID.randomUUID());
        this.holder.updateCache(entity, EquipmentSlot.CHEST, mock(ItemStack.class), Map.of(this.enchantment, 1));
        EnchantedItem<CustomEnchantment> cached = this.holder.getCached(entity, EquipmentSlot.CHEST);
        assertNotNull(cached);

        assertThrows(UnsupportedOperationException.class, () -> cached.getEnchants().clear());
        assertThrows(UnsupportedOperationException.class, () -> this.holder.getEnchants().clear());
    }

    @Test
    void removingSlotsReplacesSnapshotAndCleansEmptyEntityCache() {
        LivingEntity entity = this.entity(UUID.randomUUID());
        this.holder.updateCache(entity, EquipmentSlot.HEAD, mock(ItemStack.class), Map.of(this.enchantment, 1));
        this.holder.updateCache(entity, EquipmentSlot.CHEST, mock(ItemStack.class), Map.of(this.enchantment, 1));
        Map<EquipmentSlot, EnchantedItem<CustomEnchantment>> original = this.holder.getCached(entity);

        this.holder.removeCache(entity, EquipmentSlot.HEAD);

        assertEquals(2, original.size());
        assertEquals(1, this.holder.getCached(entity).size());
        assertNotNull(this.holder.getCached(entity, EquipmentSlot.CHEST));

        this.holder.removeCache(entity, EquipmentSlot.CHEST);

        assertTrue(this.holder.getCached(entity).isEmpty());
    }

    @Test
    void clearRemovesRegistryAndEntityCaches() {
        LivingEntity entity = this.entity(UUID.randomUUID());
        this.holder.updateCache(entity, EquipmentSlot.HEAD, mock(ItemStack.class), Map.of(this.enchantment, 1));

        this.holder.clear();

        assertTrue(this.holder.getEnchants().isEmpty());
        assertTrue(this.holder.getCached(entity).isEmpty());
    }

    private LivingEntity entity(UUID uniqueId) {
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.getUniqueId()).thenReturn(uniqueId);
        return entity;
    }
}
