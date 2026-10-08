package com.example.apothicenchantingterminal.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.lwjgl.glfw.GLFW;

import com.example.apothicenchantingterminal.library.LibrarySnapshot;
import com.example.apothicenchantingterminal.menu.LibraryTerminalMenu;

import appeng.api.config.Settings;
import appeng.api.config.SortOrder;
import appeng.api.config.SortDir;
import appeng.client.Point;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.TerminalStyle;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.Scrollbar;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;
import dev.shadowsoffire.apothic_enchanting.library.EnchLibraryTile;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * AE2のターミナルの枠(拡張インベントリ)にはめ込んだ、図書館の画面。
 * 枠・検索欄・スクロールバー・ツールバーはAE2の部品を使い、グリッドのマス目の部分に、
 * 図書館のエンチャントを1行ずつ表示する。
 */
public class LibraryTerminalScreen extends AEBaseScreen<LibraryTerminalMenu> {

    private static final int MIN_ROWS = 2;
    private static final ResourceLocation FILTER_EMPTY = ResourceLocation.fromNamespaceAndPath("apothic_enchanting_terminal", "textures/gui/filter_slot_empty.png");
    private static final ResourceLocation FILTER_FILLED = ResourceLocation.fromNamespaceAndPath("apothic_enchanting_terminal", "textures/gui/filter_slot_filled.png");
    private static final ItemStack BOOK_ICON = new ItemStack(Items.ENCHANTED_BOOK);

    // 1行の中身の大きさ(グリッド9マス分。AE2のマスの内側に合わせる)
    private static final int ENTRY_W = 160;
    private static final int ENTRY_H = 16;

    private final TerminalStyle style;
    private final AETextField searchField;
    private final Scrollbar scrollbar;
    private final List<LibrarySnapshot.Entry> shown = new ArrayList<>();
    private int rows;
    private ItemStack lastFilter = ItemStack.EMPTY;
    private SortMode sortMode = SortMode.NAME;
    private boolean descending;

    private enum SortMode { NAME, ID, AMOUNT }

    // フィルタースロットの位置(ヘッダーの左端。スタイルJSONの位置と同じにする)
    // 検索欄の直前。左ツールバーの領域には置かない。
    private static final int FILTER_X = 8;
    private static final int FILTER_Y = 1;

    public LibraryTerminalScreen(LibraryTerminalMenu menu, Inventory playerInventory, Component title, ScreenStyle screenStyle) {
        super(menu, playerInventory, title, screenStyle);

        this.style = screenStyle.getTerminalStyle();
        if (this.style == null) {
            throw new IllegalStateException("library terminal style needs a terminalStyle section");
        }

        this.searchField = this.widgets.addTextField("search");
        this.searchField.setPlaceholder(GuiText.SearchPlaceholder.text());
        this.searchField.setResponder(text -> this.refresh());

        this.scrollbar = this.widgets.addScrollBar("scrollbar", Scrollbar.BIG);

        this.imageWidth = this.style.getScreenWidth();
        this.imageHeight = this.style.getScreenHeight(0);

        // ウィンドウサイズ(ターミナルスタイル): AE2標準のボタン
        this.addToLeftToolbar(new SettingToggleButton<>(Settings.TERMINAL_STYLE, this.config.getTerminalStyle(), this::toggleTerminalStyle));
        this.addToLeftToolbar(new SettingToggleButton<>(Settings.SORT_BY, SortOrder.NAME, (button, backwards) -> {
            // AE2の設定ボタンを操作イベントとして利用し、この画面独自の一覧を確実に更新する。
            SortOrder next = button.getNextValue(backwards);
            button.set(next);
            this.sortMode = switch (this.sortMode) {
                case NAME -> SortMode.ID;
                case ID -> SortMode.AMOUNT;
                case AMOUNT -> SortMode.NAME;
            };
            this.refresh();
        }));
        this.addToLeftToolbar(new SettingToggleButton<>(Settings.SORT_DIRECTION, SortDir.ASCENDING, (button, backwards) -> {
            SortDir next = button.getNextValue(backwards);
            button.set(next);
            this.descending = next == SortDir.DESCENDING;
            this.refresh();
        }));

        menu.setNotifier(this::refresh);
        this.refresh();
    }

