package kahoot.game;

public class Player {
    private final String username;
    private int score;

    public Player(String username) {
        this.username = username;
        this.score = 0;
    }

    public void addScore(int points) {
        score += points;
    }

    public String getUsername() { return username; }
    public int getScore() { return score; }
}