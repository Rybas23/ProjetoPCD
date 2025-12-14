package kahoot.game;

import java.util.ArrayList;

public class Question {
    public String question;
    public int points;
    public int correct;
    public ArrayList<String> options;

    public Question(String question, int points, int correct, ArrayList<String> options) {
        this.question = question;
        this.points = points;
        this.correct = correct;
        this.options = options;
    }

    public String getQuestion() { return question; }
    public int getPoints() { return points; }
    public ArrayList<String> getOptions() { return options; }

    public boolean isCorrect(int index) {
        return index + 1 == correct;
    }

    @Override
    public String toString() {
        return "\nQuestion{question='" + question + "', points=" + points + ", correct=" + correct + " , options='" + options + "'}";
    }
}