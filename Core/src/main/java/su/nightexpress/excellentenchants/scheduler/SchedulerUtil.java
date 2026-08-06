package su.nightexpress.excellentenchants.scheduler;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import su.nightexpress.excellentenchants.EnchantsPlugin;
import su.nightexpress.nightcore.bridge.scheduler.AdaptedScheduler;
import su.nightexpress.nightcore.bridge.scheduler.AdaptedTask;
import su.nightexpress.nightcore.util.Version;

public final class SchedulerUtil {

    private static final boolean FOLIA = Version.isFolia();

    private final Plugin plugin;
    private final boolean folia;
    private final SchedulerOwnership ownership;
    private final RegionScheduler regionScheduler;
    private final GlobalRegionScheduler globalScheduler;
    private final AsyncScheduler asyncScheduler;
    private final AdaptedScheduler fallback;
    private final Set<TrackedTask> trackedTasks;
    private final AtomicBoolean shutdown;

    public SchedulerUtil(EnchantsPlugin plugin) {
        this(plugin, FOLIA, new BukkitOwnership(FOLIA),
            FOLIA ? plugin.getServer().getRegionScheduler() : null,
            FOLIA ? plugin.getServer().getGlobalRegionScheduler() : null,
            FOLIA ? plugin.getServer().getAsyncScheduler() : null,
            plugin.scheduler());
    }

    SchedulerUtil(Plugin plugin,
                  boolean folia,
                  SchedulerOwnership ownership,
                  RegionScheduler regionScheduler,
                  GlobalRegionScheduler globalScheduler,
                  AsyncScheduler asyncScheduler,
                  AdaptedScheduler fallback) {
        this.plugin = plugin;
        this.folia = folia;
        this.ownership = ownership;
        this.regionScheduler = regionScheduler;
        this.globalScheduler = globalScheduler;
        this.asyncScheduler = asyncScheduler;
        this.fallback = fallback;
        this.trackedTasks = ConcurrentHashMap.newKeySet();
        this.shutdown = new AtomicBoolean();
    }

    public boolean isFolia() {
        return this.folia;
    }

    public boolean isOwned(Entity entity) {
        return this.ownership.isOwned(entity);
    }

    public boolean isOwned(Location location) {
        return this.ownership.isOwned(location);
    }

    public SchedulerTask runAtEntity(Entity entity, Runnable task) {
        if (this.isOwned(entity)) {
            task.run();
            return CompletedTask.INSTANCE;
        }

        TrackedTask tracked = this.beginTask();
        if (tracked.isCancelled()) return tracked;

        Runnable retired = tracked::finish;
        if (this.folia) {
            ScheduledTask scheduled = entity.getScheduler().run(this.plugin, this.once(tracked, task), retired);
            this.bind(tracked, scheduled);
        }
        else {
            AdaptedTask scheduled = this.fallback.runTask(entity, this.onceRunnable(tracked, task));
            this.bind(tracked, scheduled);
        }
        return tracked;
    }

    public SchedulerTask runAtEntityDelayed(Entity entity, Runnable task, long ticks) {
        long delay = Math.max(1L, ticks);
        TrackedTask tracked = this.beginTask();
        if (tracked.isCancelled()) return tracked;

        if (this.folia) {
            ScheduledTask scheduled = entity.getScheduler().runDelayed(this.plugin, this.once(tracked, task),
                tracked::finish, delay);
            this.bind(tracked, scheduled);
        }
        else {
            AdaptedTask scheduled = this.fallback.runTaskLater(this.onceRunnable(tracked, task), delay);
            this.bind(tracked, scheduled);
        }
        return tracked;
    }

    public SchedulerTask runAtEntityTimer(Entity entity,
                                          Runnable task,
                                          Runnable retired,
                                          long delay,
                                          long period) {
        long initialDelay = Math.max(1L, delay);
        long fixedPeriod = Math.max(1L, period);
        TrackedTask tracked = this.beginTask();
        if (tracked.isCancelled()) return tracked;

        if (this.folia) {
            Runnable retiredTask = () -> {
                try {
                    retired.run();
                }
                finally {
                    tracked.finish();
                }
            };
            ScheduledTask scheduled = entity.getScheduler().runAtFixedRate(this.plugin, ignored -> task.run(),
                retiredTask, initialDelay, fixedPeriod);
            this.bind(tracked, scheduled);
        }
        else {
            AdaptedTask scheduled = this.fallback.runTaskTimer(task, initialDelay, fixedPeriod);
            this.bind(tracked, scheduled);
        }
        return tracked;
    }

