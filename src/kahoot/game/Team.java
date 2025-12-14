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

    public boolean addPlayer(Player player) {
        if (players.containsKey(player.getUsername())) return false;
        players.put(player.getUsername(), player);
        return true;
    }

    public void updateScore() {
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

    public Player getPlayer(String username) {
        return players.get(username);
    }

    public int getTotalScore() {
        return totalScore;
    }

    public boolean playerExists(String username) {
        return players.containsKey(username);
    }
}