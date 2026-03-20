package net.dive.tutorialmod.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record OpenNbtEditorPayload(String editType, String targetInfo, String nbtData) implements CustomPayload {

    public static final CustomPayload.Id<OpenNbtEditorPayload> ID =
            new CustomPayload.Id<>(Identifier.of("tutorialmod", "open_nbt_editor"));

    public static final PacketCodec<RegistryByteBuf, OpenNbtEditorPayload> CODEC =
            CustomPayload.codecOf(OpenNbtEditorPayload::write, OpenNbtEditorPayload::new);

    // เวลาแพ็คของ ต้องแพ็คเรียงตามลำดับ
    private void write(RegistryByteBuf buf) {
        buf.writeString(this.editType);
        buf.writeString(this.targetInfo);
        buf.writeString(this.nbtData);
    }

    private OpenNbtEditorPayload(RegistryByteBuf buf) {
        this(buf.readString(), buf.readString(), buf.readString());
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}