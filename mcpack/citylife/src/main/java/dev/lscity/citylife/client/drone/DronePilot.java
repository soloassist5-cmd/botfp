package dev.lscity.citylife.client.drone;

import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.drone.DroneControlPacket;
import dev.lscity.citylife.drone.DroneEntity;
import dev.lscity.citylife.drone.DroneMovePacket;
import dev.lscity.citylife.drone.DroneType;
import dev.lscity.citylife.drone.Drones;
import dev.lscity.citylife.net.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Пилот дрона: камера — дрон, клавиши ходьбы ведут его, мышь — поворот.
 *
 * Полёт считается здесь, у пилота, и уходит на сервер каждый тик:
 *  - «Сокол» и «Пеликан» — «режим камеры»: отпустил клавиши — завис на месте,
 *    скорость набирается и гасится плавно, горизонт всегда ровный;
 *  - «Стриж» — гоночный: тяга по взгляду (вверх-вниз — мышью), инерция,
 *    крен камеры в поворотах, Ctrl — форсаж; без газа его тянет вниз.
 *
 * Пробел/Shift — вверх/вниз, ЛКМ — снимок, ПКМ — фары (у «Пеликана» —
 * взять/сбросить груз), колесо — зум, R — домой, Q — выйти из пульта.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID, value = Dist.CLIENT)
public final class DronePilot {

    private static DroneEntity drone;
    private static CameraType savedCamera;
    private static Vec3 velocity = Vec3.ZERO;
    private static float roll;
    private static float rollO;
    private static float zoom = 1.0F;
    private static int flightTicks;
    private static int flash;
    private static float lastDamage;

    static {
        Drones.setLocalPilot(d -> d == drone);
    }

    private DronePilot() {
    }

    public static DroneEntity drone() {
        return drone;
    }

    public static boolean active() {
        return drone != null;
    }

    static float zoom() {
        return zoom;
    }

    static int flightTicks() {
        return flightTicks;
    }

    static Vec3 velocity() {
        return velocity;
    }

    static boolean flash() {
        return flash > 0;
    }

    /** Пакет от сервера: начать/закончить или поправить координаты. */
    public static void control(DroneControlPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (!p.on()) {
            if (drone != null && drone.getId() == p.id()) {
                release(mc);
            }
            return;
        }
        if (!(mc.level.getEntity(p.id()) instanceof DroneEntity d)) {
            return;
        }
        if (p.fix()) {
            d.setPos(p.x(), p.y(), p.z());
            velocity = Vec3.ZERO;
            return;
        }
        drone = d;
        if (mc.screen != null) {
            mc.setScreen(null);   // пульт в телефоне: экран закрывается, смотрим камерой
        }
        velocity = Vec3.ZERO;
        zoom = 1.0F;
        flightTicks = 0;
        roll = rollO = 0;
        lastDamage = d.health();
        savedCamera = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (mc.player != null) {
            d.setYRot(mc.player.getYRot());
            d.setXRot(mc.player.getXRot());
        }
        mc.setCameraEntity(d);
    }

