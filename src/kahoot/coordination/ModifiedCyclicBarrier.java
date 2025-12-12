package kahoot.coordination;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ModifiedCyclicBarrier {

    private final int parties;
    private final long timeoutMillis;
    private final Runnable barrierAction;

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    private int count;
    private boolean broken;
    private int generation;

    public ModifiedCyclicBarrier(int parties, long timeoutMillis, Runnable barrierAction) {
        if (parties <= 0) {
            throw new IllegalArgumentException("parties must be > 0");
        }
        this.parties = parties;
        this.timeoutMillis = timeoutMillis;
        this.barrierAction = barrierAction;
        this.count = parties;
        this.broken = false;
        this.generation = 0;
    }

    public int await() throws InterruptedException, TimeoutException {
        lock.lock();
        try {
            if (broken) {
                throw new IllegalStateException("Barrier is broken");
            }

            int arrivalGeneration = generation;
            int index = --count;

            // last thread to arrive
            if (count == 0) {
                try {
                    if (barrierAction != null) {
                        barrierAction.run();
                    }
                } finally {
                    nextGeneration();
                }
                return index;
            }

            long deadlineNanos = System.nanoTime() + timeoutMillis * 1_000_000L;

            while (arrivalGeneration == generation && !broken && count > 0) {
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0L) {
                    break; // timeout
                }
                condition.awaitNanos(remainingNanos);
            }

            // timeout \-> not everyone arrived and barrier not yet broken
            if (arrivalGeneration == generation && count > 0 && !broken) {
                broken = true;
                try {
                    if (barrierAction != null) {
                        barrierAction.run();
                    }
                } finally {
                    nextGeneration();   // advance generation and wake others
                }
                throw new TimeoutException("Barrier timeout expired");
            }

            if (broken) {
                throw new IllegalStateException("Barrier is broken");
            }

            return index;
        } finally {
            lock.unlock();
        }
    }

    private void nextGeneration() {
        count = parties;
        generation++;
        broken = false;
        condition.signalAll();
    }

    public boolean isBroken() {
        lock.lock();
        try {
            return broken;
        } finally {
            lock.unlock();
        }
    }
}