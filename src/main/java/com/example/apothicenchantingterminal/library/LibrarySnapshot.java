package com.example.apothicenchantingterminal.library;

import java.util.ArrayList;
import java.util.List;

import dev.shadowsoffire.apothic_enchanting.library.EnchLibraryTile;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 図書館の中身(ポイントと解放済み最大レベル)をクライアントへ送るためのスナップショット。
 */
public record LibrarySnapshot(int pointCap, List<Entry> entries) {

    /** 図書館が接続されていないとき(上限0)。 */
    public static final LibrarySnapshot EMPTY = new LibrarySnapshot(0, List.of());

    public record Entry(Holder<Enchantment> ench, int points, int maxLevel) {}

    private static final StreamCodec<RegistryFriendlyByteBuf, Holder<Enchantment>> ENCH = ByteBufCodecs.holderRegistry(Registries.ENCHANTMENT);

    /** 図書館の種類から上限ポイントを求める。(maxPointsフィールドがprotectedのため) */
    public static int capOf(EnchLibraryTile tile) {
        int maxLevel = tile instanceof EnchLibraryTile.EnderLibraryTile ? 31 : 16;
        return EnchLibraryTile.levelToPoints(maxLevel);
    }

    public static LibrarySnapshot of(EnchLibraryTile tile) {
        List<Entry> list = new ArrayList<>();
        for (Object2IntMap.Entry<Holder<Enchantment>> e : tile.getPointsMap().object2IntEntrySet()) {
            if (e.getIntValue() <= 0) continue;
            list.add(new Entry(e.getKey(), e.getIntValue(), tile.getMax(e.getKey())));
        }
        return new LibrarySnapshot(capOf(tile), list);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, LibrarySnapshot> CODEC = StreamCodec.of(LibrarySnapshot::write, LibrarySnapshot::read);

    public static void write(RegistryFriendlyByteBuf buf, LibrarySnapshot snap) {
        buf.writeVarInt(snap.pointCap);
        buf.writeVarInt(snap.entries.size());
        for (Entry e : snap.entries) {
            ENCH.encode(buf, e.ench);
            buf.writeVarInt(e.points);
            buf.writeVarInt(e.maxLevel);
        }
    }

    public static LibrarySnapshot read(RegistryFriendlyByteBuf buf) {
        int cap = buf.readVarInt();
        int size = buf.readVarInt();
        List<Entry> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Holder<Enchantment> ench = ENCH.decode(buf);
            list.add(new Entry(ench, buf.readVarInt(), buf.readVarInt()));
        }
        return new LibrarySnapshot(cap, list);
    }
}
