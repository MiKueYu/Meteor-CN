/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.hud.elements;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.TickRate;

public class LagNotifierHud extends HudElement {
    public static final HudElementInfo<LagNotifierHud> INFO = new HudElementInfo<>(Hud.GROUP, "服务器延迟通知器", "显示服务器是否卡顿（以滴答声为单位）.", LagNotifierHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgScale = settings.createGroup("比例");
    private final SettingGroup sgBackground = settings.createGroup("背景");

    // General

    private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("阴影")
        .description("文本阴影.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> textColor = sgGeneral.add(new ColorSetting.Builder()
        .name("文本颜色")
        .description("文本的颜色.")
        .defaultValue(new SettingColor())
        .build()
    );

    private final Setting<SettingColor> color1 = sgGeneral.add(new ColorSetting.Builder()
        .name("颜色-1")
        .description("第一色.")
        .defaultValue(new SettingColor(255, 255, 5))
        .build()
    );

    private final Setting<SettingColor> color2 = sgGeneral.add(new ColorSetting.Builder()
        .name("颜色-2")
        .description("第二色.")
        .defaultValue(new SettingColor(235, 158, 52))
        .build()
    );

    private final Setting<SettingColor> color3 = sgGeneral.add(new ColorSetting.Builder()
        .name("颜色-3")
        .description("第三色.")
        .defaultValue(new SettingColor(225, 45, 45))
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

    public LagNotifierHud() {
        super(INFO);
    }

    @Override
    public void setSize(double width, double height) {
        super.setSize(width + border.get() * 2, height + border.get() * 2);
    }

    @Override
    public void render(HudRenderer renderer) {
        if (background.get()) {
            renderer.quad(this.x, this.y, getWidth(), getHeight(), backgroundColor.get());
        }

        if (isInEditor()) {
            render(renderer, "4.3", color3.get());
            return;
        }

        float timeSinceLastTick = TickRate.INSTANCE.getTimeSinceLastTick();

        if (timeSinceLastTick >= 1f) {
            Color color;

            if (timeSinceLastTick > 10) color = color3.get();
            else if (timeSinceLastTick > 3) color = color2.get();
            else color = color1.get();

            render(renderer, String.format("%.1f", timeSinceLastTick), color);
        }
    }

    private void render(HudRenderer renderer, String right, Color rightColor) {
        double x = this.x + border.get();
        double y = this.y + border.get();

        double x2 = renderer.text("距离上次滴答的时间 ", x, y, textColor.get(), shadow.get(), getScale());
        x2 = renderer.text(right, x2, y, rightColor, shadow.get(), getScale());

        setSize(x2 - x, renderer.textHeight(shadow.get(), getScale()));
    }

    private double getScale() {
        return customScale.get() ? scale.get() : -1;
    }
}