    public SchedulerTask runAtRegion(Location location, Runnable task) {
        if (this.isOwned(location)) {
            task.run();
            return CompletedTask.INSTANCE;
        }

        TrackedTask tracked = this.beginTask();
        if (tracked.isCancelled()) return tracked;

        if (this.folia) {
            ScheduledTask scheduled = this.regionScheduler.run(this.plugin, location, this.once(tracked, task));
            this.bind(tracked, scheduled);
        }
        else {
            AdaptedTask scheduled = this.fallback.runTask(location, this.onceRunnable(tracked, task));
            this.bind(tracked, scheduled);
        }
        return tracked;
    }

    public SchedulerTask runAtRegionDelayed(Location location, Runnable task, long ticks) {
        long delay = Math.max(1L, ticks);
        TrackedTask tracked = this.beginTask();
        if (tracked.isCancelled()) return tracked;

        if (this.folia) {
            ScheduledTask scheduled = this.regionScheduler.runDelayed(this.plugin, location, this.once(tracked, task),
                delay);
            this.bind(tracked, scheduled);
        }
        else {
            AdaptedTask scheduled = this.fallback.runTaskLater(this.onceRunnable(tracked, task), delay);
            this.bind(tracked, scheduled);
        }
        return tracked;
    }

    public SchedulerTask runAtRegionTimer(Location location, Runnable task, long delay, long period) {
        long initialDelay = Math.max(1L, delay);
        long fixedPeriod = Math.max(1L, period);
        TrackedTask tracked = this.beginTask();
        if (tracked.isCancelled()) return tracked;

        if (this.folia) {
            ScheduledTask scheduled = this.regionScheduler.runAtFixedRate(this.plugin, location, ignored -> task.run(),
                initialDelay, fixedPeriod);
            this.bind(tracked, scheduled);
        }
        else {
            AdaptedTask scheduled = this.fallback.runTaskTimer(task, initialDelay, fixedPeriod);
            this.bind(tracked, scheduled);
        }
        return tracked;
    }

    public SchedulerTask runGlobal(Runnable task) {
        TrackedTask tracked = this.beginTask();
        if (tracked.isCancelled()) return tracked;

        if (this.folia) {
            ScheduledTask scheduled = this.globalScheduler.run(this.plugin, this.once(tracked, task));
            this.bind(tracked, scheduled);
        }
        else {
            AdaptedTask scheduled = this.fallback.runTask(this.onceRunnable(tracked, task));
            this.bind(tracked, scheduled);
        }
        return tracked;
    }

    public SchedulerTask runGlobalDelayed(Runnable task, long ticks) {
        if (ticks <= 0L) return this.runGlobal(task);

        TrackedTask tracked = this.beginTask();
        if (tracked.isCancelled()) return tracked;

        if (this.folia) {
            ScheduledTask scheduled = this.globalScheduler.runDelayed(this.plugin, this.once(tracked, task), ticks);
            this.bind(tracked, scheduled);
        }
        else {
            AdaptedTask scheduled = this.fallback.runTaskLater(this.onceRunnable(tracked, task), ticks);
            this.bind(tracked, scheduled);
        }
        return tracked;
    }

    public SchedulerTask runAsync(Runnable task) {
        TrackedTask tracked = this.beginTask();
        if (tracked.isCancelled()) return tracked;

        if (this.folia) {
            ScheduledTask scheduled = this.asyncScheduler.runNow(this.plugin, this.once(tracked, task));
            this.bind(tracked, scheduled);
        }
        else {
            AdaptedTask scheduled = this.fallback.runTaskAsync(this.onceRunnable(tracked, task));
            this.bind(tracked, scheduled);
        }
        return tracked;
    }

