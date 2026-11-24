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
        dev.freakyfriday.mind.DetachedProbeGameTests.FUNCTIONS.register(modBus);
        dev.freakyfriday.mind.PlayerCommandRouteGameTests.FUNCTIONS.register(modBus);
        dev.freakyfriday.mind.PlayerMobGameTests.FUNCTIONS.register(modBus);
        dev.freakyfriday.mind.PlayerPlayerGameTests.FUNCTIONS.register(modBus);
        dev.freakyfriday.mind.NativeMindSenseGameTests.FUNCTIONS.register(modBus);
        dev.freakyfriday.mind.ReciprocalMobWorkerGameTests.FUNCTIONS.register(modBus);
        modBus.addListener(PlatformHooks::addBookToCreativeTab);
        modBus.addListener(PossessionNetwork::register);
        modBus.addListener(dev.freakyfriday.mind.NativeAiWorkerServer::registerChunkTickets);
        NeoForge.EVENT_BUS.register(FFCommands.class);
        NeoForge.EVENT_BUS.register(dev.freakyfriday.mind.PlayerCommandBodyRouting.class);
        NeoForge.EVENT_BUS.register(dev.unseenaddon.UnseenAddonCommandFixture.class);
        NeoForge.EVENT_BUS.register(FFServerSmoke.class);
        NeoForge.EVENT_BUS.register(dev.freakyfriday.mind.NativeAiWorkerServer.class);
        NeoForge.EVENT_BUS.register(BookInteractions.class);
        NeoForge.EVENT_BUS.register(dev.freakyfriday.mind.PlayerMobPossession.class);
        NeoForge.EVENT_BUS.register(dev.freakyfriday.mind.NativeMindWorkerSupervisor.class);
        NeoForge.EVENT_BUS.register(dev.freakyfriday.mind.SelectionHudSync.class);
        NeoForge.EVENT_BUS.register(dev.freakyfriday.mind.PlayerPlayerExchange.class);
        NeoForge.EVENT_BUS.register(dev.freakyfriday.mind.LiveBodyProbe.class);
        NeoForge.EVENT_BUS.register(dev.freakyfriday.mind.DetachedHostMirrorProbe.class);
    }
}
