package kahoot.messages;

import java.io.Serializable;

public class AnswerMessage implements Serializable {
    int id ;
    String playerName;
    Integer answerIndex;
    String gameName;

    public AnswerMessage(int id, String playerName, Integer answerIndex, String gameName) {
        this.id = id;
        this.playerName = playerName;
        this.answerIndex = answerIndex;
        this.gameName = gameName;
    }

    public String getPlayerName() {
        return playerName;
    }

    public Integer getAnswerIndex() {
        return answerIndex;
    }

    public String getGameName() {
        return gameName;
    }

    @Override
    public String toString() {
        return "AnswerMessage{playerName='" + playerName + "', answerIndex=" + answerIndex + "}\n";
    }
}