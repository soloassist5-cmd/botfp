package dev.lscity.citylife.data;

import dev.lscity.citylife.CityConfig;
import dev.lscity.citylife.CityLife;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Резервные копии мира: раз в час (настраивается) весь мир упаковывается в
 * backups/<мир>-<дата>.zip, хранятся последние N штук.
 *
 * Как у /save-off: сначала всё сохраняется на диск, на время упаковки
 * автосохранение выключается, архив пишется в отдельном потоке — сервер не
 * подвисает. Вернуть копию — скрипт restore-backup рядом с start.bat.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class Backups {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");
    private static volatile boolean running;
    private static long nextAt;

    private Backups() {
    }

    private static boolean enabled(MinecraftServer server) {
        return CityConfig.CONFIG.backupMinutes.get() > 0
                && (server.isDedicatedServer() || CityConfig.CONFIG.backupSingleplayer.get());
    }

    private static void schedule() {
        nextAt = System.currentTimeMillis() + CityConfig.CONFIG.backupMinutes.get() * 60_000L;
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        schedule();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        MinecraftServer server = event.getServer();
        if (event.phase != TickEvent.Phase.END || server == null || server.getTickCount() % 200 != 0
                || running || System.currentTimeMillis() < nextAt || !enabled(server)) {
            return;
        }
        schedule();
        make(server, message -> CityLife.LOG.info(message));
    }

    public static Path folder(MinecraftServer server) {
        return server.getServerDirectory().toPath().toAbsolutePath().normalize().resolve("backups");
    }

    /** Список копий этого мира, от новой к старой. */
    public static List<Path> list(MinecraftServer server) {
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        String prefix = world.getFileName() + "-";
        List<Path> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder(server))) {
            files.filter(p -> p.getFileName().toString().startsWith(prefix)
                    && p.getFileName().toString().endsWith(".zip")).forEach(out::add);
        } catch (IOException ignored) {
            // папки ещё нет — копий нет
        }
        out.sort((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()));
        return out;
    }

    /**
     * Сделать копию сейчас. done получает строку-итог уже в потоке сервера.
     * false — копия уже делается.
     */
    public static boolean make(MinecraftServer server, Consumer<String> done) {
        if (running) {
            return false;
        }
        running = true;
        server.saveEverything(true, true, true);
        Map<ServerLevel, Boolean> noSave = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            noSave.put(level, level.noSave);
            level.noSave = true;
        }
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path dir = folder(server);
        String name = world.getFileName() + "-" + LocalDateTime.now().format(STAMP) + ".zip";
        int keep = CityConfig.CONFIG.backupKeep.get();
        Thread thread = new Thread(() -> {
            String result;
            try {
                long started = System.currentTimeMillis();
                Files.createDirectories(dir);
                Path part = dir.resolve(name + ".part");
                zip(world, part);
                Files.move(part, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                long size = Files.size(dir.resolve(name)) / (1024 * 1024);
                List<Path> all = list(server);
                for (int i = keep; i < all.size(); i++) {
                    Files.deleteIfExists(all.get(i));
                }
                result = Texts.ru("citylife.backup.done", name, String.valueOf(size),
                        String.valueOf((System.currentTimeMillis() - started) / 1000));
            } catch (IOException | RuntimeException e) {
                CityLife.LOG.error("City Life: резервная копия не получилась", e);
                result = Texts.ru("citylife.backup.failed", String.valueOf(e.getMessage()));
            }
            String message = result;
            server.execute(() -> {
                noSave.forEach((level, value) -> level.noSave = value);
                running = false;
                done.accept(message);
            });
        }, "citylife-backup");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    private static void zip(Path world, Path target) throws IOException {
        String root = world.getFileName().toString();
        try (OutputStream out = Files.newOutputStream(target);
             ZipOutputStream zip = new ZipOutputStream(out);
             Stream<Path> files = Files.walk(world)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                if (!Files.isRegularFile(file) || file.getFileName().toString().equals("session.lock")) {
                    continue;
                }
                String rel = world.relativize(file).toString().replace('\\', '/');
                try {
                    zip.putNextEntry(new ZipEntry(root + "/" + rel));
                    Files.copy(file, zip);
                    zip.closeEntry();
                } catch (IOException e) {
                    // Файл заняли в эту секунду — пропускаем его, копия остаётся годной.
                    CityLife.LOG.warn("City Life: в копию не попал {}: {}", rel, e.getMessage());
                }
            }
        }
    }
}
