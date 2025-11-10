package features.server;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class GameState {
    private final String roomCode;                     // Código único da sala
    private final int numTeams;                        // Número total de equipas
    private final Map<String, Team> teams;             // Nome da equipa -> objeto Team
    private final List<Question> questions;            // Lista de perguntas do quiz
    private final AtomicInteger currentQuestionIndex;  // Índice da pergunta atual
    private long roundEndTime;                         // Momento em que a ronda termina
    private boolean gameOver;                          // Flag de fim de jogo

    // ---- Construtor ----
    public GameState(String roomCode, int numTeams, List<Question> questions) {
        this.roomCode = roomCode;
        this.numTeams = numTeams;
        this.questions = new ArrayList<>(questions);
        this.teams = new HashMap<>();
        this.currentQuestionIndex = new AtomicInteger(0);
        this.gameOver = false;
    }

    // ---- Métodos de gestão de equipas ----
    public synchronized boolean addTeam(String teamName) {
        if (teams.containsKey(teamName) || teams.size() >= numTeams) return false;
        teams.put(teamName, new Team(teamName));
        return true;
    }

    public synchronized boolean addPlayer(String teamName, String username) {
        Team team = teams.get(teamName);
        if (team == null) return false;
        return team.addPlayer(username);
    }

    // ---- Gestão de perguntas ----
    public synchronized Question getCurrentQuestion() {
        if (currentQuestionIndex.get() < questions.size())
            return questions.get(currentQuestionIndex.get());
        return null;
    }

    public synchronized boolean nextQuestion() {
        int next = currentQuestionIndex.incrementAndGet();
        if (next >= questions.size()) {
            gameOver = true;
            return false;
        }
        return true;
    }

    // ---- Respostas e pontuações ----
    public synchronized void submitAnswer(String teamName, String username, int optionIndex) {
        Team team = teams.get(teamName);
        if (team != null && !gameOver) {
            Question q = getCurrentQuestion();
            boolean correct = q.isCorrect(optionIndex);
            team.recordAnswer(username, correct, q.getPoints());
        }
    }

    public synchronized Map<String, Integer> getScoreboard() {
        Map<String, Integer> scores = new HashMap<>();
        for (Team t : teams.values())
            scores.put(t.getName(), Integer.valueOf(t.getTotalScore()));
        return scores;
    }

    // ---- Estado do jogo ----
    public boolean isGameOver() { return gameOver; }

    public String getRoomCode() { return roomCode; }

    public synchronized void startRoundTimer(long durationMillis) {
        this.roundEndTime = System.currentTimeMillis() + durationMillis;
    }

    public long getTimeRemaining() {
        return Math.max(0, roundEndTime - System.currentTimeMillis());
    }
}