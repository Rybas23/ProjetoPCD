package kahoot.client;


import kahoot.messages.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class DealWithClient extends Thread{
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public DealWithClient(Socket socket) {
        this.socket = socket ;
        try {
            doConnections(socket);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    void doConnections(Socket socket) throws IOException {
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());
    }

    @Override
    public void run() {
        while (true) {
            Message str;
            try {
                str = (Message) in.readObject();
                if (str.getMessage().equals("FIM"))
                    break;
                System.out.println("Eco:" + str.getMessage() + str.getId());
                out.writeObject(str);;
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
}

