package su.nightexpress.excellentenchants.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

public interface SchedulerOwnership {

    boolean isOwned(Entity entity);

    boolean isOwned(Location location);
}
