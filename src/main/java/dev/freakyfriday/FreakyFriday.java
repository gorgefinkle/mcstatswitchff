package dev.freakyfriday;

import dev.freakyfriday.compat.PlatformHooks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(FreakyFriday.MODID)
public final class FreakyFriday {
    public static final String MODID = "freakyfriday";
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredItem<FreakyFridayBookItem> BOOK =
        ITEMS.registerItem("book", props -> new FreakyFridayBookItem(props.stacksTo(1)));

    public FreakyFriday(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(PlatformHooks::addBookToCreativeTab);
        modBus.addListener(PortableAttributes::addToEntityTypes);
        NeoForge.EVENT_BUS.register(FFCommands.class);
        NeoForge.EVENT_BUS.register(BookInteractions.class);
        NeoForge.EVENT_BUS.register(dev.freakyfriday.mind.LiveBodyProbe.class);
        NeoForge.EVENT_BUS.register(dev.freakyfriday.mind.DetachedHostMirrorProbe.class);
    }
}
