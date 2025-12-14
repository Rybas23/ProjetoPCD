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
    private final Map<String, ArrayList<Integer>> answersMap = new HashMap<>();
    private final Map<String, GameState> gameStateMap = new HashMap<>();
    private final Map<String, Player> playerMap = new HashMap<>();
    private final Map<String, ArrayList<DealWithClient>> clientsByGame = new HashMap<>();

    //ThreadPool
    private final GameThreadPool gamePool = new GameThreadPool();

    // Para perguntas individuais: latch por jogo
    private final Map<String, ModifiedCountdownLatch> latchByGame = new HashMap<>();
    private final Map<String, Integer> latchQuestionIndex = new HashMap<>();

    // Para perguntas de equipa: barreira por equipa
    private final Map<String, Map<String, ModifiedCyclicBarrier>> barriersByGameAndTeam = new HashMap<>();
    private final Map<String, Map<String, List<Boolean>>> teamAnswersByGame = new HashMap<>();

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
    public void registerAnswer(String playerName, Integer answerIndex, String gameName) {
        GameState gameState;
        int currentQuestionIndex;
        ArrayList<Integer> playerAnswers;
        Player player;
        Question question;
        boolean isIndividual;
        boolean isCorrect;

        synchronized (this) {
            gameState = gameStateMap.get(gameName);
            if (gameState == null) {
                return;
            }

            currentQuestionIndex = gameState.getCurrentQuestionIndex().get();
            playerAnswers = answersMap.computeIfAbsent(playerName, k -> new ArrayList<>());

            // ignore duplicate answers
            if (playerAnswers.size() > currentQuestionIndex) {
                return;
            }

            playerAnswers.add(answerIndex);
            player = playerMap.get(playerName);
            if (player == null) {
                return;
            }

            question = gameState.getCurrentQuestion();
            if (question == null) {
                return;
            }

            isIndividual = isIndividualQuestion(currentQuestionIndex);
            isCorrect = question.isCorrect(answerIndex);
        }

        if (isIndividual) {
            // PERGUNTA INDIVIDUAL: usa CountDownLatch
            handleIndividualQuestion(gameName, playerName, player.getTeamName(),
                    currentQuestionIndex, question, isCorrect);
        } else {
            // PERGUNTA DE EQUIPA: usa CyclicBarrier
            handleTeamQuestion(gameName, player.getTeamName(),
                    currentQuestionIndex, question, isCorrect);
        }
    }

    /**
     * Trata pergunta individual com CountDownLatch.
     * Primeiros 2 jogadores recebem multiplicador 2x dos pontos da pergunta.
     */
    private void handleIndividualQuestion(String gameName, String playerName, String teamName, int questionIndex, Question question, boolean isCorrect) {
        ModifiedCountdownLatch latch = latchByGame.get(gameName);
        Integer storedQ = latchQuestionIndex.get(gameName);

        GameState gameState = gameStateMap.get(gameName);
        if (gameState == null) {
            return;
        }

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
            gameState.awardPoints(playerName, teamName, points);

            broadcastScoresToClients(gameName);
        }
    }

    /**
     * Trata pergunta de equipa com CyclicBarrier.
     * Aguarda todas as respostas da equipa ou timeout.
     */
    private void handleTeamQuestion(String gameName, String teamName, int questionIndex, Question question, boolean isCorrect) {
        Map<String, ModifiedCyclicBarrier> barriers = barriersByGameAndTeam.computeIfAbsent(
                gameName, k -> new HashMap<>()
        );
        Map<String, List<Boolean>> teamAnswers = teamAnswersByGame.computeIfAbsent(
                gameName, k -> new HashMap<>()
        );

        GameState gameState = gameStateMap.get(gameName);
        if (gameState == null) {
            return;
        }

        Team team = gameState.getTeam(teamName);
        if (team == null) {
            return;
        }

        int teamSize = team.getNumberOfPlayers();
        String barrierKey = teamName + "_q" + questionIndex;

        if (!barriers.containsKey(barrierKey)) {
            Runnable barrierAction = () -> calculateTeamScore(gameName, teamName,
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
    private synchronized void calculateTeamScore(String gameName, String teamName, int questionIndex, Question question, String barrierKey) {
        GameState gameState = gameStateMap.get(gameName);
        if (gameState == null) {
            return;
        }

        Map<String, List<Boolean>> teamAnswers = teamAnswersByGame.get(gameName);
        if (teamAnswers == null) {
            return;
        }

        List<Boolean> answers = teamAnswers.get(barrierKey);
        if (answers == null || answers.isEmpty()) {
            return;
        }

        boolean allCorrect = answers.stream().allMatch(a -> a);
        int basePoints = question.getPoints();

        // collect players in the team
        List<Player> teamPlayers = new ArrayList<>();
        for (Player p : gameState.getPlayers()) {
            if (teamName.equals(p.getTeamName())) {
                teamPlayers.add(p);
            }
        }

        if (teamPlayers.isEmpty()) {
            // clear and finish
            teamAnswers.remove(barrierKey);
            handleQuestionComplete(gameName, gameState, true);
            return;
        }

        // find which players actually answered correctly (using answersMap)
        List<Player> correctPlayers = new ArrayList<>();
        for (Player p : teamPlayers) {
            ArrayList<Integer> playerAnswers = answersMap.get(p.getUsername());
            if (playerAnswers != null && playerAnswers.size() > questionIndex) {
                Integer ansIdx = playerAnswers.get(questionIndex);
                if (ansIdx != null && question.isCorrect(ansIdx)) {
                    correctPlayers.add(p);
                }
            }
        }

        if (!correctPlayers.isEmpty()) {
            if (allCorrect && correctPlayers.size() == teamPlayers.size()) {
                // every team member correct -> each player gets 2x base points
                int ptsPerPlayer = basePoints * 2;
                for (Player p : teamPlayers) {
                    gameState.awardPoints(p.getUsername(), teamName, ptsPerPlayer);
                }
            } else {
                // some correct -> each correct player gets base points
                for (Player p : correctPlayers) {
                    gameState.awardPoints(p.getUsername(), teamName, basePoints);
                }
            }

            // broadcast updated scores
            broadcastScoresToClients(gameName);
        }

        // clear stored answers for this question/team
        teamAnswers.remove(barrierKey);

        // after handling team score, check question completion
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
        if (clients == null || gameState == null) return;

        Map<String, Integer> teamScoresSnapshot = gameState.getTeamScores();
        Map<String, Integer> playerScoresSnapshot = gameState.getPlayerScores();

        for (DealWithClient client : clients) {
            try {
                client.sendObject(new ScoresMessage(ScoresMessagesId.getAndAdd(1),
                        teamScoresSnapshot, playerScoresSnapshot));
            } catch (Exception e) {
                System.out.println("Failed to send scores: " + e.getMessage());
            }
        }
    }

    public synchronized void broadcastGameEndToClients(String gameId) {
        GameState gameState = gameStateMap.get(gameId);
        ArrayList<DealWithClient> clients = getClientsToBroadcast(gameId);
        if (clients == null || gameState == null) return;

        // stop any running timer for this game
        stopQuestionTimer(gameId);

        Map<String, Integer> teamScoresSnapshot = gameState.getTeamScores();
        Map<String, Integer> playerScoresSnapshot = gameState.getPlayerScores();


        for (DealWithClient client : clients) {
            try {
                client.sendObject(new GameEndMessage(GameEndMessagesId.getAndAdd(1),
                        teamScoresSnapshot, playerScoresSnapshot));
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

        Thread t = new Thread(() -> {
            long end = System.currentTimeMillis() + QUESTION_TIMEOUT_MS;
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