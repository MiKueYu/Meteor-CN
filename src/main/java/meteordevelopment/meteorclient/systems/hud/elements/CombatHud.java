/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.hud.elements;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectIntImmutablePair;
import it.unimi.dsi.fastutil.objects.ObjectIntPair;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BedItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4fStack;

import java.util.*;

import static meteordevelopment.meteorclient.MeteorClient.mc;

// TODO: Rewrite this to use the hud renderer system
public class CombatHud extends HudElement {
    private static final Color GREEN = new Color(15, 255, 15);
    private static final Color RED = new Color(255, 15, 15);
    private static final Color BLACK = new Color(0, 0, 0, 255);

    public static final HudElementInfo<CombatHud> INFO = new HudElementInfo<>(Hud.GROUP, "目标信息", "显示关于你的战斗目标的信息.", CombatHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("比例")
        .description("比例.")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 5)
        .onChanged(aDouble -> calculateSize())
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("范围")
        .description("与目标玩家的范围.")
        .defaultValue(100)
        .min(1)
        .sliderMax(200)
        .build()
    );

    private final Setting<Boolean> displayPing = sgGeneral.add(new BoolSetting.Builder()
        .name("延迟")
        .description("显示玩家的延迟.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> displayDistance = sgGeneral.add(new BoolSetting.Builder()
        .name("距离")
        .description("显示你和玩家之间的距离.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Set<RegistryKey<Enchantment>>> displayedEnchantments = sgGeneral.add(new EnchantmentListSetting.Builder()
        .name("显示附魔")
        .description("名称上显示的附魔.")
        .vanillaDefaults()
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgGeneral.add(new ColorSetting.Builder()
        .name("背景颜色")
        .description("背景颜色.")
        .defaultValue(new SettingColor(0, 0, 0, 64))
        .build()
    );

    private final Setting<SettingColor> enchantmentTextColor = sgGeneral.add(new ColorSetting.Builder()
        .name("附魔颜色")
        .description("附魔文字的颜色t.")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> pingColor1 = sgGeneral.add(new ColorSetting.Builder()
        .name("延迟阶段-1")
        .description("延迟低于 75 时的文字颜色.")
        .defaultValue(new SettingColor(15, 255, 15))
        .visible(displayPing::get)
        .build()
    );

    private final Setting<SettingColor> pingColor2 = sgGeneral.add(new ColorSetting.Builder()
        .name("延迟阶段-2")
        .description("延迟介于 75 到 200 之间的文字颜色.")
        .defaultValue(new SettingColor(255, 150, 15))
        .visible(displayPing::get)
        .build()
    );

    private final Setting<SettingColor> pingColor3 = sgGeneral.add(new ColorSetting.Builder()
        .name("延迟阶段-3")
        .description("延迟超过 200 时的文字颜色.")
        .defaultValue(new SettingColor(255, 15, 15))
        .visible(displayPing::get)
        .build()
    );

    private final Setting<SettingColor> distColor1 = sgGeneral.add(new ColorSetting.Builder()
        .name("距离阶段-1")
        .description("玩家距离你 10 格以内时的颜色.")
        .defaultValue(new SettingColor(255, 15, 15))
        .visible(displayDistance::get)
        .build()
    );

    private final Setting<SettingColor> distColor2 = sgGeneral.add(new ColorSetting.Builder()
        .name("距离阶段-2")
        .description("玩家距离你 50 格以内时的颜色.")
        .defaultValue(new SettingColor(255, 150, 15))
        .visible(displayDistance::get)
        .build()
    );

    private final Setting<SettingColor> distColor3 = sgGeneral.add(new ColorSetting.Builder()
        .name("距离阶段-3")
        .description("玩家距离你超过 50 格时的颜色.")
        .defaultValue(new SettingColor(15, 255, 15))
        .visible(displayDistance::get)
        .build()
    );

    private final Setting<SettingColor> healthColor1 = sgGeneral.add(new ColorSetting.Builder()
        .name("生命值阶段-1")
        .description("生命值低的颜色.")
        .defaultValue(new SettingColor(255, 15, 15))
        .build()
    );

    private final Setting<SettingColor> healthColor2 = sgGeneral.add(new ColorSetting.Builder()
        .name("生命值阶段-2")
        .description("生命值中等的颜色.")
        .defaultValue(new SettingColor(255, 150, 15))
        .build()
    );

    private final Setting<SettingColor> healthColor3 = sgGeneral.add(new ColorSetting.Builder()
        .name("生命值阶段-3")
        .description("生命值高的颜色.")
        .defaultValue(new SettingColor(15, 255, 15))
        .build()
    );

    private PlayerEntity playerEntity;

    public CombatHud() {
        super(INFO);

        calculateSize();
    }

    private void calculateSize() {
        setSize(175 * scale.get(), 95 * scale.get());
    }

    @Override
    public void render(HudRenderer renderer) {
        renderer.post(() -> {
            double x = this.x;
            double y = this.y;

            // TODO: These should probably be settings
            Color primaryColor = TextHud.getSectionColor(0);
            Color secondaryColor = TextHud.getSectionColor(1);

            if (isInEditor()) playerEntity = mc.player;
            else playerEntity = TargetUtils.getPlayerTarget(range.get(), SortPriority.LowestDistance);

            if (playerEntity == null && !isInEditor()) return;

            // Background
            Renderer2D.COLOR.begin();
            Renderer2D.COLOR.quad(x, y, getWidth(), getHeight(), backgroundColor.get());

            if (playerEntity == null) {
                if (isInEditor()) {
                    renderer.line(x, y, x + getWidth(), y + getHeight(), Color.GRAY);
                    renderer.line(x + getWidth(), y, x, y + getHeight(), Color.GRAY);
                    Renderer2D.COLOR.render(null); // i know, ill fix it soon
                }
                return;
            }
            Renderer2D.COLOR.render(null);

            // Player Model
            InventoryScreen.drawEntity(
                renderer.drawContext,
                (int) x,
                (int) y,
                (int) (x + (25 * scale.get())),
                (int) (y + (66 * scale.get())),
                (int) (30 * scale.get()),
                0,
                -MathHelper.wrapDegrees(playerEntity.prevYaw + (playerEntity.getYaw() - playerEntity.prevYaw) * mc.getRenderTickCounter().getTickDelta(true)),
                -playerEntity.getPitch(),
                playerEntity
            );

            // Moving pos to past player model
            x += 50 * scale.get();
            y += 5 * scale.get();

            // Setting up texts
            String breakText = " | ";

            // Name
            String nameText = playerEntity.getName().getString();
            Color nameColor = PlayerUtils.getPlayerColor(playerEntity, primaryColor);

            // Ping
            int ping = EntityUtils.getPing(playerEntity);
            String pingText = ping + "ms";

            Color pingColor;
            if (ping <= 75) pingColor = pingColor1.get();
            else if (ping <= 200) pingColor = pingColor2.get();
            else pingColor = pingColor3.get();

            // Distance
            double dist = 0;
            if (!isInEditor()) dist = Math.round(mc.player.distanceTo(playerEntity) * 100.0) / 100.0;
            String distText = dist + "m";

            Color distColor;
            if (dist <= 10) distColor = distColor1.get();
            else if (dist <= 50) distColor = distColor2.get();
            else distColor = distColor3.get();

            // Status Text
            String friendText = "Unknown";

            Color friendColor = primaryColor;

            if (Friends.get().isFriend(playerEntity)) {
                friendText = "Friend";
                friendColor = Config.get().friendColor.get();
            } else {
                boolean naked = true;

                for (int position = 3; position >= 0; position--) {
                    ItemStack itemStack = getItem(position);

                    if (!itemStack.isEmpty()) naked = false;
                }

                if (naked) {
                    friendText = "Naked";
                    friendColor = GREEN;
                } else {
                    boolean threat = false;

                    for (int position = 5; position >= 0; position--) {
                        ItemStack itemStack = getItem(position);

                        if (itemStack.getItem() instanceof SwordItem
                            || itemStack.getItem() == Items.END_CRYSTAL
                            || itemStack.getItem() == Items.RESPAWN_ANCHOR
                            || itemStack.getItem() instanceof BedItem) threat = true;
                    }

                    if (threat) {
                        friendText = "Threat";
                        friendColor = RED;
                    }
                }
            }

            TextRenderer.get().begin(0.45 * scale.get(), false, true);

            double breakWidth = TextRenderer.get().getWidth(breakText);
            double pingWidth = TextRenderer.get().getWidth(pingText);
            double friendWidth = TextRenderer.get().getWidth(friendText);

            TextRenderer.get().render(nameText, x, y, nameColor != null ? nameColor : primaryColor);

            y += TextRenderer.get().getHeight();

            TextRenderer.get().render(friendText, x, y, friendColor);

            if (displayPing.get()) {
                TextRenderer.get().render(breakText, x + friendWidth, y, secondaryColor);
                TextRenderer.get().render(pingText, x + friendWidth + breakWidth, y, pingColor);

                if (displayDistance.get()) {
                    TextRenderer.get().render(breakText, x + friendWidth + breakWidth + pingWidth, y, secondaryColor);
                    TextRenderer.get().render(distText, x + friendWidth + breakWidth + pingWidth + breakWidth, y, distColor);
                }
            } else if (displayDistance.get()) {
                TextRenderer.get().render(breakText, x + friendWidth, y, secondaryColor);
                TextRenderer.get().render(distText, x + friendWidth + breakWidth, y, distColor);
            }

            TextRenderer.get().end();

            // Moving pos down for armor
            y += 10 * scale.get();

            double armorX;
            double armorY;
            int slot = 5;

            // Drawing armor
            Matrix4fStack matrices = RenderSystem.getModelViewStack();

            matrices.pushMatrix();
            matrices.scale(scale.get().floatValue(), scale.get().floatValue(), 1);

            x /= scale.get();
            y /= scale.get();

            TextRenderer.get().begin(0.35, false, true);

            for (int position = 0; position < 6; position++) {
                armorX = x + position * 20;
                armorY = y;

                ItemStack itemStack = getItem(slot);

                renderer.item(itemStack, (int) (armorX * scale.get()), (int) (armorY * scale.get()), scale.get().floatValue(), true);

                armorY += 18;

                ItemEnchantmentsComponent enchantments = EnchantmentHelper.getEnchantments(itemStack);
                List<ObjectIntPair<RegistryEntry<Enchantment>>> enchantmentsToShow = new ArrayList<>();

                for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : enchantments.getEnchantmentEntries()) {
                    if (entry.getKey().matches(displayedEnchantments.get()::contains)) {
                        enchantmentsToShow.add(new ObjectIntImmutablePair<>(entry.getKey(), entry.getIntValue()));
                    }
                }

                for (ObjectIntPair<RegistryEntry<Enchantment>> entry : enchantmentsToShow) {
                    String enchantName = Utils.getEnchantSimpleName(entry.left(), 3) + " " + entry.rightInt();

                    double enchX = (armorX + 8) - (TextRenderer.get().getWidth(enchantName) / 2);
                    TextRenderer.get().render(enchantName, enchX, armorY, entry.left().isIn(EnchantmentTags.CURSE) ? RED : enchantmentTextColor.get());
                    armorY += TextRenderer.get().getHeight();
                }
                slot--;
            }

            TextRenderer.get().end();

            y = (int) (this.y + 75 * scale.get());
            x = this.x;

            // Health bar

            x /= scale.get();
            y /= scale.get();

            x += 5;
            y += 5;

            Renderer2D.COLOR.begin();
            Renderer2D.COLOR.boxLines(x, y, 165, 11, BLACK);
            Renderer2D.COLOR.render(null);

            x += 2;
            y += 2;

            float maxHealth = playerEntity.getMaxHealth();
            int maxAbsorb = 16;
            int maxTotal = (int) (maxHealth + maxAbsorb);

            int totalHealthWidth = (int) (161 * maxHealth / maxTotal);
            int totalAbsorbWidth = 161 * maxAbsorb / maxTotal;

            float health = playerEntity.getHealth();
            float absorb = playerEntity.getAbsorptionAmount();

            double healthPercent = health / maxHealth;
            double absorbPercent = absorb / maxAbsorb;

            int healthWidth = (int) (totalHealthWidth * healthPercent);
            int absorbWidth = (int) (totalAbsorbWidth * absorbPercent);

            Renderer2D.COLOR.begin();
            Renderer2D.COLOR.quad(x, y, healthWidth, 7, healthColor1.get(), healthColor2.get(), healthColor2.get(), healthColor1.get());
            Renderer2D.COLOR.quad(x + healthWidth, y, absorbWidth, 7, healthColor2.get(), healthColor3.get(), healthColor3.get(), healthColor2.get());
            Renderer2D.COLOR.render(null);

            matrices.popMatrix();
        });
    }

    private ItemStack getItem(int i) {
        if (isInEditor()) {
            return switch (i) {
                case 0 -> Items.END_CRYSTAL.getDefaultStack();
                case 1 -> Items.NETHERITE_BOOTS.getDefaultStack();
                case 2 -> Items.NETHERITE_LEGGINGS.getDefaultStack();
                case 3 -> Items.NETHERITE_CHESTPLATE.getDefaultStack();
                case 4 -> Items.NETHERITE_HELMET.getDefaultStack();
                case 5 -> Items.TOTEM_OF_UNDYING.getDefaultStack();
                default -> ItemStack.EMPTY;
            };
        }

        if (playerEntity == null) return ItemStack.EMPTY;

        return switch (i) {
            case 4 -> playerEntity.getOffHandStack();
            case 5 -> playerEntity.getMainHandStack();
            default -> playerEntity.getInventory().getArmorStack(i);
        };
    }
}
