/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.entity.DropItemsEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.meteor.MouseButtonEvent;
import meteordevelopment.meteorclient.events.packets.InventoryEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.CloseHandledScreenC2SPacketAccessor;
import meteordevelopment.meteorclient.mixin.HandledScreenAccessor;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.player.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.AbstractSkullBlock;
import net.minecraft.block.CarvedPumpkinBlock;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Equipment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class InventoryTweaks extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSorting = settings.createGroup("排序");
    private final SettingGroup sgAutoDrop = settings.createGroup("自动转储");
    private final SettingGroup sgStealDump = settings.createGroup("拿取和转储");
    private final SettingGroup sgAutoSteal = settings.createGroup("自动拿取");

    // General

    private final Setting<Boolean> mouseDragItemMove = sgGeneral.add(new BoolSetting.Builder()
        .name("鼠标拖动物品移动")
        .description("按住Shift的同时将鼠标移到物品上会将其转移到另一个容器.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<Item>> antiDropItems = sgGeneral.add(new ItemListSetting.Builder()
        .name("防丢弃物品")
        .description("防止丢弃物品.在创造模式的物品栏界面不起作用.")
        .build()
    );

    private final Setting<Boolean> xCarry = sgGeneral.add(new BoolSetting.Builder()
        .name("额外携带")
        .description("允许您在背包合成中存储四个额外的物品堆.")
        .defaultValue(true)
        .onChanged(v -> {
            if (v || !Utils.canUpdate()) return;
            mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(mc.player.playerScreenHandler.syncId));
            invOpened = false;
        })
        .build()
    );

    private final Setting<Boolean> armorStorage = sgGeneral.add(new BoolSetting.Builder()
        .name("盔甲储存")
        .description("允许您将物品放入盔甲槽中.")
        .defaultValue(true)
        .build()
    );

    // Sorting

    private final Setting<Boolean> sortingEnabled = sgSorting.add(new BoolSetting.Builder()
        .name("启用排序")
        .description("自动对库存中的物品进行排序.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Keybind> sortingKey = sgSorting.add(new KeybindSetting.Builder()
        .name("排序键")
        .description("触发排序的键.")
        .visible(sortingEnabled::get)
        .defaultValue(Keybind.fromButton(GLFW.GLFW_MOUSE_BUTTON_MIDDLE))
        .build()
    );

    private final Setting<Integer> sortingDelay = sgSorting.add(new IntSetting.Builder()
        .name("排序间隔")
        .description("排序时移动物品之间的tick间隔.")
        .visible(sortingEnabled::get)
        .defaultValue(1)
        .min(0)
        .build()
    );

    // Auto Drop

    private final Setting<List<Item>> autoDropItems = sgAutoDrop.add(new ItemListSetting.Builder()
        .name("自动丢弃物品")
        .description("要丢弃的物品.")
        .build()
    );

    private final Setting<Boolean> autoDropExcludeEquipped = sgAutoDrop.add(new BoolSetting.Builder()
        .name("排除装备")
        .description("是否丢弃装备在盔甲槽中的物品.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoDropExcludeHotbar = sgAutoDrop.add(new BoolSetting.Builder()
        .name("排除物品栏")
        .description("是否从物品栏中转储物品.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoDropOnlyFullStacks = sgAutoDrop.add(new BoolSetting.Builder()
        .name("仅满堆")
        .description("仅当物品已满时才丢弃物品.")
        .defaultValue(false)
        .build()
    );

    // Steal & Dump

    public final Setting<List<ScreenHandlerType<?>>> stealScreens = sgStealDump.add(new ScreenHandlerListSetting.Builder()
        .name("窃取界面")
        .description("选择显示按钮和自动窃取的屏幕.")
        .defaultValue(List.of(ScreenHandlerType.GENERIC_9X3, ScreenHandlerType.GENERIC_9X6))
        .build()
    );

    private final Setting<Boolean> buttons = sgStealDump.add(new BoolSetting.Builder()
        .name("库存按钮")
        .description("在容器GUI中显示窃取和转储按钮.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> stealDrop = sgStealDump.add(new BoolSetting.Builder()
        .name("窃取丢弃")
        .description("将物品丢到地上而不是偷走它们.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> dropBackwards = sgStealDump.add(new BoolSetting.Builder()
        .name("向后丢弃")
        .description("将物品丢在身后.")
        .defaultValue(false)
        .visible(stealDrop::get)
        .build()
    );

    private final Setting<ListMode> dumpFilter = sgStealDump.add(new EnumSetting.Builder<ListMode>()
        .name("转储过滤")
        .description("转储模式.")
        .defaultValue(ListMode.None)
        .build()
    );

    private final Setting<List<Item>> dumpItems = sgStealDump.add(new ItemListSetting.Builder()
        .name("转储物品")
        .description("要转储的物品.")
        .build()
    );

    private final Setting<ListMode> stealFilter = sgStealDump.add(new EnumSetting.Builder<ListMode>()
        .name("窃取过滤")
        .description("偷窃模式.")
        .defaultValue(ListMode.None)
        .build()
    );

    private final Setting<List<Item>> stealItems = sgStealDump.add(new ItemListSetting.Builder()
        .name("偷窃物品")
        .description("要偷的物品.")
        .build()
    );

    // Auto Steal

    private final Setting<Boolean> autoSteal = sgAutoSteal.add(new BoolSetting.Builder()
        .name("自动偷窃")
        .description("打开容器时自动偷窃所有可能的物品.")
        .defaultValue(false)
        .onChanged(val -> checkAutoStealSettings())
        .build()
    );

    private final Setting<Boolean> autoDump = sgAutoSteal.add(new BoolSetting.Builder()
        .name("自动转储")
        .description("打开容器时自动转储所有可能的物品.")
        .defaultValue(false)
        .onChanged(val -> checkAutoStealSettings())
        .build()
    );

    private final Setting<Integer> autoStealDelay = sgAutoSteal.add(new IntSetting.Builder()
        .name("间隔")
        .description("窃取下一个物品之间的最小间隔(以毫秒为单位).")
        .defaultValue(20)
        .sliderMax(1000)
        .build()
    );

    private final Setting<Integer> autoStealInitDelay = sgAutoSteal.add(new IntSetting.Builder()
        .name("初始延迟")
        .description("窃取之前的初始延迟(以毫秒为单位).0使用正常间隔代替.")
        .defaultValue(50)
        .sliderMax(1000)
        .build()
    );

    private final Setting<Integer> autoStealRandomDelay = sgAutoSteal.add(new IntSetting.Builder()
        .name("随机")
        .description("随机添加最多指定时间(以毫秒为单位)的间隔.")
        .min(0)
        .sliderMax(1000)
        .defaultValue(50)
        .build()
    );

    private InventorySorter sorter;
    private boolean invOpened;

    public InventoryTweaks() {
        super(Categories.Misc, "库存调整", "各种库存相关实用程序.");
    }

    @Override
    public void onActivate() {
        invOpened = false;
    }

    @Override
    public void onDeactivate() {
        sorter = null;

        if (invOpened) {
            mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(mc.player.playerScreenHandler.syncId));
        }
    }

    // Sorting and armour swapping

    @EventHandler
    private void onKey(KeyEvent event) {
        if (event.action != KeyAction.Press) return;

        if (sortingKey.get().matches(true, event.key, event.modifiers)) {
            if (sort()) event.cancel();
        }
    }

    @EventHandler
    private void onMouseButton(MouseButtonEvent event) {
        if (event.action != KeyAction.Press) return;

        if (sortingKey.get().matches(false, event.button, 0)) {
            if (sort()) event.cancel();
        }
    }

    private boolean sort() {
        if (!sortingEnabled.get() || !(mc.currentScreen instanceof HandledScreen<?> screen) || sorter != null)
            return false;

        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            FindItemResult empty = InvUtils.findEmpty();
            if (!empty.found()) InvUtils.click().slot(-999);
            else InvUtils.click().slot(empty.slot());
        }

        Slot focusedSlot = ((HandledScreenAccessor) screen).getFocusedSlot();
        if (focusedSlot == null) return false;

        sorter = new InventorySorter(screen, focusedSlot);
        return true;
    }

    private boolean isWearable(ItemStack itemStack) {
        Item item = itemStack.getItem();

        if (item instanceof Equipment) return true;
        return item instanceof BlockItem blockItem &&
            (blockItem.getBlock() instanceof AbstractSkullBlock || blockItem.getBlock() instanceof CarvedPumpkinBlock);
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        sorter = null;
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (sorter != null && sorter.tick(sortingDelay.get())) sorter = null;
    }

    // Auto Drop

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        // Auto Drop
        if (mc.currentScreen instanceof HandledScreen<?> || autoDropItems.get().isEmpty()) return;

        for (int i = autoDropExcludeHotbar.get() ? 9 : 0; i < mc.player.getInventory().size(); i++) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);

            if (autoDropItems.get().contains(itemStack.getItem())) {
                if ((!autoDropOnlyFullStacks.get() || itemStack.getCount() == itemStack.getMaxCount()) &&
                    !(autoDropExcludeEquipped.get() && SlotUtils.isArmor(i))) InvUtils.drop().slot(i);
            }
        }
    }

    @EventHandler
    private void onDropItems(DropItemsEvent event) {
        if (antiDropItems.get().contains(event.itemStack.getItem())) event.cancel();
    }

    // XCarry

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (!xCarry.get() || !(event.packet instanceof CloseHandledScreenC2SPacket)) return;

        if (((CloseHandledScreenC2SPacketAccessor) event.packet).getSyncId() == mc.player.playerScreenHandler.syncId) {
            invOpened = true;
            event.cancel();
        }
    }

    // Auto Steal

    private void checkAutoStealSettings() {
        if (autoSteal.get() && autoDump.get()) {
            error("您不能同时启用自动窃取和自动转储!");
            autoDump.set(false);
        }
    }

    private int getSleepTime() {
        return autoStealDelay.get() + (autoStealRandomDelay.get() > 0 ? ThreadLocalRandom.current().nextInt(0, autoStealRandomDelay.get()) : 0);
    }

    private void moveSlots(ScreenHandler handler, int start, int end, boolean steal) {
        boolean initial = autoStealInitDelay.get() != 0;
        for (int i = start; i < end; i++) {
            if (!handler.getSlot(i).hasStack()) continue;

            int sleep;
            if (initial) {
                sleep = autoStealInitDelay.get();
                initial = false;
            } else sleep = getSleepTime();
            if (sleep > 0) {
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // Exit if user closes screen or exit world
            if (mc.currentScreen == null || !Utils.canUpdate()) break;

            Item item = handler.getSlot(i).getStack().getItem();
            if (steal) {
                if (stealFilter.get() == ListMode.Whitelist && !stealItems.get().contains(item))
                    continue;
                if (stealFilter.get() == ListMode.Blacklist && stealItems.get().contains(item))
                    continue;
            } else {
                if (dumpFilter.get() == ListMode.Whitelist && !dumpItems.get().contains(item))
                    continue;
                if (dumpFilter.get() == ListMode.Blacklist && dumpItems.get().contains(item))
                    continue;
            }

            if (steal && stealDrop.get()) {
                if (dropBackwards.get()) {
                    int iCopy = i;
                    Rotations.rotate(mc.player.getYaw() - 180, mc.player.getPitch(), () -> InvUtils.drop().slotId(iCopy));
                }
            } else InvUtils.shiftClick().slotId(i);
        }
    }

    public void steal(ScreenHandler handler) {
        MeteorExecutor.execute(() -> moveSlots(handler, 0, SlotUtils.indexToId(SlotUtils.MAIN_START), true));
    }

    public void dump(ScreenHandler handler) {
        int playerInvOffset = SlotUtils.indexToId(SlotUtils.MAIN_START);
        MeteorExecutor.execute(() -> moveSlots(handler, playerInvOffset, playerInvOffset + 4 * 9, false));
    }

    public boolean showButtons() {
        return isActive() && buttons.get();
    }

    public boolean mouseDragItemMove() {
        return isActive() && mouseDragItemMove.get();
    }

    public boolean armorStorage() {
        return isActive() && armorStorage.get();
    }

    public boolean canSteal(ScreenHandler handler) {
        try {
            return (stealScreens.get().contains(handler.getType()));
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }

    @EventHandler
    private void onInventory(InventoryEvent event) {
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (canSteal(handler) && event.packet.getSyncId() == handler.syncId) {
            if (autoSteal.get()) {
                steal(handler);
            } else if (autoDump.get()) {
                dump(handler);
            }
        }
    }

    public enum ListMode {
        Whitelist,
        Blacklist,
        None
    }
}
