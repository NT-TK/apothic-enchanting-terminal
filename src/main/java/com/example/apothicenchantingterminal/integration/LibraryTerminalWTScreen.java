package com.example.apothicenchantingterminal.integration;

import com.example.apothicenchantingterminal.client.LibraryTerminalScreen;
import com.example.apothicenchantingterminal.menu.LibraryTerminalMenu;

import appeng.client.gui.style.ScreenStyle;
import de.mari_023.ae2wtlib.api.terminal.IUniversalTerminalCapable;
import de.mari_023.ae2wtlib.api.terminal.ItemWUT;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;
import de.mari_023.ae2wtlib.api.gui.ScrollingUpgradesPanel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * AE2WTLib対応の図書館の画面。ユニバーサル端末(WUT)から開いたときに、
 * 端末を切り替えるボタンと、切り替えのホットキーを使えるようにする。
 * (図書館モニターのパートから開いたときは、通常の画面と同じ)
 */
public class LibraryTerminalWTScreen extends LibraryTerminalScreen implements IUniversalTerminalCapable {
    private final ScrollingUpgradesPanel upgradesPanel;

    public LibraryTerminalWTScreen(LibraryTerminalMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        if (this.isUniversal()) {
            this.addToLeftToolbar(this.cycleTerminalButton());
        }
        // WTLIBの通常無線端末でも、標準端末と同じアップグレード列を表示する。
        // 端末切り替えボタンだけはWUTに限定する。
        this.upgradesPanel = this.menu.getHost() instanceof WTMenuHost
                ? this.addUpgradePanel(this.widgets, menu)
                : null;
    }

    /** ワイヤレス端末から開かれていて、それがユニバーサル端末か。 */
    private boolean isUniversal() {
        return this.menu.getHost() instanceof WTMenuHost host && host.getItemStack().getItem() instanceof ItemWUT;
    }

    @Override
    public WTMenuHost getHost() {
        return (WTMenuHost) this.menu.getHost();
    }

    @Override
    public void init() {
        super.init();
        if (this.upgradesPanel != null) {
            // WTLIB標準と同じく、ウィンドウの高さに応じて表示行数を決める。
            // スロットが収まらない場合だけアップグレード用シークバーが表示される。
            this.upgradesPanel.setMaxRows(Math.max(2, this.getVisibleRows()));
        }
    }

    @Override
    public void storeState() {
        // 保存する状態はない
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        // ワイヤレス端末から開いているときだけ、端末のホットキー(閉じる・切り替え)を処理する
        return this.menu.getHost() instanceof WTMenuHost && this.checkForTerminalKeys(keyCode, scanCode);
    }
}
