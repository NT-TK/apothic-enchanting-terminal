package com.example.apothicenchantingterminal.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.lwjgl.glfw.GLFW;

import com.example.apothicenchantingterminal.ApothicEnchantingTerminal;
import com.example.apothicenchantingterminal.library.TerminalLibraryMenu;
import com.mojang.blaze3d.platform.InputConstants;

import dev.shadowsoffire.apothic_enchanting.library.EnchLibraryTile;
import dev.shadowsoffire.placebo.payloads.ButtonClickPayload;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 図書館モニター専用の画面。
 * Apothic Enchanting の図書館の画像(背景・行・スクロールバー・進捗バーのスプライト)だけを借りて、
 * 描画・入力・ツールチップは、このMOD独自に実装している。
 */
public class LibraryMonitorScreen extends AbstractContainerScreen<TerminalLibraryMenu> {

    /** 借りる画像(Apothic Enchanting側のファイル。実行時に参照するだけで、同梱はしない)。 */
    private static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath("apothic_enchanting", "textures/gui/library.png");
    private static final int ATLAS_W = 307;
    private static final int ATLAS_H = 256;

    // 画像の中のスプライトの位置(u, v, 幅, 高さ)
    private static final int[] ROW_NORMAL = { 194, 0, 113, 20 };
    private static final int[] ROW_HOVER = { 194, 20, 113, 20 };
    private static final int[] BAR_FILL = { 197, 42, 85, 3 };
    private static final int[] HANDLE_ON = { 303, 40, 4, 12 };
    private static final int[] HANDLE_OFF = { 303, 52, 4, 12 };

    // 画面の中のレイアウト(画面左上からの相対位置)
    private static final int VISIBLE_ROWS = 5;
    private static final int LIST_X = 20;
    private static final int LIST_Y = 30;
    private static final int ROW_W = 113;
    private static final int ROW_H = 20;
    private static final int TRACK_X = 13;
    private static final int TRACK_Y = 29;
    private static final int TRACK_LEN = 90; // ハンドルが動ける距離

