package kahoot.server;

import kahoot.game.*;
import kahoot.messages.GameEndMessage;
import kahoot.messages.QuestionMessage;
import kahoot.messages.ScoresMessage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import static kahoot.utils.Utilities.*;

public class ServerKahoot {
    private BufferedReader in;
    private PrintWriter out;

    private Map<String, ArrayList<Integer>> answersMap;
    private Map<String, GameState> gameStateMap;
    private Map<String, Player> playerMap;
    private final Map<String, ArrayList<DealWithClient>> clientsByGame;

    public static final int KAHOOT_PORT = 8080;

    public static void main(String[] args) {
        try {
            new ServerKahoot().startServer();
        } catch (IOException e) {
            // ...
        }
    }

    public ServerKahoot() {
        this.gameStateMap = new HashMap<>();
        this.playerMap = new HashMap<>();
        this.clientsByGame = new HashMap<>();
        this.answersMap = new HashMap<>();
    }

    public void startServer() throws IOException {
        ServerSocket ss = new ServerSocket(KAHOOT_PORT);
        System.out.println("Kahoot server is online.");

        // Load quiz once and start the console command loop in a separate thread.
        Quiz quiz = loadQuiz();
        Thread cmdThread = new Thread(() -> insertCommand(quiz), "ServerCommandThread");
        cmdThread.setDaemon(true); // optional: keep JVM running until other non-daemon threads finish
        cmdThread.start();

        try {
            while(true) {
                Socket socket = ss.accept();
                DealWithClient dealWithClient = new DealWithClient(socket, this);
                dealWithClient.start();
            }
        } finally {
            ss.close();
        }
    }

    public synchronized void checkIfGameIsFull(String gameId) {
        GameState gameState = gameStateMap.get(gameId);
        if (gameState == null) {
            System.out.println("The Game does not exist: " + gameId);
        } else {
            if(gameState.isGameFull()) {
                System.out.println("The game is now full... Starting game: " + gameId);
                broadcastNewQuestionToClients(gameId);
                gameState.startGame();
            }
        }
    }

    public synchronized void registerAnswer(String playerName, Integer answerIndex, String gameName) {
        GameState gameState = gameStateMap.get(gameName);
        if (gameState == null) {
            return;
        }

        int currentQuestionIndex = gameState.getCurrentQuestionIndex().get();

        // ensure we have the player's answers list
        ArrayList<Integer> playerAnswers = answersMap.computeIfAbsent(playerName, k -> new ArrayList<>());

        // if player already submitted for the current question, ignore duplicate
        if (playerAnswers.size() > currentQuestionIndex) {
            return;
        }

        // record the answer for this question
        playerAnswers.add(answerIndex);

        // count how many distinct players have at least one answer for the current question
        int answeredPlayers = 0;
        for (ArrayList<Integer> answers : answersMap.values()) {
            if (answers.size() > currentQuestionIndex) {
                answeredPlayers++;
            }
        }

        if (answeredPlayers >= gameState.getTotalNumberOfPlayers()) {
            if(gameState.nextQuestion()) {
                System.out.println("Game over for game: " + gameName);
                broadcastGameEndToClients(gameName);
            } else {
                System.out.println("All players have answered the question. Showing Scoreboard in game: " + gameName);
                broadcastScoresToClients(gameName);

                /*System.out.println("All players have answered the question. Moving to next question in game: " + gameName);
                broadcastNewQuestionToClients(gameName);*/
            }
        }
    }

    // called by DealWithClient when a player enrolls
    public synchronized boolean registerPlayer(String gameId, Player player) {
        if (player == null) {
            return false;
        }

        if (playerMap.containsKey(player.getUsername())) {
            System.out.println("Username already exists: " + player.getUsername());
            return false;
        }

        GameState gameState = gameStateMap.get(gameId);
        if (gameState == null) {
            System.out.println("The Game does not exist: " + gameId);
            return false;
        }

        if(!gameState.teamExists(player.getTeamName())) {
            if(gameState.getNumberOfTeams() >= gameState.getMaxNumberOfTeams()) {
                System.out.println("The game already has the max amount of teams: " + gameId);
                return false;
            }

            gameState.addTeam(player.getTeamName());
        }

        if(gameState.getTeam(player.getTeamName()).getNumberOfPlayers() >= gameState.getMaxPlayersPerTeam()) {
            System.out.println("This team already has the max amount of players: " + gameId + " - " + player.getTeamName());
            return false;
        }

        playerMap.put(player.getUsername(), player);
        gameState.addPlayerToTeam(player);

        System.out.print(player);

        return true;
    }

