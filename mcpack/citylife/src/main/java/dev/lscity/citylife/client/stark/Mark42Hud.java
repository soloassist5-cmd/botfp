package dev.lscity.citylife.client.stark;

import dev.lscity.citylife.stark.Mark42;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD шлема Mark 42 — интерфейс Джарвиса изнутри забрала.
 *
 * Сверху — компас, слева — высота и скорость, справа — состояние костюма
 * и включённые режимы, в центре — прицельное кольцо. На кого смотришь, о
 * том Джарвис и пишет: имя, расстояние, здоровье. Когда шлем только что
 * защёлкнулся — короткая загрузка «J.A.R.V.I.S. ONLINE».
 *
 * Рисуется только от первого лица, с опущенным забралом; F1 прячет его
 * вместе с остальным интерфейсом.
 */
@OnlyIn(Dist.CLIENT)
public final class Mark42Hud {

    private static final int CYAN = 0x7FD8FF;
    private static final int GOLD = 0xFFC861;
    private static final int RED = 0xFF5A4A;

    private static long bootAt = -1;
    private static boolean wore;
    private static Vec3 lastPos;
    private static double speed;
    private static long lastTick;

    private Mark42Hud() {
    }

    private static int a(int rgb, float alpha) {
        return Mth.clamp((int) (alpha * 255), 0, 255) << 24 | rgb;
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui || dev.lscity.citylife.client.drone.DronePilot.active()) {
            return;
        }
        boolean helmet = player.getItemBySlot(EquipmentSlot.HEAD).getItem() == Mark42.HELMET.get();
        long now = player.level().getGameTime();
        if (helmet && !wore) {
            bootAt = now;
        }
        wore = helmet;
        if (!helmet || !mc.options.getCameraType().isFirstPerson() || Mark42Client.faceOpen(player)) {
            return;
        }
        if (now != lastTick) {
            Vec3 pos = player.position();
            if (lastPos != null) {
                speed = speed * 0.8 + pos.distanceTo(lastPos) * 20 * 0.2;
            }
            lastPos = pos;
            lastTick = now;
        }
        Font font = mc.font;
        float boot = bootAt < 0 ? 1 : Mth.clamp((now - bootAt + partial) / 40.0F, 0, 1);
        float alpha = 0.25F + 0.55F * boot;

        visor(g, width, height, boot);
        brackets(g, width, height, boot, alpha);
        reticle(g, width / 2, height / 2, now + partial, alpha);
        compass(g, font, width, player, alpha);

        // Слева: полёт.
        int lx = 14;
        int ly = height / 2 - 28;
        label(g, font, "citylife.mark42.hud.alt", String.format("%.0f", player.getY()), lx, ly, alpha);
        label(g, font, "citylife.mark42.hud.speed", String.format("%.1f", speed), lx, ly + 12, alpha);
        double ground = groundDistance(player);
        label(g, font, "citylife.mark42.hud.ground", ground < 0 ? "—" : String.format("%.0f", ground), lx, ly + 24,
                alpha);

        // Справа: костюм и режимы.
        List<String> modes = new ArrayList<>();
        for (String mode : new String[]{"flight", "shield", "scan", "night_vision"}) {
            if (Mark42Client.ability(player, mode)) {
                modes.add(Component.translatable("citylife.mark42.hud.mode." + mode).getString());
            }
        }
        int rx = width - 48;   // правее — панель способностей Palladium
        int ry = height / 2 - 28;
        float hp = player.getHealth() / player.getMaxHealth();
        right(g, font, Component.translatable("citylife.mark42.hud.integrity",
                Math.round(hp * 100)).getString(), rx, ry, a(hp < 0.35F ? RED : CYAN, alpha));
        bar(g, rx - 60, ry + 11, 60, hp, hp < 0.35F ? RED : CYAN, alpha);
        int y = ry + 18;
        for (String mode : modes) {
            right(g, font, "▸ " + mode, rx, y, a(GOLD, alpha));
            y += 10;
        }

        target(g, font, mc, width, height, alpha);

