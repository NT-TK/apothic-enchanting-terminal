package com.example.apothicenchantingterminal.network;

import com.example.apothicenchantingterminal.ApothicEnchantingTerminal;
import com.example.apothicenchantingterminal.library.LibrarySnapshot;
import com.example.apothicenchantingterminal.library.TerminalLibraryMenu;
import com.example.apothicenchantingterminal.menu.LibraryTerminalMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** サーバー → クライアント: 開いている図書館メニューの中身を更新する。 */
public record LibrarySyncPayload(int containerId, LibrarySnapshot snapshot) implements CustomPacketPayload {

    public static final Type<LibrarySyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ApothicEnchantingTerminal.MOD_ID, "library_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LibrarySyncPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, LibrarySyncPayload::containerId,
        LibrarySnapshot.CODEC, LibrarySyncPayload::snapshot,
        LibrarySyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LibrarySyncPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var open = ctx.player().containerMenu;
            if (open.containerId != msg.containerId) return;
            if (open instanceof LibraryTerminalMenu menu) {
                menu.applySnapshot(msg.snapshot);
            } else if (open instanceof TerminalLibraryMenu legacy) {
                legacy.applySnapshot(msg.snapshot);
            }
        });
    }
}
