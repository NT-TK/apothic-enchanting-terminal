package com.example.apothicenchantingterminal.library;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import dev.shadowsoffire.apothic_enchanting.library.EnchLibraryTile;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * AE2上で図書館のポイントをエンチャント本として公開する仮想ストレージ。
 * Applied Apothic Enchantingのコードには依存せず、Apothicの公開マップ/APIだけを使う。
 */
public final class LibraryMEStorage implements MEStorage {
    private final EnchLibraryTile library;

    public LibraryMEStorage(EnchLibraryTile library) {
        this.library = library;
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return decode(what) != null;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        Decoded book = decode(what);
        if (book == null || amount <= 0) return 0;
        int current = library.getPointsMap().getInt(book.enchantment());
        int max = library.getMax(book.enchantment());
        long cost = EnchLibraryTile.levelToPoints(book.level());
        long capacity = Math.max(0L, ((long) EnchLibraryTile.levelToPoints(max) - current) / cost);
        long accepted = Math.min(amount, capacity);
        if (accepted > 0 && mode == Actionable.MODULATE) {
            library.getPointsMap().put(book.enchantment(), Math.toIntExact(current + accepted * cost));
            library.getLevelsMap().put(book.enchantment(), Math.max(library.getLevelsMap().getInt(book.enchantment()), book.level()));
            library.setChanged();
        }
        return accepted;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        Decoded book = decode(what);
        if (book == null || amount <= 0) return 0;
        long cost = EnchLibraryTile.levelToPoints(book.level());
        long available = library.getPointsMap().getInt(book.enchantment()) / cost;
        long extracted = Math.min(amount, available);
        if (extracted > 0 && mode == Actionable.MODULATE) {
            int remaining = Math.toIntExact(library.getPointsMap().getInt(book.enchantment()) - extracted * cost);
            library.getPointsMap().put(book.enchantment(), remaining);
            library.setChanged();
        }
        return extracted;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        var registry = library.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        for (var entry : library.getPointsMap().object2IntEntrySet()) {
            Holder<Enchantment> ench = entry.getKey();
            int max = library.getMax(ench);
            for (int level = 1; level <= max; level++) {
                long count = entry.getIntValue() / EnchLibraryTile.levelToPoints(level);
                if (count <= 0) continue;
                ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
                ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                mutable.set(ench, level);
                EnchantmentHelper.setEnchantments(stack, mutable.toImmutable());
                AEItemKey key = AEItemKey.of(stack);
                if (key != null) out.add(key, count);
            }
        }
    }

    @Override
    public net.minecraft.network.chat.Component getDescription() {
        return net.minecraft.network.chat.Component.translatable("container.apothic_enchanting.library");
    }

    private Decoded decode(AEKey key) {
        if (!(key instanceof AEItemKey item) || !item.getReadOnlyStack().is(Items.ENCHANTED_BOOK)) return null;
        var enchants = EnchantmentHelper.getEnchantmentsForCrafting(item.getReadOnlyStack());
        if (enchants.size() != 1) return null;
        var entry = enchants.entrySet().iterator().next();
        return new Decoded(entry.getKey(), entry.getIntValue());
    }

    private record Decoded(Holder<Enchantment> enchantment, int level) {}
}
