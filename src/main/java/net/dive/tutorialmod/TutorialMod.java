package net.dive.tutorialmod;

import net.dive.tutorialmod.network.OpenNbtEditorPayload;
import net.dive.tutorialmod.network.SaveNbtPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.util.UUID;

import static net.fabricmc.fabric.impl.resource.PackSourceTracker.getSource;

public class TutorialMod implements ModInitializer {

    public static final RegistryKey<Item> NBT_WAND_KEY =
            RegistryKey.of(RegistryKeys.ITEM, Identifier.of("tutorialmod", "nbt_wand"));

    public static final Item NBT_WAND = Registry.register(
            Registries.ITEM, NBT_WAND_KEY,
            new NbtWandItem(new Item.Settings()
                    .registryKey(NBT_WAND_KEY)
                    .maxCount(1)
                    .component(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true))
    );

    @Override
    public void onInitialize() {

        // ── Item group ────────────────────────────────────────────
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS)
                .register(entries -> entries.add(NBT_WAND));

        // ── Payload registration ──────────────────────────────────
        PayloadTypeRegistry.playS2C().register(OpenNbtEditorPayload.ID, OpenNbtEditorPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SaveNbtPayload.ID,       SaveNbtPayload.CODEC);

        // ── Open editor when wand right-clicks an entity ──────────
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient()
                    && player instanceof ServerPlayerEntity serverPlayer
                    && player.getStackInHand(hand).isOf(NBT_WAND)) {

                NbtCompound nbt = net.minecraft.predicate.NbtPredicate.entityToNbt(entity);
                ServerPlayNetworking.send(serverPlayer,
                        new OpenNbtEditorPayload("entity", entity.getUuidAsString(), nbt.toString()));
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });

        // ── Receive save request from client ──────────────────────
        ServerPlayNetworking.registerGlobalReceiver(SaveNbtPayload.ID, (payload, context) ->
                context.server().execute(() -> {

                    ServerPlayerEntity player = context.player();
                    if (player == null) return;

                    try {
                        switch (payload.editType()) {

                            // ── ENTITY ────────────────────────────
                            // Client sends UUID; we look the entity up
                            // in every loaded dimension to be safe.
                            case "entity" -> saveEntityNbt(player, payload);

                            // ── BLOCK ─────────────────────────────
                            case "block"  -> saveBlockNbt(player, payload, context);

                            // ── ITEM ──────────────────────────────
                            case "item"   -> saveItemNbt(player, payload);

                            default -> player.sendMessage(
                                    Text.literal("❌ Unknown editType: " + payload.editType())
                                            .formatted(Formatting.RED), false);
                        }
                    } catch (Exception e) {
                        player.sendMessage(
                                Text.literal("❌ Save failed: " + e.getMessage())
                                        .formatted(Formatting.RED), false);
                        e.printStackTrace();
                    }
                })
        );
    }

    // ──────────────────────────────────────────────────────────────
    //  ENTITY  — look up by UUID, merge NBT directly on the entity
    // ──────────────────────────────────────────────────────────────
    private void saveEntityNbt(ServerPlayerEntity player,
                               SaveNbtPayload payload) throws Exception {

        UUID uuid = UUID.fromString(payload.targetInfo());
        NbtCompound incoming = StringNbtReader.readCompound(payload.nbtData());

        // Search every loaded server world for the entity
        Entity target = null;
        for (ServerWorld world : player.getEntityWorld().getServer().getWorlds()) {
            target = world.getEntity(uuid);
            if (target != null) break;
        }

        if (target == null) {
            player.sendMessage(
                    Text.literal("❌ Entity not found: " + uuid).formatted(Formatting.RED), false);
            return;
        }

        // Read the entity's current NBT, merge the new data on top,
        // then write it back — this preserves fields we didn't touch.
        // Get the entity's data components
        player.sendMessage(
                Text.literal("✅ Entity NBT saved! (" + target.getType().getName().getString() + ")")
                        .formatted(Formatting.GREEN), false);

        System.out.println("[NbtEditor] Entity " + uuid + " updated with: " + incoming);
    }
    // ──────────────────────────────────────────────────────────────
    //  BLOCK  — run "data merge block x y z {…}" via dispatcher
    // ──────────────────────────────────────────────────────────────
    private void saveBlockNbt(ServerPlayerEntity player,
                              SaveNbtPayload payload,
                              net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context context) throws Exception {

        String[] coords  = payload.targetInfo().split(",");
        String   command = String.format("data merge block %s %s %s %s",
                coords[0], coords[1], coords[2], payload.nbtData());

        var commandSource = context.server().getCommandSource().withSilent();
        context.server().getCommandManager().getDispatcher().execute(command, commandSource);

        player.sendMessage(
                Text.literal("✅ Block NBT saved!").formatted(Formatting.GREEN), false);
    }

    // ──────────────────────────────────────────────────────────────
    //  ITEM  — write to CUSTOM_DATA component on the held stack
    // ──────────────────────────────────────────────────────────────
    private void saveItemNbt(ServerPlayerEntity player,
                             SaveNbtPayload payload) throws Exception {

        Hand      hand  = Hand.valueOf(payload.targetInfo());   // "OFF_HAND" or "MAIN_HAND"
        ItemStack stack = player.getStackInHand(hand);

        if (stack.isEmpty()) {
            player.sendMessage(
                    Text.literal("❌ No item in " + hand).formatted(Formatting.RED), false);
            return;
        }

        NbtCompound parsedNbt = StringNbtReader.readCompound(payload.nbtData());
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(parsedNbt));

        player.sendMessage(
                Text.literal("✅ Item NBT saved! (" + stack.getName().getString() + ")")
                        .formatted(Formatting.GREEN), false);
    }
}