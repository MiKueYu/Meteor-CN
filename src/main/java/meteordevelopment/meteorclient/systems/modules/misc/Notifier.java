/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.*;

import static meteordevelopment.meteorclient.utils.player.ChatUtils.formatCoords;

public class Notifier extends Module {
    private final SettingGroup sgTotemPops = settings.createGroup("图腾爆开");
    private final SettingGroup sgVisualRange = settings.createGroup("可视范围");
    private final SettingGroup sgPearl = settings.createGroup("末影珍珠");

    // Totem Pops

    private final Setting<Boolean> totemPops = sgTotemPops.add(new BoolSetting.Builder()
        .name("图腾爆开")
        .description("当玩家爆开图腾时通知您.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> totemsIgnoreOwn = sgTotemPops.add(new BoolSetting.Builder()
        .name("忽略自身")
        .description("忽略你自己的图腾爆开.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> totemsIgnoreFriends = sgTotemPops.add(new BoolSetting.Builder()
        .name("忽略好友")
        .description("忽略好友图腾爆开.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> totemsIgnoreOthers = sgTotemPops.add(new BoolSetting.Builder()
        .name("忽略其他人")
        .description("忽略其他玩家图腾爆开.")
        .defaultValue(false)
        .build()
    );

    // Visual Range

    private final Setting<Boolean> visualRange = sgVisualRange.add(new BoolSetting.Builder()
        .name("可视范围")
        .description("当实体进入您的渲染距离时通知您.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Event> event = sgVisualRange.add(new EnumSetting.Builder<Event>()
        .name("事件")
        .description("何时记录实体.")
        .defaultValue(Event.Both)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = sgVisualRange.add(new EntityTypeListSetting.Builder()
        .name("实体")
        .description("需要通知哪些实体.")
        .defaultValue(EntityType.PLAYER)
        .build()
    );

    private final Setting<Boolean> visualRangeIgnoreFriends = sgVisualRange.add(new BoolSetting.Builder()
        .name("忽略好友")
        .description("忽略好友.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> visualRangeIgnoreFakes = sgVisualRange.add(new BoolSetting.Builder()
        .name("忽略假玩家")
        .description("忽略假玩家.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> visualMakeSound = sgVisualRange.add(new BoolSetting.Builder()
        .name("声音")
        .description("进入/离开时发出声音效果")
        .defaultValue(true)
        .build()
    );

    // Pearl

    private final Setting<Boolean> pearl = sgPearl.add(new BoolSetting.Builder()
        .name("末影珍珠")
        .description("当玩家使用末影珍珠传送时通知您.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pearlIgnoreOwn = sgPearl.add(new BoolSetting.Builder()
        .name("忽略自身")
        .description("忽略自己的末影珍珠.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> pearlIgnoreFriends = sgPearl.add(new BoolSetting.Builder()
        .name("忽略好友")
        .description("忽略好友的末影珍珠.")
        .defaultValue(false)
        .build()
    );

    private final Object2IntMap<UUID> totemPopMap = new Object2IntOpenHashMap<>();
    private final Object2IntMap<UUID> chatIdMap = new Object2IntOpenHashMap<>();
    private final Map<Integer, Vec3d> pearlStartPosMap = new HashMap<>();

    private final Random random = new Random();

    public Notifier() {
        super(Categories.Misc, "通知", "通知您不同的事件.");
    }

    // Visual Range

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (!event.entity.getUuid().equals(mc.player.getUuid()) && entities.get().contains(event.entity.getType()) && visualRange.get() && this.event.get() != Event.Despawn) {
            if (event.entity instanceof PlayerEntity) {
                if ((!visualRangeIgnoreFriends.get() || !Friends.get().isFriend(((PlayerEntity) event.entity))) && (!visualRangeIgnoreFakes.get() || !(event.entity instanceof FakePlayerEntity))) {
                    ChatUtils.sendMsg(event.entity.getId() + 100, Formatting.GRAY, "(highlight)%s(default) 已经进入你的可视范围!", event.entity.getName().getString());

                    if (visualMakeSound.get())
                        mc.world.playSoundFromEntity(mc.player, mc.player, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.AMBIENT, 3.0F, 1.0F);
                }
            } else {
                MutableText text = Text.literal(event.entity.getType().getName().getString()).formatted(Formatting.WHITE);
                text.append(Text.literal(" 出现于 ").formatted(Formatting.GRAY));
                text.append(formatCoords(event.entity.getPos()));
                text.append(Text.literal(".").formatted(Formatting.GRAY));
                info(text);
            }
        }

        if (pearl.get()) {
            if (event.entity instanceof EnderPearlEntity pearl) {
                pearlStartPosMap.put(pearl.getId(), new Vec3d(pearl.getX(), pearl.getY(), pearl.getZ()));
            }
        }
    }

    @EventHandler
    private void onEntityRemoved(EntityRemovedEvent event) {
        if (!event.entity.getUuid().equals(mc.player.getUuid()) && entities.get().contains(event.entity.getType()) && visualRange.get() && this.event.get() != Event.Spawn) {
            if (event.entity instanceof PlayerEntity) {
                if ((!visualRangeIgnoreFriends.get() || !Friends.get().isFriend(((PlayerEntity) event.entity))) && (!visualRangeIgnoreFakes.get() || !(event.entity instanceof FakePlayerEntity))) {
                    ChatUtils.sendMsg(event.entity.getId() + 100, Formatting.GRAY, "(highlight)%s(default) 已离开您的可视范围!", event.entity.getName().getString());

                    if (visualMakeSound.get())
                        mc.world.playSoundFromEntity(mc.player, mc.player, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.AMBIENT, 3.0F, 1.0F);
                }
            } else {
                MutableText text = Text.literal(event.entity.getType().getName().getString()).formatted(Formatting.WHITE);
                text.append(Text.literal(" 消失于 ").formatted(Formatting.GRAY));
                text.append(formatCoords(event.entity.getPos()));
                text.append(Text.literal(".").formatted(Formatting.GRAY));
                info(text);
            }
        }

        if (pearl.get()) {
            Entity e = event.entity;
            int i = e.getId();
            if (pearlStartPosMap.containsKey(i)) {
                EnderPearlEntity pearl = (EnderPearlEntity) e;
                if (pearl.getOwner() != null && pearl.getOwner() instanceof PlayerEntity p) {
                    double d = pearlStartPosMap.get(i).distanceTo(e.getPos());
                    if ((!Friends.get().isFriend(p) || !pearlIgnoreFriends.get()) && (!p.equals(mc.player) || !pearlIgnoreOwn.get())) {
                        info("(highlight)%s's(default) 末影珍珠落在 %d, %d, %d (highlight)(%.1fm 离开,旅行 %.1fm)(default).", pearl.getOwner().getName().getString(), pearl.getBlockPos().getX(), pearl.getBlockPos().getY(), pearl.getBlockPos().getZ(), pearl.distanceTo(mc.player), d);
                    }
                }
                pearlStartPosMap.remove(i);
            }
        }
    }

    // Totem Pops

    @Override
    public void onActivate() {
        totemPopMap.clear();
        chatIdMap.clear();
        pearlStartPosMap.clear();
    }

    @EventHandler
    private void onGameJoin(GameJoinedEvent event) {
        totemPopMap.clear();
        chatIdMap.clear();
        pearlStartPosMap.clear();
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (!totemPops.get()) return;
        if (!(event.packet instanceof EntityStatusS2CPacket p)) return;

        if (p.getStatus() != 35) return;

        Entity entity = p.getEntity(mc.world);

        if (!(entity instanceof PlayerEntity)) return;

        if ((entity.equals(mc.player) && totemsIgnoreOwn.get())
            || (Friends.get().isFriend(((PlayerEntity) entity)) && totemsIgnoreOthers.get())
            || (!Friends.get().isFriend(((PlayerEntity) entity)) && totemsIgnoreFriends.get())
        ) return;

        synchronized (totemPopMap) {
            int pops = totemPopMap.getOrDefault(entity.getUuid(), 0);
            totemPopMap.put(entity.getUuid(), ++pops);

            ChatUtils.sendMsg(getChatId(entity), Formatting.GRAY, "(highlight)%s (default)爆开 (highlight)%d (default)%s.", entity.getName().getString(), pops, pops == 1 ? "图腾" : "图腾");
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!totemPops.get()) return;
        synchronized (totemPopMap) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (!totemPopMap.containsKey(player.getUuid())) continue;

                if (player.deathTime > 0 || player.getHealth() <= 0) {
                    int pops = totemPopMap.removeInt(player.getUuid());

                    ChatUtils.sendMsg(getChatId(player), Formatting.GRAY, "(highlight)%s (default)爆开后死亡 (highlight)%d (default)%s.", player.getName().getString(), pops, pops == 1 ? "图腾" : "图腾");
                    chatIdMap.removeInt(player.getUuid());
                }
            }
        }
    }

    private int getChatId(Entity entity) {
        return chatIdMap.computeIfAbsent(entity.getUuid(), value -> random.nextInt());
    }

    public enum Event {
        Spawn,
        Despawn,
        Both
    }
}
