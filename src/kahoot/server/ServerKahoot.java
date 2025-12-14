package kahoot.server;

import kahoot.coordination.GameTask;
import kahoot.coordination.GameThreadPool;
import kahoot.coordination.ModifiedCountdownLatch;
import kahoot.coordination.ModifiedCyclicBarrier;
import kahoot.game.*;
import kahoot.gui.ServerGui;
import kahoot.messages.GameEndMessage;
import kahoot.messages.QuestionMessage;
import kahoot.messages.ScoresMessage;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.TimeoutException;

import static kahoot.utils.Utilities.*;

public class ServerKahoot {
    private Map<String, ArrayList<Integer>> answersMap;
    private Map<String, GameState> gameStateMap;
    private Map<String, Player> playerMap;
    private final Map<String, ArrayList<DealWithClient>> clientsByGame;

    //ThreadPool
    private final GameThreadPool gamePool = new GameThreadPool();

    // Para perguntas individuais: latch por jogo
    private Map<String, ModifiedCountdownLatch> latchByGame;
    private Map<String, Integer> latchQuestionIndex;

    // Para perguntas de equipa: barreira por equipa
    private Map<String, Map<String, ModifiedCyclicBarrier>> barriersByGameAndTeam;
    private Map<String, Map<String, List<Boolean>>> teamAnswersByGame; // Boolean para correto/incorreto

    private final Map<String, Thread> timerThreads = new HashMap<>();

    private final Map<String, Integer> questionFinishedGeneration = new HashMap<>();

    public static final int KAHOOT_PORT = 8080;
    private static final int QUESTION_TIMEOUT_MS = 30000; // 30 segundos

    public static void main(String[] args) {
        try {
            new ServerKahoot().startServer();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ServerKahoot() {
        this.gameStateMap = new HashMap<>();
        this.playerMap = new HashMap<>();
        this.clientsByGame = new HashMap<>();
        this.answersMap = new HashMap<>();
        this.latchByGame = new HashMap<>();
        this.latchQuestionIndex = new HashMap<>();
        this.barriersByGameAndTeam = new HashMap<>();
        this.teamAnswersByGame = new HashMap<>();
    }

    public void startServer() throws IOException {
        ServerSocket ss = new ServerSocket(KAHOOT_PORT);
        System.out.println("Kahoot server is online.");

        Quiz quiz = loadQuiz();
        Thread cmdThread = new Thread(() -> insertCommand(quiz), "ServerCommandThread");
        cmdThread.setDaemon(true);
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
                //Cria GameTask e submete na ThreadPool
                gamePool.submit(new GameTask(this, gameState));

            }
        }
    }

    //Metodo run do GameTask
    public void runGame(GameState gameState){
        broadcastNewQuestionToClients(gameState.getGameID());
        gameState.startGame();
    }

    private synchronized boolean isIndividualQuestion(Integer questionIndex) {
        return questionIndex % 2 == 0;
    }

    /**
     * Regista resposta e retorna o multiplicador de pontuação.
     * Para perguntas individuais: usa CountDownLatch
     * Para perguntas de equipa: usa CyclicBarrier
     */
    public synchronized void registerAnswer(String playerName, Integer answerIndex, String gameName) {
        GameState gameState = gameStateMap.get(gameName);
        if (gameState == null) {
            return;
        }

        int currentQuestionIndex = gameState.getCurrentQuestionIndex().get();
        ArrayList<Integer> playerAnswers = answersMap.computeIfAbsent(playerName, k -> new ArrayList<>());

        // Ignorar respostas duplicadas
        if (playerAnswers.size() > currentQuestionIndex) {
            return;
        }

        playerAnswers.add(answerIndex);
        Player player = playerMap.get(playerName);
        if (player == null) {
            return;
        }

        String teamName = player.getTeamName();
        Question question = gameState.getCurrentQuestion();
        if (question == null) {
            return;
        }

        boolean isCorrect = question.isCorrect(answerIndex);
        int multiplier;

        if (isIndividualQuestion(currentQuestionIndex)) {
            // PERGUNTA INDIVIDUAL: usa CountDownLatch
            handleIndividualQuestion(gameName, gameState, playerName, teamName,
                    currentQuestionIndex, question, isCorrect);
        } else {
            // PERGUNTA DE EQUIPA: usa CyclicBarrier
            handleTeamQuestion(gameName, gameState, playerName, teamName,
                    currentQuestionIndex, question, isCorrect);
        }
    }

