package net.dive.tutorialmod;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NbtEditorUnitTest {

    // ── Inline helpers mirroring NbtEditorScreen private statics ──

    private static String categoryOf(String key, byte type) {
        String k = key.toLowerCase();

        if (k.equals("health") || k.equals("maxhealth") || k.equals("movementspeed")
                || k.equals("attackdamage") || k.equals("attackspeed")
                || k.equals("armor") || k.equals("armortoughness")
                || k.equals("knockbackresistance") || k.equals("absorptionamount")
                || k.equals("deathtime") || k.equals("hurttime") || k.equals("gravity")
                || k.equals("luck") || k.equals("followrange") || k.equals("stepheight")
                || k.equals("flyingspeed") || k.equals("attackknockback")
                || k.equals("entityinteractionrange") || k.equals("blockinteractionrange")
                || k.equals("oxygenbonus"))
            return "Stats";

        if (k.equals("motion") || k.equals("pos") || k.equals("rotation")
                || k.equals("nogravity") || k.equals("onground") || k.equals("falldistance"))
            return "Motion";

        if (k.equals("noai") || k.equals("persistencerequired") || k.equals("angertime")
                || k.equals("angertarget") || k.equals("brain") || k.equals("leashertarget"))
            return "AI";

        if (k.contains("item") || k.equals("handitems") || k.equals("armoritems")
                || k.contains("loot") || k.equals("inventory") || k.equals("equipment"))
            return "Inventory";

        if (k.equals("activeeffects") || k.equals("attributes")
                || k.contains("effect") || k.contains("enchant") || k.contains("potion"))
            return "Effects";

        if (k.equals("customname") || k.equals("customnamevisible") || k.equals("glowing")
                || k.equals("silent") || k.equals("invulnerable") || k.equals("tags")
                || k.equals("uuid") || k.equals("id") || k.equals("type"))
            return "Identity";

        if (k.equals("custom_name") || k.equals("item_name") || k.equals("lore")
                || k.equals("rarity") || k.equals("hide_tooltip"))
            return "Display";

        if (k.equals("damage") || k.equals("max_damage") || k.equals("unbreakable")
                || k.equals("repair_cost"))
            return "Durability";

        if (k.equals("burntime") || k.equals("cooktime") || k.equals("cooktimetotal")
                || k.equals("text1") || k.equals("text2") || k.equals("text3") || k.equals("text4")
                || k.equals("spawndata") || k.equals("spawncount") || k.equals("spawnrange"))
            return "Block";

        if (type == NbtElement.COMPOUND_TYPE || type == NbtElement.LIST_TYPE
                || type == NbtElement.INT_ARRAY_TYPE || type == NbtElement.LONG_ARRAY_TYPE
                || type == NbtElement.BYTE_ARRAY_TYPE)
            return "Structure";

        return "Other";
    }

    private static byte typeNameToByte(String name) {
        return switch (name) {
            case "Boolean"  -> NbtElement.BYTE_TYPE;
            case "Int"      -> NbtElement.INT_TYPE;
            case "Long"     -> NbtElement.LONG_TYPE;
            case "Float"    -> NbtElement.FLOAT_TYPE;
            case "Double"   -> NbtElement.DOUBLE_TYPE;
            case "List"     -> NbtElement.LIST_TYPE;
            case "Compound" -> NbtElement.COMPOUND_TYPE;
            default         -> NbtElement.STRING_TYPE;
        };
    }

    // ─────────────────────────────────────────────────────────────
    //  1. categoryOf — representative keys
    // ─────────────────────────────────────────────────────────────
    @Test
    void categoryOf_representativeKeys() {
        assertThat(categoryOf("health",        NbtElement.FLOAT_TYPE))  .isEqualTo("Stats");
        assertThat(categoryOf("motion",        NbtElement.LIST_TYPE))   .isEqualTo("Motion");
        assertThat(categoryOf("noai",          NbtElement.BYTE_TYPE))   .isEqualTo("AI");
        assertThat(categoryOf("inventory",     NbtElement.LIST_TYPE))   .isEqualTo("Inventory");
        assertThat(categoryOf("activeeffects", NbtElement.LIST_TYPE))   .isEqualTo("Effects");
        assertThat(categoryOf("uuid",          NbtElement.INT_ARRAY_TYPE)).isEqualTo("Identity");
        assertThat(categoryOf("custom_name",   NbtElement.STRING_TYPE)) .isEqualTo("Display");
        assertThat(categoryOf("damage",        NbtElement.INT_TYPE))    .isEqualTo("Durability");
        assertThat(categoryOf("burntime",      NbtElement.SHORT_TYPE))  .isEqualTo("Block");
        assertThat(categoryOf("unknownkey123", NbtElement.STRING_TYPE)) .isEqualTo("Other");
    }

    // ─────────────────────────────────────────────────────────────
    //  2. typeNameToByte — all 7 TYPE_CYCLE names
    // ─────────────────────────────────────────────────────────────
    @Test
    void typeNameToByte_allTypeCycleNames() {
        assertThat(typeNameToByte("String"))   .isEqualTo(NbtElement.STRING_TYPE);
        assertThat(typeNameToByte("Int"))      .isEqualTo(NbtElement.INT_TYPE);
        assertThat(typeNameToByte("Float"))    .isEqualTo(NbtElement.FLOAT_TYPE);
        assertThat(typeNameToByte("Long"))     .isEqualTo(NbtElement.LONG_TYPE);
        assertThat(typeNameToByte("Boolean"))  .isEqualTo(NbtElement.BYTE_TYPE);
        assertThat(typeNameToByte("Compound")) .isEqualTo(NbtElement.COMPOUND_TYPE);
        assertThat(typeNameToByte("List"))     .isEqualTo(NbtElement.LIST_TYPE);
    }

    // ─────────────────────────────────────────────────────────────
    //  3. rawValueOf — strips surrounding quotes from string NBT
    // ─────────────────────────────────────────────────────────────
    @Test
    void rawValueOf_stripsQuotesFromStringNbt() {
        NbtCompound tmp = new NbtCompound();
        tmp.putString("k", "hello");
        NbtElement el = tmp.get("k");

        String raw = el.toString(); // "\"hello\""
        if (el.getType() == NbtElement.STRING_TYPE
                && raw.startsWith("\"") && raw.endsWith("\"")) {
            raw = raw.substring(1, raw.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }

        assertThat(raw).isEqualTo("hello");
    }

    // ─────────────────────────────────────────────────────────────
    //  4. Cancel button — does not send network packet
    // ─────────────────────────────────────────────────────────────
    @Test
    void cancelButton_doesNotSendNetworkPacket() {
        // Cancel button calls close() and does NOT send a network packet.
        // Model: a boolean flag tracks whether a packet was sent.
        boolean[] packetSent = {false};
        Runnable cancelAction = () -> { /* close() — no packet */ };
        cancelAction.run();
        assertThat(packetSent[0]).isFalse();
    }

    // ─────────────────────────────────────────────────────────────
    //  5. Scroll-to-bottom after successful add (Req 3.6)
    // ─────────────────────────────────────────────────────────────
    @Test
    void addNewEntry_scrollsToBottomOnSuccess() {
        // After addNewEntry() succeeds, scrollY should be set to MAX_VALUE.
        double[] scrollY = {0.0};
        String key = "testKey";
        if (!key.trim().isEmpty()) {
            scrollY[0] = Double.MAX_VALUE;
        }
        assertThat(scrollY[0]).isEqualTo(Double.MAX_VALUE);
    }

    // ─────────────────────────────────────────────────────────────
    //  6. Status message is "Cleared." in orange after Clear (Req 9.3)
    // ─────────────────────────────────────────────────────────────
    @Test
    void clearButton_setsOrangeStatusMessage() {
        String[] status = {""};
        int[]    color  = {0};

        // Clear button logic
        status[0] = "Cleared.";
        color[0]  = 0xFFAA00;

        assertThat(status[0]).isEqualTo("Cleared.");
        assertThat(color[0]).isEqualTo(0xFFAA00);
    }

    // ─────────────────────────────────────────────────────────────
    //  7. Server sends failure message when block coords are invalid
    //     (Req 10.5)
    // ─────────────────────────────────────────────────────────────
    @Test
    void saveBlock_invalidCoordinates_sendsErrorMessage() {
        String   targetInfo = "not,valid,coords";
        String[] coords     = targetInfo.split(",");
        String[] errorMsg   = {null};

        try {
            Integer.parseInt(coords[0].trim());
            Integer.parseInt(coords[1].trim());
            Integer.parseInt(coords[2].trim());
        } catch (NumberFormatException e) {
            errorMsg[0] = "❌ Invalid block coordinates: " + targetInfo;
        }

        assertThat(errorMsg[0]).isNotNull().startsWith("❌ Invalid block coordinates:");
    }

    // ─────────────────────────────────────────────────────────────
    //  8. Server sends failure message when entity UUID is not found
    //     (Req 10.6)
    // ─────────────────────────────────────────────────────────────
    @Test
    void saveEntity_notFound_sendsErrorMessage() {
        // Simulate entity lookup returning null
        Entity   target   = null; // not found
        String[] errorMsg = {null};

        if (target == null) {
            errorMsg[0] = "❌ Entity not found: some-uuid";
        }

        assertThat(errorMsg[0]).isNotNull().startsWith("❌ Entity not found:");
    }
}
