package forge.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

public class AiTaskRunnerTest {
    private static final long DEADLINE_NANOS = TimeUnit.SECONDS.toNanos(5);

    @AfterMethod
    public void restoreDefault() {
        AiTaskRunner.setSingleThreaded(false);
    }

    @Test
    public void callRunsOnHelperThreadByDefault() {
        final Thread caller = Thread.currentThread();
        final Thread ran = AiTaskRunner.call(Thread::currentThread, System.nanoTime() + DEADLINE_NANOS, "Test Eval");
        Assert.assertNotNull(ran);
        Assert.assertNotSame(ran, caller, "default mode must evaluate on a helper thread");
    }

    @Test
    public void callRunsOnCallingThreadWhenSingleThreaded() {
        AiTaskRunner.setSingleThreaded(true);
        final Thread caller = Thread.currentThread();
        final Thread ran = AiTaskRunner.call(Thread::currentThread, System.nanoTime() + DEADLINE_NANOS, "Test Eval");
        Assert.assertSame(ran, caller, "single-threaded mode must evaluate inline");
    }

    @Test
    public void callReturnsNullWhenTaskFails() {
        final Callable<String> failing = () -> {
            throw new IllegalStateException("expected by test");
        };
        Assert.assertNull(AiTaskRunner.call(failing, System.nanoTime() + DEADLINE_NANOS, "Test Eval"));
        AiTaskRunner.setSingleThreaded(true);
        Assert.assertNull(AiTaskRunner.call(failing, System.nanoTime() + DEADLINE_NANOS, "Test Eval"));
    }

    @Test
    public void invokeAllRunsInOrderOnCallingThreadWhenSingleThreaded() {
        AiTaskRunner.setSingleThreaded(true);
        final Thread caller = Thread.currentThread();
        final List<Integer> order = new ArrayList<>();
        final List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final int n = i;
            tasks.add(() -> {
                Assert.assertSame(Thread.currentThread(), caller);
                order.add(n);
                return n;
            });
        }
        AiTaskRunner.invokeAll(tasks, DEADLINE_NANOS);
        Assert.assertEquals(order, List.of(0, 1, 2, 3, 4));
    }

    @Test
    public void invokeAllContinuesAfterFailingTaskWhenSingleThreaded() {
        AiTaskRunner.setSingleThreaded(true);
        final List<Integer> done = new ArrayList<>();
        final List<Callable<Integer>> tasks = List.of(
                () -> { done.add(0); return 0; },
                () -> { throw new IllegalStateException("expected by test"); },
                () -> { done.add(2); return 2; });
        AiTaskRunner.invokeAll(tasks, DEADLINE_NANOS);
        Assert.assertEquals(done, List.of(0, 2));
    }
}
