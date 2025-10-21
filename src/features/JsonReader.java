package features;

import com.google.gson.Gson;

import java.io.FileReader;
import java.util.ArrayList;

public class JsonReader {
    public static ArrayList<JsonStructure.Quizz> readQuizzes(String filename) {
        try (FileReader reader = new FileReader(filename)) {
            Gson gson = new Gson();

            JsonStructure jsonStructure = gson.fromJson(reader, JsonStructure.class);

            return jsonStructure.getQuizzes();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
