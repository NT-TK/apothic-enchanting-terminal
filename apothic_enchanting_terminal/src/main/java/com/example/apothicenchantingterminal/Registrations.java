package com.example.apothicenchantingterminal;

import com.example.apothicenchantingterminal.item.WirelessLibraryTerminalItem;
import com.example.apothicenchantingterminal.library.TerminalLibraryMenu;
import com.example.apothicenchantingterminal.menu.LibraryTerminalMenu;
import com.example.apothicenchantingterminal.part.LibraryMonitorPart;

import appeng.core.AEConfig;
import appeng.items.parts.PartItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class Registrations {

    /** AE2WTLib が入っているか。入っていれば、端末アイテムは integration 側(ItemWTの派生)を使う。 */
    public static final boolean WTLIB = ModList.get().isLoaded("ae2wtlib");

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ApothicEnchantingTerminal.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ApothicEnchantingTerminal.MOD_ID);

    /** AE2WTLibの端末定義に渡すため、メニュータイプは先に作っておく。 */
    public static final MenuType<TerminalLibraryMenu> MENU_TYPE = IMenuTypeExtension.create(TerminalLibraryMenu::fromNetwork);

    public static final DeferredHolder<Item, PartItem<LibraryMonitorPart>> LIBRARY_MONITOR =
        ITEMS.register("library_monitor", () -> new PartItem<>(new Item.Properties(), LibraryMonitorPart.class, LibraryMonitorPart::new));

    /** AE2WTLib が無いときだけ登録する単体の端末。(ある場合は同じIDで integration が登録する) */
    public static final DeferredHolder<Item, WirelessLibraryTerminalItem> WIRELESS_LIBRARY_TERMINAL = WTLIB ? null :
        ITEMS.register("wireless_library_terminal",
            () -> new WirelessLibraryTerminalItem(AEConfig.instance().getWirelessTerminalBattery(), new Item.Properties().stacksTo(1)));

    /** 旧版の独自画面(予備として残している)。 */
    public static final DeferredHolder<MenuType<?>, MenuType<TerminalLibraryMenu>> LIBRARY_MENU = MENUS.register("library_terminal", () -> MENU_TYPE);

    /** AE2のターミナルとして開く、新しい図書館メニュー。 */
    public static final DeferredHolder<MenuType<?>, MenuType<LibraryTerminalMenu>> LIBRARY_TERMINAL_MENU = MENUS.register(LibraryTerminalMenu.ID.getPath(), () -> LibraryTerminalMenu.TYPE);

    /** 実際に使われている端末アイテム。 */
    public static Item libraryTerminal() {
        return WTLIB ? com.example.apothicenchantingterminal.integration.WtlibItems.ITEM : WIRELESS_LIBRARY_TERMINAL.get();
    }
}
