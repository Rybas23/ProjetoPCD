package kahoot.messages;

import java.io.Serializable;

public class TimerMessage extends Message implements Serializable {
    private final String gameId;
    private final int questionIndex;
    private final long remainingMillis;

    public TimerMessage(int id, String gameId, int questionIndex, long remainingMillis) {
        super(id, null);
        this.gameId = gameId;
        this.questionIndex = questionIndex;
        this.remainingMillis = remainingMillis;
    }

    public String getGameId() {
        return gameId;
    }

    public int getQuestionIndex() {
        return questionIndex;
    }

    public long getRemainingMillis() {
        return remainingMillis;
    }
}