    private static void release(Minecraft mc) {
        drone = null;
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
        }
        if (savedCamera != null) {
            mc.options.setCameraType(savedCamera);
        }
        zoom = 1.0F;
        roll = rollO = 0;
    }

    private static void exit() {
        Net.sendAction("drone_exit", new CompoundTag());
        release(Minecraft.getInstance());
    }

    // --- полёт ----------------------------------------------------------------------

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (drone == null) {
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            // Q — выход. Забираем нажатие до игры, иначе она выбросит предмет из руки.
            Minecraft mc = Minecraft.getInstance();
            boolean quit = false;
            while (mc.options.keyDrop.consumeClick()) {
                quit = true;
            }
            if (quit) {
                exit();
            }
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || drone.isRemoved() || drone.level() != mc.level) {
            release(mc);
            return;
        }
        // Пилота ударили — рефлекс: руки с пульта.
        if (mc.player.hurtTime > 0 && mc.player.hurtTime == mc.player.hurtDuration) {
            exit();
            return;
        }
        if (mc.screen != null) {
            return;
        }
        flightTicks++;
        if (flash > 0) {
            flash--;
        }
        // Мышь поворачивает игрока — берём его взгляд как взгляд камеры.
        drone.setYRot(mc.player.getYRot());
        drone.setXRot(Mth.clamp(mc.player.getXRot(), -89, 89));
        drone.yRotO = drone.getYRot();
        drone.xRotO = drone.getXRot();

        if (drone.battery() <= 0) {
            return;   // сервер уронит дрон и отключит нас
        }
        DroneType type = drone.droneType();
        float fwd = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
        float side = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
        float up = (mc.options.keyJump.isDown() ? 1 : 0) - (mc.options.keyShift.isDown() ? 1 : 0);
        boolean boost = mc.options.keySprint.isDown();
        float yaw = drone.getYRot() * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
        if (type == DroneType.RACER) {
            Vec3 look = drone.getViewVector(1.0F);
            double max = type.maxSpeed * (boost ? 1.45 : 1.0);
            Vec3 thrust = look.scale(fwd * 0.085 * (boost ? 1.6 : 1.0))
                    .add(right.scale(side * 0.05)).add(0, up * 0.07, 0);
            velocity = velocity.add(thrust).scale(0.955);
            if (fwd <= 0 && up <= 0) {
                velocity = velocity.add(0, -0.025, 0);   // без газа — тянет вниз
            }
            if (velocity.length() > max) {
                velocity = velocity.normalize().scale(max);
            }
        } else {
            double max = type.maxSpeed * (boost ? 1.5 : 1.0);
            Vec3 want = forward.scale(fwd).add(right.scale(side));
            if (want.lengthSqr() > 1) {
                want = want.normalize();
            }
            want = want.scale(max).add(0, up * type.climb, 0);
            velocity = velocity.add(want.subtract(velocity).scale(type.smooth));
        }
        rollO = roll;
        float sideSpeed = (float) velocity.dot(right);
        roll = type == DroneType.RACER ? Mth.lerp(0.3F, roll, -sideSpeed * 40 - side * 6)
                : Mth.lerp(0.2F, roll, -sideSpeed * 6);
        Vec3 before = drone.position();
        drone.move(MoverType.SELF, velocity);
        float crash = 0;
        if (drone.horizontalCollision || drone.verticalCollision && velocity.y > 0.4) {
            double speed = velocity.length();
            if (speed > 0.55) {
                crash = (float) ((speed - 0.5) * 8);
                mc.player.playSound(SoundEvents.SHIELD_BLOCK, 0.8F, 1.6F);
            }
            Vec3 moved = drone.position().subtract(before);
            velocity = new Vec3(drone.horizontalCollision ? moved.x : velocity.x,
                    drone.verticalCollision ? 0 : velocity.y, drone.horizontalCollision ? moved.z : velocity.z);
        }
        if (drone.onGround() && velocity.y < 0) {
            velocity = new Vec3(velocity.x * 0.5, 0, velocity.z * 0.5);
        }
        Net.CHANNEL.sendToServer(new DroneMovePacket(drone.getId(), drone.getX(), drone.getY(), drone.getZ(),
                drone.getYRot(), drone.getXRot(), crash));
    }

    /** Пилот стоит: его ходьба выключена, клавиши уходят дрону. */
    @SubscribeEvent
    public static void onMovement(MovementInputUpdateEvent event) {
        if (drone != null) {
            var in = event.getInput();
            in.forwardImpulse = 0;
            in.leftImpulse = 0;
            in.up = in.down = in.left = in.right = false;
            in.jumping = false;
            in.shiftKeyDown = false;
        }
    }

    /** ЛКМ — снимок, ПКМ — фары или груз; удары и «использовать» не уходят игроку. */
    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (drone == null) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        if (event.isAttack()) {
            photo();
        } else if (event.isUseItem()) {
            Net.sendAction(drone.droneType() == DroneType.COURIER ? "drone_cargo" : "drone_lights", new CompoundTag());
        }
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (drone == null || mc.screen != null || event.getAction() != 1) {
            return;
        }
        if (event.getKey() == org.lwjgl.glfw.GLFW.GLFW_KEY_R) {
            Net.sendAction("drone_return", new CompoundTag());
            release(mc);
        } else if (event.getKey() == org.lwjgl.glfw.GLFW.GLFW_KEY_L && drone.droneType() == DroneType.COURIER) {
            Net.sendAction("drone_lights", new CompoundTag());
        }
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (drone != null) {
            float max = drone.droneType() == DroneType.CAMERA ? 6.0F : 2.0F;
            zoom = Mth.clamp(zoom * (event.getScrollDelta() > 0 ? 1.15F : 1 / 1.15F), 1.0F, max);
            event.setCanceled(true);
        }
    }

    private static void photo() {
        Minecraft mc = Minecraft.getInstance();
        String name = "drone_" + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss")) + ".png";
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> {
        });
        flash = 4;
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.6F, 1.8F);
            mc.player.displayClientMessage(Component.translatable("citylife.drone.photo", name)
                    .withStyle(ChatFormatting.AQUA), true);
        }
    }

    // --- камера ---------------------------------------------------------------------

    @SubscribeEvent
    public static void onFov(ComputeFovModifierEvent event) {
        if (drone != null) {
            float base = drone.droneType() == DroneType.RACER ? 1.18F : 1.0F;   // FPV — широкий угол
            event.setNewFovModifier(base / zoom);
        }
    }

    @SubscribeEvent
    public static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (drone != null && mc.player != null) {
            // Взгляд — каждый кадр от мыши, а не раз в тик: камера не дёргается.
            event.setYaw(mc.player.getViewYRot((float) event.getPartialTick()));
            event.setPitch(Mth.clamp(mc.player.getViewXRot((float) event.getPartialTick()), -89, 89));
            event.setRoll(Mth.lerp((float) event.getPartialTick(), rollO, roll));
        }
    }

    /** Пока смотрим камерой дрона, интерфейс игрока не нужен: свой OSD. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (drone == null) {
            return;
        }
        var id = event.getOverlay().id();
        // Панели других модов (способности костюма, миникарта…) тоже прячем — кроме своих.
        if (!id.getNamespace().equals("minecraft") && !id.getNamespace().equals(CityLife.MOD_ID)) {
            event.setCanceled(true);
            return;
        }
        if (id.equals(VanillaGuiOverlay.HOTBAR.id()) || id.equals(VanillaGuiOverlay.CROSSHAIR.id())
                || id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id()) || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id())
                || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id()) || id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id())
                || id.equals(VanillaGuiOverlay.AIR_LEVEL.id()) || id.equals(VanillaGuiOverlay.ITEM_NAME.id())) {
            event.setCanceled(true);
        }
    }

    /** Руки с пультом в кадре камеры дрона не видно. */
    @SubscribeEvent
    public static void onHand(net.minecraftforge.client.event.RenderHandEvent event) {
        if (drone != null) {
            event.setCanceled(true);
        }
    }

    /** Вошли в мир заново или сменили его — сеанс точно окончен. */
    @SubscribeEvent
    public static void onLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        drone = null;
        roll = rollO = 0;
    }
}
