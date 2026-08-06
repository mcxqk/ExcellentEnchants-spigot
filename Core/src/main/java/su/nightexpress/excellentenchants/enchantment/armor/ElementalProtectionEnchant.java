package su.nightexpress.excellentenchants.enchantment.armor;

import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

import su.nightexpress.excellentenchants.EnchantsPlaceholders;
import su.nightexpress.excellentenchants.EnchantsPlugin;
import su.nightexpress.excellentenchants.api.EnchantPriority;
import su.nightexpress.excellentenchants.api.Modifier;
import su.nightexpress.excellentenchants.api.damage.DamageBonus;
import su.nightexpress.excellentenchants.api.damage.DamageBonusType;
import su.nightexpress.excellentenchants.api.enchantment.type.ProtectionEnchant;
import su.nightexpress.excellentenchants.enchantment.EnchantContext;
import su.nightexpress.excellentenchants.enchantment.GameEnchantment;
import su.nightexpress.excellentenchants.manager.EnchantManager;
import su.nightexpress.nightcore.config.ConfigValue;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.util.NumberUtil;

import java.nio.file.Path;
import java.util.Set;

@NullMarked
public class ElementalProtectionEnchant extends GameEnchantment implements ProtectionEnchant {

    private static final Set<DamageType> DAMAGE_CAUSES = Set.of(
        DamageType.WITHER,
        DamageType.MAGIC,
        DamageType.FREEZE,
        DamageType.LIGHTNING_BOLT
    );

    private Modifier amount;
    private double   capacity;
    private boolean  multiplier;

    public ElementalProtectionEnchant(EnchantsPlugin plugin, EnchantManager manager, Path file,
                                      EnchantContext context) {
        super(plugin, manager, file, context);
    }

    @Override
    protected void loadAdditional(FileConfig config) {
        this.amount = Modifier.load(config, "Protection.Amount",
            Modifier.addictive(0).perLevel(5).capacity(25),
            "Protection amount given by enchantment."
        );

        this.capacity = ConfigValue.create("Protection.Capacity",
            80D,
            "Max. possible protection value from all armor pieces."
        ).read(config);

        this.multiplier = ConfigValue.create("Protection.Multiplier",
            true,
            "Controls if protection amount is in percent."
        ).read(config);

        this.addPlaceholder(EnchantsPlaceholders.GENERIC_AMOUNT, level -> NumberUtil.format(this.getProtectionAmount(
            level)));
        this.addPlaceholder(EnchantsPlaceholders.GENERIC_MAX, level -> NumberUtil.format(this.getCapacity()));
    }

    public double getProtectionAmount(int level) {
        return this.amount.getValue(level);
    }

    public double getCapacity() {
        return this.capacity;
    }

    public boolean isMultiplier() {
        return this.multiplier;
    }

    @Override

    public EnchantPriority getProtectionPriority() {
        return EnchantPriority.NORMAL;
    }

    @Override

    public DamageBonus getDamageBonus() {
        return new DamageBonus(this.multiplier ? DamageBonusType.MULTIPLIER : DamageBonusType.NORMAL);
    }

    @Override
    public boolean onProtection(EntityDamageEvent event, DamageBonus damageBonus, LivingEntity entity,
                                ItemStack itemStack, int level) {
        DamageSource source = event.getDamageSource();
        DamageType type = source.getDamageType();
        if (!DAMAGE_CAUSES.contains(type)) return false;

        double protectionAmount = this.getProtectionAmount(level);
        if (protectionAmount <= 0D) return false;

        damageBonus.addPenalty(protectionAmount, this.capacity);
        return true;
    }
}
