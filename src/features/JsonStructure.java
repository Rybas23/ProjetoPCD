package features;

import java.util.ArrayList;

public class JsonStructure {
    private ArrayList<Quizz> quizzes;

    public ArrayList<Quizz> getQuizzes() {
        return quizzes;
    }

    public static class Quizz {
        public String name;
        public ArrayList<Question> questions;

        @Override
        public String toString() {
            return "Quizz{name='" + name + "', questions=" + questions + "}\n";
        }
    }

    public static class Question {
        public String question;
        public int points;
        public int correct;
        public ArrayList<String> options;

        @Override
        public String toString() {
            return "\nQuestion{question='" + question + "', points=" + points + ", correct=" + correct + " , options='" + options + "'}";
        }
    }
}
