package kahoot.gui;

import kahoot.client.ClientKahoot;
import kahoot.messages.GameEndMessage;
import kahoot.messages.QuestionMessage;
import kahoot.messages.ScoresMessage;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

public class Gui {
    private final String gameName;
    private final String teamName;
    private final String username;

    private final ClientKahoot client;

    private JFrame frame;
    private JLabel questionLabel;
    private JPanel answersPanel;

    public Gui(String gameName, String teamName, String username, ClientKahoot client) {
        this.gameName = gameName;
        this.teamName = teamName;
        this.username = username;
        this.client = client;

        createGui();
    }

    private void createGui() {
        frame = new JFrame("Kahoot Client - Timer");
        questionLabel = new JLabel("Connecting...", SwingConstants.CENTER);
        questionLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
        questionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        questionLabel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        questionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        answersPanel = new JPanel(new GridLayout(2, 2, 15, 15));
        answersPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        frame = new JFrame("Kahoot - " + username + " (" + teamName + ")" + " - " + gameName);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setSize(800, 600);
        frame.getContentPane().setLayout(new BorderLayout(0, 10));
        frame.getContentPane().add(questionLabel, BorderLayout.NORTH);

        JPanel centerWrapper = new JPanel(new BorderLayout());
        centerWrapper.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        centerWrapper.add(answersPanel, BorderLayout.CENTER);
        frame.getContentPane().add(centerWrapper, BorderLayout.CENTER);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                SwingUtilities.invokeLater(() -> { if (frame != null) frame.dispose(); });
            }
        });
        frame.setVisible(true);
    }

    public void displayEndGame(GameEndMessage gameEndMessage) {
        if (frame == null) return;

        // use the two maps provided by the message
        java.util.Map<String, Integer> playersMap = gameEndMessage.getPlayerScores();
        java.util.Map<String, Integer> teamsMap = gameEndMessage.getTeamScores();

        java.util.List<java.util.Map.Entry<String, Integer>> sortedPlayers =
                new java.util.ArrayList<>(playersMap.entrySet());
        sortedPlayers.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        java.util.List<java.util.Map.Entry<String, Integer>> sortedTeams =
                new java.util.ArrayList<>(teamsMap.entrySet());
        sortedTeams.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        questionLabel.setText("Game Over - Final Scores");
        questionLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
        questionLabel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        questionLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // players panel (left)
        JPanel playersPanel = new JPanel();
        playersPanel.setLayout(new BoxLayout(playersPanel, BoxLayout.Y_AXIS));
        playersPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        playersPanel.setOpaque(false);

        for (java.util.Map.Entry<String, Integer> e : sortedPlayers) {
            JPanel row = new JPanel(new BorderLayout());
            row.setOpaque(false);

            JLabel nameLabel = new JLabel(e.getKey());
            nameLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));

            JLabel scoreLabel = new JLabel(String.valueOf(e.getValue()));
            scoreLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
            scoreLabel.setHorizontalAlignment(SwingConstants.RIGHT);

            row.add(nameLabel, BorderLayout.WEST);
            row.add(scoreLabel, BorderLayout.EAST);
            row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));

            playersPanel.add(row);
            playersPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        }

        // teams panel (right)
        JPanel teamsPanel = new JPanel();
        teamsPanel.setLayout(new BoxLayout(teamsPanel, BoxLayout.Y_AXIS));
        teamsPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        teamsPanel.setOpaque(false);

        if (sortedTeams.isEmpty()) {
            JLabel none = new JLabel("No team scores available");
            none.setFont(new Font("SansSerif", Font.ITALIC, 14));
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            teamsPanel.add(none);
        } else {
            for (java.util.Map.Entry<String, Integer> e : sortedTeams) {
                JPanel row = new JPanel(new BorderLayout());
                row.setOpaque(false);

                JLabel teamLabel = new JLabel(e.getKey());
                teamLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));

                JLabel scoreLabel = new JLabel(String.valueOf(e.getValue()));
                scoreLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
                scoreLabel.setHorizontalAlignment(SwingConstants.RIGHT);

                row.add(teamLabel, BorderLayout.WEST);
                row.add(scoreLabel, BorderLayout.EAST);
                row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));

                teamsPanel.add(row);
                teamsPanel.add(Box.createRigidArea(new Dimension(0, 4)));
            }
        }

        // wrappers with subtitles
        JPanel leftWrapper = new JPanel(new BorderLayout(0, 6));
        leftWrapper.setOpaque(false);
        JLabel leftSubtitle = new JLabel("Players", SwingConstants.CENTER);
        leftSubtitle.setFont(new Font("SansSerif", Font.BOLD, 14));
        leftSubtitle.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        leftWrapper.add(leftSubtitle, BorderLayout.NORTH);
        leftWrapper.add(new JScrollPane(playersPanel), BorderLayout.CENTER);

        JPanel rightWrapper = new JPanel(new BorderLayout(0, 6));
        rightWrapper.setOpaque(false);
        JLabel rightSubtitle = new JLabel("Teams", SwingConstants.CENTER);
        rightSubtitle.setFont(new Font("SansSerif", Font.BOLD, 14));
        rightSubtitle.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        rightWrapper.add(rightSubtitle, BorderLayout.NORTH);
        rightWrapper.add(new JScrollPane(teamsPanel), BorderLayout.CENTER);

        JPanel centerWrapper = new JPanel(new GridLayout(1, 2, 10, 0));
        centerWrapper.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        centerWrapper.add(leftWrapper);
        centerWrapper.add(rightWrapper);

        frame.getContentPane().removeAll();
        frame.getContentPane().setLayout(new BorderLayout(0, 10));
        frame.getContentPane().add(questionLabel, BorderLayout.NORTH);
        frame.getContentPane().add(centerWrapper, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(ev -> frame.dispose());
        south.add(closeBtn);
        frame.getContentPane().add(south, BorderLayout.SOUTH);

        frame.revalidate();
        frame.repaint();
    }

    public void displayRoundScores(ScoresMessage scoresMessage) {
        if (frame == null) return;

        // use the two maps provided by the message
        java.util.Map<String, Integer> playersMap = scoresMessage.getPlayerScores();
        java.util.Map<String, Integer> teamsMap = scoresMessage.getTeamScores();

        java.util.List<java.util.Map.Entry<String, Integer>> sortedPlayers =
                new java.util.ArrayList<>(playersMap.entrySet());
        sortedPlayers.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        java.util.List<java.util.Map.Entry<String, Integer>> sortedTeams =
                new java.util.ArrayList<>(teamsMap.entrySet());
        sortedTeams.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        questionLabel.setText("Round Scores");
        questionLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
        questionLabel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        questionLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // (reuse the same panel-building logic as displayEndGame)
        JPanel playersPanel = new JPanel();
        playersPanel.setLayout(new BoxLayout(playersPanel, BoxLayout.Y_AXIS));
        playersPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        playersPanel.setOpaque(false);

        for (java.util.Map.Entry<String, Integer> e : sortedPlayers) {
            JPanel row = new JPanel(new BorderLayout());
            row.setOpaque(false);

            JLabel nameLabel = new JLabel(e.getKey());
            nameLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));

            JLabel scoreLabel = new JLabel(String.valueOf(e.getValue()));
            scoreLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
            scoreLabel.setHorizontalAlignment(SwingConstants.RIGHT);

            row.add(nameLabel, BorderLayout.WEST);
            row.add(scoreLabel, BorderLayout.EAST);
            row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));

            playersPanel.add(row);
            playersPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        }

        JPanel teamsPanel = new JPanel();
        teamsPanel.setLayout(new BoxLayout(teamsPanel, BoxLayout.Y_AXIS));
        teamsPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        teamsPanel.setOpaque(false);

        if (sortedTeams.isEmpty()) {
            JLabel none = new JLabel("No team scores available");
            none.setFont(new Font("SansSerif", Font.ITALIC, 14));
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            teamsPanel.add(none);
        } else {
            for (java.util.Map.Entry<String, Integer> e : sortedTeams) {
                JPanel row = new JPanel(new BorderLayout());
                row.setOpaque(false);

                JLabel teamLabel = new JLabel(e.getKey());
                teamLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));

                JLabel scoreLabel = new JLabel(String.valueOf(e.getValue()));
                scoreLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
                scoreLabel.setHorizontalAlignment(SwingConstants.RIGHT);

                row.add(teamLabel, BorderLayout.WEST);
                row.add(scoreLabel, BorderLayout.EAST);
                row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));

                teamsPanel.add(row);
                teamsPanel.add(Box.createRigidArea(new Dimension(0, 4)));
            }
        }

        JPanel leftWrapper = new JPanel(new BorderLayout(0, 6));
        leftWrapper.setOpaque(false);
        JLabel leftSubtitle = new JLabel("Players", SwingConstants.CENTER);
        leftSubtitle.setFont(new Font("SansSerif", Font.BOLD, 14));
        leftSubtitle.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        leftWrapper.add(leftSubtitle, BorderLayout.NORTH);
        leftWrapper.add(new JScrollPane(playersPanel), BorderLayout.CENTER);

        JPanel rightWrapper = new JPanel(new BorderLayout(0, 6));
        rightWrapper.setOpaque(false);
        JLabel rightSubtitle = new JLabel("Teams", SwingConstants.CENTER);
        rightSubtitle.setFont(new Font("SansSerif", Font.BOLD, 14));
        rightSubtitle.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        rightWrapper.add(rightSubtitle, BorderLayout.NORTH);
        rightWrapper.add(new JScrollPane(teamsPanel), BorderLayout.CENTER);

        JPanel centerWrapper = new JPanel(new GridLayout(1, 2, 10, 0));
        centerWrapper.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        centerWrapper.add(leftWrapper);
        centerWrapper.add(rightWrapper);

        frame.getContentPane().removeAll();
        frame.getContentPane().setLayout(new BorderLayout(0, 10));
        frame.getContentPane().add(questionLabel, BorderLayout.NORTH);
        frame.getContentPane().add(centerWrapper, BorderLayout.CENTER);

        frame.revalidate();
        frame.repaint();
    }

    public void displayQuestionAndOptions(QuestionMessage questionMessage) {
        if (questionMessage == null) {
            questionLabel.setText("");
            return;
        }
        questionLabel.setText(questionMessage.getQuestion());

        answersPanel.removeAll();

        for (int i = 0; i < questionMessage.getOptions().size(); i++) {
            int index = i;
            JButton btn = new JButton(questionMessage.getOptions().get(i));
            btn.setFont(new Font("SansSerif", Font.BOLD, 14));
            btn.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            btn.addActionListener(e -> handleAnswer(index, btn, gameName));
            answersPanel.add(btn);
        }

        answersPanel.revalidate();
        answersPanel.repaint();
    }

    //Envia a resposta para o GameState
    private void handleAnswer(int optionIndex, JButton clickedButton, String gameName) {
        try {
            client.submitAnswer(username, optionIndex, gameName);
            disableButtons();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        /*Question q = gameState.getCurrentQuestion();
        boolean correct = q.isCorrect(optionIndex);
        JOptionPane.showMessageDialog(frame,
                correct ? "Correct!" : "Wrong!",
                "Answer", JOptionPane.INFORMATION_MESSAGE);*/
    }

    // Mostra a próxima pergunta no ecrã
    /*public void showNextQuestion() {
        Question q = gameState.getCurrentQuestion();
        if (q == null) {
            JOptionPane.showMessageDialog(frame, "Fim do jogo!");
            frame.dispose();
            return;
        }

        questionArea.setText(q.getQuestion());
        answersPanel.removeAll();

        ArrayList<String> options = q.getOptions();
        for (int i = 0; i < options.size(); i++) {
            int index = i;
            JButton btn = new JButton(options.get(i));
            btn.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            btn.addActionListener(e -> handleAnswer(index, btn));
            answersPanel.add(btn);
        }

        answersPanel.revalidate();
        answersPanel.repaint();
    }*/

    private void disableButtons() {
        for (Component c : answersPanel.getComponents()) {
            if (c instanceof JButton) c.setEnabled(false);
        }
    }
}
