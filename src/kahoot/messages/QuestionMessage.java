package kahoot.messages;

import java.io.Serializable;
import java.util.ArrayList;

public class QuestionMessage implements Serializable {
    int id ;
    String question;
    Integer questionIndex;
    boolean individualQuestion;
    ArrayList<String> options;

    public QuestionMessage(int id , String question, Integer questionIndex, boolean individualQuestion, ArrayList<String> options) {
        this.id=id;
        this.question = question;
        this.questionIndex = questionIndex;
        this.individualQuestion = individualQuestion;
        this.options = options;
    }

    public String getQuestion() {
        return question;
    }
    public boolean isIndividualQuestion() {
        return individualQuestion;
    }
    public ArrayList<String> getOptions() {
        return options;
    }
}
