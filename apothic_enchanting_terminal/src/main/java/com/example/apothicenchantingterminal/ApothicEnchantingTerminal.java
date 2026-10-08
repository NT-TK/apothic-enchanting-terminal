package com.example.apothicenchantingterminal;

import com.example.apothicenchantingterminal.integration.WtlibIntegration;
import com.example.apothicenchantingterminal.network.LibrarySyncPayload;
import com.example.apothicenchantingterminal.part.LibraryMonitorPart;

import appeng.api.config.Actionable;
import appeng.api.features.GridLinkables;
import appeng.api.parts.PartModels;
import appeng.items.tools.powered.WirelessTerminalItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import appeng.api.AECapabilities;
import dev.shadowsoffire.apothic_enchanting.Ench;
import dev.shadowsoffire.apothic_enchanting.library.EnchLibraryTile;
import com.example.apothicenchantingterminal.library.LibraryMEStorage;

@Mod(ApothicEnchantingTerminal.MOD_ID)
public class ApothicEnchantingTerminal {

    public static final String MOD_ID = "apothic_enchanting_terminal";

    public ApothicEnchantingTerminal(IEventBus modBus) {
        Registrations.ITEMS.register(modBus);
        Registrations.MENUS.register(modBus);
        modBus.addListener(this::registerPayloads);
        modBus.addListener(this::registerCapabilities);
        modBus.addListener(this::addToCreativeTab);

        PartModels.registerModels(LibraryMonitorPart.MODEL_OFF, LibraryMonitorPart.MODEL_ON);

        if (Registrations.WTLIB) {
            // AE2WTLibの端末として登録(アイテムの登録とネットワークツールでの紐付け登録も、この中で行う)
            WtlibIntegration.init();
        } else {
            modBus.addListener(this::commonSetup);
        }
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(AECapabilities.ME_STORAGE, Ench.Tiles.LIBRARY.get(),
            (be, side) -> new LibraryMEStorage(be));
        event.registerBlockEntity(AECapabilities.ME_STORAGE, Ench.Tiles.ENDER_LIBRARY.get(),
            (be, side) -> new LibraryMEStorage(be));
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // ネットワークツールでアクセスポイントに紐付けられるようにする(AE2のワイヤレス端末と同じ)
        event.enqueueWork(() -> GridLinkables.register(Registrations.libraryTerminal(), WirelessTerminalItem.LINKABLE_HANDLER));
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(LibrarySyncPayload.TYPE, LibrarySyncPayload.CODEC, LibrarySyncPayload::handle);
    }

    private void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(Registrations.LIBRARY_MONITOR.get());
            var terminal = (WirelessTerminalItem) Registrations.libraryTerminal();
            event.accept(terminal);
            ItemStack charged = new ItemStack(terminal);
            terminal.injectAEPower(charged, terminal.getAEMaxPower(charged), Actionable.MODULATE);
            event.accept(charged);
        }
    }
}
