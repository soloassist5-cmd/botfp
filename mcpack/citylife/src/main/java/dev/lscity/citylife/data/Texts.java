package dev.lscity.citylife.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lscity.citylife.CityLife;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Русские тексты на стороне сервера.
 *
 * Сообщения в чат мод шлёт ключами, и их переводит клиент. Но письма,
 * названия заданий и прочие строки, которые сервер хранит или вставляет
 * в другие строки, нужны уже готовым текстом, а выделенный сервер языковые
 * файлы модов не загружает — Component.getString() вернул бы сам ключ.
 * Поэтому такие строки берём прямо из ru_ru.json внутри jar мода.
 */
public final class Texts {

    private static Map<String, String> ru;

    private Texts() {
    }

    private static synchronized Map<String, String> table() {
        if (ru != null) {
            return ru;
        }
        Map<String, String> out = new HashMap<>();
        try (InputStream in = Texts.class.getResourceAsStream(
                "/assets/citylife/lang/ru_ru.json")) {
            if (in != null) {
                JsonObject root = JsonParser.parseReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                root.entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
            }
        } catch (Exception error) {
            CityLife.LOG.error("City Life: не прочитать ru_ru.json", error);
        }
        ru = out;
        return out;
    }

    /** Строка по ключу с подстановкой %s (и %1$s…) по порядку. */
    public static String ru(String key, Object... args) {
        String text = table().getOrDefault(key, key);
        try {
            return String.format(text, args);
        } catch (java.util.IllegalFormatException error) {
            return text;
        }
    }
}
