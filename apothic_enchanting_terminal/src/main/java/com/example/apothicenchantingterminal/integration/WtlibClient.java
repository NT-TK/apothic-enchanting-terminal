package com.example.apothicenchantingterminal.integration;

import com.example.apothicenchantingterminal.menu.LibraryTerminalMenu;

import appeng.init.client.InitScreens;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** AE2WTLibがあるときだけ使う、画面の登録。 */
public final class WtlibClient {

    private WtlibClient() {}

    public static void registerScreens(RegisterMenuScreensEvent event) {
        InitScreens.register(event, LibraryTerminalMenu.TYPE, LibraryTerminalWTScreen::new, "/screens/aet/library_terminal_wt.json");
    }
}
