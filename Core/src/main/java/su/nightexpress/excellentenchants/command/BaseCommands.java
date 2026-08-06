package su.nightexpress.excellentenchants.command;

import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

import su.nightexpress.excellentenchants.EnchantsPlugin;
import su.nightexpress.excellentenchants.EnchantsUtils;
import su.nightexpress.excellentenchants.EnchantsPlaceholders;
import su.nightexpress.excellentenchants.api.enchantment.CustomEnchantment;
import su.nightexpress.excellentenchants.config.Config;
import su.nightexpress.excellentenchants.config.Lang;
import su.nightexpress.excellentenchants.config.Perms;
import su.nightexpress.excellentenchants.enchantment.EnchantRegistry;
import su.nightexpress.nightcore.commands.Arguments;
import su.nightexpress.nightcore.commands.Commands;
import su.nightexpress.nightcore.commands.builder.HubNodeBuilder;
import su.nightexpress.nightcore.commands.context.CommandContext;
import su.nightexpress.nightcore.commands.context.ParsedArguments;
import su.nightexpress.nightcore.core.config.CoreLang;
import su.nightexpress.nightcore.locale.entry.MessageLocale;
import su.nightexpress.nightcore.util.*;
import su.nightexpress.nightcore.util.bridge.RegistryType;
import su.nightexpress.nightcore.util.placeholder.Replacer;

import java.util.function.Consumer;

@NullMarked
public class BaseCommands {

    private static final String RELOAD_DISABLED = "Reload is disabled on Luminol. Restart the server to reload ExcellentEnchants safely.";

    private final EnchantsPlugin plugin;

