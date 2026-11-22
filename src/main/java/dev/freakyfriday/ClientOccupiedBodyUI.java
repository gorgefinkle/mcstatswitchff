package dev.freakyfriday;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Client presentation of the *occupied physical body*.
 *
 * Important: this never copies mob health to the authenticated player or
 * creates a replacement/fake entity. The real tracked Minecraft mob remains
 * server-authoritative for damage, health, and death. On exit both displays
 * automatically revert to the vanilla player's visuals.
 */
@EventBusSubscriber(modid=FreakyFriday.MODID,value=Dist.CLIENT)
public final class ClientOccupiedBodyUI {
    private ClientOccupiedBodyUI() {}

    private static LivingEntity occupied() {
        Minecraft mc=Minecraft.getInstance();
        int id=ClientPossession.occupiedEntityId();
        if (id<0 || mc.level==null || mc.player==null) return null;
        Entity candidate=mc.level.getEntity(id);
        return candidate instanceof LivingEntity living && !living.isRemoved()
            ? living : null;
    }

    /** Show the actual possessed Mob/player's health, not the host account's. */
    @SubscribeEvent
    public static void onHud(RenderGuiEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        if (mc.gui.screen()!=null) return;
        LivingEntity body=occupied();
        if (body==null) return;
        var graphics=event.getGuiGraphics();
        float max=Math.max(1.0f,body.getMaxHealth());
        float health=Math.max(0.0f,Math.min(body.getHealth(),max));
        int width=112;
        int x=graphics.guiWidth()/2-91;
        int y=graphics.guiHeight()-68;
        String title=body.getDisplayName().getString();
        if (title.length()>20) title=title.substring(0,20);
        String healthText=String.format(Locale.ROOT,"%.1f / %.1f HP",health,max);
        // Separate from the account-owned hunger/experience/armor. This
        // colored body-HP bar is not a decorative readout: it reads the
        // SynchedEntityData of the actual tracked LivingEntity each frame.
        graphics.fill(x-3,y-3,x+width+3,y+25,0xB9000000);
        graphics.text(mc.font,title,x,y,0xFFFFFFFF,true);
        graphics.text(mc.font,healthText,x,y+9,0xFFFFD3D3,true);
        graphics.fill(x,y+20,x+width,y+24,0xFF3C1010);
        int remaining=Math.round((width-2)*(health/max));
        if(remaining>0)
            graphics.fill(x+1,y+21,x+1+remaining,y+23,0xFFF04C55);
    }

    /**
     * Survival inventory renders the account's LocalPlayer portrait by
     * default, even while its camera and movement are swapped to a Mob.
     * Overlay ONLY the preview rectangle after vanilla's inventory content
     * is extracted, then render the real occupied entity's model there.
     * Keep the original inventory slots and identity/account untouched.
     */
    @SubscribeEvent
    public static void onInventory(ScreenEvent.Render.Foreground event) {
        if (!(event.getScreen() instanceof InventoryScreen inventory)) return;
        LivingEntity body=occupied();
        if (body==null) return;
        int x=inventory.getLeftPos()+26;
        int y=inventory.getTopPos()+8;
        var graphics=event.getGuiGraphics();
        // Opaque backing prevents the original Player render from showing
        // through the substituted model in the same preview viewport.
        graphics.fill(x,y,x+51,y+70,0xFF333333);
        graphics.outline(x,y,51,70,0xFF6F6F6F);
        // Short mobs need a larger portrait, giant mobs a much smaller one.
        float longest=Math.max(body.getBbHeight(),body.getBbWidth());
        int size=(int)Math.max(7,Math.min(46,30.0f/Math.max(0.6f,longest)));
        InventoryScreen.extractEntityInInventoryFollowsMouse(
            graphics,x,y,x+51,y+70,size,0.0f,
            event.getMouseX(),event.getMouseY(),body);
    }
}
