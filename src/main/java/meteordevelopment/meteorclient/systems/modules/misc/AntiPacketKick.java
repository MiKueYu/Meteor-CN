/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

public class AntiPacketKick extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Boolean> catchExceptions = sgGeneral.add(new BoolSetting.Builder()
        .name("捕获异常")
        .description("丢弃损坏的数据包.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> logExceptions = sgGeneral.add(new BoolSetting.Builder()
        .name("记录异常")
        .description("记录捕获的异常.")
        .defaultValue(false)
        .visible(catchExceptions::get)
        .build()
    );

    public AntiPacketKick() {
        super(Categories.Misc, "防数据包踢", "防止你因为大数据包而被断开连接.");
    }

    public boolean catchExceptions() {
        return isActive() && catchExceptions.get();
    }
}
