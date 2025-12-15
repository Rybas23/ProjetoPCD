package kahoot.game;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class GameState {
    private final String gameID;
    private final AtomicInteger currentQuestionIndex = new AtomicInteger(0);
    private final AtomicInteger roundTimer = new AtomicInteger(30);
    private final Map<String, Team> teams = new HashMap<>();
    private final Map<String, Player> players = new HashMap<>();
    private final Map<String, Integer> teamScores = new HashMap<>();
    private final Map<String, Integer> playerScores = new HashMap<>();
    private final Quiz quiz;
    private final int maxNumberOfTeams;
    private final int maxPlayersPerTeam;

    private volatile boolean gameOver = false;

    public GameState(String gameID, Integer maxNumberOfTeams, Integer maxPlayersPerTeam, Integer numberOfQuestions, Quiz quiz) {
        if(quiz.questions.size() > numberOfQuestions) {
            quiz.questions = new ArrayList<>(quiz.questions.subList(0, numberOfQuestions));
        }

        this.gameID = gameID;
        this.quiz = quiz;
        this.maxNumberOfTeams = maxNumberOfTeams;
        this.maxPlayersPerTeam = maxPlayersPerTeam;
    }

    //region ---- Métodos de gestão de equipas ----
    public synchronized void addTeam(String teamName) {
        if (teamExists(teamName)) {
            return;
        }

        teams.put(teamName, new Team(teamName));
    }

    public synchronized void addPlayerToTeam(Player player) {
        if (!teams.containsKey(player.getTeamName()) && players.containsKey(player.getUsername())) {
            return;
        }

        players.put(player.getUsername(), player);
        teams.get(player.getTeamName()).addPlayer(player);
    }

    //endregion

    public synchronized void awardPoints(String playerName, String teamName, int points) {
        Player player = players.get(playerName);
        if (player != null) {
            player.addScore(points);
            playerScores.put(playerName, player.getScore());
        }

        Team team = teams.get(teamName);
        if (team != null) {
            // Recompute or update team total from players
            team.updateScore();
            teamScores.put(teamName, team.getTotalScore());
        }
    }

    // ---- Question / game management ----
    public synchronized Question getCurrentQuestion() {
        int questionIndex = currentQuestionIndex.get();
        return (questionIndex < quiz.questions.size()) ? quiz.questions.get(questionIndex) : null;
    }

    // ---- Question / game management ----
    public synchronized boolean nextQuestion() {
        currentQuestionIndex.set(currentQuestionIndex.get() + 1);

        if(currentQuestionIndex.get() == quiz.questions.size()) {
            gameOver = true;
        }

        return gameOver;
    }

    public synchronized void startGame() {
        gameOver = false;
        currentQuestionIndex.set(0);
        roundTimer.set(30);
    }

    public synchronized Map<String, Integer> getTeamScores() {
        return new HashMap<>(teamScores);
    }

    public synchronized Map<String, Integer> getPlayerScores() {
        return new HashMap<>(playerScores);
    }

    public String getGameID() {
        return gameID;
    }
    public int getMaxPlayersPerTeam() {
        return maxPlayersPerTeam;
    }
    public int getMaxNumberOfTeams() {
        return maxNumberOfTeams;
    }
    public int getNumberOfTeams() {
        return teams.size();
    }
    public synchronized AtomicInteger getCurrentQuestionIndex() {
        return currentQuestionIndex;
    }
    public Team getTeam(String teamName) {
        return teams.get(teamName);
    }
    public Collection<Player> getPlayers() {
        return players.values();
    }

    public boolean teamExists(String teamName) {
        return teams.containsKey(teamName);
    }

    public Integer getTotalNumberOfPlayers() {
        return teams.values().stream().mapToInt(Team::getNumberOfPlayers).sum();
    }

    public boolean isGameFull() {
        return teams.size() >= maxNumberOfTeams && teams.values().stream().allMatch(t -> t.getNumberOfPlayers() >= maxPlayersPerTeam);
    }

    @Override
    public String toString() {
        return "Game{id='" + gameID + "', teams=" + teams + "', maxNumberOfTeams=" + maxNumberOfTeams + "', maxPlayersPerTeam=" + maxPlayersPerTeam + "', currentQuestion=" + currentQuestionIndex + "', quiz=" + quiz +"}\n";
    }
}