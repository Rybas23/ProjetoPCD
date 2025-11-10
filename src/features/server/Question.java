package features.server;

import java.util.List;

public class Question {
    private final String text;
    private final List<String> options;
    private final int correctIndex;
    private final int points;

    public Question(String text, List<String> options, int correctIndex, int points) {
        this.text = text;
        this.options = options;
        this.correctIndex = correctIndex;
        this.points = points;
    }

    public String getText() { return text; }
    public List<String> getOptions() { return options; }
    public int getPoints() { return points; }

    public boolean isCorrect(int index) {
        return index == correctIndex;
    }
}