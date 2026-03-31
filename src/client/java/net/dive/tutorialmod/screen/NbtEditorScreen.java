package net.dive.tutorialmod.screen;

import net.dive.tutorialmod.network.SaveNbtPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.text.Text;

public class NbtEditorScreen extends Screen {

    private final String editType;    // "block" | "item" | "entity"
    private final String targetInfo;  // coords "x,y,z" | hand name | entity UUID
    private final String initialNbt;
    private NbtListWidget listWidget;

    public NbtEditorScreen(String editType, String targetInfo, String initialNbt) {
        super(Text.literal("NBT Editor"));
        this.editType = editType;
        this.targetInfo = targetInfo;
        this.initialNbt = initialNbt;
    }

    @Override
    public boolean shouldPause() {
        return true;
    }

    // ──────────────────────────────────────────────────────────────
    //  INIT
    // ──────────────────────────────────────────────────────────────
    @Override
    protected void init() {
        super.init();

        // List widget sits between y=40 and (height - 110)
        this.listWidget = new NbtListWidget(this.client, this.width, this.height - 110, 40, 25);

        // Parse incoming NBT and populate the list
        try {
            NbtCompound nbt = StringNbtReader.readCompound(this.initialNbt);
            for (String key : nbt.getKeys()) {
                var element = nbt.get(key);
                if (element != null) {
                    this.listWidget.addNbtEntry(key, String.valueOf(element.asString()), (byte) element.getType());
                }
            }
        } catch (Exception e) {
            System.err.println("[NbtEditor] Failed to parse incoming NBT for " + editType + " / " + targetInfo);
            e.printStackTrace();
        }

        this.addDrawableChild(this.listWidget);

        int btnW  = 100;
        int btnH  = 20;
        int addY  = this.height - 65;
        int botY  = this.height - 30;

        // ── Add button ───────────────────────────────────────────
        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Add"), btn ->
                        this.listWidget.addNbtEntry("new_key", "", (byte) 8)
                ).dimensions(this.width / 2 - 50, addY, 100, btnH).build()
        );

        // ── Save button ──────────────────────────────────────────
        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Save"), btn ->
                        saveNbt()
                ).dimensions(this.width / 2 - btnW - 5, botY, btnW, btnH).build()
        );

        // ── Cancel button ────────────────────────────────────────
        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Cancel"), btn ->
                        this.close()
                ).dimensions(this.width / 2 + 5, botY, btnW, btnH).build()
        );
    }

    // ──────────────────────────────────────────────────────────────
    //  SAVE  — builds NbtCompound from list, then sends the correct
    //          payload depending on editType
    // ──────────────────────────────────────────────────────────────
    private void saveNbt() {
        try {
            NbtCompound newNbt = buildNbtFromList();
            sendPayload(newNbt.toString());
        } catch (Exception e) {
            System.err.println("[NbtEditor] Error building NBT before save: " + e.getMessage());
            e.printStackTrace();
        }
        this.close();
    }

    /**
     * Reads every row in the list widget and assembles an NbtCompound.
     * Booleans use the checkbox; everything else tries smart-parse first,
     * falls back to plain String if parsing fails.
     */
    private NbtCompound buildNbtFromList() {
        NbtCompound compound = new NbtCompound();

        for (NbtListWidget.NbtEntry entry : this.listWidget.children()) {
            String key = entry.keyField.getText().trim();
            if (key.isEmpty()) continue;

            // ── Boolean field (checkbox present) ─────────────────
            if (entry.checkboxWidget != null) {
                compound.putBoolean(key, entry.checkboxWidget.isChecked());
                continue;
            }

            // ── Text field ────────────────────────────────────────
            if (entry.valueField != null) {
                String raw = entry.valueField.getText().trim();

                // Try to parse as typed NBT first (handles int, float, list, compound …)
                try {
                    NbtCompound tmp = StringNbtReader.readCompound("{__v:" + raw + "}");
                    compound.put(key, tmp.get("__v"));
                } catch (Exception ignored) {
                    // Fallback: store as plain string
                    compound.putString(key, raw);
                }
            }
        }
        return compound;
    }

    /**
     * Routes the finished NBT string to the correct payload type.
     *
     * editType | targetInfo        | server action
     * ---------|-------------------|----------------------------------
     * "block"  | "x,y,z"          | data merge block x y z {…}
     * "item"   | hand name (enum)  | set CUSTOM_DATA component on held item
     * "entity" | entity UUID       | data merge entity <uuid> {…}
     */
    private void sendPayload(String nbtString) {
        switch (this.editType) {

            // ── ENTITY ────────────────────────────────────────────
            // targetInfo = UUID string from entity.getUuidAsString()
            // Server side: "data merge entity <uuid> <nbt>"
            case "entity" -> {
                ClientPlayNetworking.send(
                        new SaveNbtPayload("entity", this.targetInfo, nbtString)
                );
                System.out.println("[NbtEditor] Sent entity NBT → UUID=" + this.targetInfo
                        + "  nbt=" + nbtString);
            }

            // ── BLOCK ─────────────────────────────────────────────
            // targetInfo = "x,y,z"
            // Server side: "data merge block x y z <nbt>"
            case "block" -> {
                ClientPlayNetworking.send(
                        new SaveNbtPayload("block", this.targetInfo, nbtString)
                );
                System.out.println("[NbtEditor] Sent block NBT → pos=" + this.targetInfo
                        + "  nbt=" + nbtString);
            }

            // ── ITEM ──────────────────────────────────────────────
            // targetInfo = hand enum name ("OFF_HAND" / "MAIN_HAND")
            // Server side: sets CUSTOM_DATA DataComponentType on the stack
            case "item" -> {
                ClientPlayNetworking.send(
                        new SaveNbtPayload("item", this.targetInfo, nbtString)
                );
                System.out.println("[NbtEditor] Sent item NBT → hand=" + this.targetInfo
                        + "  nbt=" + nbtString);
            }

            default ->
                    System.err.println("[NbtEditor] Unknown editType: " + this.editType);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  RENDER
    // ──────────────────────────────────────────────────────────────
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Title
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("NBT Editor — " + this.editType + " [" + this.targetInfo + "]"),
                this.width / 2,
                15,
                0xFFFFFF
        );

        // Hint line above the Add button
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("editing " + this.editType + " | target: " + this.targetInfo),
                this.width / 2,
                this.height - 80,
                0xAAAAAA
        );
    }
}