package kahoot.client;

import kahoot.messages.Message;
import kahoot.server.ServerKahoot;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;

public class ClientKahoot {
    private Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private JLabel timerLabel;
    private JFrame frame;

    public static void main(String[] args) {
        new ClientKahoot().runClient();
    }

    public void runClient() {
        createGui();
        try {
            connectToServer();
            //startReaderThread();
            sendMessage("Client online.");
        } catch (IOException e) {
            timerLabel.setText("Connection error");
            e.printStackTrace();
        }
    }

    void connectToServer() throws IOException {
        InetAddress endereco = InetAddress.getByName(null);
        socket = new Socket(endereco, ServerKahoot.KAHOOT_PORT);
        System.out.println("Socket:" + socket);

        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());
        SwingUtilities.invokeLater(() -> timerLabel.setText("Connected"));
    }

    private void createGui() {
        frame = new JFrame("Kahoot Client - Timer");
        timerLabel = new JLabel("Connecting...", SwingConstants.CENTER);
        timerLabel.setFont(timerLabel.getFont().deriveFont(48f));
        frame.getContentPane().add(timerLabel, BorderLayout.CENTER);
        frame.setSize(300, 150);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeSilently();
            }
        });
        frame.setVisible(true);
    }

    void startReaderThread() {
        Thread reader = new Thread(() -> {
            try {
                while (true) {
                    Object obj = in.readObject();
                    if (obj == null) break;

                    if (obj instanceof Message) {
                        Message msg = (Message) obj;
                        String payload = msg.getMessage();
                        Integer seconds = parseSecondsFromJson(payload);
                        if (seconds != null) {
                            SwingUtilities.invokeLater(() -> timerLabel.setText(seconds.toString()));
                        } else {
                            SwingUtilities.invokeLater(() -> timerLabel.setText(payload));
                        }
                    } else if (obj instanceof String) {
                        String line = (String) obj;
                        Integer seconds = parseSecondsFromJson(line);
                        if (seconds != null) {
                            SwingUtilities.invokeLater(() -> timerLabel.setText(seconds.toString()));
                        } else {
                            SwingUtilities.invokeLater(() -> timerLabel.setText(line));
                        }
                    } else {
                        System.out.println("Received object: " + obj.getClass() + " -> " + obj);
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                SwingUtilities.invokeLater(() -> timerLabel.setText("Disconnected"));
            } finally {
                closeSilently();
            }
        }, "TimerReader");
        reader.setDaemon(true);
        reader.start();
    }

    void sendMessage(String message) throws IOException {
        Message messageToSend = new Message(0, message);
        out.writeObject(messageToSend);
        Message str;
        try {
            str = (Message) in.readObject();
            System.out.println(str.getMessage());
        } catch (ClassNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private Integer parseSecondsFromJson(String jsonLine) {
        // simple, robust parsing without external libs: look for "seconds":<number>
        if (jsonLine == null) return null;
        int idx = jsonLine.indexOf("\"seconds\":");
        if (idx < 0) return null;
        int start = idx + "\"seconds\":".length();
        StringBuilder num = new StringBuilder();
        while (start < jsonLine.length()) {
            char c = jsonLine.charAt(start);
            if (Character.isDigit(c)) {
                num.append(c);
            } else if (num.length() > 0) {
                break;
            }
            start++;
        }
        try {
            return num.length() > 0 ? Integer.parseInt(num.toString()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void closeSilently() {
        try { if (out != null) out.writeObject(new Message(-1, "FIM")); } catch (Exception ignored) {}
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }
}