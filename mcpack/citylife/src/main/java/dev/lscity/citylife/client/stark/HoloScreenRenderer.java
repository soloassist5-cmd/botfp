package dev.lscity.citylife.client.stark;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.lscity.citylife.stark.HoloScreenBlockEntity;
import dev.lscity.citylife.stark.StarkSecurity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Голо-мониторы башни STARK. Экран — полупрозрачное стекло с голубой
 * подсветкой, сеткой, уголками и бегущей строкой развёртки; содержимое
 * живое и зависит от режима. При тревоге всё краснеет и мигает.
 */
@OnlyIn(Dist.CLIENT)
public class HoloScreenRenderer implements BlockEntityRenderer<HoloScreenBlockEntity> {

    /** Пикселей экрана на блок: строка шрифта — 9 пикселей. */
    private static final int PX = 64;
    private static final int CYAN = 0xFF57D8FF;
    private static final int RED = 0xFFFF4040;
    private static final int GOLD = 0xFFFFC857;
    private static final int GREEN = 0xFF5CFF9D;

    private static final String[] TICKER = {"STRK", "OSCP", "HMMR", "RAND", "ROXN", "AIMX", "PYMT", "SHLD"};

    public HoloScreenRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(HoloScreenBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 48;
    }

    @Override
    public void render(HoloScreenBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }
        Direction facing = be.facing();
        int w = be.width() * PX;
        int h = be.height() * PX;
        float t = (be.getLevel().getGameTime() + partial) / 20F;
        boolean alarm = StarkClient.alarm;
        int accent = alarm ? RED : CYAN;

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(-0.5, -0.5 + be.height(), -0.5 + 1.5F / 16F);
        pose.scale(1F / PX, -1F / PX, 1F / PX);
        HoloDraw d = new HoloDraw(pose, buffers, Minecraft.getInstance().font);

