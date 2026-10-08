package com.example.apothicenchantingterminal.item;

import java.util.function.DoubleSupplier;

import com.example.apothicenchantingterminal.menu.LibraryTerminalMenu;

import appeng.items.tools.powered.WirelessTerminalItem;
import net.minecraft.world.inventory.MenuType;

/**
 * AE2のワイヤレス端末と同じ仕組み(ネットワークツールで紐付け、アクセスポイントの範囲内、電力消費)の、図書館端末。
 * 使ったときの処理(範囲・電力の確認、メニューを開く)は、AE2のワイヤレス端末の標準のものをそのまま使う。
 */
public class WirelessLibraryTerminalItem extends WirelessTerminalItem {

    public WirelessLibraryTerminalItem(DoubleSupplier powerCapacity, Properties props) {
        super(powerCapacity, props);
    }

    @Override
    public MenuType<?> getMenuType() {
        return LibraryTerminalMenu.TYPE;
    }
}
