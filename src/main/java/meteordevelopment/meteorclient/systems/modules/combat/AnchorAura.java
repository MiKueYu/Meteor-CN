/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.*;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public class AnchorAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlace = settings.createGroup("放置");
    private final SettingGroup sgBreak = settings.createGroup("破坏");
    private final SettingGroup sgPause = settings.createGroup("暂停");
    private final SettingGroup sgRender = settings.createGroup("渲染");

    // General

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("目标范围")
        .description("玩家被锁定时的半径.")
        .defaultValue(4)
        .min(0)
        .sliderMax(5)
        .build()
    );

    private final Setting<SortPriority> targetPriority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("目标优先级")
        .description("如何选择要锁定的玩家.")
        .defaultValue(SortPriority.LowestHealth)
        .build()
    );

    private final Setting<RotationMode> rotationMode = sgGeneral.add(new EnumSetting.Builder<RotationMode>()
        .name("旋转模式")
        .description("在服务器端旋转你的模式.")
        .defaultValue(RotationMode.Both)
        .build()
    );

    private final Setting<Double> maxDamage = sgGeneral.add(new DoubleSetting.Builder()
        .name("最大自伤")
        .description("允许的最大自伤.")
        .defaultValue(8)
        .build()
    );

    private final Setting<Double> minHealth = sgGeneral.add(new DoubleSetting.Builder()
        .name("最低生命值")
        .description("重生锚光环生效所需的最低生命值.")
        .defaultValue(15)
        .build()
    );

    // Place

    private final Setting<Boolean> place = sgPlace.add(new BoolSetting.Builder()
        .name("放置")
        .description("允许重生锚光环放置锚点.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> placeDelay = sgPlace.add(new IntSetting.Builder()
        .name("放置间隔")
        .description("放置锚点之间的tick间隔.")
        .defaultValue(2)
        .range(0, 10)
        .visible(place::get)
        .build()
    );

    private final Setting<Safety> placeMode = sgPlace.add(new EnumSetting.Builder<Safety>()
        .name("放置模式")
        .description("允许锚点在你附近放置的方式.")
        .defaultValue(Safety.Safe)
        .visible(place::get)
        .build()
    );

    private final Setting<Double> placeRange = sgPlace.add(new DoubleSetting.Builder()
        .name("放置范围")
        .description("锚点放置的半径.")
        .defaultValue(5)
        .min(0)
        .sliderMax(5)
        .visible(place::get)
        .build()
    );

    private final Setting<PlaceMode> placePositions = sgPlace.add(new EnumSetting.Builder<PlaceMode>()
        .name("放置位置")
        .description("锚点将在实体上的位置.")
        .defaultValue(PlaceMode.AboveAndBelow)
        .visible(place::get)
        .build()
    );

    // Break

    private final Setting<Integer> breakDelay = sgBreak.add(new IntSetting.Builder()
        .name("破坏间隔")
        .description("破坏锚点之间的tick间隔.")
        .defaultValue(10)
        .range(0, 10)
        .build()
    );

    private final Setting<Safety> breakMode = sgBreak.add(new EnumSetting.Builder<Safety>()
        .name("破坏模式")
        .description("允许锚点在你附近被破坏的方式.")
        .defaultValue(Safety.Safe)
        .build()
    );

    private final Setting<Double> breakRange = sgBreak.add(new DoubleSetting.Builder()
        .name("破坏范围")
        .description("锚点被破坏的半径.")
        .defaultValue(5)
        .min(0)
        .sliderMax(5)
        .build()
    );

    // Pause

    private final Setting<Boolean> pauseOnEat = sgPause.add(new BoolSetting.Builder()
        .name("吃东西时暂停")
        .description("在吃东西时暂停.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> pauseOnDrink = sgPause.add(new BoolSetting.Builder()
        .name("喝药水时暂停")
        .description("在喝药水时暂停.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> pauseOnMine = sgPause.add(new BoolSetting.Builder()
        .name("挖掘时暂停")
        .description("在挖掘方块时暂停.")
        .defaultValue(false)
        .build()
    );

    // Render

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("形状模式")
        .description("形状的渲染方式.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Boolean> renderPlace = sgRender.add(new BoolSetting.Builder()
        .name("渲染放置")
        .description("渲染它放置锚点的方块.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> placeSideColor = sgRender.add(new ColorSetting.Builder()
        .name("放置侧面颜色")
        .description("要放置的位置的侧面颜色.")
        .defaultValue(new SettingColor(255, 0, 0, 75))
        .visible(renderPlace::get)
        .build()
    );

    private final Setting<SettingColor> placeLineColor = sgRender.add(new ColorSetting.Builder()
        .name("放置线颜色")
        .description("要放置的位置的线颜色.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .visible(renderPlace::get)
        .build()
    );

    private final Setting<Boolean> renderBreak = sgRender.add(new BoolSetting.Builder()
        .name("渲染破坏")
        .description("渲染它破坏锚点的方块.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> breakSideColor = sgRender.add(new ColorSetting.Builder()
        .name("破坏侧面颜色")
        .description("要破坏的锚点的侧面颜色.")
        .defaultValue(new SettingColor(255, 0, 0, 75))
        .visible(renderBreak::get)
        .build()
    );

    private final Setting<SettingColor> breakLineColor = sgRender.add(new ColorSetting.Builder()
        .name("破坏线颜色")
        .description("要破坏的锚点的线颜色.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .visible(renderBreak::get)
        .build()
    );

    private int placeDelayLeft;
    private int breakDelayLeft;
    private PlayerEntity target;
    private final BlockPos.Mutable mutable = new BlockPos.Mutable();

    public AnchorAura() {
        super(Categories.Combat, "重生锚光环", "自动放置和破坏重生锚来伤害实体.");
    }

    @Override
    public void onActivate() {
        placeDelayLeft = 0;
        breakDelayLeft = 0;
        target = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world.getDimension().respawnAnchorWorks()) {
            error("你在下界...禁用.");
            toggle();
            return;
        }

        if (PlayerUtils.shouldPause(pauseOnMine.get(), pauseOnEat.get(), pauseOnDrink.get())) return;
        if (EntityUtils.getTotalHealth(mc.player) <= minHealth.get()) return;

        if (TargetUtils.isBadTarget(target, targetRange.get())) {
            target = TargetUtils.getPlayerTarget(targetRange.get(), targetPriority.get());
            if (TargetUtils.isBadTarget(target, targetRange.get())) return;
        }

        FindItemResult anchor = InvUtils.findInHotbar(Items.RESPAWN_ANCHOR);
        FindItemResult glowStone = InvUtils.findInHotbar(Items.GLOWSTONE);

        if (!anchor.found() || !glowStone.found()) return;

        if (breakDelayLeft >= breakDelay.get()) {
            BlockPos breakPos = findBreakPos(target.getBlockPos());
            if (breakPos != null) {
                breakDelayLeft = 0;

                if (rotationMode.get() == RotationMode.Both || rotationMode.get() == RotationMode.Break) {
                    BlockPos immutableBreakPos = breakPos.toImmutable();
                    Rotations.rotate(Rotations.getYaw(breakPos), Rotations.getPitch(breakPos), 50, () -> breakAnchor(immutableBreakPos, anchor, glowStone));
                } else breakAnchor(breakPos, anchor, glowStone);
            }
        }

        if (placeDelayLeft >= placeDelay.get() && place.get()) {
            BlockPos placePos = findPlacePos(target.getBlockPos());

            if (placePos != null) {
                placeDelayLeft = 0;
                BlockUtils.place(placePos.toImmutable(), anchor, (rotationMode.get() == RotationMode.Place || rotationMode.get() == RotationMode.Both), 50);
            }
        }

        placeDelayLeft++;
        breakDelayLeft++;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (target == null) return;

        if (renderPlace.get()) {
            BlockPos placePos = findPlacePos(target.getBlockPos());
            if (placePos == null) return;

            event.renderer.box(placePos, placeSideColor.get(), placeLineColor.get(), shapeMode.get(), 0);
        }

        if (renderBreak.get()) {
            BlockPos breakPos = findBreakPos(target.getBlockPos());
            if (breakPos == null) return;

            event.renderer.box(breakPos, breakSideColor.get(), breakLineColor.get(), shapeMode.get(), 0);
        }
    }

    @Nullable
    private BlockPos findPlacePos(BlockPos targetPlacePos) {
        switch (placePositions.get()) {
            case All -> {
                if (isValidPlace(targetPlacePos, 0, -1, 0)) return mutable;
                else if (isValidPlace(targetPlacePos, 0, 2, 0)) return mutable;
                else if (isValidPlace(targetPlacePos, 1, 0, 0)) return mutable;
                else if (isValidPlace(targetPlacePos, -1, 0, 0)) return mutable;
                else if (isValidPlace(targetPlacePos, 0, 0, 1)) return mutable;
                else if (isValidPlace(targetPlacePos, 0, 0, -1)) return mutable;
                else if (isValidPlace(targetPlacePos, 1, 1, 0)) return mutable;
                else if (isValidPlace(targetPlacePos, -1, -1, 0)) return mutable;
                else if (isValidPlace(targetPlacePos, 0, 1, 1)) return mutable;
                else if (isValidPlace(targetPlacePos, 0, 0, -1)) return mutable;
            }
            case Above -> {
                if (isValidPlace(targetPlacePos, 0, 2, 0)) return mutable;
            }
            case AboveAndBelow -> {
                if (isValidPlace(targetPlacePos, 0, -1, 0)) return mutable;
                else if (isValidPlace(targetPlacePos, 0, 2, 0)) return mutable;
            }
            case Around -> {
                if (isValidPlace(targetPlacePos, 0, 0, -1)) return mutable;
                else if (isValidPlace(targetPlacePos, 1, 0, 0)) return mutable;
                else if (isValidPlace(targetPlacePos, -1, 0, 0)) return mutable;
                else if (isValidPlace(targetPlacePos, 0, 0, 1)) return mutable;
            }
        }
        return null;
    }

    @Nullable
    private BlockPos findBreakPos(BlockPos targetPos) {
        if (isValidBreak(targetPos, 0, -1, 0)) return mutable;
        else if (isValidBreak(targetPos, 0, 2, 0)) return mutable;
        else if (isValidBreak(targetPos, 1, 0, 0)) return mutable;
        else if (isValidBreak(targetPos, -1, 0, 0)) return mutable;
        else if (isValidBreak(targetPos, 0, 0, 1)) return mutable;
        else if (isValidBreak(targetPos, 0, 0, -1)) return mutable;
        else if (isValidBreak(targetPos, 1, 1, 0)) return mutable;
        else if (isValidBreak(targetPos, -1, -1, 0)) return mutable;
        else if (isValidBreak(targetPos, 0, 1, 1)) return mutable;
        else if (isValidBreak(targetPos, 0, 0, -1)) return mutable;
        return null;
    }

    private boolean getDamagePlace(BlockPos pos) {
        return placeMode.get() == Safety.Suicide || DamageUtils.bedDamage(mc.player, pos.toCenterPos()) <= maxDamage.get();
    }

    private boolean getDamageBreak(BlockPos pos) {
        return breakMode.get() == Safety.Suicide || DamageUtils.anchorDamage(mc.player, pos.toCenterPos()) <= maxDamage.get();
    }

    private boolean isValidPlace(BlockPos origin, int xOffset, int yOffset, int zOffset) {
    	BlockUtils.mutateAround(mutable, origin, xOffset, yOffset, zOffset);
        return Math.sqrt(mc.player.getBlockPos().getSquaredDistance(mutable)) <= placeRange.get() && getDamagePlace(mutable) && BlockUtils.canPlace(mutable);
    }

    private boolean isValidBreak(BlockPos origin, int xOffset, int yOffset, int zOffset) {
    	BlockUtils.mutateAround(mutable, origin, xOffset, yOffset, zOffset);
        return mc.world.getBlockState(mutable).getBlock() == Blocks.RESPAWN_ANCHOR && Math.sqrt(mc.player.getBlockPos().getSquaredDistance(mutable)) <= breakRange.get() && getDamageBreak(mutable);
    }

    private void breakAnchor(BlockPos pos, FindItemResult anchor, FindItemResult glowStone) {
        if (pos == null || mc.world.getBlockState(pos).getBlock() != Blocks.RESPAWN_ANCHOR) return;

        mc.player.setSneaking(false);

        if (glowStone.isOffhand()) {
            mc.interactionManager.interactBlock(mc.player, Hand.OFF_HAND, new BlockHitResult(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), Direction.UP, pos, true));
        } else {
            InvUtils.swap(glowStone.slot(), true);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), Direction.UP, pos, true));
        }

        if (anchor.isOffhand()) {
            mc.interactionManager.interactBlock(mc.player, Hand.OFF_HAND, new BlockHitResult(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), Direction.UP, pos, true));
        } else {
            InvUtils.swap(anchor.slot(), true);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), Direction.UP, pos, true));
        }

        InvUtils.swapBack();
    }

    @Override
    public String getInfoString() {
        return EntityUtils.getName(target);
    }

    public enum PlaceMode {
        Above,
        Around,
        AboveAndBelow,
        All
    }

    public enum RotationMode {
        Place,
        Break,
        Both,
        None
    }
}
