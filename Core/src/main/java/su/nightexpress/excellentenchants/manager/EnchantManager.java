package su.nightexpress.excellentenchants.manager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import su.nightexpress.excellentenchants.EnchantsFiles;
import su.nightexpress.excellentenchants.EnchantsPlugin;
import su.nightexpress.excellentenchants.EnchantsUtils;
import su.nightexpress.excellentenchants.api.EnchantPriority;
import su.nightexpress.excellentenchants.api.enchantment.CustomEnchantment;
import su.nightexpress.excellentenchants.api.enchantment.component.EnchantComponent;
import su.nightexpress.excellentenchants.api.enchantment.type.BlockEnchant;
import su.nightexpress.excellentenchants.api.enchantment.type.ProjectileEnchant;
import su.nightexpress.excellentenchants.api.item.ItemSetDefaults;
import su.nightexpress.excellentenchants.enchantment.EnchantCatalog;
import su.nightexpress.excellentenchants.enchantment.EnchantContext;
import su.nightexpress.excellentenchants.enchantment.EnchantHolder;
import su.nightexpress.excellentenchants.enchantment.EnchantRegistry;
import su.nightexpress.excellentenchants.enchantment.EnchantSettings;
import su.nightexpress.excellentenchants.enchantment.EnchantedItem;
import su.nightexpress.excellentenchants.manager.block.BlockKey;
import su.nightexpress.excellentenchants.manager.block.TickedBlock;
import su.nightexpress.excellentenchants.manager.block.TickedBlockJournal;
import su.nightexpress.excellentenchants.manager.damage.Explosion;
import su.nightexpress.excellentenchants.manager.listener.AnvilListener;
import su.nightexpress.excellentenchants.manager.listener.EnchantListener;
import su.nightexpress.excellentenchants.manager.listener.GenericListener;
import su.nightexpress.excellentenchants.manager.listener.PassiveEntityListener;
import su.nightexpress.excellentenchants.manager.listener.SlotListener;
import su.nightexpress.excellentenchants.manager.menu.EnchantsMenu;
import su.nightexpress.excellentenchants.scheduler.SchedulerTask;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.manager.AbstractManager;
import su.nightexpress.nightcore.util.BukkitThing;
import su.nightexpress.nightcore.util.EntityUtil;
import su.nightexpress.nightcore.util.Enums;
import su.nightexpress.nightcore.util.PDCUtil;
import su.nightexpress.nightcore.util.Version;
import su.nightexpress.nightcore.util.bridge.RegistryType;
import su.nightexpress.nightcore.util.wrapper.UniParticle;

@NullMarked
public class EnchantManager extends AbstractManager<EnchantsPlugin> {

    private final Map<UUID, ArrowEffects>   arrowEffects;
    private final Map<UUID, SchedulerTask>  passiveTasks;
    private final Map<BlockKey, ActiveBlock> tickedBlocks;
    private final Map<UUID, Explosion>      explosions;

    private final TickedBlockJournal tickedBlockJournal;
    private final AtomicBoolean active;
    private final AtomicBoolean journalLoaded;
    private final AtomicBoolean journalDirty;
    private final AtomicBoolean journalSaveRunning;

    private final EnchantSettings settings;

    private final NamespacedKey entitySpawnKey;
    private final NamespacedKey blockEnchantKey;

    private EnchantsMenu enchantsMenu;

    public EnchantManager(EnchantsPlugin plugin) {
        super(plugin);
        this.arrowEffects = new ConcurrentHashMap<>();
        this.passiveTasks = new ConcurrentHashMap<>();
        this.tickedBlocks = new ConcurrentHashMap<>();
        this.explosions = new ConcurrentHashMap<>();
        this.tickedBlockJournal = new TickedBlockJournal(plugin.getDataFolder().toPath().resolve(
            "ticked-blocks.dat"));
        this.active = new AtomicBoolean();
        this.journalLoaded = new AtomicBoolean();
        this.journalDirty = new AtomicBoolean();
        this.journalSaveRunning = new AtomicBoolean();
        this.settings = new EnchantSettings();

        this.entitySpawnKey = new NamespacedKey(plugin, "entity.spawn_reason");
        this.blockEnchantKey = new NamespacedKey(plugin, "block.enchant");
    }

