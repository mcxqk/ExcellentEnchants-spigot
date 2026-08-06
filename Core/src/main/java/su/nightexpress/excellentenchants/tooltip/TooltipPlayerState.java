package su.nightexpress.excellentenchants.tooltip;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class TooltipPlayerState {

    private final Set<UUID> updateStopList = ConcurrentHashMap.newKeySet();
    private final Set<UUID> creativePlayers = ConcurrentHashMap.newKeySet();

    public void pause(UUID playerId) {
        this.updateStopList.add(playerId);
    }

    public void resume(UUID playerId) {
        this.updateStopList.remove(playerId);
    }

    public void setCreative(UUID playerId, boolean creative) {
        if (creative) {
            this.creativePlayers.add(playerId);
        }
        else {
            this.creativePlayers.remove(playerId);
        }
    }

    public boolean isReady(UUID playerId) {
        return !this.updateStopList.contains(playerId) && !this.creativePlayers.contains(playerId);
    }

    public void clear(UUID playerId) {
        this.updateStopList.remove(playerId);
        this.creativePlayers.remove(playerId);
    }

    public void clear() {
        this.updateStopList.clear();
        this.creativePlayers.clear();
    }
}
