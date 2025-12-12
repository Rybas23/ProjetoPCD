package kahoot.server;

import kahoot.game.Player;
import kahoot.messages.AnswerMessage;
import kahoot.messages.EnrollmentMessage;
import kahoot.messages.Message;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;

public class DealWithClient extends Thread{
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final ServerKahoot server;

    // game id this client is enrolled in (set after successful enrollment)
    private String gameId;

    public DealWithClient(Socket socket, ServerKahoot server) {
        this.socket = socket ;
        this.server = server;
        try {
            doConnections(socket);
        } catch (IOException e) {
            System.out.println("Failed to create streams for " + socket.getRemoteSocketAddress());
            // streams/socket will be closed in cleanup if partially opened
            cleanup();
        }
    }

    void doConnections(Socket socket) throws IOException {
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());
    }

    @Override
    public void run() {
        try {
            while (true) {
                Object readObject;
                try {
                    System.out.println("\nWaiting for messages from clients!");
                    readObject = in.readObject();
                } catch (EOFException | SocketException e) {
                    // client closed connection or socket was closed -> exit loop quietly
                    break;
                }

                if (readObject == null) {
                    break;
                }

                if (readObject instanceof Message) {
                    Message message = (Message) readObject;

                    if (message.getMessage().equals("FIM") || message.getMessage().contains("ERROR:")) {
                        out.writeObject(message);
                        out.flush();
                        break;
                    }

                    System.out.println("Eco:" + message.getMessage() + message.getId());
                    out.writeObject(message);
                } else if (readObject instanceof EnrollmentMessage) {
                    System.out.print("Got:");

                    EnrollmentMessage enrollmentMessage = (EnrollmentMessage) readObject;
                    System.out.print(enrollmentMessage);

                    Player player = new Player(enrollmentMessage.getPlayerName(), enrollmentMessage.getTeamName());

                    boolean registered = server.registerPlayer(enrollmentMessage.getGameName(), player);
                    if (!registered) {
                        // inform client and close this connection
                        out.writeObject(new Message(-1, "ERROR:username already exists, game not found or team is full"));
                        out.flush();
                        break;
                    } else {
                        // set gameId and register this connection for broadcasts before notifying whether the game is full
                        this.gameId = enrollmentMessage.getGameName();
                        server.registerClientForGame(this.gameId, this);

                        out.writeObject(new Message(0, "ENROLLED"));
                        out.flush();

                        server.checkIfGameIsFull(enrollmentMessage.getGameName());
                    }
                } else if (readObject instanceof AnswerMessage) {
                    System.out.print("Got:");

                    AnswerMessage answerMessage = (AnswerMessage) readObject;
                    System.out.print(answerMessage);

                    server.registerAnswer(answerMessage.getPlayerName(), answerMessage.getAnswerIndex(), answerMessage.getGameName());
                } else {
                    System.out.println("Received unknown object type: " + readObject.getClass().getName());
                }
            }
        } catch (IOException e) {
            System.out.println("I/O error on connection " + socket.getRemoteSocketAddress());
        } catch (ClassNotFoundException e) {
            System.out.println("Received object of unknown class from " + socket.getRemoteSocketAddress());
        } finally {
            cleanup();
        }
    }

    // thread-safe send helper; on failure cleanup and unregister from server
    public synchronized void sendObject(Object obj) {
        if (out == null){
            return;
        }

        try {
            out.writeObject(obj);
            out.flush();
        } catch (IOException e) {
            // sending failed, cleanup and unregister client
            cleanup();
            server.unregisterClient(this);
        }
    }

    public String getGameId() {
        return gameId;
    }

    private void cleanup() {
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException ignored) {}
        // ensure server removes this client from tracking
        server.unregisterClient(this);
    }
}

