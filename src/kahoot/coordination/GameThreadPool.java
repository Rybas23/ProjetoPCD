package kahoot.coordination;

import java.util.LinkedList;
import java.util.Queue;

public class GameThreadPool {

    private int running = 0;
    private final int maxGames = 5;
    private final Queue<Runnable> queue = new LinkedList<>();

    public synchronized void submit(Runnable gameTask) {
        queue.add(gameTask);
        tryStartNext();
    }

    public synchronized void tryStartNext(){
        while(running < maxGames && !queue.isEmpty()){
            Runnable task = queue.poll();
            running++;
            System.out.println("Estão a correr " + running);

            new Thread(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).start();
        }
    }

    public synchronized void gameFinished(){
        if (running > 0) {
            running--;
        }
        tryStartNext();
    }
}
