package com.limbo2136.powerradar.client.ponder;

import net.createmod.ponder.foundation.instruction.FadeIntoSceneInstruction;
import net.minecraft.core.Direction;

/** Создаёт зомби с обычной Ponder-анимацией появления. */
public class CreateZombieInstruction extends FadeIntoSceneInstruction<ZombiePonderElement> {

    public CreateZombieInstruction(int fadeInTicks, Direction fadeInFrom, ZombiePonderElement element) {
        super(fadeInTicks, fadeInFrom, element);
    }

    @Override
    protected Class<ZombiePonderElement> getElementClass() {
        return ZombiePonderElement.class;
    }
}
