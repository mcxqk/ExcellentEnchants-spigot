package su.nightexpress.excellentenchants.enchantment;

import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import su.nightexpress.excellentenchants.api.EnchantPriority;
import su.nightexpress.excellentenchants.api.enchantment.CustomEnchantment;
import su.nightexpress.nightcore.util.LowerCase;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@NullMarked
public class EnchantHolder<T extends CustomEnchantment> {

    private final Class<T>                     type;
    private final Function<T, EnchantPriority> priority;
    private final boolean                      cacheable;
    private final Map<String, T>               enchants;

    private final Map<UUID, Map<EquipmentSlot, EnchantedItem<T>>> cachedEnchants;

    private EnchantHolder(Class<T> type, Function<T, EnchantPriority> priority, boolean cacheable) {
        this.type = type;
        this.priority = priority;
        this.cacheable = cacheable;

        this.enchants = new ConcurrentHashMap<>();
        this.cachedEnchants = new ConcurrentHashMap<>();
    }


    public static <T extends CustomEnchantment> EnchantHolder<T> withNoCache(Class<T> type,
                                                                             Function<T, EnchantPriority> priority) {
        return new EnchantHolder<>(type, priority, false);
    }


    public static <T extends CustomEnchantment> EnchantHolder<T> cached(Class<T> type,
                                                                        Function<T, EnchantPriority> priority) {
        return new EnchantHolder<>(type, priority, true);
    }

    public void clear() {
        this.enchants.clear();
        this.cachedEnchants.clear();
    }

    public boolean isEmpty() {
        return this.enchants.isEmpty();
    }

    public boolean accept(CustomEnchantment enchantment) {
        T enchant = this.adapt(enchantment);
        if (enchant == null) return false;

        this.enchants.put(enchant.getId(), enchant);
        return true;
    }


    public Map<EquipmentSlot, EnchantedItem<T>> getCached(LivingEntity entity) {
        return this.cachedEnchants.getOrDefault(entity.getUniqueId(), Map.of());
    }

    @Nullable
    public EnchantedItem<T> getCached(LivingEntity entity, EquipmentSlot slot) {
        return this.getCached(entity).get(slot);
    }

    public void updateCache(LivingEntity entity, EquipmentSlot slot, ItemStack itemStack,
                            Map<CustomEnchantment, Integer> allEnchants) {
        if (allEnchants.isEmpty()) {
            this.removeCache(entity, slot);
            return;
        }

        Map<T, Integer> adaptedEnchants = new HashMap<>();
        allEnchants.forEach((enchantment, level) -> {
            T adapted = this.getEnchant(enchantment.getId());
            if (adapted == null) return;

            //EquipmentSlot[] enchantSlots = adapted.getSupportedItems().getSlots();
            //if (!Lists.contains(enchantSlots, slot)) return;

            adaptedEnchants.put(adapted, level);
        });

        if (adaptedEnchants.isEmpty()) {
            this.removeCache(entity, slot);
            return;
        }

        this.cachedEnchants.compute(entity.getUniqueId(), (uuid, current) -> {
            Map<EquipmentSlot, EnchantedItem<T>> updated = new EnumMap<>(EquipmentSlot.class);
            if (current != null) updated.putAll(current);
            updated.put(slot, new EnchantedItem<>(itemStack, Map.copyOf(adaptedEnchants)));
            return Map.copyOf(updated);
        });
    }

    public void removeCache(LivingEntity entity, EquipmentSlot slot) {
        this.cachedEnchants.computeIfPresent(entity.getUniqueId(), (uuid, current) -> {
            Map<EquipmentSlot, EnchantedItem<T>> updated = new EnumMap<>(EquipmentSlot.class);
            updated.putAll(current);
            updated.remove(slot);
            return updated.isEmpty() ? null : Map.copyOf(updated);
        });
    }

    public void clearCache(LivingEntity entity) {
        this.clearCache(entity.getUniqueId());
    }

    public void clearCache(UUID entityId) {
        this.cachedEnchants.remove(entityId);
    }

    public void clearCache() {
        this.cachedEnchants.clear();
    }

    public boolean isCacheable() {
        return this.cacheable;
    }


    public EnchantPriority getPriority(T enchant) {
        return this.priority.apply(enchant);
    }


    public Set<T> getEnchants() {
        return Set.copyOf(this.enchants.values());
    }

    @Nullable
    public T getEnchant(String id) {
        return this.enchants.get(LowerCase.INTERNAL.apply(id));
    }

    @Nullable
    private T adapt(CustomEnchantment enchantment) {
        return this.type.isAssignableFrom(enchantment.getClass()) ? this.type.cast(enchantment) : null;
    }

    public boolean contains(CustomEnchantment enchantment) {
        return this.enchants.containsKey(enchantment.getId());
    }
}
