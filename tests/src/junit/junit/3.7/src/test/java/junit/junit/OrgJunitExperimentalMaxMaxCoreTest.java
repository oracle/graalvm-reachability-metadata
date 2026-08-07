/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import java.nio.file.Path;

import junit.framework.TestCase;
import org.junit.experimental.max.MaxCore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.runner.Result;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgJunitExperimentalMaxMaxCoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void reportsMalformedJUnit3TestClassThroughMaxCore() {
        MaxCore maxCore = MaxCore.storedLocally(temporaryDirectory.resolve("max-history.ser").toFile());

        Result result = maxCore.run(MalformedJUnit3TestCase.class);

        assertThat(result.getRunCount()).isEqualTo(1);
        assertThat(result.getFailureCount()).isEqualTo(1);
        assertThat(result.getFailures().get(0).getDescription().toString())
                .isEqualTo("warning(junit.framework.TestSuite$1)");
    }

    private static class MalformedJUnit3TestCase extends TestCase {
        private MalformedJUnit3TestCase(String name) {
            super(name);
        }

        public void testCannotBeConstructedByJUnit3Suite() {
        }
    }
}