    public SchedulerTask runAtSender(CommandSender sender, Runnable task) {
        if (sender instanceof Entity entity) return this.runAtEntity(entity, task);
        if (sender instanceof BlockCommandSender blockSender) {
            return this.runAtRegion(blockSender.getBlock().getLocation(), task);
        }
        return this.runGlobal(task);
    }

    public CompletableFuture<Boolean> teleport(Entity entity, Location location) {
        return entity.teleportAsync(location);
    }

    public void shutdown() {
        if (!this.shutdown.compareAndSet(false, true)) return;

        this.trackedTasks.forEach(TrackedTask::cancel);
        this.trackedTasks.clear();
    }

    private Consumer<ScheduledTask> once(TrackedTask tracked, Runnable task) {
        return ignored -> {
            try {
                task.run();
            }
            finally {
                tracked.finish();
            }
        };
    }

    private Runnable onceRunnable(TrackedTask tracked, Runnable task) {
        return () -> {
            try {
                task.run();
            }
            finally {
                tracked.finish();
            }
        };
    }

    private TrackedTask beginTask() {
        TrackedTask tracked = new TrackedTask(this.trackedTasks);
        if (this.shutdown.get()) {
            tracked.cancel();
            return tracked;
        }

        this.trackedTasks.add(tracked);
        if (this.shutdown.get()) tracked.cancel();
        return tracked;
    }

    private void bind(TrackedTask tracked, ScheduledTask scheduled) {
        if (scheduled == null) {
            tracked.reject();
            return;
        }
        tracked.bind(new FoliaTask(scheduled));
    }

    private void bind(TrackedTask tracked, AdaptedTask scheduled) {
        if (scheduled == null) {
            tracked.reject();
            return;
        }
        tracked.bind(new AdaptedSchedulerTask(scheduled));
    }

    private record BukkitOwnership(boolean folia) implements SchedulerOwnership {

        @Override
        public boolean isOwned(Entity entity) {
            return this.folia ? Bukkit.isOwnedByCurrentRegion(entity) : Bukkit.isPrimaryThread();
        }

        @Override
        public boolean isOwned(Location location) {
            return this.folia ? Bukkit.isOwnedByCurrentRegion(location) : Bukkit.isPrimaryThread();
        }
    }

    private record FoliaTask(ScheduledTask backend) implements SchedulerTask {

        @Override
        public void cancel() {
            this.backend.cancel();
        }

        @Override
        public boolean isCancelled() {
            return this.backend.isCancelled();
        }
    }

    private record AdaptedSchedulerTask(AdaptedTask backend) implements SchedulerTask {

        @Override
        public void cancel() {
            this.backend.cancel();
        }

        @Override
        public boolean isCancelled() {
            return this.backend.isCancelled();
        }
    }

    private enum CompletedTask implements SchedulerTask {
        INSTANCE;

        @Override
        public void cancel() {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }

    private static final class TrackedTask implements SchedulerTask {

        private final Set<TrackedTask> owner;
        private final AtomicReference<SchedulerTask> backend;
        private final AtomicBoolean terminal;
        private final AtomicBoolean cancelled;

        private TrackedTask(Set<TrackedTask> owner) {
            this.owner = owner;
            this.backend = new AtomicReference<>();
            this.terminal = new AtomicBoolean();
            this.cancelled = new AtomicBoolean();
        }

        private void bind(SchedulerTask task) {
            if (!this.backend.compareAndSet(null, task)) throw new IllegalStateException("Task is already bound");
            if (this.cancelled.get()) task.cancel();
        }

        private void finish() {
            if (!this.terminal.compareAndSet(false, true)) return;
            this.owner.remove(this);
        }

        private void reject() {
            this.cancelled.set(true);
            this.finish();
        }

        @Override
        public void cancel() {
            if (!this.terminal.compareAndSet(false, true)) return;

            this.cancelled.set(true);
            SchedulerTask task = this.backend.get();
            if (task != null) task.cancel();
            this.owner.remove(this);
        }

        @Override
        public boolean isCancelled() {
            if (this.cancelled.get()) return true;

            SchedulerTask task = this.backend.get();
            return task != null && task.isCancelled();
        }
    }
}
