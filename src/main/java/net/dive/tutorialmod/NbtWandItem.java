package net.dive.tutorialmod;

import net.dive.tutorialmod.network.OpenNbtEditorPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class NbtWandItem extends Item {

    public NbtWandItem(Settings settings) {
        super(settings);
    }
//    // checking op methond(failing)
//    private boolean isPlayerOp(ServerPlayerEntity player) {
//        if (player.getServer() == null) return false;
//        return player.getServer().getPlayerManager().isOperator(player.getGameProfile());
//    }

    // ── 1. Right-click ON a block ──
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (world.isClient()) return ActionResult.SUCCESS;

        if (!(context.getPlayer() instanceof ServerPlayerEntity serverPlayer)) return ActionResult.FAIL;

        // check permission
//        if (!isPlayerOp(serverPlayer)) {
//            serverPlayer.sendMessage(Text.literal("You need operator permission to use the NBT Wand.").formatted(Formatting.RED), true);
//            return ActionResult.FAIL;
//        }

        BlockPos pos = context.getBlockPos();
        BlockEntity blockEntity = world.getBlockEntity(pos);

        if (blockEntity == null) {
            serverPlayer.sendMessage(Text.literal("This block has no NBT data.").formatted(Formatting.RED), true);
            return ActionResult.FAIL;
        }

        NbtCompound nbt = blockEntity.createNbt(world.getRegistryManager());
        nbt.remove("id"); nbt.remove("x"); nbt.remove("y"); nbt.remove("z");

        String targetInfo = pos.getX() + "," + pos.getY() + "," + pos.getZ();
        ServerPlayNetworking.send(serverPlayer, new OpenNbtEditorPayload("block", targetInfo, nbt.toString()));

        serverPlayer.sendMessage(Text.literal("Opened NBT editor for block at " + targetInfo).formatted(Formatting.AQUA), true);
        return ActionResult.SUCCESS;
    }

    // ── 2. Right-click ON an entity ──
    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        World world = user.getEntityWorld();
        if (world.isClient()) return ActionResult.SUCCESS;
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

        if (!(user instanceof ServerPlayerEntity serverPlayer)) return ActionResult.FAIL;

        // check OP
//        if (!isPlayerOp(serverPlayer)) {
//            serverPlayer.sendMessage(Text.literal("You need operator permission to use the NBT Wand.").formatted(Formatting.RED), true);
//            return ActionResult.FAIL;
//        }

        // Pull Nbt entity data
        NbtCompound nbt = new NbtCompound();
        try {
            Method m = entity.getClass().getMethod("toNbt");
            Object res = m.invoke(entity);
            if (res instanceof NbtCompound) nbt = (NbtCompound) res;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            try {
                Method m2 = entity.getClass().getMethod("saveNbt", NbtCompound.class);
                m2.invoke(entity, nbt);
            } catch (Exception ex) {
                // last resort: try "save"
                try {
                    Method m3 = entity.getClass().getMethod("save", NbtCompound.class);
                    m3.invoke(entity, nbt);
                } catch (Exception ignored) {}
            }
        }



        nbt.remove("UUID");
        nbt.remove("Pos");

        // sent data from entity to server
        String targetInfo = entity.getType().getUntranslatedName();
        ServerPlayNetworking.send(serverPlayer, new OpenNbtEditorPayload("entity", targetInfo, nbt.toString()));

        serverPlayer.sendMessage(Text.literal("Opened NBT editor for " + targetInfo).formatted(Formatting.AQUA), true);

        return ActionResult.SUCCESS;
    }

    // ── 3. Right-click in AIR: edit item in off-hand ──
    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient()) return ActionResult.SUCCESS;
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

        if (!(user instanceof ServerPlayerEntity serverPlayer)) return ActionResult.FAIL;

        // Check OP
//        if (!isPlayerOp(serverPlayer)) {
//            serverPlayer.sendMessage(Text.literal("You need operator permission to use the NBT Wand.").formatted(Formatting.RED), true);
//            return ActionResult.FAIL;
//        }

        ItemStack targetStack = user.getOffHandStack();
        if (targetStack.isEmpty()) {
            serverPlayer.sendMessage(Text.literal("Hold an item in your off-hand to edit its NBT.").formatted(Formatting.YELLOW), true);
            return ActionResult.FAIL;
        }

        NbtComponent customData = targetStack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        NbtCompound nbt = customData.copyNbt();

        String itemName = targetStack.getName().getString();
        serverPlayer.sendMessage(Text.literal("Opened NBT editor for: " + itemName).formatted(Formatting.AQUA), true);

        ServerPlayNetworking.send(serverPlayer, new OpenNbtEditorPayload("item", Hand.OFF_HAND.name(), nbt.toString()));

        return ActionResult.SUCCESS;
    }
}