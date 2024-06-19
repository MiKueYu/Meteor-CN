/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.hud.elements;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.hud.*;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class PlayerRadarHud extends HudElement {
    public static final HudElementInfo<PlayerRadarHud> INFO = new HudElementInfo<>(Hud.GROUP, "玩家雷达", "显示在你视觉范围内的玩家.", PlayerRadarHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgScale = settings.createGroup("比例");
    private final SettingGroup sgBackground = settings.createGroup("背景");

    // General

    private final Setting<Integer> limit = sgGeneral.add(new IntSetting.Builder()
        .name("限制")
        .description("要显示的玩家的最大数量.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Boolean> distance = sgGeneral.add(new BoolSetting.Builder()
        .name("距离")
        .description("在玩家名字旁边显示到他们的距离.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> friends = sgGeneral.add(new BoolSetting.Builder()
        .name("显示好友")
        .description("是否显示好友.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("阴影")
        .description("在文本后面渲染阴影.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> primaryColor = sgGeneral.add(new ColorSetting.Builder()
        .name("主色")
        .description("主色.")
        .defaultValue(new SettingColor())
        .build()
    );

    private final Setting<SettingColor> secondaryColor = sgGeneral.add(new ColorSetting.Builder()
        .name("次要颜色")
        .description("次要颜色.")
        .defaultValue(new SettingColor(175, 175, 175))
        .build()
    );

    private final Setting<Alignment> alignment = sgGeneral.add(new EnumSetting.Builder<Alignment>()
        .name("对齐")
        .description("水平对齐.")
        .defaultValue(Alignment.Auto)
        .build()
    );

    private final Setting<Integer> border = sgGeneral.add(new IntSetting.Builder()
        .name("边框")
        .description("边框大小.")
        .defaultValue(0)
        .build()
    );

    // Scale

    private final Setting<Boolean> customScale = sgScale.add(new BoolSetting.Builder()
        .name("自定义比例")
        .description("应用自定义文本缩放比例,而不是全局缩放比例.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> scale = sgScale.add(new DoubleSetting.Builder()
        .name("比例")
        .description("自定义比例.")
        .visible(customScale::get)
        .defaultValue(1)
        .min(0.5)
        .sliderRange(0.5, 3)
        .build()
    );

    // Background

    private final Setting<Boolean> background = sgBackground.add(new BoolSetting.Builder()
        .name("背景")
        .description("显示背景.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgBackground.add(new ColorSetting.Builder()
        .name("背景颜色")
        .description("用于背景的颜色.")
        .visible(background::get)
        .defaultValue(new SettingColor(25, 25, 25, 50))
        .build()
    );

    private final List<AbstractClientPlayerEntity> players = new ArrayList<>();

    public PlayerRadarHud() {
        super(INFO);
    }

    @Override
    public void setSize(double width, double height) {
        super.setSize(width + border.get() * 2, height + border.get() * 2);
    }

    @Override
    protected double alignX(double width, Alignment alignment) {
        return box.alignX(getWidth() - border.get() * 2, width, alignment);
    }

    @Override
    public void tick(HudRenderer renderer) {
        double width = renderer.textWidth("玩家:", shadow.get(), getScale());
        double height = renderer.textHeight(shadow.get(), getScale());

        if (mc.world == null) {
            setSize(width, height);
            return;
        }

        for (PlayerEntity entity : getPlayers()) {
            if (entity.equals(mc.player)) continue;
            if (!friends.get() && Friends.get().isFriend(entity)) continue;

            String text = entity.getName().getString();
            if (distance.get()) text += String.format("(%sm)", Math.round(mc.getCameraEntity().distanceTo(entity)));

            width = Math.max(width, renderer.textWidth(text, shadow.get(), getScale()));
            height += renderer.textHeight(shadow.get(), getScale()) + 2;
        }

        setSize(width, height);
    }

    @Override
    public void render(HudRenderer renderer) {
        double y = this.y + border.get();

        if (background.get()) {
            renderer.quad(this.x, this.y, getWidth(), getHeight(), backgroundColor.get());
        }

        renderer.text("Players:", x + border.get() + alignX(renderer.textWidth("玩家:", shadow.get(), getScale()), alignment.get()), y, secondaryColor.get(), shadow.get(), getScale());

        if (mc.world == null) return;
        double spaceWidth = renderer.textWidth(" ", shadow.get(), getScale());

        for (PlayerEntity entity : getPlayers()) {
            if (entity.equals(mc.player)) continue;
            if (!friends.get() && Friends.get().isFriend(entity)) continue;

            String text = entity.getName().getString();
            Color color = PlayerUtils.getPlayerColor(entity, primaryColor.get());
            String distanceText = null;

            double width = renderer.textWidth(text, shadow.get(), getScale());
            if (distance.get()) width += spaceWidth;

            if (distance.get()) {
                distanceText = String.format("(%sm)", Math.round(mc.getCameraEntity().distanceTo(entity)));
                width += renderer.textWidth(distanceText, shadow.get(), getScale());
            }

            double x = this.x + border.get() + alignX(width, alignment.get());
            y += renderer.textHeight(shadow.get(), getScale()) + 2;

            x = renderer.text(text, x, y, color, shadow.get());
            if (distance.get()) renderer.text(distanceText, x + spaceWidth, y, secondaryColor.get(), shadow.get(), getScale());
        }
    }

    private List<AbstractClientPlayerEntity> getPlayers() {
        players.clear();
        players.addAll(mc.world.getPlayers());
        if (players.size() > limit.get()) players.subList(limit.get() - 1, players.size() - 1).clear();
        players.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(mc.getCameraEntity())));

        return players;
    }

    private double getScale() {
        return customScale.get() ? scale.get() : -1;
    }
}
