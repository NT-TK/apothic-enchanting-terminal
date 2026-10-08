package com.example.apothicenchantingterminal.part;

import com.example.apothicenchantingterminal.ApothicEnchantingTerminal;
import com.example.apothicenchantingterminal.menu.LibraryTerminalMenu;

import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.PartModel;
import appeng.parts.reporting.AbstractDisplayPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** AE2のターミナルと同じ薄いパネル。右クリックで、ネットワーク内の最初の図書館を開く。 */
public class LibraryMonitorPart extends AbstractDisplayPart {

    public static final ResourceLocation MODEL_OFF = ResourceLocation.fromNamespaceAndPath(ApothicEnchantingTerminal.MOD_ID, "part/library_monitor_off");
    public static final ResourceLocation MODEL_ON = ResourceLocation.fromNamespaceAndPath(ApothicEnchantingTerminal.MOD_ID, "part/library_monitor_on");

    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    public LibraryMonitorPart(IPartItem<?> partItem) {
        super(partItem, true);
    }

    @Override
    public IPartModel getStaticModels() {
        return this.selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!super.onUseWithoutItem(player, pos) && !this.isClientSide()) {
            MenuOpener.open(LibraryTerminalMenu.TYPE, player, MenuLocators.forPart(this));
        }
        return true;
    }
}
