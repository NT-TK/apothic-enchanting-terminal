package com.example.apothicenchantingterminal.integration;

import com.example.apothicenchantingterminal.menu.LibraryTerminalMenu;

import appeng.menu.locator.ItemMenuHostLocator;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

/** AE2WTLib用の図書館端末。ユニバーサル端末(WUT)に組み込める。開き方は、AE2WTLibの標準のものを使う。 */
public class LibraryWTItem extends ItemWT {

    @Override
    public MenuType<?> getMenuType(ItemMenuHostLocator locator, Player player) {
        return LibraryTerminalMenu.TYPE;
    }
}