        chrome(d, w, h, t, accent, alarm);
        d.layer();
        switch (be.mode()) {
            case "security" -> security(d, w, h, t, accent);
            case "armor" -> armor(d, w, h, t, accent);
            case "radar" -> radar(d, be, w, h, t, accent);
            case "reactor" -> reactor(d, w, h, t, accent);
            case "stocks" -> stocks(d, w, h, t, accent, be.getLevel().getDayTime() / 24000L);
            default -> jarvis(d, w, h, t, accent);
        }
        if (alarm && (int) (t * 2) % 2 == 0) {
            d.layer();
            d.rect(0, h / 2F - 11, w, h / 2F + 11, 0xC0400000);
            String text = "⚠ ТРЕВОГА ⚠";
            d.bigText(text, (w - d.width(text) * 1.5F) / 2, h / 2F - 7, 1.5F, 0xFFFFFFFF);
        }
        pose.popPose();
    }

    /** Стекло, сетка, рамка, уголки, шапка с часами и строка развёртки. */
    private static void chrome(HoloDraw d, int w, int h, float t, int accent, boolean alarm) {
        d.rect(0, 0, w, h, alarm ? 0xB8240608 : 0xB8061420);
        d.layer();
        int grid = alpha(accent, 0.08F);
        for (int x = 16; x < w; x += 16) {
            d.rect(x, 18, x + 0.5F, h - 2, grid);
        }
        for (int y = 32; y < h; y += 16) {
            d.rect(2, y, w - 2, y + 0.5F, grid);
        }
        d.frame(0, 0, w, h, 0.75F, alpha(accent, 0.6F));
        int c = alpha(accent, 0.95F);
        float l = Math.min(14, Math.min(w, h) / 6F);
        for (int i = 0; i < 4; i++) {
            float x = i % 2 == 0 ? 0 : w;
            float y = i < 2 ? 0 : h;
            float sx = i % 2 == 0 ? 1 : -1;
            float sy = i < 2 ? 1 : -1;
            d.rect(Math.min(x, x + sx * l), Math.min(y, y + sy * 2), Math.max(x, x + sx * l),
                    Math.max(y, y + sy * 2), c);
            d.rect(Math.min(x, x + sx * 2), Math.min(y, y + sy * l), Math.max(x, x + sx * 2),
                    Math.max(y, y + sy * l), c);
        }
        d.rect(4, 15, w - 4, 15.75F, alpha(accent, 0.5F));
        float scan = (t * 24) % h;
        d.rect(1, scan, w - 1, scan + 1.5F, alpha(accent, 0.12F));
        d.layer();
        var level = Minecraft.getInstance().level;
        String clock = level == null ? "" : StarkSecurity.clock(level.getDayTime());
        d.text(clock, w - 6 - d.width(clock), 5, alpha(accent, 0.9F));
        if ((int) (t * 1.5F) % 2 == 0) {
            d.rect(w - 14 - d.width(clock), 7, w - 10 - d.width(clock), 11, alarm ? RED : GREEN);
        }
        d.text("STARK INDUSTRIES", 6, h - 11, alpha(accent, 0.35F));
    }

    // --- режимы -------------------------------------------------------------------

    private static void jarvis(HoloDraw d, int w, int h, float t, int accent) {
        d.text("J.A.R.V.I.S.", 6, 5, accent);
        float r = Math.min((h - 30) / 2F, w / 4F);
        float cx = 10 + r;
        float cy = 18 + (h - 30) / 2F;
        d.ring(cx, cy, r - 1, r, alpha(accent, 0.4F));
        for (int i = 0; i < 3; i++) {
            float a = t * 40 + i * 120;
            d.arc(cx, cy, r - 6, r - 3, a, a + 70, alpha(accent, 0.85F));
        }
        for (int i = 0; i < 6; i++) {
            float a = -t * 25 + i * 60;
            d.arc(cx, cy, r * 0.62F, r * 0.62F + 2, a, a + 38, alpha(accent, 0.6F));
        }
        // Голос: столбики по кругу пляшут, как волна речи.
        float inner = r * 0.34F;
        for (int i = 0; i < 48; i++) {
            float wave = (float) Math.abs(Math.sin(t * 3 + i * 0.7) * Math.cos(t * 1.3 + i * 0.21));
            float a = i * 7.5F;
            d.arc(cx, cy, inner, inner + 2 + wave * r * 0.22F, a, a + 4, alpha(accent, 0.75F));
        }
        float pulse = 0.55F + 0.45F * (float) Math.sin(t * 4);
        d.ring(cx, cy, 0, inner * 0.55F, alpha(0xFFE8FBFF, 0.35F + 0.4F * pulse));
        int tx = (int) (cx + r + 10);
        int width = w - tx - 6;
        String[] lines = {
                "СИСТЕМЫ: НОРМА",
                "ОХРАНА: " + (StarkClient.alarm ? "ТРЕВОГА" : StarkClient.armed ? "ВКЛЮЧЕНА" : "ВЫКЛЮЧЕНА"),
                "БРОНЯ: " + StarkClient.suits().size() + " ГОТОВЫ",
                "РЕАКТОР: 3.0 ГВт",
                "ПРИНТЕР: ОЖИДАНИЕ",
                "ПОГОДА: ЯСНО, +24°",
        };
        int y = 22;
        for (int i = 0; i < lines.length && y < h - 22; i++, y += 11) {
            d.text(d.fit(lines[i], width), tx, y, i == 1 && StarkClient.alarm ? RED : alpha(accent, 0.9F));
        }
        String hello = "Добрый день, сэр.";
        int shown = Math.min(hello.length(), (int) (t * 8 % (hello.length() + 24)));
        d.text(d.fit(hello.substring(0, shown) + ((int) (t * 3) % 2 == 0 ? "_" : ""), width), tx, h - 24, 0xFFE8FBFF);
    }

    private static void security(HoloDraw d, int w, int h, float t, int accent) {
        d.text("STARK SECURITY", 6, 5, accent);
        String status = StarkClient.alarm ? "ТРЕВОГА: " + StarkClient.by
                : StarkClient.armed ? "ОХРАНА ВКЛЮЧЕНА" : "ОХРАНА ВЫКЛЮЧЕНА";
        d.text(d.fit(status, w - 12), 6, 20, StarkClient.alarm ? RED : StarkClient.armed ? GREEN : GOLD);
        // Сетка камер 2×2: коридор в перспективе, шум и мигающая запись.
        int camsW = w * 3 / 5 - 8;
        int top = 33;
        int camH = (h - top - 16) / 2 - 3;
        int camW = camsW / 2 - 3;
        for (int i = 0; i < 4; i++) {
            float x0 = 6 + (i % 2) * (camW + 6);
            float y0 = top + (i / 2) * (camH + 6);
            d.rect(x0, y0, x0 + camW, y0 + camH, 0xCC02070B);
            float vx = x0 + camW / 2F + (float) Math.sin(t * 0.3 + i) * camW * 0.08F;
            float vy = y0 + camH * 0.45F;
            int line = alpha(accent, 0.35F);
            d.line(x0, y0, vx - camW * 0.12F, vy - camH * 0.12F, 0.8F, line);
            d.line(x0 + camW, y0, vx + camW * 0.12F, vy - camH * 0.12F, 0.8F, line);
            d.line(x0, y0 + camH, vx - camW * 0.12F, vy + camH * 0.12F, 0.8F, line);
            d.line(x0 + camW, y0 + camH, vx + camW * 0.12F, vy + camH * 0.12F, 0.8F, line);
            d.frame(vx - camW * 0.12F, vy - camH * 0.12F, vx + camW * 0.12F, vy + camH * 0.12F, 0.8F, line);
            long seed = (long) (t * 12) * 31 + i * 7919L;
            for (int n = 0; n < 14; n++) {
                seed = seed * 6364136223846793005L + 1442695040888963407L;
                float nx = x0 + ((seed >>> 33) % Math.max(1, camW));
                float ny = y0 + ((seed >>> 17) % Math.max(1, camH));
                d.rect(nx, ny, nx + 1, ny + 1, 0x55FFFFFF);
            }
            d.frame(x0, y0, x0 + camW, y0 + camH, 0.6F, alpha(accent, 0.7F));
            d.text("CAM 0" + (i + 1), x0 + 3, y0 + 2, alpha(accent, 0.9F));
            if ((int) (t * 2 + i) % 2 == 0) {
                d.rect(x0 + camW - 7, y0 + 3, x0 + camW - 3, y0 + 7, RED);
            }
        }
        int lx = w * 3 / 5 + 2;
        int width = w - lx - 6;
        d.text("ЖУРНАЛ", lx, top, alpha(accent, 0.6F));
        int y = top + 11;
        for (int i = 0; i < StarkClient.LOG.size() && y < h - 20; i++, y += 10) {
            d.text(d.fit(StarkClient.LOG.get(i), width), lx, y,
                    StarkClient.LOG_ALARM.get(i) ? RED : alpha(0xFFE8FBFF, 0.85F));
        }
        if (StarkClient.LOG.isEmpty()) {
            d.text("Событий нет", lx, y, alpha(accent, 0.6F));
        }
    }

    private static void armor(HoloDraw d, int w, int h, float t, int accent) {
        List<String> suits = StarkClient.suits();
        d.text("HALL OF ARMOR · " + suits.size(), 6, 5, accent);
        // Схема костюма справа: силуэт и светящийся реактор на груди.
        float sx = w - 38;
        float sy = 24;
        float s = Math.min(1.4F, (h - 40) / 70F);
        int line = alpha(accent, 0.8F);
        d.frame(sx - 7 * s, sy, sx + 7 * s, sy + 14 * s, 1, line);
        d.frame(sx - 11 * s, sy + 16 * s, sx + 11 * s, sy + 40 * s, 1, line);
        d.frame(sx - 18 * s, sy + 16 * s, sx - 13 * s, sy + 40 * s, 1, line);
        d.frame(sx + 13 * s, sy + 16 * s, sx + 18 * s, sy + 40 * s, 1, line);
        d.frame(sx - 10 * s, sy + 42 * s, sx - 2 * s, sy + 68 * s, 1, line);
        d.frame(sx + 2 * s, sy + 42 * s, sx + 10 * s, sy + 68 * s, 1, line);
        float pulse = 0.5F + 0.5F * (float) Math.sin(t * 5);
        d.ring(sx, sy + 24 * s, 0, 3 * s, alpha(0xFFE8FBFF, 0.5F + 0.5F * pulse));
        d.ring(sx, sy + 24 * s, 3.5F * s, 4.5F * s, line);
        float scanY = sy + (t * 20 % (68 * s));
        d.rect(sx - 20 * s, scanY, sx + 20 * s, scanY + 1, alpha(accent, 0.5F));
        // Список марок прокручивается снизу вверх.
        int width = (int) (sx - 26 * s) - 12;
        int rows = (h - 40) / 10;
        int first = suits.isEmpty() ? 0 : (int) (t / 1.5F) % suits.size();
        for (int i = 0; i < rows && !suits.isEmpty(); i++) {
            int k = (first + i) % suits.size();
            float y = 21 + i * 10;
            String name = d.fit(suits.get(k), width - 44);
            d.text(name, 6, y, alpha(0xFFE8FBFF, 0.9F));
            float fill = 0.6F + 0.4F * (float) Math.abs(Math.sin(k * 1.7 + t * 0.2));
            float bx = 6 + width - 40;
            d.rect(bx, y + 2, bx + 38, y + 6, alpha(accent, 0.25F));
            d.rect(bx, y + 2, bx + 38 * fill, y + 6, fill > 0.95F ? GREEN : accent);
        }
    }

    private static void radar(HoloDraw d, HoloScreenBlockEntity be, int w, int h, float t, int accent) {
        d.text("RADAR · 160 м", 6, 5, accent);
        float r = Math.min((h - 30) / 2F, w / 3F);
        float cx = 10 + r;
        float cy = 18 + (h - 30) / 2F;
        for (int i = 1; i <= 4; i++) {
            d.ring(cx, cy, r * i / 4 - 0.6F, r * i / 4, alpha(accent, 0.35F));
        }
        d.rect(cx - r, cy - 0.3F, cx + r, cy + 0.3F, alpha(accent, 0.25F));
        d.rect(cx - 0.3F, cy - r, cx + 0.3F, cy + r, alpha(accent, 0.25F));
        float sweep = t * 90 % 360;
        for (int i = 0; i < 10; i++) {
            d.arc(cx, cy, 0, r, sweep - i * 4 - 4, sweep - i * 4, alpha(accent, 0.3F - i * 0.028F));
        }
        d.text("N", cx - 2, cy - r - 9, alpha(accent, 0.8F));
        int tx = (int) (cx + r + 10);
        int y = 22;
        var level = be.getLevel();
        var origin = be.getBlockPos().getCenter();
        if (level != null) {
            for (Player p : level.players()) {
                double dx = p.getX() - origin.x;
                double dz = p.getZ() - origin.z;
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 160) {
                    continue;
                }
                float px = cx + (float) (dx / 160 * r);
                float py = cy + (float) (dz / 160 * r);
                d.rect(px - 1.5F, py - 1.5F, px + 1.5F, py + 1.5F, GOLD);
                if (y < h - 20) {
                    d.text(d.fit(p.getGameProfile().getName() + "  " + (int) dist + " м", w - tx - 6), tx, y,
                            alpha(0xFFE8FBFF, 0.9F));
                    y += 10;
                }
            }
        }
        d.rect(cx - 2, cy - 2, cx + 2, cy + 2, accent);
    }

    private static void reactor(HoloDraw d, int w, int h, float t, int accent) {
        d.text("ARC REACTOR", 6, 5, accent);
        float r = Math.min((h - 30) / 2F, w / 4F);
        float cx = 10 + r;
        float cy = 18 + (h - 30) / 2F;
        float pulse = 0.6F + 0.4F * (float) Math.sin(t * 6);
        d.ring(cx, cy, r - 2, r, alpha(accent, 0.8F));
        for (int i = 0; i < 10; i++) {
            float a = i * 36 + t * 6;
            d.arc(cx, cy, r * 0.55F, r - 5, a + 4, a + 30, alpha(accent, 0.45F + 0.3F * pulse));
        }
        // Треугольник нового элемента.
        float tr = r * 0.45F;
        float rot = t * 10;
        float[] px = new float[3];
        float[] py = new float[3];
        for (int i = 0; i < 3; i++) {
            double a = Math.toRadians(rot + i * 120);
            px[i] = cx + (float) Math.sin(a) * tr;
            py[i] = cy - (float) Math.cos(a) * tr;
        }
        for (int i = 0; i < 3; i++) {
            d.line(px[i], py[i], px[(i + 1) % 3], py[(i + 1) % 3], 1.5F, 0xFFE8FBFF);
        }
        d.ring(cx, cy, 0, r * 0.22F, alpha(0xFFFFFFFF, 0.5F + 0.5F * pulse));
        // График мощности.
        float gx = cx + r + 10;
        float gw = w - gx - 8;
        float gy = 30;
        float gh = h - gy - 34;
        d.frame(gx, gy, gx + gw, gy + gh, 0.6F, alpha(accent, 0.5F));
        float prev = 0;
        for (int i = 0; i <= 40; i++) {
            float x = gx + gw * i / 40;
            float v = 0.55F + 0.25F * (float) Math.sin((i + t * 8) * 0.35) + 0.08F * (float) Math.sin((i + t * 8) * 1.7);
            float y = gy + gh * (1 - v);
            if (i > 0) {
                d.line(x - gw / 40, prev, x, y, 1.2F, accent);
            }
            prev = y;
        }
        float gw2 = 3.0F + 0.04F * (float) Math.sin(t * 2);
        d.text(String.format("ВЫХОД %.2f ГВт", gw2), (int) gx, (int) (gy + gh + 4), 0xFFE8FBFF);
        d.text("ПАЛЛАДИЙ: 0%  НОВЫЙ ЭЛЕМЕНТ", (int) gx, (int) (gy - 11), alpha(accent, 0.7F));
    }

    private static void stocks(HoloDraw d, int w, int h, float t, int accent, long day) {
        d.text("STRK · NASDAQ", 6, 5, accent);
        float gx = 8;
        float gy = 34;
        float gw = w - 16;
        float gh = h - gy - 30;
        long seed = day * 2654435761L;
        float[] values = new float[60];
        float v = 0.5F;
        for (int i = 0; i < values.length; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            v += ((seed >>> 40) % 1000 / 1000F - 0.47F) * 0.08F;
            v = Math.max(0.1F, Math.min(0.9F, v));
            values[i] = v + 0.02F * (float) Math.sin(t * 2 + i);
        }
        for (int i = 1; i < 4; i++) {
            d.rect(gx, gy + gh * i / 4, gx + gw, gy + gh * i / 4 + 0.4F, alpha(accent, 0.15F));
        }
        for (int i = 1; i < values.length; i++) {
            float x0 = gx + gw * (i - 1) / (values.length - 1);
            float x1 = gx + gw * i / (values.length - 1);
            boolean up = values[i] >= values[i - 1];
            d.line(x0, gy + gh * (1 - values[i - 1]), x1, gy + gh * (1 - values[i]), 1.2F, up ? GREEN : RED);
        }
        float price = 412.30F + values[values.length - 1] * 40;
        float change = (values[values.length - 1] - values[0]) * 10;
        d.bigText(String.format("%.2f $", price), 6, 18, 1.2F, 0xFFE8FBFF);
        String ch = String.format("%+.2f%%", change);
        d.text(ch, w - 8 - d.width(ch), 20, change >= 0 ? GREEN : RED);
        // Бегущая строка котировок.
        StringBuilder line = new StringBuilder();
        long s2 = day * 97;
        for (String name : TICKER) {
            s2 = s2 * 6364136223846793005L + 1442695040888963407L;
            float p = ((s2 >>> 40) % 1000 - 450) / 100F;
            line.append(name).append(String.format(" %+.1f%%   ", p));
        }
        String text = line.toString();
        int idx = (int) (t * 4) % text.length();
        d.text(d.clip((text + text).substring(idx), w - 12), 6, h - 22, alpha(0xFFE8FBFF, 0.8F));
    }

    static int alpha(int argb, float factor) {
        int a = Math.round(((argb >>> 24) & 255) * Math.max(0F, Math.min(1F, factor)));
        return (a << 24) | (argb & 0xFFFFFF);
    }
}
