package net.dive.tutorialmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.predicate.NbtPredicate;
import net.minecraft.registry.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.dive.tutorialmod.network.OpenNbtEditorPayload;
import net.dive.tutorialmod.network.SaveNbtPayload;

public class TutorialMod implements ModInitializer {

    public static final String MOD_ID = "tutorialmod";

    public static final RegistryKey<Item> NBT_WAND_KEY = RegistryKey.of(
            RegistryKeys.ITEM, Identifier.of(MOD_ID, "nbt_wand"));

    public static final Item NBT_WAND = Registry.register(
            Registries.ITEM,
            NBT_WAND_KEY,
            new NbtWandItem(new Item.Settings()
                    .registryKey(NBT_WAND_KEY)
                    .maxCount(1)
                    .component(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true))
    );

    @Override
    public void onInitialize() {
        // Register item into tools tab
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS)
                .register(entries -> entries.add(NBT_WAND));

        // Register network payloads
        PayloadTypeRegistry.playS2C().register(OpenNbtEditorPayload.ID, OpenNbtEditorPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SaveNbtPayload.ID, SaveNbtPayload.CODEC);

        // ── Entity right-click: open NBT editor ──
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (!player.getStackInHand(hand).isOf(NBT_WAND)) return ActionResult.PASS;

            if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;

            // Permission check — op level 2 required
            if (!serverPlayer.hasPermissionLevel(2)) {
                serverPlayer.sendMessage(
                        Text.literal("You need operator permission to use the NBT Wand.")
                                .formatted(Formatting.RED), true);
                return ActionResult.FAIL;
            }

            NbtCompound nbt = NbtPredicate.entityToNbt(entity);
            ServerPlayNetworking.send(serverPlayer,
                    new OpenNbtEditorPayload("entity", entity.getUuidAsString(), nbt.toString()));
            return ActionResult.SUCCESS;
        });

        // ── Save NBT back to the world ──
        ServerPlayNetworking.registerGlobalReceiver(SaveNbtPayload.ID, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayerEntity player = context.player();
                    if (player == null) return;

                    // Permission check on every save, not just open
                    if (!player.hasPermissionLevel(2)) {
                        player.sendMessage(
                                Text.literal("You need operator permission to save NBT.")
                                        .formatted(Formatting.RED), false);
                        return;
                    }

                    try {
                        switch (payload.editType()) {
                            case "item" -> handleItemSave(payload, player);
                            case "block" -> handleBlockSave(payload, player, context.server()
                                    .getCommandSource().withSilent().withLevel(4));
                            case "entity" -> handleEntitySave(payload, player, context.server()
                                    .getCommandSource().withSilent().withLevel(4));
                            default -> player.sendMessage(
                                    Text.literal("Unknown edit type: " + payload.editType())
                                            .formatted(Formatting.RED), false);
                        }
                    } catch (Exception e) {
                        player.sendMessage(
                                Text.literal("Failed to save NBT: " + e.getMessage())
                                        .formatted(Formatting.RED), false);
                        System.err.println("[TutorialMod] Save error: " + e.getMessage());
                    }
                })
        );
    }

    private void handleItemSave(SaveNbtPayload payload, ServerPlayerEntity player) {
        try {
            NbtCompound parsedNbt = StringNbtReader.readCompound(payload.nbtData());

            // Determine which hand was being edited
            Hand hand;
            try {
                hand = Hand.valueOf(payload.targetInfo());
            } catch (IllegalArgumentException e) {
                hand = Hand.OFF_HAND;
            }

            ItemStack stack = player.getStackInHand(hand);
            if (stack.isEmpty()) {
                player.sendMessage(
                        Text.literal("The item is no longer in your hand.")
                                .formatted(Formatting.RED), false);
                return;
            }

            // Merge custom data only — don't wipe other components
            NbtComponent existing = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
            NbtCompound merged = existing.copyNbt();
            merged.copyFrom(parsedNbt);
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(merged));

            player.sendMessage(
                    Text.literal("Item NBT saved successfully.")
                            .formatted(Formatting.GREEN), false);
        } catch (Exception e) {
            player.sendMessage(
                    Text.literal("Failed to parse item NBT: " + e.getMessage())
                            .formatted(Formatting.RED), false);
        }
    }

    private void handleBlockSave(SaveNbtPayload payload, ServerPlayerEntity player,
                                  net.minecraft.server.command.ServerCommandSource source) {
        try {
            String[] coords = payload.targetInfo().split(",");
            if (coords.length != 3) {
                player.sendMessage(Text.literal("Invalid block coordinates.").formatted(Formatting.RED), false);
                return;
            }
            String cmd = String.format("data merge block %s %s %s %s",
                    coords[0].trim(), coords[1].trim(), coords[2].trim(), payload.nbtData());

            int result = source.getServer().getCommandManager()
                    .getDispatcher().execute(cmd, source);

            if (result > 0) {
                player.sendMessage(
                        Text.literal("Block NBT saved successfully.")
                                .formatted(Formatting.GREEN), false);
            } else {
                player.sendMessage(
                        Text.literal("Block NBT merge returned no changes.")
                                .formatted(Formatting.YELLOW), false);
            }
        } catch (Exception e) {
            player.sendMessage(
                    Text.literal("Failed to save block NBT: " + e.getMessage())
                            .formatted(Formatting.RED), false);
        }
    }

    private void handleEntitySave(SaveNbtPayload payload, ServerPlayerEntity player,
                                   net.minecraft.server.command.ServerCommandSource source) {
        try {
            String cmd = String.format("data merge entity %s %s",
                    payload.targetInfo(), payload.nbtData());

            int result = source.getServer().getCommandManager()
                    .getDispatcher().execute(cmd, source);

            if (result > 0) {
                player.sendMessage(
                        Text.literal("Entity NBT saved successfully.")
                                .formatted(Formatting.GREEN), false);
            } else {
                player.sendMessage(
                        Text.literal("Entity NBT merge returned no changes.")
                                .formatted(Formatting.YELLOW), false);
            }
        } catch (Exception e) {
            player.sendMessage(
                    Text.literal("Failed to save entity NBT: " + e.getMessage())
                            .formatted(Formatting.RED), false);
        }
    }
}