    public BaseCommands(EnchantsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(HubNodeBuilder builder) {
        builder.branch(Commands.literal("reload")
            .description(CoreLang.COMMAND_RELOAD_DESC)
            .permission(Perms.COMMAND_RELOAD)
            .executes((context, arguments) -> {
                CommandSender sender = context.getSender();
                this.plugin.schedulerUtil().runAtSender(sender, () -> sender.sendMessage(RELOAD_DISABLED));
                return true;
            })
        );

        builder.branch(Commands.literal("book")
            .description(Lang.COMMAND_BOOK_DESC)
            .permission(Perms.COMMAND_BOOK)
            .withArguments(
                CommandArguments.enchantArgument(CommandArguments.ENCHANT),
                CommandArguments.levelArgument(CommandArguments.LEVEL).optional(),
                Arguments.player(CommandArguments.PLAYER).optional()
            )
            .withFlags(CommandArguments.FLAG_CHARGED)
            .executes(this::giveEnchantBook)
        );

        builder.branch(Commands.literal("randombook")
            .description(Lang.COMMAND_RANDOM_BOOK_DESC)
            .permission(Perms.COMMAND_RANDOM_BOOK)
            .withArguments(Arguments.player(CommandArguments.PLAYER).optional())
            .withFlags(CommandArguments.FLAG_CUSTOM, CommandArguments.FLAG_CHARGED)
            .executes(this::giveRandomBook)
        );

        builder.branch(Commands.literal("enchant")
            .description(Lang.COMMAND_ENCHANT_DESC)
            .permission(Perms.COMMAND_ENCHANT)
            .withArguments(
                CommandArguments.enchantArgument(CommandArguments.ENCHANT),
                CommandArguments.levelArgument(CommandArguments.LEVEL),
                Arguments.player(CommandArguments.PLAYER).optional(),
                CommandArguments.slotArgument(CommandArguments.SLOT).optional()
            )
            .withFlags(CommandArguments.FLAG_CHARGED)
            .executes(this::enchantItem)
        );

        builder.branch(Commands.literal("disenchant")
            .description(Lang.COMMAND_DISENCHANT_DESC)
            .permission(Perms.COMMAND_DISENCHANT)
            .withArguments(
                CommandArguments.enchantArgument(CommandArguments.ENCHANT),
                Arguments.player(CommandArguments.PLAYER).optional(),
                CommandArguments.slotArgument(CommandArguments.SLOT).optional()
            )
            .executes(this::disenchantItem)
        );

        builder.branch(Commands.literal("list")
            .playerOnly()
            .description(Lang.COMMAND_LIST_DESC)
            .permission(Perms.COMMAND_LIST)
            .withArguments(Arguments.player(CommandArguments.PLAYER).permission(Perms.COMMAND_LIST_OTHERS).optional())
            .executes(this::openList)
        );

        if (Config.isChargesEnabled()) {
            builder.branch(Commands.literal("givefuel")
                .playerOnly()
                .description(Lang.COMMAND_GIVE_FUEL_DESC)
                .permission(Perms.COMMAND_GIVE_FUEL)
                .withArguments(
                    CommandArguments.customEnchantArgument(CommandArguments.ENCHANT)
                        .suggestions((reader, context) -> EnchantRegistry.getRegistered().stream().filter(
                            CustomEnchantment::isChargeable).map(CustomEnchantment::getId).toList()),
                    Arguments.integer(CommandArguments.AMOUNT, 1).suggestions((rader, context) -> Lists.newList("1",
                        "8", "16", "32", "64")).optional(),
                    Arguments.player(CommandArguments.PLAYER).optional()
                )
                .executes(this::giveFuel)
            );
        }
    }

    private int getLevel(Enchantment enchantment, ParsedArguments arguments) {
        int level = arguments.getInt(CommandArguments.LEVEL, -1);
        if (level <= 0) {
            level = EnchantsUtils.randomLevel(enchantment);
        }
        return level;
    }

    private boolean giveEnchantBook(CommandContext context, ParsedArguments arguments) {
        if (!context.isPlayer() && !arguments.contains(CommandArguments.PLAYER)) {
            context.printUsage();
            return false;
        }

        Player player = arguments.contains(CommandArguments.PLAYER) ? arguments.getPlayer(
            CommandArguments.PLAYER) : context.getPlayerOrThrow();

        boolean charged = context.hasFlag(CommandArguments.FLAG_CHARGED);
        Enchantment enchantment = arguments.getEnchantment(CommandArguments.ENCHANT);
        int level = getLevel(enchantment, arguments);

        CommandSender sender = context.getSender();
        return this.runAtPlayer(player, () -> this.giveBook(sender, player, enchantment, level, charged));
    }

    private boolean giveRandomBook(CommandContext context, ParsedArguments arguments) {
        if (!context.isPlayer() && !arguments.contains(CommandArguments.PLAYER)) {
            context.printUsage();
            return false;
        }

        Player player = arguments.contains(CommandArguments.PLAYER) ? arguments.getPlayer(
            CommandArguments.PLAYER) : context.getPlayerOrThrow();

        boolean custom = context.hasFlag(CommandArguments.FLAG_CUSTOM);
        boolean charged = context.hasFlag(CommandArguments.FLAG_CHARGED);
        Enchantment enchantment = Randomizer.pick(custom ? EnchantRegistry.getRegisteredBukkit() : BukkitThing.getAll(
            RegistryType.ENCHANTMENT));
        int level = EnchantsUtils.randomLevel(enchantment);

        CommandSender sender = context.getSender();
        return this.runAtPlayer(player, () -> this.giveBook(sender, player, enchantment, level, charged));
    }

    private void giveBook(CommandSender sender, Player player, Enchantment enchantment, int level, boolean charged) {
        ItemStack itemStack = new ItemStack(Material.ENCHANTED_BOOK);
        if (charged) {
            EnchantsUtils.restoreCharges(itemStack, enchantment, level);
        }

        EnchantsUtils.add(itemStack, enchantment, level, true);
        Players.addItem(player, itemStack);

        String playerName = player.getName();
        String playerDisplayName = Players.getDisplayNameSerialized(player);
        String enchantName = LangUtil.getSerializedName(enchantment);
        String enchantLevel = NumberUtil.toRoman(level);
        this.send(sender, Lang.ENCHANTED_BOOK_GAVE, replacer -> replacer
            .replace(EnchantsPlaceholders.GENERIC_ENCHANT, enchantName)
            .replace(EnchantsPlaceholders.GENERIC_LEVEL, enchantLevel)
            .replace(Placeholders.PLAYER_NAME, playerName)
            .replace(Placeholders.PLAYER_DISPLAY_NAME, playerDisplayName)
        );
    }

    private boolean enchantItem(CommandContext context, ParsedArguments arguments) {
        if (!context.isPlayer() && !arguments.contains(CommandArguments.PLAYER)) {
            context.printUsage();
            return false;
        }

        Player player = arguments.contains(CommandArguments.PLAYER) ? arguments.getPlayer(
            CommandArguments.PLAYER) : context.getPlayerOrThrow();
        EquipmentSlot slot = arguments.getOr(CommandArguments.SLOT, EquipmentSlot.class, EquipmentSlot.HAND);
        boolean charged = context.hasFlag(CommandArguments.FLAG_CHARGED);
        Enchantment enchantment = arguments.getEnchantment(CommandArguments.ENCHANT);
        int level = getLevel(enchantment, arguments);
        CommandSender sender = context.getSender();

        return this.runAtPlayer(player,
            () -> this.enchantItem(sender, player, slot, enchantment, level, charged));
    }

    private void enchantItem(CommandSender sender,
                             Player player,
                             EquipmentSlot slot,
                             Enchantment enchantment,
                             int level,
                             boolean charged) {
        ItemStack itemStack = EntityUtil.getItemInSlot(player, slot);
        if (itemStack == null || itemStack.getType().isAir()) {
            this.send(sender, Lang.COMMAND_ENCHANT_ERROR_NO_ITEM);
            return;
        }

        EnchantsUtils.add(itemStack, enchantment, level, true);

        if (charged) {
            EnchantsUtils.restoreCharges(itemStack, enchantment, level);
        }

        MessageLocale locale = sender == player ? Lang.COMMAND_ENCHANT_DONE_SELF : Lang.COMMAND_ENCHANT_DONE_OTHERS;
        String playerName = player.getName();
        String playerDisplayName = Players.getDisplayNameSerialized(player);
        String itemName = ItemUtil.getNameSerialized(itemStack);
        String enchantName = LangUtil.getSerializedName(enchantment);
        String enchantLevel = NumberUtil.toRoman(level);
        this.send(sender, locale, replacer -> replacer
            .replace(Placeholders.PLAYER_NAME, playerName)
            .replace(Placeholders.PLAYER_DISPLAY_NAME, playerDisplayName)
            .replace(EnchantsPlaceholders.GENERIC_ITEM, itemName)
            .replace(EnchantsPlaceholders.GENERIC_ENCHANT, enchantName)
            .replace(EnchantsPlaceholders.GENERIC_LEVEL, enchantLevel)
        );
    }

    private boolean disenchantItem(CommandContext context, ParsedArguments arguments) {
        if (!context.isPlayer() && !arguments.contains(CommandArguments.PLAYER)) {
            context.printUsage();
            return false;
        }

        Player player = arguments.contains(CommandArguments.PLAYER) ? arguments.getPlayer(
            CommandArguments.PLAYER) : context.getPlayerOrThrow();
        EquipmentSlot slot = arguments.getOr(CommandArguments.SLOT, EquipmentSlot.class, EquipmentSlot.HAND);
        Enchantment enchantment = arguments.getEnchantment(CommandArguments.ENCHANT);
        CommandSender sender = context.getSender();

        return this.runAtPlayer(player, () -> this.disenchantItem(sender, player, slot, enchantment));
    }

    private void disenchantItem(CommandSender sender,
                                Player player,
                                EquipmentSlot slot,
                                Enchantment enchantment) {
        ItemStack itemStack = EntityUtil.getItemInSlot(player, slot);
        if (itemStack == null || itemStack.getType().isAir()) {
            this.send(sender, Lang.COMMAND_ENCHANT_ERROR_NO_ITEM);
            return;
        }

        EnchantsUtils.remove(itemStack, enchantment);

        MessageLocale locale = sender == player ? Lang.COMMAND_DISENCHANT_DONE_SELF : Lang.COMMAND_DISENCHANT_DONE_OTHERS;
        String playerName = player.getName();
        String playerDisplayName = Players.getDisplayNameSerialized(player);
        String itemName = ItemUtil.getNameSerialized(itemStack);
        String enchantName = LangUtil.getSerializedName(enchantment);
        this.send(sender, locale, replacer -> replacer
            .replace(Placeholders.PLAYER_NAME, playerName)
            .replace(Placeholders.PLAYER_DISPLAY_NAME, playerDisplayName)
            .replace(EnchantsPlaceholders.GENERIC_ITEM, itemName)
            .replace(EnchantsPlaceholders.GENERIC_ENCHANT, enchantName)
        );
    }

    private boolean giveFuel(CommandContext context, ParsedArguments arguments) {
        if (!context.isPlayer() && !arguments.contains(CommandArguments.PLAYER)) {
            context.printUsage();
            return false;
        }

        Player player = arguments.contains(CommandArguments.PLAYER) ? arguments.getPlayer(
            CommandArguments.PLAYER) : context.getPlayerOrThrow();
        CustomEnchantment enchantment = arguments.get(CommandArguments.ENCHANT, CustomEnchantment.class);
        int amount = arguments.getInt(CommandArguments.AMOUNT, 1);

        if (!enchantment.isChargeable()) {
            String enchantName = enchantment.getDisplayName();
            this.send(context.getSender(), Lang.CHARGES_FUEL_BAD_ENCHANTMENT, replacer -> replacer.replace(
                EnchantsPlaceholders.GENERIC_NAME, enchantName));
            return false;
        }

        CommandSender sender = context.getSender();
        return this.runAtPlayer(player, () -> this.giveFuel(sender, player, enchantment, amount));
    }

    private void giveFuel(CommandSender sender, Player player, CustomEnchantment enchantment, int amount) {
        ItemStack fuel = enchantment.getFuel().clone();
        fuel.setAmount(amount);

        Players.addItem(player, fuel);

        String playerName = player.getName();
        String playerDisplayName = Players.getDisplayNameSerialized(player);
        String fuelName = ItemUtil.getNameSerialized(fuel);
        String formattedAmount = NumberUtil.format(amount);
        this.send(sender, Lang.CHARGES_FUEL_GAVE, replacer -> replacer
            .replace(EnchantsPlaceholders.GENERIC_AMOUNT, formattedAmount)
            .replace(EnchantsPlaceholders.GENERIC_NAME, fuelName)
            .replace(Placeholders.PLAYER_NAME, playerName)
            .replace(Placeholders.PLAYER_DISPLAY_NAME, playerDisplayName)
        );
    }

    private boolean openList(CommandContext context, ParsedArguments arguments) {
        if (!context.isPlayer() && !arguments.contains(CommandArguments.PLAYER)) {
            context.printUsage();
            return false;
        }

        Player player = arguments.contains(CommandArguments.PLAYER) ? arguments.getPlayer(
            CommandArguments.PLAYER) : context.getPlayerOrThrow();
        CommandSender sender = context.getSender();

        return this.runAtPlayer(player, () -> {
            this.plugin.getEnchantManager().openEnchantsMenu(player);

            if (player != sender) {
                String playerName = player.getName();
                String playerDisplayName = Players.getDisplayNameSerialized(player);
                this.send(sender, Lang.COMMAND_LIST_DONE_OTHERS, replacer -> replacer
                    .replace(Placeholders.PLAYER_NAME, playerName)
                    .replace(Placeholders.PLAYER_DISPLAY_NAME, playerDisplayName));
            }
        });
    }

    private boolean runAtPlayer(Player player, Runnable action) {
        this.plugin.schedulerUtil().runAtEntity(player, () -> {
            if (!player.isOnline()) return;
            action.run();
        });
        return true;
    }

    private void send(CommandSender sender, MessageLocale locale) {
        this.send(sender, locale, null);
    }

    private void send(CommandSender sender, MessageLocale locale, Consumer<Replacer> replacements) {
        this.plugin.schedulerUtil().runAtSender(sender, () -> locale.message().send(sender, replacements));
    }
}