    private record Row(Holder<Enchantment> ench, int points, int maxLevel) {
        Component name() {
            return this.ench.value().description();
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private int firstRow;
    private boolean draggingHandle;
    private EditBox search;

    public LibraryMonitorScreen(TerminalLibraryMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = 230;
        menu.setNotifier(this::refreshRows);
        this.refreshRows();
    }

    // ---------------------------------------------------------------- 初期化・入力

    @Override
    protected void init() {
        super.init();
        this.search = new EditBox(this.font, this.leftPos + 16, this.topPos + 16, 110, 11, Component.empty());
        this.search.setBordered(false);
        this.search.setTextColor(0xE8C898);
        this.search.setHint(Component.translatable("gui.apothic_enchanting_terminal.search").withStyle(ChatFormatting.DARK_GRAY));
        this.search.setResponder(text -> this.refreshRows());
        this.addRenderableWidget(this.search);
        this.setFocused(this.search);
        this.refreshRows();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 検索欄に入力中は、インベントリキー(E)で画面が閉じないようにする
        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        if (this.search.isFocused() && this.minecraft.options.keyInventory.isActiveAndMatches(key)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.draggingHandle = false;

        if (this.isHovering(TRACK_X + 1, TRACK_Y, 4, TRACK_LEN + 13, mouseX, mouseY) && this.canScroll()) {
            this.draggingHandle = true;
            this.dragHandleTo(mouseY);
            return true;
        }

        Row row = this.rowAt(mouseX, mouseY);
        if (row != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int id = this.minecraft.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getId(row.ench().value());
            if (Screen.hasShiftDown()) id |= 0x80000000; // Shift: 取り出せる最大レベルまで
            PacketDistributor.sendToServer(new ButtonClickPayload(id));
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && this.search.isMouseOver(mouseX, mouseY)) {
            this.search.setValue("");
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingHandle && this.canScroll()) {
            this.dragHandleTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingHandle = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.canScroll()) {
            this.firstRow = Mth.clamp(this.firstRow - (int) Math.signum(scrollY), 0, this.maxFirstRow());
        }
        return true;
    }

    private void dragHandleTo(double mouseY) {
        double y = mouseY - (this.topPos + TRACK_Y) - HANDLE_ON[3] / 2.0;
        double ratio = Mth.clamp(y / TRACK_LEN, 0.0, 1.0);
        this.firstRow = (int) Math.round(ratio * this.maxFirstRow());
    }

    // ---------------------------------------------------------------- 一覧のデータ

    private boolean canScroll() {
        return this.rows.size() > VISIBLE_ROWS;
    }

    private int maxFirstRow() {
        return Math.max(0, this.rows.size() - VISIBLE_ROWS);
    }

    /** 保存データ・検索語・スロットのアイテムから、表示する行を作り直す。 */
    private void refreshRows() {
        this.rows.clear();
        String needle = this.search == null ? "" : this.search.getValue().trim().toLowerCase(Locale.ROOT);
        ItemStack target = this.menu.ioInv.getItem(2);

        for (Object2IntMap.Entry<Holder<Enchantment>> e : this.menu.getPointsForDisplay()) {
            Holder<Enchantment> ench = e.getKey();
            if (!target.isEmpty() && !target.supportsEnchantment(ench)) continue;
            String name = ChatFormatting.stripFormatting(ench.value().description().getString());
            if (!needle.isEmpty() && (name == null || !name.toLowerCase(Locale.ROOT).contains(needle))) continue;
            this.rows.add(new Row(ench, e.getIntValue(), this.menu.getMaxLevel(ench)));
        }
        this.rows.sort(Comparator.comparing(r -> r.name().getString()));
        this.firstRow = Mth.clamp(this.firstRow, 0, this.maxFirstRow());
    }

    private Row rowAt(double mouseX, double mouseY) {
        for (int i = 0; i < VISIBLE_ROWS && this.firstRow + i < this.rows.size(); i++) {
            if (this.isHovering(LIST_X + 1, LIST_Y + 1 + i * ROW_H, ROW_W, ROW_H - 2, mouseX, mouseY)) {
                return this.rows.get(this.firstRow + i);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 描画

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        this.renderTooltip(gfx, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        gfx.blit(ATLAS, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, ATLAS_W, ATLAS_H);

        int[] handle = this.canScroll() ? HANDLE_ON : HANDLE_OFF;
        int handleY = this.maxFirstRow() == 0 ? 0 : Math.round(TRACK_LEN * this.firstRow / (float) this.maxFirstRow());
        this.sprite(gfx, handle, this.leftPos + TRACK_X, this.topPos + TRACK_Y + handleY);

        Row hovered = this.rowAt(mouseX, mouseY);
        for (int i = 0; i < VISIBLE_ROWS && this.firstRow + i < this.rows.size(); i++) {
            Row row = this.rows.get(this.firstRow + i);
            this.drawRow(gfx, row, this.leftPos + LIST_X, this.topPos + LIST_Y + i * ROW_H, row == hovered);
        }
    }

    private void drawRow(GuiGraphics gfx, Row row, int x, int y, boolean hovered) {
        this.sprite(gfx, hovered ? ROW_HOVER : ROW_NORMAL, x, y);

        // ポイントの進捗(上限に対する割合。平方根でならして、少量でも見えるようにする)
        double fill = Math.sqrt(row.points()) / Math.sqrt(Math.max(1, this.menu.getPointCap()));
        int width = Mth.clamp((int) Math.round(BAR_FILL[2] * fill), 0, BAR_FILL[2]);
        gfx.blit(ATLAS, x + 3, y + 14, BAR_FILL[0], BAR_FILL[1], width, BAR_FILL[3], ATLAS_W, ATLAS_H);

        // 名前(行からはみ出す場合は縮小する)
        Component name = row.name();
        int textWidth = this.font.width(name);
        float scale = textWidth > 85 ? 85f / textWidth : 1f;
        gfx.pose().pushPose();
        gfx.pose().translate(x + 3, y + 3, 0);
        gfx.pose().scale(scale, scale, 1f);
        gfx.drawString(this.font, name, 0, 0, 0xFFF3D6, true);
        gfx.pose().popPose();
    }

    private void sprite(GuiGraphics gfx, int[] s, int x, int y) {
        gfx.blit(ATLAS, x, y, s[0], s[1], s[2], s[3], ATLAS_W, ATLAS_H);
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        // タイトルなどは、画像に含まれているので描かない
    }

    // ---------------------------------------------------------------- ツールチップ

    @Override
    protected void renderTooltip(GuiGraphics gfx, int mouseX, int mouseY) {
        super.renderTooltip(gfx, mouseX, mouseY);
        Row row = this.rowAt(mouseX, mouseY);
        if (row == null) return;

        List<Component> lines = new ArrayList<>();
        lines.add(row.name().copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE));
        if (this.minecraft.options.advancedItemTooltips) {
            lines.add(Component.literal(String.valueOf(row.ench().getKey().location())).withStyle(ChatFormatting.DARK_GRAY));
        }

        String descKey = row.ench().getKey().location().toLanguageKey("enchantment") + ".desc";
        if (I18n.exists(descKey)) {
            lines.add(Component.translatable(descKey).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }

        lines.add(Component.translatable("gui.apothic_enchanting_terminal.max_level", Component.translatable("enchantment.level." + row.maxLevel())).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gui.apothic_enchanting_terminal.points", shorten(row.points()), shorten(this.menu.getPointCap())).withStyle(ChatFormatting.GRAY));

        // 取り出しの見込み(出力スロットの本に、今のレベルから何レベルまで上げられるか)
        int current = EnchantmentHelper.getEnchantmentsForCrafting(this.menu.ioInv.getItem(1)).getLevel(row.ench());
        int target = current + 1;
        if (Screen.hasShiftDown()) {
            int affordable = 1 + (int) (Math.log(row.points() + EnchLibraryTile.levelToPoints(current)) / Math.log(2));
            target = Math.max(target, Math.min(row.maxLevel(), affordable));
        }
        if (target > row.maxLevel()) {
            lines.add(Component.translatable("gui.apothic_enchanting_terminal.unavailable").withStyle(ChatFormatting.RED));
        } else {
            int cost = EnchLibraryTile.levelToPoints(target) - EnchLibraryTile.levelToPoints(current);
            lines.add(Component.translatable("gui.apothic_enchanting_terminal.extracting", Component.translatable("enchantment.level." + target)).withStyle(ChatFormatting.BLUE));
            lines.add(Component.translatable("gui.apothic_enchanting_terminal.cost", cost).withStyle(cost > row.points() ? ChatFormatting.RED : ChatFormatting.GOLD));
        }

        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : lines) wrapped.addAll(this.font.split(line, 220));
        gfx.renderTooltip(this.font, wrapped, mouseX, mouseY);
    }

    /** 1,500 → 1.5K のように短くする。 */
    private static String shorten(int n) {
        if (n < 10_000) return String.valueOf(n);
        if (n < 10_000_000) return trim(n / 1_000.0) + "K";
        if (n < 1_000_000_000) return trim(n / 1_000_000.0) + "M";
        return trim(n / 1_000_000_000.0) + "B";
    }

    private static String trim(double v) {
        String s = String.format(Locale.ROOT, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    @Override
    public int getSlotColor(int index) {
        return 0x40FFFFFF;
    }
}