    protected void onLoad() {
        this.active.set(true);
        this.settings.load(this.plugin.getConfig());
        this.loadEnchants();

        this.enchantsMenu = new EnchantsMenu(this.plugin);

        this.addListener(new GenericListener(this.plugin, this));
        this.addListener(new AnvilListener(this.plugin, this.settings));
        this.addListener(new EnchantListener(this.plugin, this));

        if (Version.isPaper()) {
            this.addListener(new SlotListener(this.plugin, this));
        }

        if (!EnchantRegistry.PASSIVE.isEmpty()) {
            this.addListener(new PassiveEntityListener(this.plugin, this));
            this.startOnlinePlayerPassiveTasks();
        }

        this.loadTickedBlockJournal();
    }

    @Override
    protected void onShutdown() {
        if (!this.active.compareAndSet(true, false)) return;

        if (this.enchantsMenu != null) this.enchantsMenu.clear();

        this.arrowEffects.values().forEach(effects -> effects.task().cancel());
        this.arrowEffects.clear();

        this.passiveTasks.values().forEach(SchedulerTask::cancel);
        this.passiveTasks.clear();

        this.tickedBlocks.values().forEach(activeBlock -> activeBlock.task().cancel());
        this.tickedBlocks.clear();

        this.explosions.clear();
        EnchantRegistry.getHolders().forEach(EnchantHolder::clearCache);
        this.saveJournalOnShutdown();
    }

    private void loadEnchants() {
        EnchantCatalog.enabled().forEach(this::loadEnchant);
        ItemSetDefaults.clearAll(); // Clear default item sets from memory.

        this.plugin.info("Loaded " + EnchantRegistry.getRegistered().size() + " enchantments.");
    }

    private boolean loadEnchant(EnchantCatalog catalog) {
        String id = catalog.getId();
        CustomEnchantment registered = EnchantRegistry.getById(id);
        if (registered != null) {
            registered.load();
            return true;
        }

        Path file = Path.of(this.plugin.getDataFolder() + EnchantsFiles.DIR_ENCHANTS, FileConfig.withExtension(id));
        if (!Files.exists(file)) {
            this.plugin.error("No config file present for the '%s' enchantment.".formatted(id));
            return false;
        }

        Enchantment bukkitEnchant = BukkitThing.getByKey(RegistryType.ENCHANTMENT, catalog.getKey());
        if (bukkitEnchant == null) {
            this.plugin.error("No registered bukkit enchant found for '%s'.".formatted(id));
            return false;
        }

        EnchantContext context = new EnchantContext(id, bukkitEnchant, catalog.getDefinition(), catalog
            .getDistribution(), catalog.isCurse());
        CustomEnchantment enchantment = catalog.createEnchantment(this.plugin, this, file, context);

        enchantment.load();
        EnchantRegistry.registerEnchant(enchantment);
        return true;
    }

    public void updateCache(LivingEntity entity, EquipmentSlot slot, @Nullable ItemStack itemStack) {
        EnchantRegistry.getHolders().forEach(holder -> {
            if (!holder.isCacheable()) return;

            if (itemStack == null || itemStack.getType().isAir() || !EnchantsUtils.hasEnchantsAndNotABook(
                itemStack) || !EnchantsUtils.isValidSlotForEnchantEffects(itemStack, slot)) {
                holder.removeCache(entity, slot);
                return;
            }

            Map<CustomEnchantment, Integer> allEnchants = EnchantsUtils.getCustomEnchantments(itemStack);
            holder.updateCache(entity, slot, itemStack, allEnchants);
        });
    }

    public void clearCache(LivingEntity entity) {
        this.clearCache(entity.getUniqueId());
    }

    private void clearCache(UUID entityId) {
        EnchantRegistry.getHolders().forEach(holder -> {
            if (!holder.isCacheable()) return;

            holder.clearCache(entityId);
        });
    }

