package kahoot.coordination;

import kahoot.game.GameState;
import kahoot.server.ServerKahoot;

public class GameTask implements Runnable {

    private final ServerKahoot server;
    private final GameState gameState;

    public GameTask(ServerKahoot server, GameState gameState) {
        this.server = server;
        this.gameState = gameState;
    }

    @Override
    public void run() {
        server.runGame(gameState);
    }
}

