package dev.lscity.citylife.client.device.pc;

import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.client.device.DeviceApp;
import dev.lscity.citylife.client.device.DeviceScreen;
import dev.lscity.citylife.device.PcCatalog;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Программы компьютера: название, цвета значка, символ на значке и как
 * программу создать. Идентификаторы — из PcCatalog (его же видит сервер).
 */
@OnlyIn(Dist.CLIENT)
public final class PcApps {

    /** Описание программы. glyph — символ или пара букв на значке. */
    public record Def(String id, String title, int top, int bottom, String glyph,
                      Function<DeviceScreen, DeviceApp> make) {
    }

    private static final Map<String, Def> DEFS = new LinkedHashMap<>();

    private static void add(String id, String title, int top, int bottom, String glyph,
                            Function<DeviceScreen, DeviceApp> make) {
        DEFS.put(id, new Def(id, title, top, bottom, glyph, make));
    }

    private static void info(String id, String title, int top, int bottom, String glyph,
                             java.util.function.Supplier<java.util.List<InfoApp.Section>> text,
                             boolean cards) {
        add(id, title, top, bottom, glyph, s -> new InfoApp(s, id, text.get(), cards));
    }

    private static void conv(String id, String title, String glyph, String note,
                             java.util.function.Supplier<java.util.List<ConverterApp.Unit>> units) {
        add(id, title, 0xFF4FC3A1, 0xFF1F7A63, glyph, s -> new ConverterApp(s, id, note, units.get()));
    }

    private static void calc(String id, String title, String glyph, Function<DeviceScreen, DeviceApp> make) {
        add(id, title, 0xFF6C7BFF, 0xFF3442B0, glyph, make);
    }

