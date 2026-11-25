package dev.freakyfriday;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Keeps account-owned Player inventory presentation out of an occupied-body
 * session. Equipment shown here belongs to the actual physical Mob being
 * controlled; the original ServerPlayer inventory is neither copied nor
 * mutated and becomes visible again when the local Player is the camera body.
 */
@EventBusSubscriber(modid="freakyfriday",value=Dist.CLIENT)
public final class ClientBodyInventoryUI {
    private static final EquipmentSlot[] BODY_SLOTS={
        EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND,
        EquipmentSlot.HEAD,EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,EquipmentSlot.FEET
    };

    private ClientBodyInventoryUI() {}

    private static Mob occupiedMob() {
        Minecraft minecraft=Minecraft.getInstance();
        if(minecraft.player==null)return null;
        Entity camera=minecraft.getCameraEntity();
        return camera instanceof Mob mob?mob:null;
    }

    @SubscribeEvent
    public static void onHotbarScroll(InputEvent.MouseScrollingEvent event) {
        if(occupiedMob()!=null)event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onInventoryScreen(ScreenEvent.Opening event) {
        if(occupiedMob()!=null&&event.getNewScreen() instanceof InventoryScreen)
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void hideAccountHotbar(RenderGuiLayerEvent.Pre event) {
        if(occupiedMob()!=null
                &&event.getName().equals(VanillaGuiLayers.HOTBAR))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void drawOccupiedBodyEquipment(RenderGuiEvent.Post event) {
        Mob mob=occupiedMob();
        if(mob==null)return;
        Minecraft minecraft=Minecraft.getInstance();
        GuiGraphicsExtractor graphics=event.getGuiGraphics();
        int width=graphics.guiWidth();
        int height=graphics.guiHeight();
        int slotSize=18;
        int startX=width/2-(BODY_SLOTS.length*slotSize)/2;
        int y=height-42;
        graphics.centeredText(minecraft.font,"BODY EQUIPMENT",width/2,y-11,0xFFFFFFFF);
        for(int i=0;i<BODY_SLOTS.length;i++) {
            int x=startX+i*slotSize;
            graphics.fill(x-1,y-1,x+17,y+17,0xAA101010);
            graphics.fill(x,y,x+16,y+16,0xFF555555);
            ItemStack stack=mob.getItemBySlot(BODY_SLOTS[i]);
            if(!stack.isEmpty()) {
                graphics.item(stack,x,y);
                graphics.itemDecorations(minecraft.font,stack,x,y);
            }
        }
    }
}
