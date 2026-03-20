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
import net.minecraft.registry.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.dive.tutorialmod.network.OpenNbtEditorPayload;
import net.dive.tutorialmod.network.SaveNbtPayload;

public class TutorialMod implements ModInitializer {

	public static final RegistryKey<Item> NBT_WAND_KEY = RegistryKey.of(RegistryKeys.ITEM, Identifier.of("tutorialmod", "nbt_wand"));
	public static final Item NBT_WAND = Registry.register(Registries.ITEM, NBT_WAND_KEY, new NbtWandItem(new Item.Settings().registryKey(NBT_WAND_KEY).maxCount(1).component(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE,true)));


	@Override
	public void onInitialize() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(NBT_WAND));

		PayloadTypeRegistry.playS2C().register(OpenNbtEditorPayload.ID, OpenNbtEditorPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SaveNbtPayload.ID, SaveNbtPayload.CODEC);

		/* ================= 1. ดึงข้อมูล ENTITY ================= */
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer && player.getStackInHand(hand).isOf(NBT_WAND)) {

				NbtCompound nbt = new NbtCompound();
				nbt = net.minecraft.predicate.NbtPredicate.entityToNbt(entity);

				ServerPlayNetworking.send(serverPlayer, new OpenNbtEditorPayload("entity", entity.getUuidAsString(), nbt.toString()));
				return ActionResult.SUCCESS;
			}
			return ActionResult.PASS;
		});

		/* ================= 2. เซฟข้อมูลกลับลงเกม (ใช้วิธีรันคำสั่งเบื้องหลัง) ================= */
		ServerPlayNetworking.registerGlobalReceiver(SaveNbtPayload.ID, (payload, context) -> context.server().execute(() -> {
					ServerPlayerEntity player = context.player();
					if (player == null) return;

					var commandSource = context.server().getCommandSource().withSilent();

					try {
						switch (payload.editType()) {
							case "item" -> {
								// สำหรับไอเทมใช้ระบบ Component
								NbtCompound parsedNbt = StringNbtReader.readCompound(payload.nbtData());
								ItemStack stack = player.getOffHandStack();
								if (!stack.isEmpty()) {
									stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(parsedNbt));
									player.sendMessage(Text.literal("✅ บันทึก NBT ของไอเทมเรียบร้อย!").formatted(Formatting.GREEN), false);
								}
							}
							case "block" -> {
								String[] coords = payload.targetInfo().split(",");
								String command = String.format("data merge block %s %s %s %s", coords[0], coords[1], coords[2], payload.nbtData());

								// 💡 ใช้ executeWithPrefix รับรองไม่แดง!
								context.server().getCommandManager().getDispatcher().execute(command, commandSource);
								player.sendMessage(Text.literal("✅ บันทึก NBT ของบล็อกเรียบร้อย!").formatted(Formatting.GREEN), false);
							}
							case "entity" -> {
								String command = String.format("data merge entity %s %s", payload.targetInfo(), payload.nbtData());

								// 💡 ใช้ executeWithPrefix รับรองไม่แดง!
								context.server().getCommandManager().getDispatcher().execute(command, commandSource);
								player.sendMessage(Text.literal("✅ บันทึก NBT Entity เรียบร้อย!").formatted(Formatting.GREEN), false);
							}
						}
					} catch (Exception e) {
						player.sendMessage(Text.literal("❌ เกิดข้อผิดพลาดในการรันคำสั่ง /data").formatted(Formatting.RED), false);
					}
				})
		);
	}
}