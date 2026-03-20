package net.dive.tutorialmod;

import net.dive.tutorialmod.network.OpenNbtEditorPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class NbtWandItem extends Item {

    public NbtWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        ItemStack stack = context.getStack();
        World world = context.getWorld();
        if (world.isClient()) return ActionResult.SUCCESS;

        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) context.getPlayer();
        BlockPos pos = context.getBlockPos();
        BlockEntity blockEntity = world.getBlockEntity(pos);

        if (blockEntity == null) {
            serverPlayer.sendMessage(Text.literal("❌ บล็อกนี้ไม่มีข้อมูล NBT").formatted(Formatting.RED), true);
            return ActionResult.FAIL;
        }

        // ดึง NBT ของบล็อก
        String nbtString = blockEntity.createNbt(world.getRegistryManager()).toString();

        // แพ็คพิกัด X,Y,Z ส่งผ่าน targetInfo
        String targetInfo = pos.getX() + "," + pos.getY() + "," + pos.getZ();

        // ส่ง Payload (editType, targetInfo, nbtData)
        ServerPlayNetworking.send(serverPlayer, new OpenNbtEditorPayload("block", targetInfo, nbtString));

        return ActionResult.SUCCESS;
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient()) return ActionResult.SUCCESS;

        if (user instanceof ServerPlayerEntity serverPlayer) {
            Hand otherHand = (hand == Hand.MAIN_HAND) ? Hand.OFF_HAND : Hand.MAIN_HAND;
            ItemStack targetStack = user.getStackInHand(otherHand);

            if (targetStack.isEmpty()) {
                serverPlayer.sendMessage(Text.literal("💡 ถือไอเทมที่ต้องการแก้ไว้ในมืออีกข้าง (Off-hand) ด้วยครับ").formatted(Formatting.YELLOW), true);
                return ActionResult.FAIL;
            }

            try {
                var ops = world.getRegistryManager().getOps(NbtOps.INSTANCE);

                // ใช้ NbtElement สำหรับ Fabric 1.21
                NbtElement itemNbt = ItemStack.CODEC.encodeStart(ops, targetStack).getOrThrow();

                // ส่ง Payload (editType, targetInfo, nbtData)
                ServerPlayNetworking.send(serverPlayer, new OpenNbtEditorPayload("item", otherHand.name(), itemNbt.toString()));
            } catch (Exception e) {
                serverPlayer.sendMessage(Text.literal("❌ ไม่สามารถดึงข้อมูลไอเทมได้").formatted(Formatting.RED), true);
            }
        }
        return ActionResult.SUCCESS;
    }
}