    static {
        // Живые сведения.
        add("clock", "Часы", 0xFF2F3A55, 0xFF141A2B, "◷", ClockApp::new);
        add("calendar", "Календарь", 0xFFFF7A7A, 0xFFC03A3A, "31", CalendarApp::new);
        add("weather", "Погода", 0xFF5CC8FF, 0xFF2A7FD0, "☁", WeatherApp::new);
        add("gps", "Координаты", 0xFF3FB27F, 0xFF1D6E4C, "⌖", GpsApp::new);
        add("online", "Кто в сети", 0xFF8E7BFF, 0xFF4C3FD0, "☺", OnlineApp::new);
        add("wallet", "Кошелёк", 0xFFE0B04A, 0xFF9A6F1C, "₽", WalletApp::new);
        add("bankview", "Финансы", 0xFF57D48B, 0xFF1F8F57, "▤", BankViewApp::new);
        add("moon", "Луна", 0xFF4A4F7A, 0xFF1C1F3A, "☾", MoonApp::new);
        add("system", "Монитор системы", 0xFF2FD1A0, 0xFF16806A, "≋", SystemApp::new);
        add("inventory", "Инвентарь", 0xFFB08A5A, 0xFF6A4E2A, "▦", InventoryApp::new);

        // Инструменты.
        add("stopwatch", "Секундомер", 0xFFFF9F43, 0xFFD06A1E, "⏱", StopwatchApp::new);
        add("timer", "Таймер", 0xFFFF6B6B, 0xFFB03434, "⌛", TimerApp::new);
        add("password", "Пароли", 0xFF5A6478, 0xFF2A303E, "***", PasswordApp::new);
        add("random", "Случайное число", 0xFF7BE07B, 0xFF2F9E4F, "?", RandomApp::new);
        add("dice", "Кости", 0xFFF4F4F4, 0xFFB8BEC9, "⚄", DiceApp::new);
        add("coin", "Монетка", 0xFFE8C35A, 0xFFA07A1A, "◎", CoinApp::new);
        add("color", "Палитра", 0xFFFF5C8A, 0xFF6F3FD0, "◐", ColorApp::new);
        add("tally", "Счётчик", 0xFF45A3F0, 0xFF1A5FA8, "+1", TallyApp::new);
        add("todo", "Список дел", 0xFFFFE27A, 0xFFE0A93A, "✔", TodoApp::new);
        add("morse", "Азбука Морзе", 0xFF3A3F50, 0xFF15171F, "·–", MorseApp::new);
        add("cipher", "Шифратор", 0xFF2A2F38, 0xFF0E1014, "Аб", CipherApp::new);
        add("textstats", "Счётчик слов", 0xFF9AA3B5, 0xFF555D70, "Т", TextStatsApp::new);
        add("metronome", "Метроном", 0xFFC77DFF, 0xFF7B2FBF, "♩", MetronomeApp::new);
        add("piano", "Пианино", 0xFF2A2A2A, 0xFF000000, "♫", PianoApp::new);

        // Калькуляторы.
        calc("calc_bmi", "Индекс массы тела", "ИМТ", FormApp::bmi);
        calc("calc_loan", "Кредит", "%", FormApp::loan);
        calc("calc_deposit", "Вклад", "₽↑", FormApp::deposit);
        calc("calc_tip", "Чаевые", "☕", FormApp::tip);
        calc("calc_percent", "Проценты", "%%", FormApp::percent);
        calc("calc_discount", "Скидка", "−%", FormApp::discount);
        calc("calc_vat", "НДС", "НДС", FormApp::vat);
        calc("calc_fuel", "Бензин в дорогу", "⛽", FormApp::fuel);
        calc("calc_average", "Среднее", "x̄", FormApp::average);
        calc("calc_proportion", "Пропорция", "a:b", FormApp::proportion);
        calc("calc_base", "Системы счисления", "0x", FormApp::base);
        calc("calc_roman", "Римские цифры", "XIV", FormApp::roman);

        // Конвертеры.
        conv("conv_length", "Длина", "м", "1 блок = 1 метр", ConverterApp::length);
        conv("conv_mass", "Масса", "кг", "", ConverterApp::mass);
        conv("conv_temp", "Температура", "°", "", ConverterApp::temperature);
        conv("conv_speed", "Скорость", "км/ч", "блок/тик — скорость в игре", ConverterApp::speed);
        conv("conv_area", "Площадь", "м²", "чанк — 16 × 16 блоков", ConverterApp::area);
        conv("conv_volume", "Объём", "л", "ведро — 1 м³", ConverterApp::volume);
        conv("conv_time", "Время", "ч", "игровые сутки — 20 минут", ConverterApp::time);
        conv("conv_data", "Данные", "МБ", "", ConverterApp::data);
        conv("conv_pressure", "Давление", "атм", "", ConverterApp::pressure);
        conv("conv_energy", "Энергия", "кВт", "", ConverterApp::energy);
        conv("conv_currency", "Валюты", "$", "Курс городского банка, условный", ConverterApp::currency);
        conv("conv_angle", "Углы", "∠", "", ConverterApp::angle);

        // Справочники.
        info("guide_city", "Путеводитель", 0xFF45D0F0, 0xFF1A7FA8, "LS", InfoTexts::city, false);
        info("guide_laws", "Законы города", 0xFF1C2B5A, 0xFF0E1530, "⚖", InfoTexts::laws, false);
        info("guide_jobs", "Работа и доход", 0xFFFFC857, 0xFFD9822B, "₽+", InfoTexts::jobs, false);
        info("guide_estate", "Недвижимость", 0xFF6FD08C, 0xFF2E8A5A, "⌂", InfoTexts::estate, false);
        info("guide_cars", "Автомобилисту", 0xFFFF7A45, 0xFFB04A1E, "Авто",
                InfoTexts::cars, false);
        info("guide_guns", "Оружие и закон", 0xFF5A5F6E, 0xFF2A2D36, "⌖", InfoTexts::guns, false);
        info("guide_phones", "Каталог телефонов", 0xFF3FA9F5, 0xFF1E6FD0, "☎", InfoTexts::phones, false);
        info("guide_pc", "Сборка ПК", 0xFF2FD1A0, 0xFF16806A, "PC", InfoTexts::pc, false);
        info("guide_metro", "Метро", 0xFFB03434, 0xFF701A1A, "М", InfoTexts::metro, false);
        info("guide_tips", "Советы новичку", 0xFF7BE07B, 0xFF2F9E4F, "!", InfoTexts::tips, false);
        info("facts", "Интересные факты", 0xFFFFB347, 0xFFCC7A00, "i", InfoTexts::facts, true);
        info("jokes", "Анекдоты", 0xFFFF8AD8, 0xFFB0408A, "☻", InfoTexts::jokes, true);
        info("quotes", "Цитаты", 0xFFB8A27A, 0xFF7A6440, "«»", InfoTexts::quotes, true);
        info("recipes", "Рецепты", 0xFFE08A4A, 0xFFA0501A, "♨", InfoTexts::recipes, false);
        info("horoscope", "Гороскоп", 0xFF6F3FD0, 0xFF2A1A60, "★", InfoTexts::horoscope, true);
        info("history", "История города", 0xFF8C6E4A, 0xFF4A3820, "Ⅰ", InfoTexts::history, false);

        // Игры.
        add("tictactoe", "Крестики-нолики", 0xFF5AA8FF, 0xFF2F55D6, "×○", TicTacToeApp::new);
        add("rps", "Камень, ножницы, бумага", 0xFFFFA24D, 0xFFD06A1E, "✊", RpsApp::new);
        add("guess", "Угадай число", 0xFF7BE07B, 0xFF2F9E4F, "1?", GuessApp::new);
        add("reaction", "Реакция", 0xFFFF6B6B, 0xFFC02B3F, "⚡", ReactionApp::new);
        add("memory", "Найди пару", 0xFFB07BFF, 0xFF6F3FD0, "▣", MemoryApp::new);
        add("puzzle15", "Пятнашки", 0xFFF2B179, 0xFFD27A2E, "15", Puzzle15App::new);
        add("lightsout", "Погаси свет", 0xFFFFE27A, 0xFF8A6A1A, "☀", LightsOutApp::new);
        add("simon", "Саймон", 0xFF45D0F0, 0xFFD04A2E, "◕", SimonApp::new);
        add("hangman", "Виселица", 0xFF9AA3B5, 0xFF4A5268, "А_", HangmanApp::new);
        add("blackjack", "Блэкджек", 0xFF2E8A5A, 0xFF14462C, "21", BlackjackApp::new);
        add("slots", "Слоты", 0xFFFF5C8A, 0xFFB0245A, "7", SlotsApp::new);
        add("connect4", "Четыре в ряд", 0xFFFF6B6B, 0xFFE0C040, "●", Connect4App::new);
        add("typing", "Слепая печать", 0xFF3A3F50, 0xFF15171F, "⌨", TypingApp::new);
        add("mathquiz", "Устный счёт", 0xFF4A4F5E, 0xFF22252E, "2+2", MathQuizApp::new);
        add("quiz", "Викторина", 0xFFC77DFF, 0xFF7B2FBF, "?!", QuizApp::new);
        add("breakout", "Арканоид", 0xFF5B6B8C, 0xFF263049, "▬", BreakoutApp::new);

        for (String id : PcCatalog.ALL) {
            if (!DEFS.containsKey(id)) {
                CityLife.LOG.warn("City Life: у программы {} нет описания на клиенте", id);
            }
        }
    }

    private PcApps() {
    }

    public static Def get(String id) {
        return DEFS.get(id);
    }

    public static int count() {
        return DEFS.size();
    }
}
