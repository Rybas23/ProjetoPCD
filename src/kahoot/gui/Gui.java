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
import java.util.ArrayList;
import java.util.Map;

public class Gui {
    private final String gameName;
    private final String teamName;
    private final String username;

    private final ClientKahoot client;

    private JFrame frame;
    private JLabel questionLabel;
    private JPanel answersPanel;
    private JLabel timerLabel;
    private JPanel scoreboardPanel;

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

        timerLabel = new JLabel("Time left: --s");
        timerLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel north = new JPanel(new BorderLayout());
        north.add(questionLabel, BorderLayout.CENTER);
        north.add(timerLabel, BorderLayout.EAST);

        answersPanel = new JPanel(new GridLayout(2, 2, 8, 8));
        answersPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        // painel do scoreboard (players + teams) mantido sempre na UI
        scoreboardPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        scoreboardPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        scoreboardPanel.setPreferredSize(new Dimension(260, 0));

        frame = new JFrame("Kahoot - " + username + " (" + teamName + ")" + " - " + gameName);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setSize(800, 600);
        frame.getContentPane().setLayout(new BorderLayout(0, 10));
        frame.getContentPane().add(north, BorderLayout.NORTH);

        JPanel centerWrapper = new JPanel(new BorderLayout());
        centerWrapper.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        centerWrapper.add(answersPanel, BorderLayout.CENTER);
        frame.getContentPane().add(centerWrapper, BorderLayout.CENTER);

        // scoreboard fica sempre no lado direito
        frame.getContentPane().add(scoreboardPanel, BorderLayout.EAST);

        // mostrar logo estrutura vazia de scoreboard
        updateScoreboard(null);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                SwingUtilities.invokeLater(() -> { if (frame != null) frame.dispose(); });
            }
        });
        frame.setVisible(true);
    }

    public void updateTimer(long seconds) {
        timerLabel.setText("Time left: " + seconds + "s");
    }

    public void displayGameStats(GameEndMessage gameEndMessage, String title) {
        disableButtons();

        timerLabel.setVisible(false);

        // reaproveita o updateScoreboard para mostrar o estado final
        ScoresMessage scores = new ScoresMessage(
                -1,
                gameEndMessage.getTeamScores(),
                gameEndMessage.getPlayerScores()
        );
        updateScoreboard(scores);

        questionLabel.setText(title);

        frame.getContentPane().removeAll();
        frame.getContentPane().setLayout(new BorderLayout(0, 10));
        frame.getContentPane().add(questionLabel, BorderLayout.NORTH);
        frame.getContentPane().add(scoreboardPanel, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(ev -> frame.dispose());
        south.add(closeBtn);
        frame.getContentPane().add(south, BorderLayout.SOUTH);

        frame.revalidate();
        frame.repaint();

        JOptionPane.showMessageDialog(
                frame,
                "The Game has ended. Check the final scores!",
                "Game Over",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    public static JPanel buildScoreboardPanel(JPanel jPanel, java.util.Map<String, Integer> dataMap, String title) {
        java.util.List<Map.Entry<String, Integer>> sortedData = new ArrayList<>(dataMap.entrySet());
        sortedData.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        for (java.util.Map.Entry<String, Integer> e : sortedData) {
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

            jPanel.add(row);
            jPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        }

        JPanel wrapper = new JPanel(new BorderLayout(0, 6));
        wrapper.setOpaque(false);
        JLabel leftSubtitle = new JLabel(title, SwingConstants.CENTER);
        leftSubtitle.setFont(new Font("SansSerif", Font.BOLD, 14));
        leftSubtitle.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        wrapper.add(leftSubtitle, BorderLayout.NORTH);
        wrapper.add(new JScrollPane(jPanel), BorderLayout.CENTER);

        return wrapper;
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
            btn.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            btn.addActionListener(e -> handleAnswer(index, btn, gameName));
            answersPanel.add(btn);
        }

        answersPanel.revalidate();
        answersPanel.repaint();
    }

    public void displayQuestionAndOptions(QuestionMessage questionMessage, ScoresMessage scoresMessage) {
        displayQuestionAndOptions(questionMessage);
        if (scoresMessage != null) {
            updateScoreboard(scoresMessage);
        }
    }

    /** Atualiza o painel do scoreboard mantendo-o sempre visível. */
    public void updateScoreboard(ScoresMessage scoresMessage) {
        if (scoreboardPanel == null) return;

        scoreboardPanel.removeAll();

        Map<String, Integer> playerScores;
        Map<String, Integer> teamScores;

        if (scoresMessage == null) {
            // estrutura vazia, sem dados
            playerScores = java.util.Collections.emptyMap();
            teamScores = java.util.Collections.emptyMap();
        } else {
            playerScores = scoresMessage.getPlayerScores();
            teamScores = scoresMessage.getTeamScores();
        }

        JPanel playersPanel = new JPanel();
        playersPanel.setLayout(new BoxLayout(playersPanel, BoxLayout.Y_AXIS));
        playersPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        playersPanel.setOpaque(false);

        JPanel leftWrapper = buildScoreboardPanel(playersPanel, playerScores, "Players");

        JPanel teamsPanel = new JPanel();
        teamsPanel.setLayout(new BoxLayout(teamsPanel, BoxLayout.Y_AXIS));
        teamsPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        teamsPanel.setOpaque(false);

        JPanel rightWrapper = buildScoreboardPanel(teamsPanel, teamScores, "Teams");

        scoreboardPanel.add(leftWrapper);
        scoreboardPanel.add(rightWrapper);

        scoreboardPanel.revalidate();
        scoreboardPanel.repaint();
    }

    //Envia a resposta para o GameState
    private void handleAnswer(int optionIndex, JButton clickedButton, String gameName) {
        try {
            client.submitAnswer(username, optionIndex, gameName);
            disableButtons();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void disableButtons() {
        for (Component c : answersPanel.getComponents()) {
            if (c instanceof JButton) c.setEnabled(false);
        }
    }
}
