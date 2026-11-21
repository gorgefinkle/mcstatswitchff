package dev.freakyfriday.compat;

import dev.freakyfriday.FreakyFriday;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Platform API seam: Minecraft/NeoForge-version-dependent UI integration.
 * The JVM compiles only ONE platform source tree, chosen by the build profile.
 * Do not move category exchange logic here.
 */
public final class PlatformHooks {
    private PlatformHooks() {}

    public static void addBookToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().identifier().equals(
                Identifier.withDefaultNamespace("tools_and_utilities"))) {
            event.accept(FreakyFriday.BOOK);
        }
    }
}