    /**
     * Trata pergunta individual com CountDownLatch.
     * Primeiros 2 jogadores recebem multiplicador 2x dos pontos da pergunta.
     */
    private void handleIndividualQuestion(String gameName, GameState gameState, String playerName, String teamName, int questionIndex, Question question, boolean isCorrect) {
        ModifiedCountdownLatch latch = latchByGame.get(gameName);
        Integer storedQ = latchQuestionIndex.get(gameName);

        if (latch == null || storedQ == null || storedQ != questionIndex) {
            int total = Math.max(1, gameState.getTotalNumberOfPlayers());
            latch = new ModifiedCountdownLatch(2, 2, QUESTION_TIMEOUT_MS, total);
            latchByGame.put(gameName, latch);
            latchQuestionIndex.put(gameName, questionIndex);

            final ModifiedCountdownLatch finalLatch = latch;
            new Thread(() -> {
                try {
                    finalLatch.await(); // ends on all answers or timeout
                    // from this point, we always allow advancing this question
                    handleQuestionComplete(gameName, gameState, true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "LatchAwait-" + gameName + "-q" + questionIndex).start();
        }

        int multiplier = latch.countdown();

        if (isCorrect) {
            int basePoints = question.getPoints();
            int points = basePoints * multiplier;
            awardPoints(gameState, playerName, teamName, points);

            broadcastScoresToClients(gameName);
        }
    }

    /**
     * Trata pergunta de equipa com CyclicBarrier.
     * Aguarda todas as respostas da equipa ou timeout.
     */
    private void handleTeamQuestion(String gameName, GameState gameState, String playerName, String teamName, int questionIndex, Question question, boolean isCorrect) {
        Map<String, ModifiedCyclicBarrier> barriers = barriersByGameAndTeam.computeIfAbsent(
                gameName, k -> new HashMap<>()
        );
        Map<String, List<Boolean>> teamAnswers = teamAnswersByGame.computeIfAbsent(
                gameName, k -> new HashMap<>()
        );

        Team team = gameState.getTeam(teamName);
        if (team == null) {
            return;
        }

        int teamSize = team.getNumberOfPlayers();
        String barrierKey = teamName + "_q" + questionIndex;

        if (!barriers.containsKey(barrierKey)) {
            Runnable barrierAction = () -> calculateTeamScore(gameName, gameState, teamName,
                    questionIndex, question, barrierKey);
            ModifiedCyclicBarrier barrier =
                    new ModifiedCyclicBarrier(teamSize, QUESTION_TIMEOUT_MS, barrierAction);
            barriers.put(barrierKey, barrier);
            teamAnswers.put(barrierKey, Collections.synchronizedList(new ArrayList<>()));
        }

        List<Boolean> answers = teamAnswers.get(barrierKey);
        answers.add(isCorrect);

        ModifiedCyclicBarrier barrier = barriers.get(barrierKey);

        try {
            barrier.await(); // bloqueia até todos os jogadores da equipa responderem ou timeout
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (TimeoutException e) {
            // timeout: barrierAction já foi executada na própria barreira
        }
    }

    /**
     * Calcula pontuação da equipa após todas as respostas ou timeout.
     * Todos corretos: 2x pontos base da pergunta.
     * Algum errado: apenas melhor resposta individual (1x pontos base).
     */
    private synchronized void calculateTeamScore(String gameName, GameState gameState, String teamName, int questionIndex, Question question, String barrierKey) {
        Map<String, List<Boolean>> teamAnswers = teamAnswersByGame.get(gameName);
        if (teamAnswers == null) {
            return;
        }

        List<Boolean> answers = teamAnswers.get(barrierKey);
        if (answers == null || answers.isEmpty()) {
            return;
        }

        boolean allCorrect = answers.stream().allMatch(a -> a);
        long correctCount = answers.stream().filter(a -> a).count();

        int basePoints = question.getPoints();
        int points;

        if (allCorrect) {
            points = basePoints * 2;      // todos acertam \-> cotação duplicada
        } else if (correctCount > 0) {
            points = basePoints;          // alguma correta \-> melhor resposta (sem bónus)
        } else {
            points = 0;                   // todas erradas
        }

        if (points > 0) {
            Map<String, Integer> teamScores = gameState.getTeamScores();
            teamScores.put(teamName, teamScores.getOrDefault(teamName, 0) + points);

            broadcastScoresToClients(gameName);
        }

        // limpar respostas desta pergunta/equipa
        teamAnswers.remove(barrierKey);

        // depois de tratar a pontuação da equipa, verificar se a pergunta terminou
        handleQuestionComplete(gameName, gameState, true);
    }

    private synchronized void handleQuestionComplete(String gameName, GameState gameState, boolean timeout) {
        int currentQuestionIndex = gameState.getCurrentQuestionIndex().get();

        // ignore repeated calls for the same game/question
        Integer alreadyFinished = questionFinishedGeneration.get(gameName);
        if (alreadyFinished != null && alreadyFinished == currentQuestionIndex) {
            return;
        }

        int answeredPlayers = 0;

        Collection<Player> playersInGame = gameState.getPlayers();
        if (playersInGame == null) {
            return;
        }

        for (Player p : playersInGame) {
            ArrayList<Integer> answers = answersMap.get(p.getUsername());
            if (answers != null && answers.size() > currentQuestionIndex) {
                answeredPlayers++;
            }
        }

        if (timeout || answeredPlayers >= gameState.getTotalNumberOfPlayers()) {
            // mark this question as completed
            questionFinishedGeneration.put(gameName, currentQuestionIndex);

            // stop timer for this question
            stopQuestionTimer(gameName);

            // clear per\-question structures
            latchByGame.remove(gameName);
            latchQuestionIndex.remove(gameName);
            barriersByGameAndTeam.remove(gameName);
            teamAnswersByGame.remove(gameName);

            if (gameState.nextQuestion()) {
                System.out.println("Game over for game: " + gameName);
                broadcastGameEndToClients(gameName);
            } else {
                if (timeout) {
                    System.out.println("Question timeout. Showing next question in game: " + gameName);
                } else {
                    System.out.println("All players answered. Showing next question in game: " + gameName);
                }
                broadcastNewQuestionToClients(gameName);
                broadcastScoresToClients(gameName);
            }
        }
    }

    private void awardPoints(GameState gameState, String playerName, String teamName, int points) {
        Map<String, Integer> playerScores = gameState.getPlayerScores();
        playerScores.put(playerName, playerScores.getOrDefault(playerName, 0) + points);

        Map<String, Integer> teamScores = gameState.getTeamScores();
        teamScores.put(teamName, teamScores.getOrDefault(teamName, 0) + points);
    }

    // ... (resto dos métodos permanecem iguais)

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

    public synchronized void registerClientForGame(String gameId, DealWithClient client) {
        clientsByGame.computeIfAbsent(gameId, k -> new ArrayList<>()).add(client);
    }

    public synchronized void unregisterClient(DealWithClient client) {
        if (client == null) return;
        String gameId = client.getGameId();
        if (gameId == null) return;

        ArrayList<DealWithClient> list = clientsByGame.get(gameId);
        if (list == null) return;

        list.remove(client);
        if (list.isEmpty()) {
            clientsByGame.remove(gameId);
        }
    }

    public synchronized ArrayList<DealWithClient> getClientsToBroadcast(String gameId) {
        ArrayList<DealWithClient> clients = clientsByGame.get(gameId);
        if (clients == null || clients.isEmpty()) return null;
        return new ArrayList<>(clients);
    }

    public synchronized void broadcastScoresToClients(String gameId) {
        GameState gameState = gameStateMap.get(gameId);
        ArrayList<DealWithClient> clients = getClientsToBroadcast(gameId);
        if (clients == null) return;

        for (DealWithClient client : clients) {
            try {
                client.sendObject(new ScoresMessage(ScoresMessagesId.getAndAdd(1),
                        gameState.getTeamScores(), gameState.getPlayerScores()));
            } catch (Exception e) {
                System.out.println("Failed to send scores: " + e.getMessage());
            }
        }
    }

    public synchronized void broadcastGameEndToClients(String gameId) {
        GameState gameState = gameStateMap.get(gameId);
        ArrayList<DealWithClient> clients = getClientsToBroadcast(gameId);
        if (clients == null) return;

        // stop any running timer for this game
        stopQuestionTimer(gameId);

        for (DealWithClient client : clients) {
            try {
                client.sendObject(new GameEndMessage(GameEndMessagesId.getAndAdd(1),
                        gameState.getTeamScores(), gameState.getPlayerScores()));
                gameStateMap.remove(gameId);
            } catch (Exception e) {
                System.out.println("Failed to send game end: " + e.getMessage());
            }
        }

        //Indicação à pool que pode iniciar outro jogo
        gamePool.gameFinished();
    }

    public synchronized void broadcastNewQuestionToClients(String gameId) {
        GameState gameState = gameStateMap.get(gameId);
        ArrayList<DealWithClient> clients = getClientsToBroadcast(gameId);
        if (clients == null) return;

        for (DealWithClient client : clients) {
            try {
                Question question = gameState.getCurrentQuestion();
                if (question == null) continue;

                client.sendObject(new QuestionMessage(
                        QuestionMessagesId.getAndAdd(1),
                        question.getQuestion(),
                        gameState.getCurrentQuestionIndex().get(),
                        isIndividualQuestion(gameState.getCurrentQuestionIndex().get()),
                        question.getOptions()
                ));
            } catch (Exception e) {
                System.out.println("Failed to send question: " + e.getMessage());
            }
        }

        // start timer for this question
        startQuestionTimer(gameId);
    }

    private synchronized void startQuestionTimer(String gameId) {
        // stop previous timer if any
        Thread old = timerThreads.get(gameId);
        if (old != null && old.isAlive()) {
            old.interrupt();
        }

        GameState gameState = gameStateMap.get(gameId);
        if (gameState == null) return;

        int qIndex = gameState.getCurrentQuestionIndex().get();
        long totalMillis = QUESTION_TIMEOUT_MS;

        Thread t = new Thread(() -> {
            long end = System.currentTimeMillis() + totalMillis;
            while (!Thread.currentThread().isInterrupted()) {
                long remaining = end - System.currentTimeMillis();
                if (remaining <= 0) {
                    remaining = 0;
                }

                // broadcast timer
                broadcastTimerToClients(gameId, qIndex, remaining);

                if (remaining <= 0) {
                    // timer reached 0 -> force finish this question as timeout
                    synchronized (ServerKahoot.this) {
                        GameState gs = gameStateMap.get(gameId);
                        if (gs != null) {
                            handleQuestionComplete(gameId, gs, true);
                        }
                    }
                    break;
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "Timer-" + gameId + "-q" + qIndex);
        t.setDaemon(true);
        timerThreads.put(gameId, t);
        t.start();
    }

    private synchronized void stopQuestionTimer(String gameId) {
        Thread t = timerThreads.remove(gameId);
        if (t != null && t.isAlive()) {
            t.interrupt();
        }
    }

    private synchronized void broadcastTimerToClients(String gameId, int questionIndex, long remainingMillis) {
        ArrayList<DealWithClient> clients = getClientsToBroadcast(gameId);
        if (clients == null) return;

        for (DealWithClient client : clients) {
            try {
                client.sendObject(new kahoot.messages.TimerMessage(
                        TimerMessagesId.getAndAdd(1),
                        gameId,
                        questionIndex,
                        remainingMillis
                ));
            } catch (Exception e) {
                System.out.println("Failed to send timer: " + e.getMessage());
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
            } else if(command.startsWith("view ")) {
                String[] params = command.split(" ");
                if(params.length == 2) {
                    GameState gameState = gameStateMap.get(params[1]);
                    if (gameState == null) {
                        System.out.println("Game ID not found: " + params[1]);
                        System.out.println("Command:");
                        continue;
                    }
                    new ServerGui(params[1] + " view").displayGame(gameState.getCurrentQuestionIndex().get() + 1,
                            quiz.questions.size(), gameState.getPlayerScores(), gameState.getTeamScores());
                } else {
                    System.out.println("Command not recognized. Usage: view <param1>");
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