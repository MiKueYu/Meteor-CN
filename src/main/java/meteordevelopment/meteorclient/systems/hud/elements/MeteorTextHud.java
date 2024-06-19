/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.hud.elements;

import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;

public class MeteorTextHud {
    public static final HudElementInfo<TextHud> INFO = new HudElementInfo<>(Hud.GROUP, "文本", "使用 Starscript 显示任意文本.", MeteorTextHud::create);

    public static final HudElementInfo<TextHud>.Preset FPS;
    public static final HudElementInfo<TextHud>.Preset TPS;
    public static final HudElementInfo<TextHud>.Preset PING;
    public static final HudElementInfo<TextHud>.Preset SPEED;
    public static final HudElementInfo<TextHud>.Preset GAME_MODE;
    public static final HudElementInfo<TextHud>.Preset DURABILITY;
    public static final HudElementInfo<TextHud>.Preset POSITION;
    public static final HudElementInfo<TextHud>.Preset OPPOSITE_POSITION;
    public static final HudElementInfo<TextHud>.Preset LOOKING_AT;
    public static final HudElementInfo<TextHud>.Preset LOOKING_AT_WITH_POSITION;
    public static final HudElementInfo<TextHud>.Preset BREAKING_PROGRESS;
    public static final HudElementInfo<TextHud>.Preset SERVER;
    public static final HudElementInfo<TextHud>.Preset BIOME;
    public static final HudElementInfo<TextHud>.Preset WORLD_TIME;
    public static final HudElementInfo<TextHud>.Preset REAL_TIME;
    public static final HudElementInfo<TextHud>.Preset ROTATION;
    public static final HudElementInfo<TextHud>.Preset MODULE_ENABLED;
    public static final HudElementInfo<TextHud>.Preset MODULE_ENABLED_WITH_INFO;
    public static final HudElementInfo<TextHud>.Preset WATERMARK;
    public static final HudElementInfo<TextHud>.Preset BARITONE;

    static {
        addPreset("空", null);
        FPS = addPreset("FPS", "FPS: #1{fps}", 0);
        TPS = addPreset("TPS", "TPS: #1{round(server.tps, 1)}");
        PING = addPreset("Ping", "Ping: #1{ping}");
        SPEED = addPreset("速度", "速度：#1{round(player.speed, 1)}", 0);
        GAME_MODE = addPreset("游戏模式", "游戏模式：#1{player.gamemode}", 0);
        DURABILITY = addPreset("耐久", "耐久: #1{player.hand_or_offhand.durability}");
        POSITION = addPreset("位置", "位置: #1{floor(camera.pos.x)}, {floor(camera.pos.y)}, {floor(camera.pos.z)}", 0);
        OPPOSITE_POSITION = addPreset("反向位置", "{player.opposite_dimension != \"End\" ? player.opposite_dimension + \":\" : \"\"} #1{player.opposite_dimension != \"End\" ? \"\" + floor(camera.opposite_dim_pos.x) + \", \" + floor(camera.opposite_dim_pos.y) + \", \" + floor(camera.opposite_dim_pos.z) : \"\"}", 0);
        LOOKING_AT = addPreset("正在查看", "正在查看: #1{crosshair_target.value}", 0);
        LOOKING_AT_WITH_POSITION = addPreset("查看物体的位置", "查看物体的位置: #1{crosshair_target.value} {crosshair_target.type != \"miss\" ? \"(\" + \"\" + floor(crosshair_target.value.pos.x) + \", \" + floor(crosshair_target.value.pos.y) + \", \" + floor(crosshair_target.value.pos.z) + \")\" : \"\"}", 0);
        BREAKING_PROGRESS = addPreset("破坏进度", "破坏进度: #1{round(player.breaking_progress * 100)}%", 0);
        SERVER = addPreset("服务器", "服务器: #1{server}");
        BIOME = addPreset("生物群落", "生物群落: #1{player.biome}", 0);
        WORLD_TIME = addPreset("世界时间", "时间: #1{server.time}");
        REAL_TIME = addPreset("现实时间", "时间: #1{time}");
        ROTATION = addPreset("方向", "{camera.direction} #1({round(camera.yaw, 1)}, {round(camera.pitch, 1)})", 0);
        MODULE_ENABLED = addPreset("启用的模块", "杀戮光环: {meteor.is_module_active(\"kill-aura\") ? #2 \"开启\" : #3 \"关闭\"}", 0);
        MODULE_ENABLED_WITH_INFO = addPreset("启用的模块,并提供信息", "杀戮光环: {meteor.is_module_active(\"kill-aura\") ? #2 \"开启\" : #3 \"关闭\"} #1{meteor.get_module_info(\"kill-aura\")}", 0);
        WATERMARK = addPreset("水印", "{meteor.name} #1{meteor.version}");
        BARITONE = addPreset("Baritone", "Baritone: #1{baritone.process_name}");
    }

    private static TextHud create() {
        return new TextHud(INFO);
    }

    private static HudElementInfo<TextHud>.Preset addPreset(String title, String text, int updateDelay) {
        return INFO.addPreset(title, textHud -> {
            if (text != null) textHud.text.set(text);
            if (updateDelay != -1) textHud.updateDelay.set(updateDelay);
        });
    }

    private static HudElementInfo<TextHud>.Preset addPreset(String title, String text) {
        return addPreset(title, text, -1);
    }
}
