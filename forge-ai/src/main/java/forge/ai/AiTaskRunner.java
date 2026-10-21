package forge.ai;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs AI evaluation work either on helper threads (default) or inline on the calling thread.
 *
 * Single-threaded mode is meant for platforms without threads (e.g. a WebAssembly build) and for deterministic
 * headless runs. The work itself still honours the cooperative {@link forge.util.AITimeoutTracker} deadline,
 * but there is no hard cutoff: code that never calls check() runs to completion.
 *
 * All thread handling of the AI lives in this class, so a platform without threads can replace it as a whole.
 */
public final class AiTaskRunner {
    private static volatile boolean singleThreaded = false;

    private AiTaskRunner() { }

    public static boolean isSingleThreaded() {
        return singleThreaded;
    }

    public static void setSingleThreaded(final boolean value) {
        singleThreaded = value;
    }

    /**
     * Runs task and returns its result, or null if it fails or is still running at deadlineNanos
     * (absolute System.nanoTime()). On a timeout the helper thread is asked to stop.
     */
    public static <T> T call(final Callable<T> task, final long deadlineNanos, final String threadName) {
        if (singleThreaded) {
            try {
                return task.call();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        FutureTask<T> future = new FutureTask<>(task);
        Thread t = new Thread(future, threadName);
        t.setDaemon(true);
        t.start();
        try {
            // wait only for what is left of the shared deadline (not a fresh full timeout per call)
            return future.get(Math.max(0, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            e.printStackTrace();
            if (e instanceof TimeoutException) {
                // log where the eval thread currently is - each timeout doubles as a
                // profiler sample for diagnosing remaining AI slowdowns from user logs
                StringBuilder sb = new StringBuilder("AI eval thread at timeout:");
                StackTraceElement[] evalStack = t.getStackTrace();
                for (int i = 0; i < Math.min(30, evalStack.length); i++) {
                    sb.append("\n\tat ").append(evalStack[i]);
                }
                System.out.println(sb);
            }
            // ask the eval thread to exit at the next SpellAbility check first: a brutal
            // Thread.stop() mid-evaluation can leave partially mutated shared state behind
            future.cancel(true);
            try {
                // join returns as soon as the eval thread exits, so the common case costs nothing. The full 2 s only matters
                // for a thread inside code without check() (mana payment, threat prediction, simulation): giving up earlier
                // just starts the stop() fallback sooner, and on Java 20+/Android stop() throws, so the thread would keep
                // running next to the game thread
                t.join(2000); // 2 seconds wait
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            if (t.isAlive()) {
                // last resort, see #8302: the eval thread may be stuck inside a single
                // evaluation or an infinite loop and never reach the cooperative exit
                try {
                    t.stop();
                } catch (UnsupportedOperationException | NoSuchMethodError ex) {
                    // Stop support: dropped by Android and Java 20 / 26 removed it completely - so sadly thread will keep running
                }
            }
            return null;
        }
    }

    /**
     * Runs all tasks, in parallel unless single-threaded, and returns once they finished or timeoutNanos passed.
     * Tasks still running after the timeout are interrupted; callers must guard shared state against them.
     */
    public static <T> void invokeAll(final List<? extends Callable<T>> tasks, final long timeoutNanos) {
        if (singleThreaded) {
            for (final Callable<T> task : tasks) {
                try {
                    task.call();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(), r -> {
                Thread t = Executors.defaultThreadFactory().newThread(r);
                t.setDaemon(true);
                return t;
            }
        );
        try {
            executor.invokeAll(tasks, timeoutNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdownNow();
        }
    }
}