    // ---------------------------------------------------------------- レイアウト

    @Override
    protected void init() {
        int availableHeight = this.height - 2 * AEConfig.instance().getTerminalMargin();
        this.rows = Math.max(MIN_ROWS, this.config.getTerminalStyle().getRows(this.style.getPossibleRows(availableHeight)));
        this.imageHeight = this.style.getScreenHeight(this.rows);

        super.init();

        this.setInitialFocus(this.searchField);
        this.updateScrollbar();
    }

    private void toggleTerminalStyle(SettingToggleButton<appeng.api.config.TerminalStyle> button, boolean backwards) {
        appeng.api.config.TerminalStyle next = button.getNextValue(backwards);
        this.config.setTerminalStyle(next);
        button.set(next);
        new ArrayList<>(this.children()).forEach(this::removeWidget);
        this.init();
    }

    private void updateScrollbar() {
        this.scrollbar.setHeight(this.rows * this.style.getRow().getSrcHeight() - 2);
        this.scrollbar.setRange(0, Math.max(0, this.shown.size() - this.rows), Math.max(1, this.rows / 6));
    }

    /** WTLIBのアップグレード列が使える表示行数。画面サイズに応じて変化する。 */
    protected int getVisibleRows() {
        return this.rows;
    }

    // ---------------------------------------------------------------- データ

    private void refresh() {
        String needle = this.searchField == null ? "" : this.searchField.getValue().trim().toLowerCase(Locale.ROOT);
        boolean modSearch = needle.startsWith("@");
        if (modSearch) needle = needle.substring(1).trim();
        this.shown.clear();
        ItemStack filter = this.menu.getFilterItem();
        for (LibrarySnapshot.Entry e : this.menu.getEntries()) {
            // フィルター: 置かれたアイテムに付けられるエンチャントだけ表示する
            if (!filter.isEmpty() && !filter.supportsEnchantment(e.ench())) continue;
            String name = ChatFormatting.stripFormatting(e.ench().value().description().getString());
            String namespace = e.ench().getKey().location().getNamespace().toLowerCase(Locale.ROOT);
            if (!needle.isEmpty()) {
                if (modSearch) {
                    if (!namespace.contains(needle)) continue;
                } else if (name == null || !name.toLowerCase(Locale.ROOT).contains(needle)) {
                    continue;
                }
            }
            this.shown.add(e);
        }
        Comparator<LibrarySnapshot.Entry> comparator = switch (this.sortMode) {
            case ID -> Comparator.comparing(e -> e.ench().getKey().location().toString());
            case AMOUNT -> Comparator.comparingInt(LibrarySnapshot.Entry::points);
            case NAME -> Comparator.comparing(e -> e.ench().value().description().getString());
        };
        if (this.descending) comparator = comparator.reversed();
        this.shown.sort(comparator);
        if (this.rows > 0) this.updateScrollbar();
    }