    public void reCache(LivingEntity entity) {
        this.clearCache(entity);

        EntityUtil.getEquippedItems(entity).forEach((slot, itemStack) -> {
            this.updateCache(entity, slot, itemStack);
        });
    }


    public EnchantSettings getSettings() {
        return this.settings;
    }

    public void openEnchantsMenu(Player player) {
        this.enchantsMenu.open(player);
    }

    public void addArrowEffect(AbstractArrow arrow, UniParticle particle) {
        if (!this.active.get()) return;

        UUID entityId = arrow.getUniqueId();
        ArrowEffects current = this.arrowEffects.get(entityId);
        if (current != null) {
            current.particles().add(particle);
            return;
        }

        Set<UniParticle> particles = ConcurrentHashMap.newKeySet();
        particles.add(particle);
        AtomicReference<ArrowEffects> stateReference = new AtomicReference<>();
        SchedulerTask task = this.plugin.schedulerUtil().runAtEntityTimer(arrow,
            () -> this.tickArrowEffects(entityId, arrow, particles),
            () -> {
                ArrowEffects state = stateReference.get();
                if (state != null) this.arrowEffects.remove(entityId, state);
            },
            1L,
            this.settings.getArrowEffectsTickInterval());
        if (task.isCancelled()) return;

        ArrowEffects created = new ArrowEffects(particles, task);
        stateReference.set(created);
        ArrowEffects existing = this.arrowEffects.putIfAbsent(entityId, created);
        if (existing != null) {
            task.cancel();
            existing.particles().add(particle);
        }
        else if (task.isCancelled() || !this.active.get()) {
            this.arrowEffects.remove(entityId, created);
            task.cancel();
        }
    }

    public void removeArrowEffects(AbstractArrow arrow) {
        this.stopArrowEffects(arrow.getUniqueId());
    }

    private void tickArrowEffects(UUID entityId, AbstractArrow arrow, Set<UniParticle> particles) {
        if (!this.active.get() || !arrow.isValid() || arrow.isDead()) {
            this.stopArrowEffects(entityId);
            return;
        }

        Location location = arrow.getLocation();
        particles.forEach(particle -> particle.play(location, 0F, 0F, 10));
    }

    private void stopArrowEffects(UUID entityId) {
        ArrowEffects effects = this.arrowEffects.remove(entityId);
        if (effects != null) effects.task().cancel();
    }

    public void startPassiveTask(LivingEntity entity) {
        if (!this.active.get()) return;
        if (EnchantRegistry.PASSIVE.isEmpty()) return;
        if (!(entity instanceof Player) && !this.settings.isPassiveEnchantsAllowedForMobs()) return;
        if (!entity.isValid() || entity.isDead()) return;

        UUID entityId = entity.getUniqueId();
        if (this.passiveTasks.containsKey(entityId)) return;

        AtomicReference<SchedulerTask> taskReference = new AtomicReference<>();
        SchedulerTask task = this.plugin.schedulerUtil().runAtEntityTimer(entity,
            () -> this.tickPassiveEnchants(entityId, entity),
            () -> {
                SchedulerTask scheduled = taskReference.get();
                if (scheduled != null) this.passiveTasks.remove(entityId, scheduled);
                this.clearCache(entityId);
            },
            1L,
            this.settings.getPassiveEnchantsTickInterval());
        if (task.isCancelled()) return;

        taskReference.set(task);
        SchedulerTask existing = this.passiveTasks.putIfAbsent(entityId, task);
        if (existing != null) task.cancel();
        else if (task.isCancelled() || !this.active.get()) {
            this.passiveTasks.remove(entityId, task);
            task.cancel();
        }
    }

    public void stopPassiveTask(LivingEntity entity) {
        UUID entityId = entity.getUniqueId();
        SchedulerTask task = this.passiveTasks.remove(entityId);
        if (task != null) task.cancel();
        this.clearCache(entityId);
    }

