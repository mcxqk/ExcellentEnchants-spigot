package su.nightexpress.excellentenchants.manager.block;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.jspecify.annotations.NullMarked;

import su.nightexpress.excellentenchants.scheduler.SchedulerUtil;
import su.nightexpress.nightcore.util.EntityUtil;
import su.nightexpress.nightcore.util.LocationUtil;
import su.nightexpress.nightcore.util.TimeUtil;
import su.nightexpress.nightcore.util.wrapper.UniParticle;

import java.util.Objects;

@NullMarked
public class TickedBlock {

    private final SchedulerUtil scheduler;
    private final Location location;
    private final Material originalType;
    private final long     lifeTime;
    private final int      sourceId;

    private long livedTicks;

    public TickedBlock(SchedulerUtil scheduler, Location location, Material originalType, int seconds) {
        World world = Objects.requireNonNull(location.getWorld());
        this.scheduler = scheduler;
        this.location = location.clone();
        this.originalType = originalType;
        this.lifeTime = TimeUtil.secondsToTicks(seconds);
        this.sourceId = EntityUtil.nextEntityId(world);

        this.livedTicks = 0;
    }

    public void restore() {
        if (!this.location.isWorldLoaded()) return;

        this.location.getBlock().setType(this.originalType);
    }

    public void sendDamageInfo(float progress) {
        if (!this.location.isWorldLoaded()) return;

        Location snapshot = this.location.clone();
        this.location.getWorld().getNearbyPlayers(this.location, 64D).forEach(player -> this.scheduler.runAtEntity(
            player, () -> player.sendBlockDamage(snapshot, progress, this.sourceId)));
    }

    public void tick() {
        if (!this.location.isWorldLoaded()) return;

        this.livedTicks++;

        if (this.isDead()) {
            Location centered = LocationUtil.setCenter3D(this.location);
            UniParticle.blockCrack(this.location.getBlock().getType()).play(centered, 0.5, 0.7, 0.5, 0.03, 30);
            this.sendDamageInfo(0F);
            this.restore();
            return;
        }

        this.sendDamageInfo(this.getProgress());
    }

    public float getProgress() {
        return (float) this.livedTicks / (float) this.lifeTime;
    }

    public boolean isDead() {
        return this.livedTicks >= this.lifeTime;
    }

    public boolean isAlive() {
        return !this.isDead();
    }

    public int getSourceId() {
        return this.sourceId;
    }

    public Location getLocation() {
        return this.location.clone();
    }
}
