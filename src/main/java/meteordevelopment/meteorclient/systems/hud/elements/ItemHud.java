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
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class ItemHud extends HudElement {
    public static final HudElementInfo<ItemHud> INFO = new HudElementInfo<>(Hud.GROUP, "物品", "显示物品数量.", ItemHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBackground = settings.createGroup("背景");

    // General

    private final Setting<Item> item = sgGeneral.add(new ItemSetting.Builder()
        .name("物品")
        .description("要显示的物品")
        .defaultValue(Items.TOTEM_OF_UNDYING)
        .build()
    );

    private final Setting<NoneMode> noneMode = sgGeneral.add(new EnumSetting.Builder<NoneMode>()
        .name("无模式")
        .description("当你的背包中没有指定物品时如何渲染该物品.")
        .defaultValue(NoneMode.HideCount)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("比例")
        .description("物品的缩放比例.")
        .defaultValue(2)
        .onChanged(aDouble -> calculateSize())
        .min(1)
        .sliderRange(1, 4)
        .build()
    );

    private final Setting<Integer> border = sgGeneral.add(new IntSetting.Builder()
        .name("边框")
        .description("边框大小.")
        .defaultValue(0)
        .onChanged(integer -> calculateSize())
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

    private ItemHud() {
        super(INFO);

        calculateSize();
    }

    @Override
    public void setSize(double width, double height) {
        super.setSize(width + border.get() * 2, height + border.get() * 2);
    }

    private void calculateSize() {
        setSize(17 * scale.get(), 17 * scale.get());
    }

    @Override
    public void render(HudRenderer renderer) {
        ItemStack itemStack = new ItemStack(item.get(), InvUtils.find(item.get()).count());

        if (noneMode.get() == NoneMode.HideItem && itemStack.isEmpty()) {
            if (isInEditor()) {
                renderer.line(x, y, x + getWidth(), y + getHeight(), Color.GRAY);
                renderer.line(x, y + getHeight(), x + getWidth(), y, Color.GRAY);
            }
        } else {
            renderer.post(() -> {
                double x = this.x + border.get();
                double y = this.y + border.get();

                render(renderer, itemStack, (int) x, (int) y);
            });
        }

        if (background.get()) renderer.quad(x, y, getWidth(), getHeight(), backgroundColor.get());
    }

    private void render(HudRenderer renderer, ItemStack itemStack, int x, int y) {
        if (noneMode.get() == NoneMode.HideItem) {
            renderer.item(itemStack, x, y, scale.get().floatValue(), true);
            return;
        }

        String countOverride = null;
        boolean resetToZero = false;

        if (itemStack.isEmpty()) {
            if (noneMode.get() == NoneMode.ShowCount)
                countOverride = "0";

            itemStack.setCount(1);
            resetToZero = true;
        }

        renderer.item(itemStack, x, y, scale.get().floatValue(), true, countOverride);

        if (resetToZero)
            itemStack.setCount(0);
    }

    public enum NoneMode {
        HideItem,
        HideCount,
        ShowCount;

        @Override
        public String toString() {
            return switch (this) {
                case HideItem -> "隐藏物品";
                case HideCount -> "隐藏数量";
                case ShowCount -> "显示数量";
            };
        }
    }
}
