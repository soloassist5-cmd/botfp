package dev.lscity.citylife.estate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lscity.citylife.CityLife;
import net.minecraft.core.BlockPos;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Каталог жилья города: дома, виллы, секции таунхаусов и квартиры.
 *
 * Файл data/citylife/estate.json собирает генератор города
 * (citylife/tools/gen_estate.py) тем же кодом, что строит дома, поэтому
 * границы совпадают с постройкой блок в блок. Каталог лежит в jar мода и
 * одинаков на клиенте и сервере — по сети идут только владельцы.
 */
public final class Estate {

    /** Объект недвижимости. box — сам дом, plot — вся собственность, door — вход. */
    public record Unit(String id, String kind, String title, String address, String district,
                       long price, String rooms, int[] box, int[] plot, BlockPos door) {

        /** Бизнес приносит доход, но открыт для всех: без замков и защиты участка. */
        public boolean business() {
            return "business".equals(kind);
        }

        public boolean inBox(double x, double y, double z) {
            return inside(box, x, y, z);
        }

        public boolean inBox(BlockPos pos) {
            return inside(box, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        }

        public boolean inPlot(BlockPos pos) {
            return inside(plot, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        }

        /** Куда выставить чужого: две клетки наружу от входной двери. */
        public BlockPos outside() {
            int x = door.getX();
            int z = door.getZ();
            int dx = 0;
            int dz = 0;
            if (x <= box[0]) {
                dx = -1;
            } else if (x >= box[3]) {
                dx = 1;
            } else if (z <= box[2]) {
                dz = -1;
            } else {
                dz = 1;
            }
            return new BlockPos(x + dx * 2, door.getY(), z + dz * 2);
        }

        /** «Дом — улица, номер»: так объект называют в сообщениях и списках. */
        public String label() {
            return title + " — " + address;
        }

        private static boolean inside(int[] b, double x, double y, double z) {
            return x >= b[0] && x < b[3] + 1 && y >= b[1] && y < b[4] + 1
                    && z >= b[2] && z < b[5] + 1;
        }
    }

    private static List<Unit> units;
    /** Камера в участке и выход из него (из того же файла каталога). */
    private static BlockPos jailCell;
    private static BlockPos jailExit;
    private static Map<String, Unit> byId;
    /** Индекс по чанкам: какие объекты задевают чанк (по plot). */
    private static Map<Long, List<Unit>> byChunk;

    private Estate() {
    }

    public static synchronized List<Unit> all() {
        if (units == null) {
            load();
        }
        return units;
    }

    public static Unit get(String id) {
        all();
        return byId.get(id);
    }

    /** Камера в полицейском участке, или null, если в каталоге её нет. */
    public static BlockPos jailCell() {
        all();
        return jailCell;
    }

    /** Куда выпускают из камеры: тротуар у входа в участок. */
    public static BlockPos jailExit() {
        all();
        return jailExit;
    }

    /** Объект, в собственности которого лежит блок (двор, стены, крыша). */
    public static Unit plotAt(BlockPos pos) {
        all();
        for (Unit unit : byChunk.getOrDefault(key(pos.getX() >> 4, pos.getZ() >> 4), List.of())) {
            if (unit.inPlot(pos) && !unit.business()) {
                return unit;
            }
        }
        return null;
    }

    /** Дом (или квартира), внутри которого точка. */
    public static Unit boxAt(double x, double y, double z) {
        all();
        int cx = ((int) Math.floor(x)) >> 4;
        int cz = ((int) Math.floor(z)) >> 4;
        for (Unit unit : byChunk.getOrDefault(key(cx, cz), List.of())) {
            if (unit.inBox(x, y, z) && !unit.business()) {
                return unit;
            }
        }
        return null;
    }

    public static Unit boxAt(BlockPos pos) {
        return boxAt(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    /** Добавить объект вне каталога — для автотестов на своей площадке. */
    public static synchronized void addTestUnit(Unit unit) {
        all();
        byId.put(unit.id(), unit);
        int[] p = unit.plot();
        for (int cx = p[0] >> 4; cx <= p[3] >> 4; cx++) {
            for (int cz = p[2] >> 4; cz <= p[5] >> 4; cz++) {
                byChunk.computeIfAbsent(key(cx, cz), k -> new ArrayList<>()).add(0, unit);
            }
        }
    }

    public static synchronized void removeTestUnit(String id) {
        all();
        Unit unit = byId.remove(id);
        if (unit != null) {
            byChunk.values().forEach(list -> list.remove(unit));
        }
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    private static void load() {
        List<Unit> list = new ArrayList<>();
        try (InputStream in = Estate.class.getResourceAsStream("/data/citylife/estate.json")) {
            if (in != null) {
                JsonObject root = JsonParser.parseReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                if (root.has("jail")) {
                    JsonObject jail = root.getAsJsonObject("jail");
                    int[] c = ints(jail.getAsJsonArray("cell"));
                    int[] e = ints(jail.getAsJsonArray("exit"));
                    jailCell = new BlockPos(c[0], c[1], c[2]);
                    jailExit = new BlockPos(e[0], e[1], e[2]);
                }
                for (JsonElement element : root.getAsJsonArray("units")) {
                    JsonObject o = element.getAsJsonObject();
                    int[] door = ints(o.getAsJsonArray("door"));
                    list.add(new Unit(o.get("id").getAsString(), o.get("kind").getAsString(),
                            o.get("title").getAsString(), o.get("address").getAsString(),
                            o.get("district").getAsString(), o.get("price").getAsLong(),
                            o.get("rooms").getAsString(), ints(o.getAsJsonArray("box")),
                            ints(o.getAsJsonArray("plot")),
                            new BlockPos(door[0], door[1], door[2])));
                }
            }
        } catch (Exception error) {
            CityLife.LOG.error("City Life: не прочитать каталог недвижимости", error);
        }
        Map<String, Unit> ids = new LinkedHashMap<>();
        Map<Long, List<Unit>> chunks = new HashMap<>();
        for (Unit unit : list) {
            ids.put(unit.id(), unit);
            int[] p = unit.plot();
            for (int cx = p[0] >> 4; cx <= p[3] >> 4; cx++) {
                for (int cz = p[2] >> 4; cz <= p[5] >> 4; cz++) {
                    chunks.computeIfAbsent(key(cx, cz), k -> new ArrayList<>()).add(unit);
                }
            }
        }
        units = Collections.unmodifiableList(list);
        byId = ids;
        byChunk = chunks;
        CityLife.LOG.info("City Life: в каталоге недвижимости {} объектов", list.size());
    }

    private static int[] ints(JsonArray array) {
        int[] out = new int[array.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.get(i).getAsInt();
        }
        return out;
    }
}
