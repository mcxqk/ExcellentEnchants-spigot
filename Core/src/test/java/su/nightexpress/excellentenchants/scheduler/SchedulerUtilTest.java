package su.nightexpress.excellentenchants.scheduler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import su.nightexpress.nightcore.bridge.scheduler.AdaptedScheduler;
import su.nightexpress.nightcore.bridge.scheduler.AdaptedTask;

class SchedulerUtilTest {

    private Plugin plugin;
    private SchedulerOwnership ownership;
    private RegionScheduler regionScheduler;
    private GlobalRegionScheduler globalScheduler;
    private AsyncScheduler asyncScheduler;
    private AdaptedScheduler fallback;
    private Entity entity;
    private EntityScheduler entityScheduler;
    private Location location;
    private ScheduledTask scheduledTask;
    private AdaptedTask adaptedTask;
    private Runnable action;
    private SchedulerUtil scheduler;

    @BeforeEach
    void setUp() {
        this.plugin = mock(Plugin.class);
        this.ownership = mock(SchedulerOwnership.class);
        this.regionScheduler = mock(RegionScheduler.class);
        this.globalScheduler = mock(GlobalRegionScheduler.class);
        this.asyncScheduler = mock(AsyncScheduler.class);
        this.fallback = mock(AdaptedScheduler.class);
        this.entity = mock(Entity.class);
        this.entityScheduler = mock(EntityScheduler.class);
        this.location = mock(Location.class);
        this.scheduledTask = mock(ScheduledTask.class);
        this.adaptedTask = mock(AdaptedTask.class);
        this.action = mock(Runnable.class);

        when(this.plugin.isEnabled()).thenReturn(true);
        when(this.entity.getScheduler()).thenReturn(this.entityScheduler);
        when(this.entityScheduler.run(eq(this.plugin), any(), any(Runnable.class))).thenReturn(this.scheduledTask);
        when(this.entityScheduler.runDelayed(eq(this.plugin), any(), any(Runnable.class), anyLong())).thenReturn(
            this.scheduledTask);
        when(this.entityScheduler.runAtFixedRate(eq(this.plugin), any(), any(), anyLong(), anyLong()))
            .thenReturn(this.scheduledTask);
        when(this.regionScheduler.run(eq(this.plugin), eq(this.location), any())).thenReturn(this.scheduledTask);
        when(this.regionScheduler.runDelayed(eq(this.plugin), eq(this.location), any(), anyLong())).thenReturn(
            this.scheduledTask);
        when(this.regionScheduler.runAtFixedRate(eq(this.plugin), eq(this.location), any(), anyLong(), anyLong()))
            .thenReturn(this.scheduledTask);
        when(this.globalScheduler.run(eq(this.plugin), any())).thenReturn(this.scheduledTask);
        when(this.globalScheduler.runDelayed(eq(this.plugin), any(), anyLong())).thenReturn(this.scheduledTask);
        when(this.asyncScheduler.runNow(eq(this.plugin), any())).thenReturn(this.scheduledTask);
        when(this.fallback.runTask(any(Runnable.class))).thenReturn(this.adaptedTask);
        when(this.fallback.runTask(any(Entity.class), any(Runnable.class))).thenReturn(this.adaptedTask);
        when(this.fallback.runTask(any(Location.class), any(Runnable.class))).thenReturn(this.adaptedTask);
        when(this.fallback.runTaskAsync(any(Runnable.class))).thenReturn(this.adaptedTask);
        when(this.fallback.runTaskLater(any(Runnable.class), anyLong())).thenReturn(this.adaptedTask);
        when(this.fallback.runTaskTimer(any(Runnable.class), anyLong(), anyLong())).thenReturn(
            this.adaptedTask);

        this.scheduler = new SchedulerUtil(this.plugin, true, this.ownership, this.regionScheduler,
            this.globalScheduler, this.asyncScheduler, this.fallback);
    }

    @Test
    void reportsCachedPlatformMode() {
        assertTrue(this.scheduler.isFolia());
    }

    @Test
    void clampsEntityAndRegionDelayToOneTick() {
        this.scheduler.runAtEntityDelayed(this.entity, this.action, 0L);
        this.scheduler.runAtRegionDelayed(this.location, this.action, -5L);

        verify(this.entityScheduler).runDelayed(eq(this.plugin), any(), any(Runnable.class), eq(1L));
        verify(this.regionScheduler).runDelayed(eq(this.plugin), eq(this.location), any(), eq(1L));
    }

    @Test
    void executesImmediatelyOnlyWhenCurrentThreadOwnsEntity() {
        when(this.ownership.isOwned(this.entity)).thenReturn(true);

        SchedulerTask task = this.scheduler.runAtEntity(this.entity, this.action);

        verify(this.action).run();
        verifyNoInteractions(this.entityScheduler);
        assertFalse(task.isCancelled());
    }

    @Test
    void schedulesNextTickWhenEntityIsNotOwned() {
        when(this.ownership.isOwned(this.entity)).thenReturn(false);

        this.scheduler.runAtEntity(this.entity, this.action);

        verify(this.entityScheduler).run(eq(this.plugin), any(), any(Runnable.class));
        verifyNoInteractions(this.action);
    }

