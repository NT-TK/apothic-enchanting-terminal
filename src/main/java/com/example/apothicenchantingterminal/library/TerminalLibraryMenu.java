package com.example.apothicenchantingterminal.library;

import java.util.List;
import java.util.function.BooleanSupplier;

import com.example.apothicenchantingterminal.Registrations;
import com.example.apothicenchantingterminal.network.LibrarySyncPayload;

import dev.shadowsoffire.apothic_enchanting.library.EnchLibraryBlock;
import dev.shadowsoffire.apothic_enchanting.library.EnchLibraryTile;
import dev.shadowsoffire.placebo.menu.PlaceboContainerMenu;
import dev.shadowsoffire.placebo.payloads.ButtonClickPayload.IButtonContainer;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Apothic Enchanting の図書館メニューの遠隔版。
 * サーバー側は図書館のタイルを直接操作し、クライアント側は送られてきたスナップショットだけを持つ。
 * (クライアントの世界に図書館のブロックが無くても開ける)
 */
public class TerminalLibraryMenu extends PlaceboContainerMenu implements IButtonContainer {

    public final SimpleContainer ioInv = new SimpleContainer(3);

    // 両側: 表示用データ(サーバー側では未使用)
    private final Object2IntMap<Holder<Enchantment>> points = new Object2IntOpenHashMap<>();
    private final Object2IntMap<Holder<Enchantment>> maxLevels = new Object2IntOpenHashMap<>();
    private int pointCap;
    private Runnable notifier;

    // サーバー側のみ
    private final EnchLibraryTile tile;
    private final BooleanSupplier valid;
    private final ServerPlayer serverPlayer;
    private int lastSignature;

    /** サーバー側コンストラクタ。 */
    public TerminalLibraryMenu(int id, Inventory inv, EnchLibraryTile tile, BooleanSupplier valid) {
        super(Registrations.MENU_TYPE, id, inv);
        this.tile = tile;
        this.valid = valid;
        this.serverPlayer = inv.player instanceof ServerPlayer sp ? sp : null;
        this.pointCap = LibrarySnapshot.capOf(tile);
        this.lastSignature = signature();
        this.initSlots(inv);
    }

    /** クライアント側コンストラクタ(IContainerFactory)。 */
    public static TerminalLibraryMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        TerminalLibraryMenu menu = new TerminalLibraryMenu(id, inv);
        menu.applySnapshot(LibrarySnapshot.read(buf));
        return menu;
    }

    private TerminalLibraryMenu(int id, Inventory inv) {
        super(Registrations.MENU_TYPE, id, inv);
        this.tile = null;
        this.valid = null;
        this.serverPlayer = null;
        this.initSlots(inv);
    }

    /** サーバー側からメニューを開く。 */
    public static void open(ServerPlayer player, EnchLibraryTile tile, BooleanSupplier valid) {
        player.openMenu(
            new SimpleMenuProvider((id, inv, p) -> new TerminalLibraryMenu(id, inv, tile, valid), EnchLibraryBlock.NAME),
            buf -> LibrarySnapshot.write(buf, LibrarySnapshot.of(tile)));
    }

    private void initSlots(Inventory inv) {
        this.addSlot(new Slot(this.ioInv, 0, 142, 77){
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() == Items.ENCHANTED_BOOK;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public void setChanged() {
                super.setChanged();
                if (!TerminalLibraryMenu.this.level.isClientSide && !this.getItem().isEmpty() && TerminalLibraryMenu.this.tile != null) {
                    TerminalLibraryMenu.this.tile.depositBook(this.getItem());
                }
                if (!this.getItem().isEmpty() && TerminalLibraryMenu.this.level.isClientSide) {
                    inv.player.level().playSound(inv.player, inv.player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.NEUTRAL, 0.5F, 0.7F);
                }
                TerminalLibraryMenu.this.ioInv.setItem(0, ItemStack.EMPTY);
            }
        });
        this.addSlot(new Slot(this.ioInv, 1, 142, 106){
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() == Items.ENCHANTED_BOOK;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        this.addSlot(new Slot(this.ioInv, 2, 142, 18){
            @Override
            public boolean mayPlace(ItemStack stack) {
                return true;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public void setChanged() {
                TerminalLibraryMenu.this.onChanged();
            }
        });
        this.addPlayerSlots(inv, 8, 148);
        this.mover.registerRule((stack, slot) -> slot == 0, 3, 39);
        this.mover.registerRule((stack, slot) -> slot == 1, 3, 39);
        this.mover.registerRule((stack, slot) -> slot == 2, 3, 39);
        this.mover.registerRule((stack, slot) -> stack.is(Items.ENCHANTED_BOOK), 0, 1);
        this.mover.registerRule((stack, slot) -> true, 2, 3);
        this.registerInvShuffleRules();
    }

    private int signature() {
        return 31 * this.tile.getPointsMap().hashCode() + this.tile.getLevelsMap().hashCode();
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (this.tile != null && this.serverPlayer != null) {
            int sig = this.signature();
            if (sig != this.lastSignature) {
                this.lastSignature = sig;
                PacketDistributor.sendToPlayer(this.serverPlayer, new LibrarySyncPayload(this.containerId, LibrarySnapshot.of(this.tile)));
            }
        }
    }

    public void applySnapshot(LibrarySnapshot snap) {
        this.points.clear();
        this.maxLevels.clear();
        this.pointCap = snap.pointCap();
        for (LibrarySnapshot.Entry e : snap.entries()) {
            this.points.put(e.ench(), e.points());
            this.maxLevels.put(e.ench(), e.maxLevel());
        }
        this.onChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.tile == null) return true; // クライアント側
        return !this.tile.isRemoved() && this.valid.getAsBoolean();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.clearContainer(player, this.ioInv);
    }

    public int getNumStoredEnchants() {
        return (int) this.points.values().intStream().filter(s -> s > 0).count();
    }

    public List<Object2IntMap.Entry<Holder<Enchantment>>> getPointsForDisplay() {
        return this.points.object2IntEntrySet().stream().filter(s -> s.getIntValue() > 0).toList();
    }

    public int getMaxLevel(Holder<Enchantment> enchant) {
        return this.maxLevels.getInt(enchant);
    }

    public int getPointCap() {
        return this.pointCap;
    }

    public void setNotifier(Runnable r) {
        this.notifier = r;
    }

    public void onChanged() {
        if (this.notifier != null) this.notifier.run();
    }

    @Override
    public void onButtonClick(int id) {
        if (this.level.isClientSide || this.tile == null) return; // 結果はスロット同期でクライアントへ届く
        boolean shift = (id & 0x80000000) == 0x80000000;
        if (shift) id = id & 0x7FFFFFFF;
        Holder<Enchantment> ench = this.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(id).orElse(null);
        if (ench == null) return;
        ItemStack outSlot = this.ioInv.getItem(1);
        int curLvl = EnchantmentHelper.getEnchantmentsForCrafting(outSlot).getLevel(ench);
        int targetLevel = shift ? Math.min(this.tile.getMax(ench), 1 + (int) (Math.log(this.tile.getPointsMap().getInt(ench) + EnchLibraryTile.levelToPoints(curLvl)) / Math.log(2))) : curLvl + 1;
        if (!this.tile.canExtract(ench, targetLevel, curLvl)) return;
        if (outSlot.isEmpty()) outSlot = new ItemStack(Items.ENCHANTED_BOOK);
        this.tile.extractEnchant(outSlot, ench, targetLevel);
        this.ioInv.setItem(1, outSlot);
    }
}
