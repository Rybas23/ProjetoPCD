package features;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class Gui {
    private JFrame frame;

    public Gui(Map<JTextField,List<JTextField>> questionsAndAnswers){ //parametros receber perguntas e respostas
        frame = new JFrame("Kahoot");
        createContent();
        frame.pack();
    }

    public void open() {
        // para abrir a janela (torna-la visivel)
        frame.setVisible(true);
    }

    public void createContent(){
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(600, 500);
        frame.setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.Y_AXIS));

        JTextArea questionArea = new JTextArea("What is the capital of France?");
        questionArea.setLineWrap(true);
        questionArea.setWrapStyleWord(true);
        questionArea.setEditable(false);
        questionArea.setFont(new Font("SansSerif", Font.BOLD, 20));
        questionArea.setMargin(new Insets(20, 20, 20, 20));
        questionArea.setBackground(new Color(240, 240, 255));
        questionArea.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Wrap in a panel for padding
        JPanel questionPanel = new JPanel();
        questionPanel.setLayout(new BorderLayout());
        questionPanel.add(questionArea, BorderLayout.CENTER);
        questionPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

        // === Bottom area (Answers) ===
        JPanel answersPanel = new JPanel(new GridLayout(2, 2, 15, 15));
        answersPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Create buttons with Kahoot-style colors
        JButton btn1 = new JButton("Paris");
        JButton btn2 = new JButton("Rome");
        JButton btn3 = new JButton("Madrid");
        JButton btn4 = new JButton("Berlin");

        JButton[] buttons = {btn1, btn2, btn3, btn4};
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            answersPanel.add(buttons[i]);
        }

        frame.add(questionPanel);
        frame.add(Box.createVerticalStrut(10)); // small gap
        frame.add(answersPanel);
    }
}