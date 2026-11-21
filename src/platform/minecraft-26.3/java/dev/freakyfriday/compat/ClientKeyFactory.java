package dev.freakyfriday.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

/** Minecraft 26.3 uses the SDL/keyboard constants instead of GLFW. */
public final class ClientKeyFactory {
    private ClientKeyFactory() {}
    public static KeyMapping create() {
        return new KeyMapping("key.freakyfriday.self",
            KeyConflictContext.IN_GAME, KeyModifier.CONTROL,
            InputConstants.Type.KEYBOARD, InputConstants.KEY_S, KeyMapping.Category.MISC);
    }
}
