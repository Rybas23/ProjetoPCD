import features.Gui;
import features.JsonReader;
import features.JsonStructure;
import features.server.GameState;
import features.server.Question;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        runGame1stPhase();
        //jsonStructureTest();
    }

    // 3 - Json Reader test
    public static void jsonStructureTest() {
        ArrayList<JsonStructure.Quizz> quizzes = JsonReader.readQuizzes("quizzes.json");

        if(quizzes != null && !quizzes.isEmpty()) {
            for (JsonStructure.Quizz quiz : quizzes) {
                System.out.println(quiz);
            }
        }
    }

    // Test Run GUI
    public static void runGame1stPhase() {
        String filename = "quizzes.json";
        ArrayList<JsonStructure.Quizz> quizzes = JsonReader.readQuizzes(filename);
        List<Question> questions = new ArrayList<>();
        JsonStructure.Quizz quiz = quizzes.get(0);
        for (JsonStructure.Question q : quiz.questions) {
            questions.add(new Question(q.question, q.options, q.correct, q.points));
        }
        GameState gameState = new GameState("ROOM001", 1, questions);
        gameState.addTeam("Team1");
        gameState.addPlayer("Team1", "Player1");
        SwingUtilities.invokeLater(() -> new Gui(gameState, "Team1", "Player1"));

    }
}