    @Test
    void executesImmediatelyOnlyWhenCurrentThreadOwnsRegion() {
        when(this.ownership.isOwned(this.location)).thenReturn(true);

        this.scheduler.runAtRegion(this.location, this.action);

        verify(this.action).run();
        verifyNoInteractions(this.regionScheduler);
    }

    @Test
    void routesGlobalAndAsyncTasksToFoliaSchedulers() {
        this.scheduler.runGlobal(this.action);
        this.scheduler.runAsync(this.action);

        verify(this.globalScheduler).run(eq(this.plugin), any());
        verify(this.asyncScheduler).runNow(eq(this.plugin), any());
    }

    @Test
    void rejectsTaskSubmissionWhenPluginIsDisabled() {
        when(this.plugin.isEnabled()).thenReturn(false);

        SchedulerTask task = this.scheduler.runAsync(this.action);

        assertTrue(task.isCancelled());
        verifyNoInteractions(this.asyncScheduler);
        verifyNoInteractions(this.action);
    }

    @Test
    void delegatesTeleportToTeleportAsync() {
        CompletableFuture<Boolean> expected = CompletableFuture.completedFuture(true);
        when(this.entity.teleportAsync(this.location)).thenReturn(expected);

        assertSame(expected, this.scheduler.teleport(this.entity, this.location));
    }

    @Test
    void shutdownCancelsTrackedRepeatingTasks() {
        Runnable retired = mock(Runnable.class);
        SchedulerTask task = this.scheduler.runAtEntityTimer(this.entity, this.action, retired, 0L, 0L);

        this.scheduler.shutdown();

        verify(this.entityScheduler).runAtFixedRate(eq(this.plugin), any(), any(), eq(1L), eq(1L));
        verify(this.scheduledTask).cancel();
        assertTrue(task.isCancelled());
    }

    @Test
    void completedOneShotTaskIsNotCancelledDuringShutdown() {
        ArgumentCaptor<Consumer<ScheduledTask>> callback = ArgumentCaptor.forClass(Consumer.class);
        this.scheduler.runAtEntityDelayed(this.entity, this.action, 1L);
        verify(this.entityScheduler).runDelayed(eq(this.plugin), callback.capture(), any(Runnable.class), eq(1L));

        callback.getValue().accept(this.scheduledTask);
        this.scheduler.shutdown();

        verify(this.action).run();
        verify(this.scheduledTask, never()).cancel();
    }

    @Test
    void retiredEntityTaskRunsCleanupAndLeavesTracking() {
        Runnable retired = mock(Runnable.class);
        ArgumentCaptor<Runnable> retiredCallback = ArgumentCaptor.forClass(Runnable.class);
        this.scheduler.runAtEntityTimer(this.entity, this.action, retired, 1L, 20L);
        verify(this.entityScheduler).runAtFixedRate(eq(this.plugin), any(), retiredCallback.capture(), eq(1L), eq(20L));

        retiredCallback.getValue().run();
        this.scheduler.shutdown();

        verify(retired).run();
        verify(this.scheduledTask, never()).cancel();
    }

    @Test
    void rejectedEntityTaskIsReportedAsCancelled() {
        when(this.entityScheduler.runAtFixedRate(eq(this.plugin), any(), any(), anyLong(), anyLong())).thenReturn(null);

        SchedulerTask task = this.scheduler.runAtEntityTimer(this.entity, this.action, mock(Runnable.class), 1L, 20L);

        assertTrue(task.isCancelled());
    }

    @Test
    void paperFallbackUsesAdaptedSchedulerAndClampsDelay() {
        SchedulerUtil paper = new SchedulerUtil(this.plugin, false, this.ownership, this.regionScheduler,
            this.globalScheduler, this.asyncScheduler, this.fallback);

        SchedulerTask task = paper.runAtEntityDelayed(this.entity, this.action, 0L);

        verify(this.fallback).runTaskLater(any(Runnable.class), eq(1L));
        paper.shutdown();
        verify(this.adaptedTask).cancel();
        assertTrue(task.isCancelled());
    }

    @Test
    void routesEntityBlockAndConsoleSendersByOwnership() {
        Player player = mock(Player.class);
        BlockCommandSender blockSender = mock(BlockCommandSender.class);
        Block block = mock(Block.class);
        CommandSender console = mock(CommandSender.class);
        Location blockLocation = mock(Location.class);
        when(blockSender.getBlock()).thenReturn(block);
        when(block.getLocation()).thenReturn(blockLocation);
        when(this.ownership.isOwned(player)).thenReturn(true);
        when(this.ownership.isOwned(blockLocation)).thenReturn(true);

        this.scheduler.runAtSender(player, this.action);
        this.scheduler.runAtSender(blockSender, this.action);
        this.scheduler.runAtSender(console, this.action);

        verify(this.action, org.mockito.Mockito.times(2)).run();
        verify(this.globalScheduler).run(eq(this.plugin), any());
    }

    @Test
    void routesRemotePlayerSenderToEntityScheduler() {
        Player player = mock(Player.class);
        when(player.getScheduler()).thenReturn(this.entityScheduler);
        when(this.ownership.isOwned(player)).thenReturn(false);

        this.scheduler.runAtSender(player, this.action);

        verify(this.entityScheduler).run(eq(this.plugin), any(), any(Runnable.class));
    }
}
