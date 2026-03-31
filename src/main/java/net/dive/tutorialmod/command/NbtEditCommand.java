package net.dive.tutorialmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.dive.tutorialmod.network.OpenNbtEditorPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.ItemSlotArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.Objects;

public class NbtEditCommand {

    /**
     * Call this from TutorialMod.onInitialize() like:
     *
     *   CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
     *       NbtEditCommand.register(dispatcher));
     *
     * Subcommands:
     *
     *   /nbtedit block <x> <y> <z>
     *   /nbtedit entity <target>          — @e[...] or a player name
     *   /nbtedit item                     — item in your main hand
     *   /nbtedit item offhand             — item in your off hand
     *   /nbtedit item hand <main|off>     — explicit hand
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {

        dispatcher.register(
                CommandManager.literal("nbtedit")
                        // ── /nbtedit block <pos> ─────────────────────────
                        .then(CommandManager.literal("block")
                                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                        .executes(NbtEditCommand::executeBlock)
                                )
                        )

                        // ── /nbtedit entity <target> ─────────────────────
                        .then(CommandManager.literal("entity")
                                .then(CommandManager.argument("target", EntityArgumentType.entity())
                                        .executes(NbtEditCommand::executeEntity)
                                )
                        )

                        // ── /nbtedit item ────────────────────────────────
                        .then(CommandManager.literal("item")
                                // /nbtedit item              → main hand
                                .executes(ctx -> executeItem(ctx, Hand.MAIN_HAND))

                                // /nbtedit item offhand       → off hand shortcut
                                .then(CommandManager.literal("offhand")
                                        .executes(ctx -> executeItem(ctx, Hand.OFF_HAND))
                                )

                                // /nbtedit item hand <main|off>
                                .then(CommandManager.literal("hand")
                                        .then(CommandManager.argument("which", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    builder.suggest("main");
                                                    builder.suggest("off");
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> {
                                                    String which = StringArgumentType.getString(ctx, "which");
                                                    Hand hand = which.equalsIgnoreCase("off") ? Hand.OFF_HAND : Hand.MAIN_HAND;
                                                    return executeItem(ctx, hand);
                                                })
                                        )
                                )
                        )
        );
    }

    // ──────────────────────────────────────────────────────────────
    //  /nbtedit block <x> <y> <z>
    // ──────────────────────────────────────────────────────────────
    private static int executeBlock(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerCommandSource src    = ctx.getSource();
            ServerPlayerEntity  player = src.getPlayerOrThrow();
            BlockPos            pos    = BlockPosArgumentType.getBlockPos(ctx, "pos");
            BlockEntity         be     = player.getEntityWorld().getBlockEntity(pos);

            if (be == null) {
                player.sendMessage(
                        Text.literal("❌ No block entity at " + formatPos(pos))
                                .formatted(Formatting.RED), false);
                return 0;
            }

            String nbtString = be.createNbt(player.getRegistryManager()).toString();
            String targetInfo = pos.getX() + "," + pos.getY() + "," + pos.getZ();

            ServerPlayNetworking.send(player,
                    new OpenNbtEditorPayload("block", targetInfo, nbtString));

            player.sendMessage(
                    Text.literal("📦 Opening block NBT at " + formatPos(pos))
                            .formatted(Formatting.AQUA), false);
            return 1;

        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("❌ " + e.getMessage()));
            return 0;
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  /nbtedit entity <target>
    // ──────────────────────────────────────────────────────────────
    private static int executeEntity(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerCommandSource src    = ctx.getSource();
            ServerPlayerEntity  player = src.getPlayerOrThrow();
            Entity              entity = EntityArgumentType.getEntity(ctx, "target");

            NbtCompound nbt = net.minecraft.predicate.NbtPredicate.entityToNbt(entity);

            ServerPlayNetworking.send(player,
                    new OpenNbtEditorPayload("entity", entity.getUuidAsString(), nbt.toString()));

            player.sendMessage(
                    Text.literal("👾 Opening entity NBT: " + entity.getType().getName().getString()
                                    + " [" + entity.getUuidAsString().substring(0, 8) + "…]")
                            .formatted(Formatting.AQUA), false);
            return 1;

        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("❌ " + e.getMessage()));
            return 0;
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  /nbtedit item [offhand | hand <main|off>]
    // ──────────────────────────────────────────────────────────────
    private static int executeItem(CommandContext<ServerCommandSource> ctx, Hand hand) {
        try {
            ServerCommandSource src    = ctx.getSource();
            ServerPlayerEntity  player = src.getPlayerOrThrow();
            ItemStack           stack  = player.getStackInHand(hand);

            if (stack.isEmpty()) {
                player.sendMessage(
                        Text.literal("❌ No item in " + (hand == Hand.OFF_HAND ? "off hand" : "main hand"))
                                .formatted(Formatting.RED), false);
                return 0;
            }

            // Encode the full item stack to NBT so the editor sees
            // all components (enchantments, custom_data, lore, etc.)
            var ops    = player.getRegistryManager().getOps(NbtOps.INSTANCE);
            NbtElement itemNbt = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();

            ServerPlayNetworking.send(player,
                    new OpenNbtEditorPayload("item", hand.name(), itemNbt.toString()));

            player.sendMessage(
                    Text.literal("🪄 Opening item NBT: " + stack.getName().getString()
                                    + " (" + hand.name().toLowerCase() + ")")
                            .formatted(Formatting.AQUA), false);
            return 1;

        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("❌ " + e.getMessage()));
            return 0;
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────────────────────
    private static String formatPos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}