    private void tickPassiveEnchants(UUID entityId, LivingEntity entity) {
        if (!this.active.get() || !entity.isValid() || entity.isDead()) {
            SchedulerTask task = this.passiveTasks.remove(entityId);
            if (task != null) task.cancel();
            this.clearCache(entityId);
            return;
        }

        this.handleInSlots(entity, EntityUtil.EQUIPMENT_SLOTS, EnchantRegistry.PASSIVE, (item, enchant,
                                                                                         level) -> enchant.onTrigger(
            entity, item, level));
    }

    public void addTickedBlock(Block block, Material origin, Material transform, int lifeTime) {
        if (!this.active.get()) return;

        Location location = block.getLocation();
        BlockKey key = BlockKey.from(location);
        this.tickedBlockJournal.put(key, origin);
        this.requestJournalSave();

        TickedBlock tickedBlock = new TickedBlock(this.plugin.schedulerUtil(), location, origin, lifeTime);
        SchedulerTask task = this.plugin.schedulerUtil().runAtRegionTimer(location, () -> this.tickBlock(key), 1L, 1L);
        if (task.isCancelled()) {
            this.tickedBlockJournal.remove(key);
            this.requestJournalSave();
            return;
        }

        ActiveBlock created = new ActiveBlock(tickedBlock, task);
        ActiveBlock previous = this.tickedBlocks.put(key, created);
        if (previous != null) previous.task().cancel();
        if (!this.active.get()) {
            this.tickedBlocks.remove(key, created);
            task.cancel();
            this.tickedBlockJournal.remove(key);
            this.requestJournalSave();
            return;
        }

        block.setType(transform);
    }

    public boolean removeTickedBlock(Block block) {
        return this.removeTickedBlock(block.getLocation());
    }

    public boolean removeTickedBlock(Location location) {
        BlockKey key = BlockKey.from(location);
        ActiveBlock activeBlock = this.tickedBlocks.remove(key);
        if (activeBlock == null) return false;

        activeBlock.task().cancel();
        activeBlock.block().sendDamageInfo(0F);
        activeBlock.block().restore();
        this.tickedBlockJournal.remove(key);
        this.requestJournalSave();
        return true;
    }

    private void tickBlock(BlockKey key) {
        ActiveBlock activeBlock = this.tickedBlocks.get(key);
        if (activeBlock == null) return;
        if (!this.active.get()) {
            activeBlock.task().cancel();
            return;
        }

        activeBlock.block().tick();
        if (!activeBlock.block().isDead()) return;
        if (!this.tickedBlocks.remove(key, activeBlock)) return;

        activeBlock.task().cancel();
        this.tickedBlockJournal.remove(key);
        this.requestJournalSave();
    }

    private void startOnlinePlayerPassiveTasks() {
        this.plugin.schedulerUtil().runGlobal(() -> {
            if (!this.active.get()) return;

            this.plugin.getServer().getOnlinePlayers().forEach(player -> this.plugin.schedulerUtil().runAtEntity(
                player, () -> this.startPassiveTask(player)));
        });
    }

    private void loadTickedBlockJournal() {
        this.plugin.schedulerUtil().runAsync(() -> {
            try {
                this.tickedBlockJournal.load();
            }
            catch (Exception exception) {
                this.plugin.warn("Could not load the ticked block recovery journal.", exception);
                return;
            }

            this.journalLoaded.set(true);
            if (this.journalDirty.get()) this.requestJournalSave();

            Map<BlockKey, Material> snapshot = this.tickedBlockJournal.snapshot();
            if (snapshot.isEmpty()) return;

            this.plugin.schedulerUtil().runGlobal(() -> this.restoreJournalEntries(snapshot));
        });
    }

    private void restoreJournalEntries(Map<BlockKey, Material> entries) {
        if (!this.active.get()) return;

        entries.forEach((key, material) -> {
            World world = this.plugin.getServer().getWorld(key.worldId());
            if (world == null) {
                this.plugin.warn("Could not restore a ticked block because world '%s' is not loaded. The recovery entry was retained."
                    .formatted(key.worldId()));
                return;
            }

            Location location = new Location(world, key.x(), key.y(), key.z());
            this.plugin.schedulerUtil().runAtRegion(location, () -> {
                if (!this.active.get() || this.tickedBlocks.containsKey(key)) return;

                location.getBlock().setType(material);
                this.tickedBlockJournal.remove(key);
                this.requestJournalSave();
            });
        });
    }

