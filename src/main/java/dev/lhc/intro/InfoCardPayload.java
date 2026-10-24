package dev.lhc.intro;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client: show/hide the bottom-left member card. */
public record InfoCardPayload(boolean show, int index, int total, String name, String role, String description)
        implements CustomPacketPayload {

    public static final Type<InfoCardPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("lhc_intro", "info_card"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InfoCardPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.show);
                buf.writeVarInt(p.index);
                buf.writeVarInt(p.total);
                buf.writeUtf(p.name, 64);
                buf.writeUtf(p.role, 64);
                buf.writeUtf(p.description, 256);
            },
            buf -> new InfoCardPayload(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(64), buf.readUtf(64), buf.readUtf(256)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
