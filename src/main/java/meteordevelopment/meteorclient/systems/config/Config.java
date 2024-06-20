/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.config;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.renderer.Fonts;
import meteordevelopment.meteorclient.renderer.text.FontFace;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.System;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class Config extends System<Config> {
    public final Settings settings = new Settings();

    private final SettingGroup sgVisual = settings.createGroup("视觉");
    private final SettingGroup sgChat = settings.createGroup("聊天");
    private final SettingGroup sgMisc = settings.createGroup("杂项");

    // Visual

    public final Setting<Boolean> customFont = sgVisual.add(new BoolSetting.Builder()
        .name("自定义字体(开启将无法渲染中文)")
        .description("使用自定义字体.")
        .defaultValue(false)
        .build()
    );

    public final Setting<FontFace> font = sgVisual.add(new FontFaceSetting.Builder()
        .name("字体")
        .description("要使用的自定义字体.")
        .visible(customFont::get)
        .onChanged(Fonts::load)
        .build()
    );

    public final Setting<Double> rainbowSpeed = sgVisual.add(new DoubleSetting.Builder()
        .name("彩虹速度")
        .description("全局彩虹速度.")
        .defaultValue(0.5)
        .range(0, 10)
        .sliderMax(5)
        .build()
    );

    public final Setting<Boolean> titleScreenCredits = sgVisual.add(new BoolSetting.Builder()
        .name("主菜单制作人员表")
        .description("在主菜单上显示制作人员名单")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> titleScreenSplashes = sgVisual.add(new BoolSetting.Builder()
        .name("主菜单启动信息")
        .description("在主菜单上显示Meteor的启动文字")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> customWindowTitle = sgVisual.add(new BoolSetting.Builder()
        .name("自定义窗口标题")
        .description("在窗口标题栏显示自定义文本.")
        .defaultValue(false)
        .onModuleActivated(setting -> mc.updateWindowTitle())
        .onChanged(value -> mc.updateWindowTitle())
        .build()
    );

    public final Setting<String> customWindowTitleText = sgVisual.add(new StringSetting.Builder()
        .name("窗口标题文本")
        .description("窗口标题中显示的文本.")
        .visible(customWindowTitle::get)
        .defaultValue("Minecraft {mc_version} - {meteor.name} {meteor.version}")
        .onChanged(value -> mc.updateWindowTitle())
        .build()
    );

    public final Setting<SettingColor> friendColor = sgVisual.add(new ColorSetting.Builder()
        .name("好友颜色")
        .description("显示好友的颜色.")
        .defaultValue(new SettingColor(0, 255, 180))
        .build()
    );

    // Chat

    public final Setting<String> prefix = sgChat.add(new StringSetting.Builder()
        .name("前缀")
        .description("前缀.")
        .defaultValue(".")
        .build()
    );

    public final Setting<Boolean> chatFeedback = sgChat.add(new BoolSetting.Builder()
        .name("聊天反馈")
        .description("Meteor执行特定动作时发送聊天反馈.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> deleteChatFeedback = sgChat.add(new BoolSetting.Builder()
        .name("删除聊天反馈")
        .description("删除之前重复的聊天反馈,保持聊天清晰.")
        .visible(chatFeedback::get)
        .defaultValue(true)
        .build()
    );

    // Misc

    public final Setting<Integer> rotationHoldTicks = sgMisc.add(new IntSetting.Builder()
        .name("旋转保持")
        .description("长按以在不发送数据包时保持服务器端旋转.")
        .defaultValue(4)
        .build()
    );

    public final Setting<Boolean> useTeamColor = sgMisc.add(new BoolSetting.Builder()
        .name("使用团队颜色")
        .description("使用玩家的队伍颜色渲染 ESP 和追踪线等内容.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> moduleSearchCount = sgMisc.add(new IntSetting.Builder()
        .name("模块搜索数量")
        .description("模块搜索栏中显示的模块和设置数量.")
        .defaultValue(8)
        .min(1).sliderMax(12)
        .build()
    );

    public final Setting<Boolean> heuristicCombatUtils = sgMisc.add(new BoolSetting.Builder()
            .name("启发式伤害工具")
            .description(" 通过增加计算时间来提高战斗相关计算的准确性,但会牺牲帧率.")
            .defaultValue(true)
            .build()
    );

    public final Setting<Integer> heuristicDepth = sgMisc.add(new IntSetting.Builder()
            .name("启发式深度")
            .description("以指数级增长的额外计算时间.")
            .defaultValue(4)
            .min(2)
            .sliderRange(2, 5)
            .visible(heuristicCombatUtils::get)
            .build()
    );

    public List<String> dontShowAgainPrompts = new ArrayList<>();

    public Config() {
        super("配置");
    }

    public static Config get() {
        return Systems.get(Config.class);
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();

        tag.putString("版本", MeteorClient.VERSION.toString());
        tag.put("设置", settings.toTag());
        tag.put("不再显示提示", listToTag(dontShowAgainPrompts));

        return tag;
    }

    @Override
    public Config fromTag(NbtCompound tag) {
        if (tag.contains("设置")) settings.fromTag(tag.getCompound("设置"));
        if (tag.contains("不再显示提示")) dontShowAgainPrompts = listFromTag(tag, "dontShowAgainPrompts");

        return this;
    }

    private NbtList listToTag(List<String> list) {
        NbtList nbt = new NbtList();
        for (String item : list) nbt.add(NbtString.of(item));
        return nbt;
    }

    private List<String> listFromTag(NbtCompound tag, String key) {
        List<String> list = new ArrayList<>();
        for (NbtElement item : tag.getList(key, 8)) list.add(item.asString());
        return list;
    }
}
