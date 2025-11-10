package features;

import features.server.GameState;
import features.server.Question;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class Gui {
    private final JFrame frame;
    private final GameState gameState;
    private final String teamName;
    private final String username;

    private JTextArea questionArea;
    private JPanel answersPanel;

    public Gui(GameState gameState, String teamName, String username) {
        this.gameState = gameState;
        this.teamName = teamName;
        this.username = username;

        frame = new JFrame("IsKahoot - " + username);
        createContent();
        frame.pack();
        frame.setVisible(true);
    }

    private void createContent() {
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(600, 500);
        frame.setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.Y_AXIS));

        questionArea = new JTextArea();
        questionArea.setLineWrap(true);
        questionArea.setWrapStyleWord(true);
        questionArea.setEditable(false);
        questionArea.setFont(new Font("SansSerif", Font.BOLD, 20));
        questionArea.setMargin(new Insets(20, 20, 20, 20));
        questionArea.setBackground(new Color(240, 240, 255));
        questionArea.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel questionPanel = new JPanel(new BorderLayout());
        questionPanel.add(questionArea, BorderLayout.CENTER);
        questionPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

        answersPanel = new JPanel(new GridLayout(2, 2, 15, 15));
        answersPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        frame.add(questionPanel);
        frame.add(Box.createVerticalStrut(10));
        frame.add(answersPanel);

        showNextQuestion();
    }

    // Mostra a próxima pergunta no ecrã
    public void showNextQuestion() {
        Question q = gameState.getCurrentQuestion();
        if (q == null) {
            JOptionPane.showMessageDialog(frame, "Fim do jogo!");
            frame.dispose();
            return;
        }

        questionArea.setText(q.getText());
        answersPanel.removeAll();

        List<String> options = q.getOptions();
        for (int i = 0; i < options.size(); i++) {
            int index = i;
            JButton btn = new JButton(options.get(i));
            btn.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            btn.addActionListener(e -> handleAnswer(index, btn));
            answersPanel.add(btn);
        }

        answersPanel.revalidate();
        answersPanel.repaint();
    }

    //Envia a resposta para o GameState
    private void handleAnswer(int optionIndex, JButton clickedButton) {
        gameState.submitAnswer(teamName, username, optionIndex);
        disableButtons();

        Question q = gameState.getCurrentQuestion();
        boolean correct = q.isCorrect(optionIndex);
        JOptionPane.showMessageDialog(frame,
                correct ? "Correct!" : "Wrong!",
                "Answer", JOptionPane.INFORMATION_MESSAGE);

        if (gameState.nextQuestion())
            showNextQuestion();
        else
            JOptionPane.showMessageDialog(frame, "Game Over!");
    }

    private void disableButtons() {
        for (Component c : answersPanel.getComponents()) {
            if (c instanceof JButton) c.setEnabled(false);
        }
    }
}
