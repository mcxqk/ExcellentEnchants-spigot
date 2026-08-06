package su.nightexpress.excellentenchants;


import org.jspecify.annotations.Nullable;
import su.nightexpress.excellentenchants.command.BaseCommands;
import su.nightexpress.excellentenchants.config.Config;
import su.nightexpress.excellentenchants.config.Lang;
import su.nightexpress.excellentenchants.config.Perms;
import su.nightexpress.excellentenchants.placeholder.PlaceholderHook;
import su.nightexpress.excellentenchants.manager.EnchantManager;
import su.nightexpress.excellentenchants.scheduler.SchedulerUtil;
import su.nightexpress.excellentenchants.tooltip.TooltipManager;
import su.nightexpress.nightcore.NightPlugin;
import su.nightexpress.nightcore.commands.command.NightCommand;
import su.nightexpress.nightcore.config.PluginDetails;
import su.nightexpress.nightcore.util.Plugins;

public class EnchantsPlugin extends NightPlugin {

    private TooltipManager tooltipManager;
    private EnchantManager enchantManager;
    private SchedulerUtil schedulerUtil;

    @Override

    protected PluginDetails getDefaultDetails() {
        return PluginDetails.create("Enchants", new String[]{"eenchants", "excellentenchants"})
            .setConfigClass(Config.class)
            .setPermissionsClass(Perms.class);
    }

    @Override
    protected boolean disableCommandManager() {
        return true;
    }

    @Override
    protected void addRegistries() {
        super.addRegistries();
        this.registerLang(Lang.class);
    }

    @Override
    protected void onStartup() {
        super.onStartup();
        this.schedulerUtil = new SchedulerUtil(this);
        EnchantsAPI.load(this);
    }

    @Override
    public void enable() {
        Config settings = new Config();
        settings.load(this.config);

        if (settings.isEnchantTooltipEnabled()) {
            this.tooltipManager = new TooltipManager(this);
            this.tooltipManager.setup();
        }

        this.enchantManager = new EnchantManager(this);
        this.enchantManager.setup();

        this.loadHooks();
        this.loadCommands();
    }

    @Override
    public void disable() {
        if (this.enchantManager != null) {
            this.enchantManager.shutdown();
            this.enchantManager = null;
        }

        if (this.tooltipManager != null) {
            this.tooltipManager.shutdown();
            this.tooltipManager = null;
        }

        if (Plugins.hasPlaceholderAPI()) {
            PlaceholderHook.shutdown();
        }

        if (this.schedulerUtil != null) {
            this.schedulerUtil.shutdown();
        }
    }

    @Override
    protected void onShutdown() {
        super.onShutdown();

        EnchantsAPI.clear();
        this.schedulerUtil = null;
    }

    private void loadCommands() {
        this.rootCommand = NightCommand.forPlugin(this, builder -> new BaseCommands(this).load(builder));
    }

    private void loadHooks() {
        if (Plugins.hasPlaceholderAPI()) {
            PlaceholderHook.setup(this);
        }
    }


    public EnchantManager getEnchantManager() {
        return this.enchantManager;
    }

    public SchedulerUtil schedulerUtil() {
        return this.schedulerUtil;
    }

    @Nullable
    public TooltipManager getTooltipManager() {
        return this.tooltipManager;
    }
}
