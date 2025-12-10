package kahoot.utils;

import com.google.gson.Gson;
import kahoot.game.Quiz;

import java.io.FileReader;

public class JsonReader {
    public static Quiz readQuiz(String filename) {
        try (FileReader reader = new FileReader(filename)) {
            Gson gson = new Gson();

            Quiz quiz = gson.fromJson(reader, Quiz.class);

            return quiz;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
