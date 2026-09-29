package dev.lscity.citylife.client.device;

import dev.lscity.citylife.client.game.SnakeGame;
import dev.lscity.citylife.client.game.TetrisGame;
import net.minecraft.client.gui.GuiGraphics;

/** Игры из магазина: тетрис и змейка. Состояние живёт, пока открыт экран. */
class GameApp extends DeviceApp {

    private final String id;
    private TetrisGame tetris;
    private SnakeGame snake;

    GameApp(DeviceScreen screen, String id) {
        super(screen);
        this.id = id;
    }

    @Override
    public String title() {
        return screen.appTitle(id);
    }

    @Override
    public void render(GuiGraphics g, int[] area, int mouseX, int mouseY) {
        if (id.equals("tetris")) {
            if (tetris == null) {
                tetris = new TetrisGame();
            }
            tetris.tick();
            tetris.render(g, screen.font(), area[0], area[1], area[2], area[3]);
        } else {
            if (snake == null) {
                snake = new SnakeGame();
            }
            snake.tick();
            snake.render(g, screen.font(), area[0], area[1], area[2], area[3]);
        }
    }

    @Override
    public boolean key(int code) {
        if (tetris != null) {
            return tetris.key(code);
        }
        return snake != null && snake.key(code);
    }
}
