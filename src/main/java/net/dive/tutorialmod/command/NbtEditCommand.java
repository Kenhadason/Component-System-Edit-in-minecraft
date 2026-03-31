package net.dive.tutorialmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.dive.tutorialmod.network.OpenNbtEditorPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.predicate.NbtPredicate;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

public class NbtEditCommand {

    /**
     * Register in TutorialMod.onInitialize():
     *
     *   CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
     *       NbtEditCommand.register(dispatcher));
     *
     * Commands:
     *   /nbtedit block <x> <y> <z>
     *   /nbtedit entity <selector>
     *   /nbtedit item
     *   /nbtedit item offhand
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("nbtedit")

                        .then(CommandManager.literal("block")
                                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                        .executes(NbtEditCommand::executeBlock)))

                        .then(CommandManager.literal("entity")
                                .then(CommandManager.argument("target", EntityArgumentType.entity())
                                        .executes(NbtEditCommand::executeEntity)))

                        .then(CommandManager.literal("item")
                                .executes(ctx -> executeItem(ctx, Hand.MAIN_HAND))
                                .then(CommandManager.literal("offhand")
                                        .executes(ctx -> executeItem(ctx, Hand.OFF_HAND))))
        );
    }

    // ──────────────────────────────────────────────────────────────
    //  BLOCK — reads block entity NBT live from world
    // ──────────────────────────────────────────────────────────────
    private static int executeBlock(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            BlockPos           pos    = BlockPosArgumentType.getBlockPos(ctx, "pos");
            BlockEntity        be     = player.getEntityWorld().getBlockEntity(pos);

            if (be == null) {
                player.sendMessage(Text.literal("❌ No block entity at " + fmtPos(pos))
                        .formatted(Formatting.RED), false);
                return 0;
            }

            NbtCompound nbt = be.createNbt(player.getRegistryManager());
            // Strip internal positional keys — user shouldn't edit these
            nbt.remove("id"); nbt.remove("x"); nbt.remove("y"); nbt.remove("z");

            String targetInfo = pos.getX() + "," + pos.getY() + "," + pos.getZ();
            ServerPlayNetworking.send(player,
                    new OpenNbtEditorPayload("block", targetInfo, nbt.toString()));

            player.sendMessage(Text.literal("📦 Block NBT opened at " + fmtPos(pos))
                    .formatted(Formatting.AQUA), false);
            return 1;

        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("❌ " + e.getMessage()));
            return 0;
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  ENTITY — reads full live NBT PLUS injects readable attribute
    //           values (Health, Speed, AttackDamage, etc.) as extra
    //           top-level keys so the editor shows real numbers
    // ──────────────────────────────────────────────────────────────
    private static int executeEntity(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            Entity             entity = EntityArgumentType.getEntity(ctx, "target");

            // Full raw NBT from the entity
            NbtCompound nbt = NbtPredicate.entityToNbt(entity);

            // ── Inject live attribute values directly ─────────────
            // These are pulled from the actual EntityAttributeInstance,
            // so they reflect buffs, modifiers, and base values live.
            if (entity instanceof LivingEntity living) {
                NbtCompound live = new NbtCompound();

                // Health
                live.putFloat("Health",
                        living.getHealth());
                live.putFloat("MaxHealth",
                        (float) living.getAttributeValue(EntityAttributes.MAX_HEALTH));

                // Movement
                live.putFloat("MovementSpeed",
                        (float) living.getAttributeValue(EntityAttributes.MOVEMENT_SPEED));
                live.putFloat("FlyingSpeed",
                        (float) living.getAttributeValue(EntityAttributes.FLYING_SPEED));

                // Combat
                live.putFloat("AttackDamage",
                        (float) living.getAttributeValue(EntityAttributes.ATTACK_DAMAGE));
                live.putFloat("AttackSpeed",
                        (float) living.getAttributeValue(EntityAttributes.ATTACK_SPEED));
                live.putFloat("AttackKnockback",
                        (float) living.getAttributeValue(EntityAttributes.ATTACK_KNOCKBACK));

                // Defence
                live.putFloat("Armor",
                        (float) living.getAttributeValue(EntityAttributes.ARMOR));
                live.putFloat("ArmorToughness",
                        (float) living.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS));
                live.putFloat("KnockbackResistance",
                        (float) living.getAttributeValue(EntityAttributes.KNOCKBACK_RESISTANCE));

                // Range / reach
                live.putFloat("EntityInteractionRange",
                        (float) living.getAttributeValue(EntityAttributes.ENTITY_INTERACTION_RANGE));
                live.putFloat("BlockInteractionRange",
                        (float) living.getAttributeValue(EntityAttributes.BLOCK_INTERACTION_RANGE));

                // Luck & follow range
                live.putFloat("Luck",
                        (float) living.getAttributeValue(EntityAttributes.LUCK));
                live.putFloat("FollowRange",
                        (float) living.getAttributeValue(EntityAttributes.FOLLOW_RANGE));

                // Oxygen / step height
                live.putFloat("OxygenBonus",
                        (float) living.getAttributeValue(EntityAttributes.OXYGEN_BONUS));
                live.putFloat("StepHeight",
                        (float) living.getAttributeValue(EntityAttributes.STEP_HEIGHT));

                // Gravity
                live.putFloat("Gravity",
                        (float) living.getAttributeValue(EntityAttributes.GRAVITY));

                // Merge live block into the root NBT — these sit alongside
                // the raw Attributes list so the editor shows both forms
                nbt.copyFrom(live);
            }

            ServerPlayNetworking.send(player,
                    new OpenNbtEditorPayload("entity", entity.getUuidAsString(), nbt.toString()));

            player.sendMessage(Text.literal("👾 Entity NBT opened: "
                    + entity.getType().getName().getString()).formatted(Formatting.AQUA), false);
            return 1;

        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("❌ " + e.getMessage()));
            return 0;
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  ITEM — reads full item stack data component by component
    // ──────────────────────────────────────────────────────────────
    private static int executeItem(CommandContext<ServerCommandSource> ctx, Hand hand) {
        try {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            ItemStack          stack  = player.getStackInHand(hand);

            if (stack.isEmpty()) {
                player.sendMessage(Text.literal("❌ No item in "
                                + (hand == Hand.OFF_HAND ? "off hand" : "main hand"))
                        .formatted(Formatting.RED), false);
                return 0;
            }

            // Build a compound that the editor can show key-by-key
            NbtCompound nbt = new NbtCompound();

            // ── Identity ─────────────────────────────────────────
            nbt.putString("id", stack.getItem().toString());
            nbt.putInt("count", stack.getCount());
            nbt.putInt("max_stack_size", stack.getMaxCount());
            nbt.putBoolean("is_stackable", stack.isStackable());

            // ── Display ──────────────────────────────────────────
            nbt.putString("name", stack.getName().getString());

            // ── Damage / durability ──────────────────────────────
            if (stack.isDamageable()) {
                nbt.putInt("damage",     stack.getDamage());
                nbt.putInt("max_damage", stack.getMaxDamage());
                nbt.putBoolean("is_broken", stack.isDamaged());
            }

            // ── Custom data component ─────────────────────────────
            var customData = stack.getOrDefault(
                    DataComponentTypes.CUSTOM_DATA,
                    NbtComponent.DEFAULT);
            NbtCompound cd = customData.copyNbt();
            if (!cd.isEmpty()) {
                nbt.put("custom_data", cd);
            }

            // ── Enchantments ──────────────────────────────────────
            var enchComp = stack.get(DataComponentTypes.ENCHANTMENTS);
            if (enchComp != null && !enchComp.isEmpty()) {
                NbtList enchList = new NbtList();
                enchComp.getEnchantments().forEach(entry ->
                        enchList.add(NbtString.of(entry.getIdAsString())));
                nbt.put("enchantments", enchList);
            }

            // ── Glint override ────────────────────────────────────
            Boolean glint = stack.get(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);
            if (glint != null) nbt.putBoolean("enchantment_glint_override", glint);

            // ── Rarity ───────────────────────────────────────────
            var rarity = stack.get(DataComponentTypes.RARITY);
            if (rarity != null) nbt.putString("rarity", rarity.name().toLowerCase());

            // ── Food ─────────────────────────────────────────────
            var food = stack.get(DataComponentTypes.FOOD);
            if (food != null) {
                NbtCompound fc = new NbtCompound();
                fc.putInt("nutrition", food.nutrition());
                fc.putFloat("saturation", food.saturation());
                fc.putBoolean("can_always_eat", food.canAlwaysEat());
                nbt.put("food", fc);
            }

            ServerPlayNetworking.send(player,
                    new OpenNbtEditorPayload("item", hand.name(), nbt.toString()));

            player.sendMessage(Text.literal("🪄 Item NBT opened: "
                    + stack.getName().getString()).formatted(Formatting.AQUA), false);
            return 1;

        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("❌ " + e.getMessage()));
            return 0;
        }
    }

    private static String fmtPos(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }
}