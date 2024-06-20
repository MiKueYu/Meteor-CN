/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class Sprint extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum Mode {
        Strict,
        Rage
    }

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("速度模式")
        .description("奔跑的模式.")
        .defaultValue(Mode.Strict)
        .build()
    );

    public final Setting<Boolean> jumpFix = sgGeneral.add(new BoolSetting.Builder()
        .name("跳跃修正")
        .description("是否修正跳跃方向.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Rage)
        .build()
    );

    private final Setting<Boolean> keepSprint = sgGeneral.add(new BoolSetting.Builder()
        .name("保持奔跑")
        .description("攻击实体后是否继续奔跑.")
        .defaultValue(false)
        .build()
    );

    public Sprint() {
        super(Categories.Movement, "奔跑", "自动奔跑.");
    }

    @Override
    public void onDeactivate() {
        mc.player.setSprinting(false);
    }

    private void sprint() {
        if (mc.player.getHungerManager().getFoodLevel() <= 6) return;
        mc.player.setSprinting(true);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        switch (mode.get()) {
            case Strict -> {
                if (mc.player.forwardSpeed > 0) sprint();
            }
            case Rage -> sprint();
        }
    }

    public boolean stopSprinting() {
        return !isActive() || !keepSprint.get();
    }
}
