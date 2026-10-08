package com.example.apothicenchantingterminal.integration;

import java.util.function.BiConsumer;

import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;
import net.minecraft.world.entity.player.Player;

/** AE2WTLibの端末定義に必要なホスト。図書館メニューは独自なので、中身は標準のままでよい。 */
public class LibraryWTMenuHost extends WTMenuHost {

    public LibraryWTMenuHost(ItemWT item, Player player, ItemMenuHostLocator locator, BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator, returnToMainMenu);
    }
}
