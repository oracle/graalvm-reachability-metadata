/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.util.VersionUtil;
import java.util.List;
import org.junit.jupiter.api.Test;

public class VersionUtilTest {

    private static final String TEST_MODULE_NAME = "version-util-test";
    private static final String TEST_MODULE_VERSION = "1.2.3-test";
    private static final String TEST_DEPENDENCY_NAME = "version-util-dependency";
    private static final String TEST_DEPENDENCY_VERSION = "4.5.6-test";

    @Test
    void reportsVersionMismatchWithWarning() {
        ContextBase context = new ContextBase();

        VersionUtil.checkForVersionEquality(
                context,
                TEST_MODULE_VERSION,
                TEST_DEPENDENCY_VERSION,
                TEST_MODULE_NAME,
                TEST_DEPENDENCY_NAME
        );

        List<Status> statuses = context.getStatusManager().getCopyOfStatusList();
        assertThat(statuses).extracting(Status::getMessage).containsExactly(
                "Found " + TEST_MODULE_NAME + " version " + TEST_MODULE_VERSION,
                "Found " + TEST_DEPENDENCY_NAME + " version " + TEST_DEPENDENCY_VERSION,
                "Versions of " + TEST_DEPENDENCY_NAME + " and " + TEST_MODULE_VERSION
                        + " are different or unknown."
        );
        assertThat(statuses).extracting(Status::getLevel).containsExactly(
                Status.INFO,
                Status.INFO,
                Status.WARN
        );
    }

    @Test
    void comparesExpectedDependencyVersion() {
        ContextBase context = new ContextBase();
        TestVersionUtil versionUtil = new TestVersionUtil(context);

        versionUtil.compareExpectedAndFoundVersion(
                TEST_DEPENDENCY_VERSION,
                VersionUtilTest.class,
                TEST_MODULE_VERSION,
                TEST_MODULE_NAME,
                TEST_DEPENDENCY_NAME
        );

        List<Status> statuses = context.getStatusManager().getCopyOfStatusList();
        assertThat(statuses).extracting(Status::getMessage).containsExactly(
                "Found " + TEST_DEPENDENCY_NAME + " version " + TEST_DEPENDENCY_VERSION,
                "Found " + TEST_MODULE_NAME + " version " + TEST_MODULE_VERSION
        );
        assertThat(statuses).extracting(Status::getLevel).containsOnly(Status.INFO);
    }

    private static final class TestVersionUtil extends VersionUtil {

        private TestVersionUtil(ContextBase context) {
            super(context);
        }

        @Override
        protected String getExpectedVersionOfDependencyByProperties(Class<?> dependerClass,
                String propertiesFileName, String dependencyNameAsKey) {
            assertThat(dependerClass).isEqualTo(VersionUtilTest.class);
            assertThat(propertiesFileName).isEqualTo(TEST_MODULE_NAME + "-dependencies.properties");
            assertThat(dependencyNameAsKey).isEqualTo(TEST_DEPENDENCY_NAME);
            return TEST_DEPENDENCY_VERSION;
        }
    }
}
