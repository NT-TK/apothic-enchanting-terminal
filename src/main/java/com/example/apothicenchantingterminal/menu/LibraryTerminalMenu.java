package com.example.apothicenchantingterminal.menu;

import java.util.ArrayList;
import java.util.List;

import com.example.apothicenchantingterminal.ApothicEnchantingTerminal;
import com.example.apothicenchantingterminal.library.LibraryFinder;
import com.example.apothicenchantingterminal.library.LibrarySnapshot;
import com.example.apothicenchantingterminal.network.LibrarySyncPayload;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.SlotSemantic;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.menu.slot.FakeSlot;
import appeng.util.inv.AppEngInternalInventory;
import appeng.menu.implementations.MenuTypeBuilder;
import dev.shadowsoffire.apothic_enchanting.library.EnchLibraryTile;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.network.PacketDistributor;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;
import de.mari_023.ae2wtlib.api.gui.AE2wtlibSlotSemantics;

/**
 * AE2のターミナルとして開く図書館メニュー。
 * ホストは、図書館モニター(パート)でも、ワイヤレス端末でも、AE2側の標準のホスト(IActionHost)を使う。
 * 範囲・電力・有効性の確認は、AE2のメニュー(AEBaseMenu)とホストが行う。
 */
public class LibraryTerminalMenu extends AEBaseMenu {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ApothicEnchantingTerminal.MOD_ID, "library_terminal_ae");

    public static final net.minecraft.world.inventory.MenuType<LibraryTerminalMenu> TYPE = MenuTypeBuilder
        .create((int id, Inventory inv, IActionHost host) -> new LibraryTerminalMenu(id, inv, host), IActionHost.class)
        .withMenuTitle(host -> Component.translatable("gui.apothic_enchanting_terminal.library_terminal"))
        .buildUnregistered(ID);

    /** エンチャントのフィルター用スロット(画面のスタイルJSONで位置を決める)。 */
    public static final SlotSemantic FILTER_SEMANTIC = SlotSemantics.register("AET_LIBRARY_FILTER", false);

    private static final String ACTION_EXTRACT = "extract";

    /** フィルター用の見本アイテム(アイテムは消費されない)。 */
    private final FakeSlot filterSlot = new FakeSlot(new AppEngInternalInventory(1), 0);

    // サーバー側
    private final IActionHost host;
    private EnchLibraryTile library;
    private int libraryRecheck;
    private int lastSignature = Integer.MIN_VALUE;

    // クライアント側(サーバーから届いた表示用データ)
    private List<LibrarySnapshot.Entry> entries = new ArrayList<>();
    private int pointCap;
    private Runnable notifier;

    public LibraryTerminalMenu(int id, Inventory playerInventory, IActionHost host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;
        this.createPlayerInventorySlots(playerInventory);
        this.addSlot(this.filterSlot, FILTER_SEMANTIC);
        if (host instanceof WTMenuHost wtHost) {
            // WTLIBのアップグレードパネルは先頭スロットをシンギュラリティ枠として扱う。
            this.addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.QE_SINGULARITY,
                    wtHost.getSubInventory(WTMenuHost.INV_SINGULARITY), 0), AE2wtlibSlotSemantics.SINGULARITY);
            IUpgradeInventory upgrades = wtHost.getUpgrades();
            for (int i = 0; i < upgrades.size(); i++) {
                var slot = new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.UPGRADES, upgrades, i);
                slot.setNotDraggable();
                this.addSlot(slot, SlotSemantics.UPGRADE);
            }
        }
        this.registerClientAction(ACTION_EXTRACT, Integer.class, this::extract);
    }

    /** このメニューのホスト(図書館モニターのパート、またはワイヤレス端末)。 */
    public IActionHost getHost() {
        return this.host;
    }

    // ---------------------------------------------------------------- サーバー側

    private EnchLibraryTile findLibrary() {
        IGridNode node = this.host.getActionableNode();
        if (node == null) return null;
        IGrid grid = node.getGrid();
        return grid == null ? null : LibraryFinder.find(grid).orElse(null);
    }

    @Override
    public void broadcastChanges() {
        if (this.isServerSide() && this.getPlayer() instanceof ServerPlayer sp) {
            if (--this.libraryRecheck <= 0) {
                this.library = this.findLibrary();
                this.libraryRecheck = 10;
            }
            EnchLibraryTile lib = this.library != null && !this.library.isRemoved() ? this.library : null;
            int signature = lib == null ? 0 : 31 * lib.getPointsMap().hashCode() + lib.getLevelsMap().hashCode() + 1;
            if (signature != this.lastSignature) {
                this.lastSignature = signature;
                PacketDistributor.sendToPlayer(sp, new LibrarySyncPayload(this.containerId, lib == null ? LibrarySnapshot.EMPTY : LibrarySnapshot.of(lib)));
            }
        }
        super.broadcastChanges();
    }

    /** Shiftクリックで、エンチャントの本を図書館に登録する。 */
    @Override
    public ItemStack quickMoveStack(Player player, int idx) {
        if (this.isServerSide() && this.library != null && idx >= 0 && idx < this.slots.size()) {
            Slot slot = this.slots.get(idx);
            ItemStack stack = slot.getItem();
            if (slot.hasItem() && stack.is(Items.ENCHANTED_BOOK) && slot.mayPickup(player)) {
                for (int i = 0; i < stack.getCount(); i++) {
                    this.library.depositBook(stack);
                }
                slot.set(ItemStack.EMPTY);
                return ItemStack.EMPTY;
            }
        }
        return super.quickMoveStack(player, idx);
    }

    /** クライアントの操作: 持っている(空なら新しい)エンチャントの本に、図書館のエンチャントを加える。 */
    public void requestExtract(int encodedId) {
        this.sendClientAction(ACTION_EXTRACT, encodedId);
    }

    private void extract(Integer encoded) {
        if (this.isClientSide() || this.library == null) return;
        boolean shift = (encoded & 0x80000000) != 0;
        int id = encoded & 0x7FFFFFFF;
        Holder<Enchantment> ench = this.getPlayer().level().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(id).orElse(null);
        if (ench == null) return;

        ItemStack carried = this.getCarried();
        if (!carried.isEmpty() && !carried.is(Items.ENCHANTED_BOOK)) return;
        ItemStack book = carried.isEmpty() ? new ItemStack(Items.ENCHANTED_BOOK) : carried;

        int current = EnchantmentHelper.getEnchantmentsForCrafting(book).getLevel(ench);
        int target = shift
            ? Math.min(this.library.getMax(ench), 1 + (int) (Math.log(this.library.getPointsMap().getInt(ench) + EnchLibraryTile.levelToPoints(current)) / Math.log(2)))
            : current + 1;
        if (!this.library.canExtract(ench, target, current)) return;
        this.library.extractEnchant(book, ench, target);
        this.setCarried(book);
    }

    // ---------------------------------------------------------------- クライアント側

    public void applySnapshot(LibrarySnapshot snapshot) {
        this.entries = new ArrayList<>(snapshot.entries());
        this.pointCap = snapshot.pointCap();
        if (this.notifier != null) this.notifier.run();
    }

    public List<LibrarySnapshot.Entry> getEntries() {
        return this.entries;
    }

    /** フィルターに置かれているアイテム。空ならフィルターなし。 */
    public ItemStack getFilterItem() {
        return this.filterSlot.getItem();
    }

    public int getPointCap() {
        return this.pointCap;
    }

    /** 図書館が接続されているか(上限0は未接続の目印)。 */
    public boolean hasLibrary() {
        return this.pointCap > 0;
    }

    public void setNotifier(Runnable notifier) {
        this.notifier = notifier;
    }
}
