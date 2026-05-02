/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package metadata.junit;

import junit.framework.TestCase;
import org.junit.experimental.max.MaxCore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MaxCoreTest {
    @TempDir
    Path temporaryFolder;

    @Test
    public void runsMalformedJUnit3TestClass() {
        MaxCore maxCore = MaxCore.storedLocally(temporaryFolder.resolve("max-core-history.bin").toFile());

        Result result = maxCore.run(MalformedJUnit3Fixture.class);

        assertFalse(result.wasSuccessful());
        assertEquals(1, result.getRunCount());
        assertEquals(1, result.getFailureCount());

        Failure failure = result.getFailures().get(0);
        assertTrue(failure.getMessage().contains("No tests found in"));
        assertTrue(failure.getMessage().contains(MalformedJUnit3Fixture.class.getName()));
    }

    public abstract static class MalformedJUnit3Fixture extends TestCase {
        public void helperMethod() {
        }
    }
}