        String sig = boot < 1 ? bootText(boot) : "J.A.R.V.I.S. · MARK 42";
        g.drawCenteredString(font, sig, width / 2, height - 62, a(boot < 1 ? CYAN : GOLD, 0.35F + 0.5F * alpha));
    }

    private static String bootText(float boot) {
        String full = "J.A.R.V.I.S. ONLINE";
        return full.substring(0, Math.max(1, Math.round(full.length() * Math.min(1, boot * 1.4F))));
    }

    /** Края забрала: лёгкое затемнение и тёплый отсвет. */
    private static void visor(GuiGraphics g, int w, int h, float boot) {
        int edge = 0x30000000;
        int clear = 0x00000000;
        int band = Math.max(18, h / 10);
        g.fillGradient(0, 0, w, band, edge, clear);
        g.fillGradient(0, h - band, w, h, clear, edge);
        if (boot < 1) {
            int sweep = (int) (h * boot);
            g.fill(0, sweep, w, sweep + 1, a(CYAN, 0.5F * (1 - boot)));
        }
    }

    private static void brackets(GuiGraphics g, int w, int h, float boot, float alpha) {
        int inset = (int) (24 + (1 - boot) * 40);
        int len = 26;
        int c = a(CYAN, alpha * 0.8F);
        int x0 = inset;
        int y0 = inset;
        int x1 = w - inset;
        int y1 = h - inset;
        g.fill(x0, y0, x0 + len, y0 + 1, c);
        g.fill(x0, y0, x0 + 1, y0 + len, c);
        g.fill(x1 - len, y0, x1, y0 + 1, c);
        g.fill(x1 - 1, y0, x1, y0 + len, c);
        g.fill(x0, y1 - 1, x0 + len, y1, c);
        g.fill(x0, y1 - len, x0 + 1, y1, c);
        g.fill(x1 - len, y1 - 1, x1, y1, c);
        g.fill(x1 - 1, y1 - len, x1, y1, c);
    }

    /** Прицельное кольцо: точки по кругу, медленно вращается. */
    private static void reticle(GuiGraphics g, int cx, int cy, float time, float alpha) {
        int c = a(CYAN, alpha * 0.9F);
        for (int i = 0; i < 16; i++) {
            if (i % 4 == 3) {
                continue;
            }
            double ang = time * 0.02 + i * Math.PI / 8;
            int x = cx + (int) Math.round(Math.cos(ang) * 14);
            int y = cy + (int) Math.round(Math.sin(ang) * 14);
            g.fill(x, y, x + 1, y + 1, c);
        }
        g.fill(cx - 22, cy, cx - 17, cy + 1, c);
        g.fill(cx + 17, cy, cx + 22, cy + 1, c);
        g.fill(cx, cy - 22, cx + 1, cy - 17, c);
        g.fill(cx, cy + 17, cx + 1, cy + 22, c);
    }

    /** Полоса компаса: стороны света по-русски и деления. */
    private static void compass(GuiGraphics g, Font font, int w, Player player, float alpha) {
        int cx = w / 2;
        int y = 10;
        int span = 120;
        float yaw = Mth.wrapDegrees(player.getYRot());
        g.fill(cx - span, y + 9, cx + span, y + 10, a(CYAN, alpha * 0.5F));
        String[] names = {"Ю", "ЮЗ", "З", "СЗ", "С", "СВ", "В", "ЮВ"};
        for (int i = 0; i < 24; i++) {
            float deg = i * 15;
            float d = Mth.wrapDegrees(deg - yaw);
            if (Math.abs(d) > 60) {
                continue;
            }
            int x = cx + Math.round(d * 2);
            boolean major = i % 3 == 0;
            g.fill(x, y + (major ? 4 : 6), x + 1, y + 9, a(CYAN, alpha * (1 - Math.abs(d) / 70)));
            if (major) {
                String n = names[i / 3];
                g.drawCenteredString(font, n, x, y - 5, a(n.length() == 1 ? GOLD : CYAN,
                        alpha * (1 - Math.abs(d) / 70)));
            }
        }
        g.fill(cx, y + 2, cx + 1, y + 12, a(GOLD, alpha));
    }

    private static void label(GuiGraphics g, Font font, String key, String value, int x, int y, float alpha) {
        String name = Component.translatable(key).getString();
        g.drawString(font, name, x, y, a(CYAN, alpha * 0.8F), false);
        g.drawString(font, value, x + 34, y, a(GOLD, alpha), false);
    }

    private static void right(GuiGraphics g, Font font, String text, int rx, int y, int colour) {
        g.drawString(font, text, rx - font.width(text), y, colour, false);
    }

    private static void bar(GuiGraphics g, int x, int y, int w, float value, int rgb, float alpha) {
        g.fill(x, y, x + w, y + 3, a(0x10202A, alpha * 0.8F));
        g.fill(x, y, x + Math.round(w * Mth.clamp(value, 0, 1)), y + 3, a(rgb, alpha));
    }

    /** Цель под прицелом: имя, расстояние, здоровье — в рамке справа от кольца. */
    private static void target(GuiGraphics g, Font font, Minecraft mc, int w, int h, float alpha) {
        Entity hit = mc.hitResult instanceof EntityHitResult e ? e.getEntity() : mc.crosshairPickEntity;
        if (hit == null || mc.player == null) {
            return;
        }
        int x = w / 2 + 30;
        int y = h / 2 - 20;
        String name = hit.getDisplayName().getString();
        double dist = mc.player.distanceTo(hit);
        int box = Math.max(80, font.width(name) + 10);
        int c = a(GOLD, alpha);
        g.fill(x, y, x + box, y + 1, c);
        g.fill(x, y, x + 1, y + 34, c);
        g.drawString(font, name, x + 5, y + 4, a(0xFFFFFF, alpha), false);
        g.drawString(font, Component.translatable("citylife.mark42.hud.distance",
                String.format("%.1f", dist)).getString(), x + 5, y + 14, a(CYAN, alpha), false);
        if (hit instanceof LivingEntity living) {
            float hp = living.getHealth() / Math.max(1, living.getMaxHealth());
            bar(g, x + 5, y + 26, box - 10, hp, hp < 0.35F ? RED : 0x7CFF8A, alpha);
        }
    }

    /** Сколько блоков до земли под ногами (−1 — дальше 64). */
    private static double groundDistance(Player player) {
        var level = player.level();
        int x = Mth.floor(player.getX());
        int z = Mth.floor(player.getZ());
        int top = Mth.floor(player.getY());
        for (int y = top; y > top - 64 && y > level.getMinBuildHeight(); y--) {
            if (!level.getBlockState(new net.minecraft.core.BlockPos(x, y - 1, z)).isAir()) {
                return player.getY() - y;
            }
        }
        return -1;
    }
}
