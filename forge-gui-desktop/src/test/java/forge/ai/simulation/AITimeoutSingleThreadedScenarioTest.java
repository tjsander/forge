package forge.ai.simulation;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

import forge.ai.AiTaskRunner;

/**
 * Reruns every {@link AITimeoutScenarioTest} scenario with the AI evaluating inline on the game thread,
 * as it does on platforms without threads.
 */
public class AITimeoutSingleThreadedScenarioTest extends AITimeoutScenarioTest {
    @BeforeClass
    public void enableSingleThreaded() {
        AiTaskRunner.setSingleThreaded(true);
    }

    @AfterClass(alwaysRun = true)
    public void restoreDefault() {
        AiTaskRunner.setSingleThreaded(false);
    }
}
