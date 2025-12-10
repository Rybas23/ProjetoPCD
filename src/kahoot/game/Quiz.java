package kahoot.game;

import java.util.ArrayList;

public class Quiz {
    public String name;
    public ArrayList<Question> questions;

    @Override
    public String toString() {
        return "Quiz{name='" + name + "', questions=" + questions + "}\n";
    }
}
