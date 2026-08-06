package su.nightexpress.excellentenchants.tooltip;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class TooltipPlayerStateTest {

    private final UUID playerId = UUID.randomUUID();
    private final TooltipPlayerState state = new TooltipPlayerState();

    @Test
    void blocksCreativeAndPausedPlayersWithoutPlayerObject() {
        this.state.setCreative(this.playerId, true);
        assertFalse(this.state.isReady(this.playerId));

        this.state.setCreative(this.playerId, false);
        this.state.pause(this.playerId);
        assertFalse(this.state.isReady(this.playerId));

        this.state.resume(this.playerId);
        assertTrue(this.state.isReady(this.playerId));
    }

    @Test
    void clearRemovesAllPlayerState() {
        this.state.pause(this.playerId);
        this.state.setCreative(this.playerId, true);

        this.state.clear(this.playerId);

        assertTrue(this.state.isReady(this.playerId));
    }
}
