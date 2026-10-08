package com.example.apothicenchantingterminal.client;

import com.example.apothicenchantingterminal.ApothicEnchantingTerminal;
import com.example.apothicenchantingterminal.Registrations;
import com.example.apothicenchantingterminal.menu.LibraryTerminalMenu;
import appeng.init.client.InitScreens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = ApothicEnchantingTerminal.MOD_ID, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(Registrations.LIBRARY_MENU.get(), LibraryMonitorScreen::new);
        if (Registrations.WTLIB) {
            // AE2WTLibがある場合は、端末の切り替えボタンなどを持つ画面を使う(クラスは、ある場合だけ読み込む)
            com.example.apothicenchantingterminal.integration.WtlibClient.registerScreens(event);
        } else {
            InitScreens.register(event, LibraryTerminalMenu.TYPE, LibraryTerminalScreen::new, "/screens/aet/library_terminal.json");
        }
    }
}
