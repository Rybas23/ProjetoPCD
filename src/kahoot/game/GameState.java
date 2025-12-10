package kahoot.game;

import kahoot.server.ServerKahoot;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class GameState {
    private final String gameID;
    private final AtomicInteger currentQuestionIndex = new AtomicInteger(0);
    private final AtomicInteger roundTimer = new AtomicInteger(30);
    private final AtomicLong roundEndTime = new AtomicLong(0L);
    private final Map<String, Team> teams = new HashMap<>();
    private final Quiz quiz;
    private final AtomicInteger numberOfTeams;

    private volatile boolean gameOver = false;

    private ScheduledExecutorService roundScheduler;

    // Internal socket acceptor and connected clients (thread-safe)
    private ServerSocket serverSocket;
    private final ExecutorService acceptor = Executors.newSingleThreadExecutor();
    private final List<Socket> clientSockets = Collections.synchronizedList(new ArrayList<>());

    public GameState(String gameID, Integer numberOfTeams, Quiz quiz) {
        this.gameID = gameID;
        this.quiz = quiz;
        this.numberOfTeams = new AtomicInteger(numberOfTeams);
    }

    // ---- Métodos de gestão de equipas ----
    public synchronized boolean addTeam(String teamName) {
        if (teams.containsKey(teamName)) {
            return false;
        }

        teams.put(teamName, new Team(teamName));

        return true;
    }

    public synchronized boolean addPlayer(String teamName, String username) {
        Team team = teams.get(teamName);

        if (team == null || team.playerExists(teamName)){
            return false;
        }

        return team.addPlayer(username);
    }

    // ---- Question / game management ----
    public synchronized Question getCurrentQuestion() {
        int idx = currentQuestionIndex.get();
        return (idx < quiz.questions.size()) ? quiz.questions.get(idx) : null;
    }

    public synchronized boolean nextQuestion() {
        int next = currentQuestionIndex.incrementAndGet();
        if (next >= quiz.questions.size()) {
            gameOver = true;
            return false;
        }
        return true;
    }

    public synchronized void submitAnswer(String teamName, String username, int optionIndex) {
        Team team = teams.get(teamName);
        if (team != null && !gameOver) {
            Question q = getCurrentQuestion();
            if (q != null) {
                boolean correct = q.isCorrect(optionIndex);
                team.recordAnswer(username, correct, q.getPoints());
            }
        }
    }

    public synchronized Map<String, Integer> getScoreboard() {
        Map<String, Integer> scores = new HashMap<>();
        for (Team t : teams.values()) scores.put(t.getName(), t.getTotalScore());
        return scores;
    }

    //region Timer

    // --- Socket acceptor API ---
    public void startSocketAcceptor(int port) throws IOException {
        if (serverSocket != null && !serverSocket.isClosed()) return;
        serverSocket = new ServerSocket(port);
        acceptor.submit(() -> {
            try {
                while (!serverSocket.isClosed()) {
                    Socket client = serverSocket.accept();
                    clientSockets.add(client);
                }
            } catch (IOException ignored) {
                // acceptor stopped or error occurred
            }
        });
    }

    private void broadcastTimer(int seconds) {
        synchronized (clientSockets) {
            Iterator<Socket> it = clientSockets.iterator();
            while (it.hasNext()) {
                Socket s = it.next();
                try {
                    PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                    out.println("{\"type\":\"timer\",\"seconds\":" + seconds + "}");
                } catch (IOException e) {
                    try { s.close(); } catch (IOException ignored) {}
                    it.remove();
                }
            }
        }
    }

    // ---- Countdown / timer ----
    public synchronized void startRoundCountdown() {
        try {
            startSocketAcceptor(ServerKahoot.KAHOOT_PORT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        long durationMillis = TimeUnit.SECONDS.toMillis(roundTimer.get());

        if (roundScheduler != null && !roundScheduler.isShutdown()) return;

        long end = System.currentTimeMillis() + durationMillis;
        roundEndTime.set(end);

        int seconds = (int) Math.max(0, TimeUnit.MILLISECONDS.toSeconds(durationMillis));
        this.roundTimer.set(seconds);

        roundScheduler = Executors.newSingleThreadScheduledExecutor();
        roundScheduler.scheduleAtFixedRate(() -> {
            int newValue = roundTimer.updateAndGet(v -> v > 0 ? v - 1 : 0);

            broadcastTimer(newValue);

            if (newValue == 0) {
                stopRoundCountdown();
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    public synchronized void stopRoundCountdown() {
        if (roundScheduler != null) {
            roundScheduler.shutdownNow();
            roundScheduler = null;
        }
    }

    public int getRoundTimerSeconds() {
        return roundTimer.get();
    }

    public synchronized void resetRoundTimer(int seconds) {
        roundTimer.set(Math.max(0, seconds));
        roundEndTime.set(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(seconds));
    }

    public long getTimeRemainingMillis() {
        long end = roundEndTime.get();
        return Math.max(0L, end - System.currentTimeMillis());
    }

    //endregion

    @Override
    public String toString() {
        return "Game{id='" + gameID + "', teams=" + teams + "', numberOfTeams=" + numberOfTeams.get() + "', currentQuestion=" + currentQuestionIndex + "', quiz=" + quiz +"}\n";
    }
}