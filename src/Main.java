import features.JsonReader;
import features.JsonStructure;

import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        jsonStructureTest();
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
}
