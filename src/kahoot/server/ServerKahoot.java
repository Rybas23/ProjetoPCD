package kahoot.server;

import kahoot.client.DealWithClient;
import kahoot.game.*;
import kahoot.utils.JsonReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Scanner;

public class ServerKahoot {
    private BufferedReader in;
    private PrintWriter out;

    private ArrayList<Question> questions;
    private GameState gameState;

    public static final int KAHOOT_PORT = 8080;
    public static void main(String[] args) {
        try {
            Quiz quiz = JsonReader.readQuiz("quiz.json");

            if(quiz != null) {
                System.out.println(quiz);
            }

            if(quiz == null) {
                throw new NullPointerException("O quiz não pode ser nulo");
            }

            ServerKahoot server = new ServerKahoot();

            System.out.println("Command:");
            Scanner in = new Scanner(System.in);
            String command = in.nextLine();

            while(command != null && !command.contains("quit")) {
                if(command.contains("new") && !command.equals("new")) {
                    String[] params = command.split(" ");

                    if(isNumeric(params[1])){
                        server.createGame(Integer.valueOf(params[1]), quiz);
                        server.startServer();
                    } else {
                        System.out.println("Command not recognized.");
                        System.out.println("Please try again.");
                        System.out.println("Command:");
                        command = in.nextLine();
                    }
                } else if(command.equals("quit")) {
                    System.exit(0);
                } else {
                    System.out.println("Command not recognized.");
                    System.out.println("Please try again.");
                    System.out.println("Command:");
                    command = in.nextLine();
                }
            }
        } catch (IOException e) {
            // ...
        }
    }

    public void createGame(Integer numberOfTeams, Quiz quiz) {
        gameState = new GameState("game0", numberOfTeams, quiz);
        System.out.println(gameState);
    }

    public void startGame() {
        gameState.startRoundCountdown();
    }

    public static boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch(NumberFormatException e){
            return false;
        }
    }

    public void startServer() throws IOException {
        ServerSocket ss = new ServerSocket(KAHOOT_PORT);
        System.out.println("Kahoot server is online.");
        try {
            while(true) {
                Socket socket = ss.accept();
                DealWithClient dealWithClient= new DealWithClient(socket);
                dealWithClient.start();
            }
        } finally {
            ss.close();
        }
    }
}