    private void requestJournalSave() {
        this.journalDirty.set(true);
        if (!this.active.get()) return;
        if (!this.journalLoaded.get()) return;
        if (!this.journalSaveRunning.compareAndSet(false, true)) return;

        SchedulerTask task = this.plugin.schedulerUtil().runAsync(this::saveJournal);
        if (task.isCancelled()) this.journalSaveRunning.set(false);
    }

    private void saveJournal() {
        try {
            do {
                this.journalDirty.set(false);
                try {
                    this.tickedBlockJournal.save();
                }
                catch (Exception exception) {
                    this.plugin.warn("Could not save the ticked block recovery journal.", exception);
                }
            }
            while (this.journalDirty.get());
        }
        finally {
            this.journalSaveRunning.set(false);
            if (this.active.get() && this.journalDirty.get()) this.requestJournalSave();
        }
    }

    private void saveJournalOnShutdown() {
        this.journalDirty.set(false);
        try {
            if (!this.journalLoaded.get()) {
                this.tickedBlockJournal.load();
                this.journalLoaded.set(true);
            }
            this.tickedBlockJournal.save();
        }
        catch (Exception exception) {
            this.plugin.warn("Could not save the ticked block recovery journal during shutdown.", exception);
        }
    }

    public void setBlockEnchant(ItemStack itemStack, BlockEnchant enchant) {
        PDCUtil.set(itemStack, this.blockEnchantKey, enchant.getId());
    }

    @Nullable
    public BlockEnchant getBlockEnchant(ItemStack itemStack) {
        String enchantId = PDCUtil.getString(itemStack, this.blockEnchantKey).orElse(null);
        if (enchantId == null) return null;

        return EnchantRegistry.BLOCK.getEnchant(enchantId);
    }

    public void setSpawnReason(Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        PDCUtil.set(entity, this.entitySpawnKey, reason.name());
    }

    public CreatureSpawnEvent.@Nullable SpawnReason getSpawnReason(Entity entity) {
        String name = PDCUtil.getString(entity, this.entitySpawnKey).orElse(null);
        return name == null ? null : Enums.get(name, CreatureSpawnEvent.SpawnReason.class);
    }

    public boolean createExplosion(LivingEntity entity, Location location, float power, boolean fire, boolean destroy,
                                   Consumer<Explosion> consumer) {
        if (!this.active.get()) return false;

        Explosion explosion = new Explosion();
        consumer.accept(explosion);

        this.explosions.put(entity.getUniqueId(), explosion);

        return entity.getWorld().createExplosion(location, power, fire, destroy, entity);
    }

    public void handleEnchantExplosion(EntityExplodeEvent event, LivingEntity entity) {
        UUID entityId = entity.getUniqueId();
        Explosion explosion = this.explosions.get(entityId);
        if (explosion == null) return;

        explosion.handleExplosion(event);

        this.plugin.schedulerUtil().runGlobalDelayed(() -> this.explosions.remove(entityId), 1L);
    }

    public void handleEnchantExplosionDamage(EntityDamageByEntityEvent event, LivingEntity entity) {
        Explosion explosion = this.explosions.get(entity.getUniqueId());
        if (explosion == null) return;

        explosion.handleDamage(event);
    }

    public <T extends CustomEnchantment> void handleInventoryEnchants(Player player, EnchantHolder<T> holder,
                                                                      EnchantUsage<T> usage) {
        this.handleFully(player, EnchantsUtils.getAll(player, holder), holder::getPriority, usage);
    }

    public <T extends CustomEnchantment> void handleItemEnchants(LivingEntity entity,
                                                                 ItemStack itemStack,
                                                                 EnchantHolder<T> holder,
                                                                 EnchantUsage<T> usage) {
        Map<ItemStack, Map<T, Integer>> enchants = new HashMap<>();
        enchants.put(itemStack, EnchantsUtils.getCustomEnchantments(itemStack, holder));

        this.handleFully(entity, enchants, holder::getPriority, usage);
    }

