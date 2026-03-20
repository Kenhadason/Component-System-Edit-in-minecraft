package net.dive.tutorialmod.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SaveNbtPayload(String editType, String targetInfo, String nbtData) implements CustomPayload {

    public static final CustomPayload.Id<SaveNbtPayload> ID =
            new CustomPayload.Id<>(Identifier.of("tutorialmod", "save_nbt"));

    public static final PacketCodec<RegistryByteBuf, SaveNbtPayload> CODEC =
            CustomPayload.codecOf(SaveNbtPayload::write, SaveNbtPayload::new);

    private void write(RegistryByteBuf buf) {
        buf.writeString(this.editType);
        buf.writeString(this.targetInfo);
        buf.writeString(this.nbtData);
    }

    private SaveNbtPayload(RegistryByteBuf buf) {
        this(buf.readString(), buf.readString(), buf.readString());
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}