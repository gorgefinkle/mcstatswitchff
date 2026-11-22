package dev.freakyfriday;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** Compact first-body + countdown indicator positioned above vanilla hearts. */
@EventBusSubscriber(modid=FreakyFriday.MODID,value=Dist.CLIENT)
public final class ClientSelectionHud {
    private ClientSelectionHud() {}
    private static String firstBody="";
    private static int remainingTicks;
    private static ClientLevel lastLevel;

    @SubscribeEvent
    public static void registerPayload(RegisterClientPayloadHandlersEvent event) {
        event.register(SelectionHudPayload.TYPE,(message,context)-> {
            if(!message.active()) {
                clear();
            } else {
                Minecraft mc=Minecraft.getInstance();
                firstBody=message.bodyName();
                remainingTicks=Math.max(0,Math.min(160,message.remainingTicks()));
                lastLevel=mc.level;
            }
        });
    }

    public static void clear() {
        firstBody="";
        remainingTicks=0;
        lastLevel=null;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if(remainingTicks<=0)return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null || mc.level!=lastLevel || !mc.player.isAlive()) {
            clear();
            return;
        }
        if(--remainingTicks<=0)clear();
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        if(remainingTicks<=0 || mc.player==null || mc.gui.screen()!=null)return;
        var g=event.getGuiGraphics();
        String timer=String.format(Locale.ROOT,"  %.1fs",remainingTicks/20.0);
        String shortName=firstBody;
        while(shortName.length()>1 && mc.font.width("First: "+shortName+timer)>168)
            shortName=shortName.substring(0,shortName.length()-1);
        if(!shortName.equals(firstBody))shortName+="…";
        String text="First: "+shortName+timer;
        int x=g.guiWidth()/2-91;
        int y=g.guiHeight()-66; // Above vanilla heart/health rows, not the hotbar.
        int width=Math.min(182,Math.max(100,mc.font.width(text)+10));
        g.fill(x-3,y-3,x+width,y+18,0xB9000000);
        g.text(mc.font,text,x,y,0xFFFFFFFF,true);
        g.fill(x,y+12,x+width-4,y+15,0xFF444444);
        int fill=(int)((width-4)*remainingTicks/160.0f);
        if(fill>0)g.fill(x,y+12,x+fill,y+15,0xFF67C9F4);
    }
}
