package net.dive.tutorialmod;

import net.dive.tutorialmod.network.OpenNbtEditorPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.entity.player.PlayerEntity;

public class NbtWandItem extends Item {

    public NbtWandItem(Settings settings) {
        super(settings);
    }

    // ── Right-click ON a block ──
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (world.isClient()) return ActionResult.SUCCESS;

        if (!(context.getPlayer() instanceof ServerPlayerEntity serverPlayer)) return ActionResult.FAIL;

        // Permission check
        if (!serverPlayer.hasPermissionLevel(2)) {
            serverPlayer.sendMessage(
                    Text.literal("You need operator permission to use the NBT Wand.")
                            .formatted(Formatting.RED), true);
            return ActionResult.FAIL;
        }

        BlockPos pos = context.getBlockPos();
        BlockEntity blockEntity = world.getBlockEntity(pos);

        if (blockEntity == null) {
            serverPlayer.sendMessage(
                    Text.literal("This block has no NBT data.")
                            .formatted(Formatting.RED), true);
            return ActionResult.FAIL;
        }

        NbtCompound nbt = blockEntity.createNbt(world.getRegistryManager());
        // Remove the internal id/pos keys — they're not editable
        nbt.remove("id");
        nbt.remove("x");
        nbt.remove("y");
        nbt.remove("z");

        String targetInfo = pos.getX() + "," + pos.getY() + "," + pos.getZ();
        ServerPlayNetworking.send(serverPlayer,
                new OpenNbtEditorPayload("block", targetInfo, nbt.toString()));

        serverPlayer.sendMessage(
                Text.literal("Opened NBT editor for block at " + targetInfo)
                        .formatted(Formatting.AQUA), true);
        return ActionResult.SUCCESS;
    }

    // ── Right-click in AIR: edit item in off-hand ──
    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient()) return ActionResult.SUCCESS;
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS; // only trigger from main hand

        if (!(user instanceof ServerPlayerEntity serverPlayer)) return ActionResult.FAIL;

        // Permission check
        if (!serverPlayer.hasPermissionLevel(2)) {
            serverPlayer.sendMessage(
                    Text.literal("You need operator permission to use the NBT Wand.")
                            .formatted(Formatting.RED), true);
            return ActionResult.FAIL;
        }

        ItemStack targetStack = user.getOffHandStack();
        if (targetStack.isEmpty()) {
            serverPlayer.sendMessage(
                    Text.literal("Hold an item in your off-hand to edit its NBT.")
                            .formatted(Formatting.YELLOW), true);
            return ActionResult.FAIL;
        }

        // Read CUSTOM_DATA component — this is the safe way to get editable NBT in 1.21
        NbtComponent customData = targetStack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        NbtCompound nbt = customData.copyNbt();

        // Also include the item's display name and lore if present, as readable info
        // (we show it but won't overwrite non-custom components on save)
        String itemName = targetStack.getName().getString();
        serverPlayer.sendMessage(
                Text.literal("Opened NBT editor for: " + itemName)
                        .formatted(Formatting.AQUA), true);

        ServerPlayNetworking.send(serverPlayer,
                new OpenNbtEditorPayload("item", Hand.OFF_HAND.name(), nbt.toString()));

        return ActionResult.SUCCESS;
    }
}
