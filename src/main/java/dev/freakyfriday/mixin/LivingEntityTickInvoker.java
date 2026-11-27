package dev.freakyfriday.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Invokes the exact LivingEntity base tick on a physical Mob body. */
@Mixin(LivingEntity.class)
public interface LivingEntityTickInvoker {
    @Invoker("tick")
    void freakyfriday$invokeLivingEntityTick();
}
