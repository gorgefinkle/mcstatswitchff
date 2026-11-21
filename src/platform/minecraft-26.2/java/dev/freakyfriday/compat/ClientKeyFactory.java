package dev.freakyfriday.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/** Minecraft 26.2 GLFW keyboard mapping; modifier and key remain remappable. */
public final class ClientKeyFactory {
    private ClientKeyFactory() {}
    public static KeyMapping create() {
        return new KeyMapping("key.freakyfriday.self",
            KeyConflictContext.IN_GAME, KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_S, KeyMapping.Category.MISC);
    }
}
