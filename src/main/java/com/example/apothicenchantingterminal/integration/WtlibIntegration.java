package com.example.apothicenchantingterminal.integration;

import com.example.apothicenchantingterminal.ApothicEnchantingTerminal;

import appeng.api.features.GridLinkables;
import appeng.items.tools.powered.WirelessTerminalItem;
import de.mari_023.ae2wtlib.api.gui.Icon;
import de.mari_023.ae2wtlib.api.registration.AddTerminalEvent;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * AE2WTLibとの統合。ae2wtlib が読み込まれているときだけ、このクラスをロードすること。
 * (ここで参照しているAPIのクラスが無い環境で、クラスロードエラーになるのを避けるため)
 */
public final class WtlibIntegration {

    public static final String TERMINAL_NAME = "library";

    private WtlibIntegration() {}

    public static void init() {
        AddTerminalEvent.register(event -> {
            // アイテムはここ(登録イベントの中)で初めて作る。翻訳キーはレジストリ名から決まるので、先に登録する
            LibraryWTItem item = WtlibItems.ITEM;
            Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(ApothicEnchantingTerminal.MOD_ID, "wireless_library_terminal"), item);
            GridLinkables.register(item, WirelessTerminalItem.LINKABLE_HANDLER);

            Icon icon = new Icon(0, 0, 16, 16, new Icon.Texture(
                ResourceLocation.fromNamespaceAndPath(ApothicEnchantingTerminal.MOD_ID, "textures/gui/library_icon.png"), 16, 16));
            event.builder(TERMINAL_NAME, LibraryWTMenuHost::new, com.example.apothicenchantingterminal.menu.LibraryTerminalMenu.TYPE, item, icon)
                .addTerminal();
        });
    }
}
