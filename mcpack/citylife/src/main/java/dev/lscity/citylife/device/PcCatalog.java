package dev.lscity.citylife.device;

import java.util.List;

/**
 * Программы компьютера сверх общих с ноутбуком: конвертеры, калькуляторы,
 * инструменты, справочники, «живые» сведения и игры. Вместе с базовыми их
 * на компьютере больше сотни.
 *
 * Здесь только идентификаторы — сервер по ним собирает рабочий стол.
 * Как программа выглядит и что делает, знает клиент (client/device/pc/PcApps).
 */
public final class PcCatalog {

    public static final List<String> CONVERTERS = List.of("conv_length", "conv_mass", "conv_temp",
            "conv_speed", "conv_area", "conv_volume", "conv_time", "conv_data", "conv_pressure",
            "conv_energy", "conv_currency", "conv_angle");
    public static final List<String> CALCULATORS = List.of("calc_bmi", "calc_loan", "calc_deposit",
            "calc_tip", "calc_percent", "calc_discount", "calc_vat", "calc_fuel", "calc_average",
            "calc_proportion", "calc_base", "calc_roman");
    public static final List<String> TOOLS = List.of("stopwatch", "timer", "password", "random",
            "dice", "coin", "color", "tally", "todo", "morse", "cipher", "textstats", "metronome",
            "piano");
    public static final List<String> INFO = List.of("guide_city", "guide_laws", "guide_jobs",
            "guide_estate", "guide_cars", "guide_guns", "guide_phones", "guide_pc", "guide_metro",
            "guide_tips", "facts", "jokes", "quotes", "recipes", "horoscope", "history");
    public static final List<String> LIVE = List.of("clock", "calendar", "weather", "gps",
            "online", "wallet", "bankview", "moon", "system", "inventory");
    public static final List<String> GAMES = List.of("tictactoe", "rps", "guess", "reaction",
            "memory", "puzzle15", "lightsout", "simon", "hangman", "blackjack", "slots", "connect4",
            "typing", "mathquiz", "quiz", "breakout");

    /** Все программы каталога в порядке значков на столе. */
    public static final List<String> ALL;

    static {
        java.util.List<String> all = new java.util.ArrayList<>();
        all.addAll(LIVE);
        all.addAll(TOOLS);
        all.addAll(CALCULATORS);
        all.addAll(CONVERTERS);
        all.addAll(INFO);
        all.addAll(GAMES);
        ALL = List.copyOf(all);
    }

    private PcCatalog() {
    }
}
