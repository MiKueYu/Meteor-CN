/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

import java.util.Set;

public class BowAimbot extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("范围")
        .description("实体可以被瞄准的最大范围.")
        .defaultValue(20)
        .range(0, 100)
        .sliderMax(100)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("实体")
        .description("攻击的实体.")
        .onlyAttackable()
        .build()
    );

    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("优先级")
        .description("目标实体的类型.")
        .defaultValue(SortPriority.LowestHealth)
        .build()
    );

    private final Setting<Boolean> babies = sgGeneral.add(new BoolSetting.Builder()
        .name("幼崽")
        .description("是否攻击实体的幼崽变种.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> nametagged = sgGeneral.add(new BoolSetting.Builder()
        .name("名称标签")
        .description("是否攻击带有名称标签的生物.")
        .defaultValue(false)
        .build()
    );


    private final Setting<Boolean> pauseOnCombat = sgGeneral.add(new BoolSetting.Builder()
        .name("战斗时暂停")
        .description("暂时冻结Baritone,直到你释放弓.")
        .defaultValue(false)
        .build()
    );

    private boolean wasPathing;
    private Entity target;

    public BowAimbot() {
        super(Categories.Combat, "弓箭自瞄", "自动帮你瞄准弓箭.");
    }

    @Override
    public void onDeactivate() {
        target = null;
        wasPathing = false;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!PlayerUtils.isAlive() || !itemInHand()) return;
        if (!mc.player.getAbilities().creativeMode && !InvUtils.find(itemStack -> itemStack.getItem() instanceof ArrowItem).found()) return;

        target = TargetUtils.get(entity -> {
            if (entity == mc.player || entity == mc.cameraEntity) return false;
            if ((entity instanceof LivingEntity && ((LivingEntity) entity).isDead()) || !entity.isAlive()) return false;
            if (!PlayerUtils.isWithin(entity, range.get())) return false;
            if (!entities.get().contains(entity.getType())) return false;
            if (!nametagged.get() && entity.hasCustomName()) return false;
            if (!PlayerUtils.canSeeEntity(entity)) return false;
            if (entity instanceof PlayerEntity) {
                if (((PlayerEntity) entity).isCreative()) return false;
                if (!Friends.get().shouldAttack((PlayerEntity) entity)) return false;
            }
            return !(entity instanceof AnimalEntity) || babies.get() || !((AnimalEntity) entity).isBaby();
        }, priority.get());

        if (target == null) {
            if (wasPathing) {
                PathManagers.get().resume();
                wasPathing = false;
            }
            return;
        }

        if (mc.options.useKey.isPressed() && itemInHand()) {
            if (pauseOnCombat.get() && PathManagers.get().isPathing() && !wasPathing) {
                PathManagers.get().pause();
                wasPathing = true;
            }
            aim(event.tickDelta);
        }
    }

    private boolean itemInHand() {
        return InvUtils.testInMainHand(Items.BOW, Items.CROSSBOW);
    }

    private void aim(double tickDelta) {
        // Velocity based on bow charge.
        float velocity = (mc.player.getItemUseTime() - mc.player.getItemUseTimeLeft()) / 20f;
        velocity = (velocity * velocity + velocity * 2) / 3;
        if (velocity > 1) velocity = 1;

        // Positions
        double posX = target.getPos().getX() + (target.getPos().getX() - target.prevX) * tickDelta;
        double posY = target.getPos().getY() + (target.getPos().getY() - target.prevY) * tickDelta;
        double posZ = target.getPos().getZ() + (target.getPos().getZ() - target.prevZ) * tickDelta;

        // Adjusting for hitbox heights
        posY -= 1.9f - target.getHeight();

        double relativeX = posX - mc.player.getX();
        double relativeY = posY - mc.player.getY();
        double relativeZ = posZ - mc.player.getZ();

        // Calculate the pitch
        double hDistance = Math.sqrt(relativeX * relativeX + relativeZ * relativeZ);
        double hDistanceSq = hDistance * hDistance;
        float g = 0.006f;
        float velocitySq = velocity * velocity;
        float pitch = (float) -Math.toDegrees(Math.atan((velocitySq - Math.sqrt(velocitySq * velocitySq - g * (g * hDistanceSq + 2 * relativeY * velocitySq))) / (g * hDistance)));

        // Set player rotation
        if (Float.isNaN(pitch)) {
            Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target));
        } else {
            Rotations.rotate(Rotations.getYaw(new Vec3d(posX, posY, posZ)), pitch);
        }
    }

    @Override
    public String getInfoString() {
        return EntityUtils.getName(target);
    }
}
