package su.nightexpress.excellentenchants.manager.damage;

import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

@NullMarked
public class Explosion {

    @Nullable
    private Consumer<EntityExplodeEvent>        onExplode;
    @Nullable
    private Consumer<EntityDamageByEntityEvent> onDamage;

    public void handleExplosion(EntityExplodeEvent event) {
        if (this.onExplode != null) {
            this.onExplode.accept(event);
        }
    }

    public void handleDamage(EntityDamageByEntityEvent event) {
        if (this.onDamage != null) {
            this.onDamage.accept(event);
        }
    }

    public void setOnExplode(Consumer<EntityExplodeEvent> onExplode) {
        this.onExplode = onExplode;
    }

    public void setOnDamage(Consumer<EntityDamageByEntityEvent> onDamage) {
        this.onDamage = onDamage;
    }
}
