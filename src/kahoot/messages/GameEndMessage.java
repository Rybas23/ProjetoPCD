package kahoot.messages;

import java.io.Serializable;
import java.util.Map;

public class GameEndMessage implements Serializable {
    int id ;
    Map<String, Integer> teamScores;
    Map<String, Integer> playerScores;

    public GameEndMessage(int id, Map<String, Integer> teamScores, Map<String, Integer> playerScores) {
        this.id = id;
        this.teamScores = teamScores;
        this.playerScores = playerScores;
    }

    public Map<String, Integer> getTeamScores() {
        return teamScores;
    }

    public Map<String, Integer> getPlayerScores() {
        return playerScores;
    }
}