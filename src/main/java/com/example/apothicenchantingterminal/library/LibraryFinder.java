package com.example.apothicenchantingterminal.library;

import java.util.Comparator;
import java.util.Optional;

import appeng.api.networking.IGrid;
import appeng.parts.storagebus.StorageBusPart;
import dev.shadowsoffire.apothic_enchanting.library.EnchLibraryTile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** ネットワーク内のストレージバスに隣接している図書館を探す。 */
public final class LibraryFinder {

    private LibraryFinder() {}

    /**
     * ストレージバスの優先度が最も高い図書館を返す。
     * 同じ優先度の場合は座標順で決定的に選ぶ。
     */
    public static Optional<EnchLibraryTile> find(IGrid grid) {
        return grid.getMachines(StorageBusPart.class).stream()
            .map(bus -> {
                Level level = bus.getLevel();
                if (level == null) return null;
                BlockPos pos = bus.getBlockEntity().getBlockPos().relative(bus.getSide());
                if (!level.isLoaded(pos)) return null;
                return level.getBlockEntity(pos) instanceof EnchLibraryTile tile
                    ? new Candidate(tile, priorityOf(bus)) : null;
            })
            .filter(c -> c != null)
            .sorted(Comparator.comparingInt(Candidate::priority).reversed()
                .thenComparingLong(c -> c.tile().getBlockPos().asLong()))
            .map(Candidate::tile)
            .findFirst();
    }

    private record Candidate(EnchLibraryTile tile, int priority) {}

    /** AE2のバージョン差に影響されないよう、優先度取得は読み取り専用で反射する。 */
    private static int priorityOf(StorageBusPart bus) {
        try {
            var method = bus.getClass().getMethod("getPriority");
            Object value = method.invoke(bus);
            return value instanceof Number n ? n.intValue() : 0;
        } catch (ReflectiveOperationException ignored) {
            return 0;
        }
    }
}
