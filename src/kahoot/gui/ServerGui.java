package kahoot.gui;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class ServerGui {
    private final JFrame frame;
    private final JLabel headerLabel; // shows "Game - current question" or similar

    public ServerGui(String title) {
        frame = new JFrame(title);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null);

        headerLabel = new JLabel("", SwingConstants.CENTER);
        headerLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
        headerLabel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        headerLabel.setHorizontalAlignment(SwingConstants.CENTER);

        frame.getContentPane().setLayout(new BorderLayout(0, 10));
        frame.getContentPane().add(headerLabel, BorderLayout.NORTH);

        // start visible but empty; content will be populated by displayGame(...)
        frame.setVisible(true);
    }

    /**
     * Display a game view similar to displayEndGame but showing the current question.
     * Call this on server command with the current question text and the two score maps.
     */
    public void displayGame(Integer currentQuestionIndex,
                            Integer totalQuestions,
                            Map<String, Integer> playerScores,
                            Map<String, Integer> teamScores) {
        if (SwingUtilities.isEventDispatchThread()) {
            updateUi(currentQuestionIndex, totalQuestions, playerScores, teamScores);
        } else {
            SwingUtilities.invokeLater(() -> updateUi(currentQuestionIndex, totalQuestions, playerScores, teamScores));
        }
    }

    private void updateUi(Integer currentQuestionIndex,
                          Integer totalQuestions,
                          Map<String, Integer> playerScores,
                          Map<String, Integer> teamScores) {

        String progress;
        if (currentQuestionIndex != null && totalQuestions != null) {
            progress = "Question " + currentQuestionIndex + "/" + totalQuestions;
        } else if (currentQuestionIndex != null) {
            progress = "Question " + currentQuestionIndex;
        } else {
            progress = "";
        }

        if (frame == null) return;

        headerLabel.setText(progress);

        // (reuse the same panel-building logic as displayEndGame)
        JPanel playersPanel = new JPanel();
        playersPanel.setLayout(new BoxLayout(playersPanel, BoxLayout.Y_AXIS));
        playersPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        playersPanel.setOpaque(false);

        // Builds the players scoreboard panel
        JPanel leftWrapper = Gui.buildScoreboardPanel(playersPanel, playerScores, "Players");

        JPanel teamsPanel = new JPanel();
        teamsPanel.setLayout(new BoxLayout(teamsPanel, BoxLayout.Y_AXIS));
        teamsPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        teamsPanel.setOpaque(false);

        // Builds the teams scoreboard panel
        JPanel rightWrapper = Gui.buildScoreboardPanel(teamsPanel, teamScores, "Teams");

        JPanel centerWrapper = new JPanel(new GridLayout(1, 2, 10, 0));
        centerWrapper.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        centerWrapper.add(leftWrapper);
        centerWrapper.add(rightWrapper);

        frame.getContentPane().removeAll();
        frame.getContentPane().setLayout(new BorderLayout(0, 10));
        frame.getContentPane().add(headerLabel, BorderLayout.NORTH);
        frame.getContentPane().add(centerWrapper, BorderLayout.CENTER);

        frame.revalidate();
        frame.repaint();
    }
}