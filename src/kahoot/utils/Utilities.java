package kahoot.utils;

import com.google.gson.Gson;
import kahoot.game.Quiz;

import java.io.FileReader;
import java.util.concurrent.atomic.AtomicInteger;

public class Utilities {
    public static AtomicInteger EnrollmentMessagesId = new AtomicInteger(0);
    public static AtomicInteger QuestionMessagesId = new AtomicInteger(0);
    public static AtomicInteger AnswerMessagesId = new AtomicInteger(0);
    public static AtomicInteger GameEndMessagesId = new AtomicInteger(0);
    public static AtomicInteger ScoresMessagesId = new AtomicInteger(0);
    public static AtomicInteger GameStateId = new AtomicInteger(0);

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

    public static Quiz loadQuiz() {
        Quiz quiz = Utilities.readQuiz("quiz.json");

        if(quiz != null) {
            System.out.println(quiz);
        }

        if(quiz == null) {
            throw new NullPointerException("The quiz can't be null.");
        }

        return quiz;
    }

    public static boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch(NumberFormatException e){
            return false;
        }
    }
}