    public <P extends AbstractArrow, T extends ProjectileEnchant<P>> void handleArrowEnchants(P projectile,
                                                                                              EnchantHolder<T> holder,
                                                                                              EnchantUsage<T> usage) {
        ItemStack bow = projectile.getWeapon();
        if (bow == null || !EnchantsUtils.hasEnchantsAndNotABook(bow)) return;

        Map<ItemStack, Map<T, Integer>> enchants = new HashMap<>();
        enchants.put(bow, EnchantsUtils.getArrowEnchants(projectile, holder));

        this.handleDirect(enchants, holder::getPriority, usage);
    }

    public <T extends CustomEnchantment> void handleInSlot(LivingEntity entity,
                                                           EquipmentSlot slot,
                                                           EnchantHolder<T> holder,
                                                           EnchantUsage<T> usage) {
        this.handleInSlots(entity, new EquipmentSlot[]{slot}, holder, usage);
    }

    public <T extends CustomEnchantment> void handleInSlots(LivingEntity entity,
                                                            EquipmentSlot[] slots,
                                                            EnchantHolder<T> holder,
                                                            EnchantUsage<T> usage) {

        Map<ItemStack, Map<T, Integer>> enchantMap = new HashMap<>();
        boolean noCache = entity.getType() != EntityType.PLAYER || !holder.isCacheable() || Version.isSpigot();

        for (EquipmentSlot slot : slots) {
            if (noCache || slot == EquipmentSlot.HAND) { // Main hand is not cached
                ItemStack itemStack = EntityUtil.getItemInSlot(entity, slot);
                if (itemStack == null || itemStack.getType().isAir()) continue; // Ignore empty slots.
                if (!EnchantsUtils.hasEnchantsAndNotABook(itemStack)) continue; // Ignore books and items without enchants.
                if (!EnchantsUtils.isValidSlotForEnchantEffects(itemStack, slot)) continue; // Ignore armor items when holding in hands.

                enchantMap.put(itemStack, EnchantsUtils.getCustomEnchantments(itemStack, holder));
            }
            else {
                EnchantedItem<T> enchantedItem = holder.getCached(entity, slot);
                if (enchantedItem == null) continue;

                enchantMap.put(enchantedItem.getItemStack(), enchantedItem.getEnchants());
            }
        }

        this.handleFully(entity, enchantMap, holder::getPriority, usage);
    }

    public <T extends CustomEnchantment> void handleFully(LivingEntity entity,
                                                          Map<ItemStack, Map<T, Integer>> enchantMap,
                                                          Function<T, EnchantPriority> priority,
                                                          EnchantUsage<T> usage) {

        this.handleDirect(enchantMap, priority, (itemStack, enchant, level) -> {
            if (this.settings.isEnchantDisabledInWorld(entity.getWorld(), enchant)) return false;
            if (enchant.isOutOfCharges(itemStack)) return false;
            if (enchant.hasComponent(EnchantComponent.PERIODIC) && !enchant.isTriggerTime(entity)) return false;
            if (enchant.hasComponent(EnchantComponent.PROBABILITY) && !enchant.testTriggerChance(level)) return false;
            if (!usage.useEnchant(itemStack, enchant, level)) return false;

            enchant.consumeCharges(itemStack, level); // TODO Re-add equipment for mobs to apply changes
            return true;
        });
    }

    public <T extends CustomEnchantment> void handleDirect(Map<ItemStack, Map<T, Integer>> enchantMap,
                                                           Function<T, EnchantPriority> priority,
                                                           EnchantUsage<T> usage) {
        enchantMap.forEach((itemStack, enchants) -> {
            enchants.entrySet().stream().sorted(Comparator.comparingInt(entry -> priority.apply(entry.getKey())
                .ordinal())).forEach(entry -> {
                    T enchant = entry.getKey();
                    int level = entry.getValue();

                    usage.useEnchant(itemStack, enchant, level);
            });
        });
    }

    private record ArrowEffects(Set<UniParticle> particles, SchedulerTask task) {
    }

    private record ActiveBlock(TickedBlock block, SchedulerTask task) {
    }
}