    // register a connection under a game id so the server can broadcast to all game clients
    public synchronized void registerClientForGame(String gameId, DealWithClient client) {
        clientsByGame.computeIfAbsent(gameId, k -> new ArrayList<>()).add(client);
    }

    // unregister a client (called on cleanup)
    public synchronized void unregisterClient(DealWithClient client) {
        if (client == null){
            return;
        }

        String gameId = client.getGameId();
        if (gameId == null){
            return;
        }

        ArrayList<DealWithClient> list = clientsByGame.get(gameId);
        if (list == null){
            return;
        }

        list.remove(client);
        if (list.isEmpty()) {
            clientsByGame.remove(gameId);
        }
    }

    public synchronized ArrayList<DealWithClient> getClientsToBroadcast(String gameId) {
        ArrayList<DealWithClient> clients = clientsByGame.get(gameId);
        if (clients == null || clients.isEmpty()){
            return null;
        }

        // defensive copy to avoid concurrent modification while clients cleanup themselves
        return new ArrayList<>(clients);
    }

    public synchronized void broadcastScoresToClients(String gameId) {
        GameState gameState = gameStateMap.get(gameId);
        for (DealWithClient dealWithClient : getClientsToBroadcast(gameId)) {
            try {
                dealWithClient.sendObject(new ScoresMessage(ScoresMessagesId.getAndAdd(1), gameState.getTeamScores(), gameState.getPlayerScores()));
            } catch (Exception e) {
                // defensive: log and continue; client cleanup may happen elsewhere
                System.out.println("Failed to send to client: " + e.getMessage());
            }
        }
    }

    public synchronized void broadcastGameEndToClients(String gameId) {
        GameState gameState = gameStateMap.get(gameId);
        for (DealWithClient dealWithClient : getClientsToBroadcast(gameId)) {
            try {
                dealWithClient.sendObject(new GameEndMessage(GameEndMessagesId.getAndAdd(1), gameState.getTeamScores(), gameState.getPlayerScores()));
            } catch (Exception e) {
                // defensive: log and continue; client cleanup may happen elsewhere
                System.out.println("Failed to send to client: " + e.getMessage());
            }
        }
    }

    // send current question object or game over message to every client connected to the game
    public synchronized void broadcastNewQuestionToClients(String gameId) {
        GameState gameState = gameStateMap.get(gameId);
        for (DealWithClient dealWithClient : getClientsToBroadcast(gameId)) {
            try {
                Question question = gameState.getCurrentQuestion();
                if (question == null) {
                    // nothing to send for this non-game-over broadcast
                    continue;
                }
                dealWithClient.sendObject(new QuestionMessage(
                        QuestionMessagesId.getAndAdd(1),
                        question.getQuestion(),
                        gameState.getCurrentQuestionIndex().get(),
                        question.getOptions()
                ));
            } catch (Exception e) {
                // defensive: log and continue; client cleanup may happen elsewhere
                System.out.println("Failed to send to client: " + e.getMessage());
            }
        }
    }

    public void insertCommand(Quiz quiz) {
        System.out.println("Command:");
        Scanner scanner = new Scanner(System.in);

        while(scanner.hasNextLine()) {
            String command = scanner.nextLine();
            if (command.isEmpty()) {
                System.out.println("Command:");
                continue;
            }

            if(command.startsWith("new ")) {
                String[] params = command.split(" ");

                if(params.length == 4 && isNumeric(params[1]) && isNumeric(params[2]) && isNumeric(params[3])) {
                    GameState gameState = new GameState(
                            "game" + GameStateId.getAndAdd(1),
                            Integer.valueOf(params[1]),
                            Integer.valueOf(params[2]),
                            Integer.valueOf(params[3]),
                            quiz
                    );
                    gameStateMap.put(gameState.getGameID(), gameState);
                    System.out.println(gameState);
                } else {
                    System.out.println("Command not recognized. Usage: new <param1> <param2> <param3> (all numeric)");
                }
            } else if(command.equals("quit")) {
                System.exit(0);
            } else {
                System.out.println("Command not recognized.");
            }

            System.out.println("Command:");
        }
    }
}