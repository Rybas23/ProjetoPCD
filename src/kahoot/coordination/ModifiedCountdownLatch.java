package kahoot.coordination;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ModifiedCountdownLatch {

    private final Lock lock = new ReentrantLock();
    private final Condition released = lock.newCondition();

    private final int maxBonusAnswers;
    private final int bonusMultiplier;
    private final long timeoutMillis;
    private final int totalPlayers;

    private int remainingToAnswer;
    private int bonusLeft;
    private boolean finished;

    public ModifiedCountdownLatch(int maxBonusAnswers, int bonusMultiplier, long timeoutMillis, int totalPlayers) {
        if (totalPlayers <= 0) {
            throw new IllegalArgumentException("totalPlayers must be > 0");
        }

        this.maxBonusAnswers = maxBonusAnswers;
        this.bonusMultiplier = bonusMultiplier;
        this.timeoutMillis = timeoutMillis;
        this.totalPlayers = totalPlayers;

        this.remainingToAnswer = totalPlayers;
        this.bonusLeft = maxBonusAnswers;
        this.finished = false;
    }

    public int countdown() {
        lock.lock();
        try {
            if (finished) {
                return 1;
            }

            if (remainingToAnswer > 0) {
                remainingToAnswer--;
            }

            int factor = 1;
            if (bonusLeft > 0) {
                factor = bonusMultiplier;
                bonusLeft--;
            }

            if (remainingToAnswer == 0) {
                finished = true;
                released.signalAll();
            }

            return factor;
        } finally {
            lock.unlock();
        }
    }

    public void await() throws InterruptedException {
        lock.lock();
        try {
            if (finished) {
                return;
            }

            long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);

            while (!finished) {
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0L) {
                    break; // timeout
                }
                released.awaitNanos(remainingNanos);
            }

            // timeout: force finish and wake any waiters
            if (!finished) {
                finished = true;
                released.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }
}