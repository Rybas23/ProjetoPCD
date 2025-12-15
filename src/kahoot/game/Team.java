package kahoot.game;

import java.util.*;

public class Team {
    private final String name;
    private final Map<String, Player> players;
    private int totalScore;

    public Team(String name) {
        this.name = name;
        this.players = new HashMap<>();
        this.totalScore = 0;
    }

    public void addPlayer(Player player) {
        if (players.containsKey(player.getUsername())) {
            return;
        }
        players.put(player.getUsername(), player);
    }

    public void updateScore() {
        totalScore = 0;
        for (Player player : players.values()) {
            totalScore += player.getScore();
        }
    }

    public int getNumberOfPlayers() {
        return players.size();
    }

    public String getName() {
        return name;
    }

    public int getTotalScore() {
        return totalScore;
    }
}