    private LibrarySnapshot.Entry entryAt(double mouseX, double mouseY) {
        for (int r = 0; r < this.rows; r++) {
            int index = this.scrollbar.getCurrentScroll() + r;
            if (index >= this.shown.size()) break;
            Point pos = this.style.getSlotPos(r, 0);
            if (this.isHovering(pos.getX(), pos.getY(), ENTRY_W, ENTRY_H, mouseX, mouseY)) {
                return this.shown.get(index);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 描画

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        // スロットの中身は、サーバーから届くので、変わったら一覧を作り直す
        ItemStack filter = this.menu.getFilterItem();
        if (!ItemStack.matches(filter, this.lastFilter)) {
            this.lastFilter = filter.copy();
            this.refresh();
        }
    }

    @Override
    public void drawBG(GuiGraphics gfx, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        // AE2のターミナルの枠(ヘッダー・行・下部)
        int y = offsetY;
        this.style.getHeader().dest(offsetX, y).blit(gfx);
        y += this.style.getHeader().getSrcHeight();
        int rowsToDraw = Math.max(2, this.rows);
        for (int i = 0; i < rowsToDraw; i++) {
            var row = this.style.getRow();
            if (i == 0) row = this.style.getFirstRow();
            else if (i + 1 == rowsToDraw) row = this.style.getLastRow();
            row.dest(offsetX, y).blit(gfx);
            y += this.style.getRow().getSrcHeight();
        }
        this.style.getBottom().dest(offsetX, y).blit(gfx);

        this.searchField.render(gfx, mouseX, mouseY, partialTicks);

        // フィルタースロットの背景。空のときは、Apothicの図書館と同じ雰囲気の虫眼鏡マークを表示する
        // (アイテムは、この上にAE2が描く)
        gfx.blit(this.menu.getFilterItem().isEmpty() ? FILTER_EMPTY : FILTER_FILLED, offsetX + FILTER_X, offsetY + FILTER_Y, 0, 0, 16, 16, 16, 16);

        if (!this.menu.hasLibrary()) {
            Point first = this.style.getSlotPos(0, 0);
            gfx.drawCenteredString(this.font, Component.translatable("gui.apothic_enchanting_terminal.no_library"),
                offsetX + first.getX() + ENTRY_W / 2, offsetY + first.getY() + 8, 0xFFE0E0E0);
            return;
        }

        LibrarySnapshot.Entry hovered = this.entryAt(mouseX, mouseY);
        for (int r = 0; r < this.rows; r++) {
            int index = this.scrollbar.getCurrentScroll() + r;
            if (index >= this.shown.size()) break;
            Point pos = this.style.getSlotPos(r, 0);
            this.drawEntry(gfx, this.shown.get(index), offsetX + pos.getX(), offsetY + pos.getY(), this.shown.get(index) == hovered);
        }
    }

    private void drawEntry(GuiGraphics gfx, LibrarySnapshot.Entry entry, int x, int y, boolean hovered) {
        // マス目の背景に重ねる、1行ぶんのパネル
        gfx.fill(x - 1, y - 1, x + ENTRY_W + 1, y + ENTRY_H + 1, 0xFF373737);
        gfx.fill(x, y, x + ENTRY_W, y + ENTRY_H, hovered ? 0xFFA8A8B8 : 0xFF8B8B8B);

        gfx.renderFakeItem(BOOK_ICON, x, y);

        // 名前(はみ出す場合は縮小)
        Component name = entry.ench().value().description();
        int maxWidth = 96;
        int textWidth = this.font.width(name);
        float scale = textWidth > maxWidth ? maxWidth / (float) textWidth : 1f;
        gfx.pose().pushPose();
        gfx.pose().translate(x + 19, y + 2, 0);
        gfx.pose().scale(scale, scale, 1f);
        gfx.drawString(this.font, name, 0, 0, 0xFF202020, false);
        gfx.pose().popPose();

        // ポイントの量(上限に対する割合。平方根でならして、少量でも見えるようにする)
        double fill = Math.sqrt(entry.points()) / Math.sqrt(Math.max(1, this.menu.getPointCap()));
        int barWidth = Mth.clamp((int) Math.round(96 * fill), 0, 96);
        // AE2風の青灰色ゲージ(背景1px + 充填1px)
        gfx.fill(x + 19, y + 12, x + 19 + 96, y + 14, 0xFF626B86);
        gfx.fill(x + 19, y + 12, x + 19 + barWidth, y + 13, 0xFF9AA8C3);

        // 右端: ポイント数と、最大レベル
        String points = shorten(entry.points());
        gfx.pose().pushPose();
        gfx.pose().translate(x + ENTRY_W - 2, y + 2, 0);
        gfx.pose().scale(0.75f, 0.75f, 1f);
        gfx.drawString(this.font, points, -this.font.width(points), 0, 0xFF202020, false);
        String level = Component.translatable("enchantment.level." + entry.maxLevel()).getString();
        gfx.drawString(this.font, level, -this.font.width(level), 9, 0xFF404060, false);
        gfx.pose().popPose();
    }

    // ---------------------------------------------------------------- 入力

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            LibrarySnapshot.Entry entry = this.entryAt(mouseX, mouseY);
            if (entry != null) {
                int id = this.minecraft.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getId(entry.ench().value());
                if (Screen.hasShiftDown()) id |= 0x80000000; // Shift: 取り出せる最大レベルまで
                this.menu.requestExtract(id);
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
                return true;
            }
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && this.searchField.isMouseOver(mouseX, mouseY)) {
            this.searchField.setValue("");
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ---------------------------------------------------------------- ツールチップ

    @Override
    protected void renderTooltip(GuiGraphics gfx, int mouseX, int mouseY) {
        super.renderTooltip(gfx, mouseX, mouseY);

        // フィルタースロットが空のときの説明
        if (this.isHovering(FILTER_X, FILTER_Y, 16, 16, mouseX, mouseY) && this.menu.getFilterItem().isEmpty() && this.menu.getCarried().isEmpty()) {
            this.drawTooltip(gfx, mouseX, mouseY, List.of(
                Component.translatable("gui.apothic_enchanting_terminal.filter").withStyle(ChatFormatting.YELLOW),
                Component.translatable("gui.apothic_enchanting_terminal.filter_hint").withStyle(ChatFormatting.GRAY)));
            return;
        }

        LibrarySnapshot.Entry entry = this.entryAt(mouseX, mouseY);
        if (entry == null) return;

        List<Component> lines = new ArrayList<>();
        lines.add(entry.ench().value().description().copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE));
        if (this.minecraft.options.advancedItemTooltips) {
            lines.add(Component.literal(String.valueOf(entry.ench().getKey().location())).withStyle(ChatFormatting.DARK_GRAY));
        }
        String descKey = entry.ench().getKey().location().toLanguageKey("enchantment") + ".desc";
        if (I18n.exists(descKey)) {
            lines.add(Component.translatable(descKey).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        lines.add(Component.translatable("gui.apothic_enchanting_terminal.max_level", Component.translatable("enchantment.level." + entry.maxLevel())).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gui.apothic_enchanting_terminal.points", shorten(entry.points()), shorten(this.menu.getPointCap())).withStyle(ChatFormatting.GRAY));

        // 取り出しの見込み(手に持っている本に、今のレベルから何レベルまで上げられるか)
        ItemStack carried = this.menu.getCarried();
        int current = carried.is(Items.ENCHANTED_BOOK) ? EnchantmentHelper.getEnchantmentsForCrafting(carried).getLevel(entry.ench()) : 0;
        int target = current + 1;
        if (Screen.hasShiftDown()) {
            int affordable = 1 + (int) (Math.log(entry.points() + EnchLibraryTile.levelToPoints(current)) / Math.log(2));
            target = Math.max(target, Math.min(entry.maxLevel(), affordable));
        }
        if (!carried.isEmpty() && !carried.is(Items.ENCHANTED_BOOK)) {
            lines.add(Component.translatable("gui.apothic_enchanting_terminal.need_book").withStyle(ChatFormatting.RED));
        } else if (target > entry.maxLevel()) {
            lines.add(Component.translatable("gui.apothic_enchanting_terminal.unavailable").withStyle(ChatFormatting.RED));
        } else {
            int cost = EnchLibraryTile.levelToPoints(target) - EnchLibraryTile.levelToPoints(current);
            lines.add(Component.translatable("gui.apothic_enchanting_terminal.extracting", Component.translatable("enchantment.level." + target)).withStyle(ChatFormatting.BLUE));
            lines.add(Component.translatable("gui.apothic_enchanting_terminal.cost", cost).withStyle(cost > entry.points() ? ChatFormatting.RED : ChatFormatting.GOLD));
        }
        this.drawTooltip(gfx, mouseX, mouseY, lines);
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
}
