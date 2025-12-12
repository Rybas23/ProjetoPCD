package kahoot.messages;

import java.io.Serializable;

public class EnrollmentMessage implements Serializable {
    int id ;
    String playerName;
    String teamName;
    String gameName;

    public EnrollmentMessage(int id , String playerName, String teamName, String gameName) {
        this.id=id;
        this.playerName=playerName;
        this.teamName=teamName;
        this.gameName=gameName;
    }

    public String getPlayerName() {
        return playerName;
    }
    public String getTeamName() {
        return teamName;
    }
    public String getGameName() {
        return gameName;
    }

    @Override
    public String toString() {
        return "EnrollmentMessage{playerName='" + playerName + "', teamName=" + teamName + "', gameName=" + gameName + "}\n";
    }
}
