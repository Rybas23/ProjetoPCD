package kahoot.client;

import kahoot.gui.Gui;
import kahoot.messages.*;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;

import static kahoot.utils.Utilities.*;

public class ClientKahoot {
    private Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;

    private Gui clientGui;

    private volatile boolean running = false;
    private Thread listenerThread;

    public static void main(String[] args) {
        new ClientKahoot().runClient(args);
    }

    public void runClient(String[] args) {
        if(args.length != 5) {
            System.out.println("ERROR: Client is missing parameters.");
            System.exit(1);
        }

        try {
            clientGui = new Gui(args[2], args[3], args[4], this);
            connectToServer(args[0], args[1]);
            sendPlayerDetails(args[2], args[3], args[4]);
            waitForEnrollmentResponse();
            printMessage("Client online.");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    void connectToServer(String host, String port) throws IOException {
        InetAddress endereco = InetAddress.getByName(host);
        socket = new Socket(endereco, Integer.parseInt(port));
        System.out.println("Socket:" + socket);

        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());
    }

    void sendPlayerDetails(String gameName, String teamName, String playerName) throws IOException {
        out.writeObject(new EnrollmentMessage(EnrollmentMessagesId.getAndAdd(1), playerName, teamName, gameName));
    }

    public void submitAnswer(String playerName, Integer optionIndex, String gameName) throws IOException {
        out.writeObject(new AnswerMessage(AnswerMessagesId.getAndAdd(1), playerName, optionIndex, gameName));
    }

    /**
     * Waits for a single server response after enrollment.
     * If server replies with an error message the client prints it,
     * updates the UI, closes resources and exits the process.
     */
    void waitForEnrollmentResponse() throws IOException, ClassNotFoundException {
        Object obj;
        try {
            obj = in.readObject();
        } catch (IOException e) {
            // connection lost before response
            System.out.println("No response from server.");
            closeSilently();
            System.exit(0);
            return;
        }

        if (obj instanceof Message) {
            Message response = (Message) obj;
            String text = response.getMessage();

            if (response.getId() == -1 || (text != null && text.toUpperCase().contains("ERROR"))) {
                System.out.println("Server rejected enrollment: " + text);
                closeSilently();
                System.exit(0);
            } else {
                System.out.println("Server response: " + text);

                // start listener to receive questions and other server messages
                startListener();
            }
        } else {
            System.out.println("Unexpected response from server.");
        }
    }

    private void startListener() {
        running = true;
        listenerThread = new Thread(() -> {
            try {
                while (running) {
                    Object incoming;
                    try {
                        incoming = in.readObject();
                    } catch (IOException e) {
                        System.out.println("Connection lost: " + e.getMessage());
                        break;
                    }

                    if (incoming == null) {
                        System.out.println("Stream closed by server.");
                        break;
                    }

                    if (incoming instanceof QuestionMessage) {
                        QuestionMessage questionMessage = (QuestionMessage) incoming;
                        // update GUI with question (use q.toString() to avoid depending on unknown getters)
                        SwingUtilities.invokeLater(() -> clientGui.displayQuestionAndOptions(questionMessage));
                    } else if (incoming instanceof GameEndMessage) {
                        GameEndMessage gameEndMessage = (GameEndMessage) incoming;
                        // update GUI after Game Ends
                        SwingUtilities.invokeLater(() -> clientGui.displayEndGame(gameEndMessage));
                    } else if (incoming instanceof ScoresMessage) {
                        ScoresMessage scoresMessage = (ScoresMessage) incoming;
                        // update GUI after Game Ends
                        SwingUtilities.invokeLater(() -> clientGui.displayRoundScores(scoresMessage));
                    } else if (incoming instanceof Message) {
                        Message m = (Message) incoming;
                        String msgText = m.getMessage();
                        if (msgText != null && (msgText.equals("FIM") || msgText.toUpperCase().contains("ERROR"))) {
                            System.out.println("Server signaled end/error: " + msgText);
                            break;
                        }
                        // otherwise can show other messages if needed
                    } else {
                        System.out.println("Received object: " + incoming.getClass().getName());
                    }
                }
            } catch (ClassNotFoundException e) {
                System.out.println("Unknown incoming class.");
            } finally {
                closeSilently();
            }
        }, "ClientListenerThread");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    void printMessage(String message) {
        System.out.println(message);
    }

    private void closeSilently() {
        try { if (out != null) out.writeObject(new Message(-1, "FIM")); } catch (Exception ignored) {}
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
    }
}