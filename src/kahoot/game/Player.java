package kahoot.game;

public class Player {
    private final String username;
    private final String teamName;
    private int score;

    public Player(String username, String teamName) {
        this.username = username;
        this.teamName = teamName;
        this.score = 0;
    }

    public void addScore(int points) {
        score += points;
    }

    public String getUsername() { return username; }
    public String getTeamName() { return teamName; }
    public int getScore() { return score; }

    @Override
    public String toString() {
        return "New Player{name='" + username + "', team=" + teamName + "}\n";
    }
}