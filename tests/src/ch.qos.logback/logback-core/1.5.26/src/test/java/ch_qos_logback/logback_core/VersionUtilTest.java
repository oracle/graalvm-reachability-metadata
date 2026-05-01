/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.util.VersionUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionUtilTest {
    private static final String SELF_DECLARED_MODULE_NAME = "version-util-test-module";
    private static final String SELF_DECLARED_MODULE_VERSION = "self-declared-test-version";
    private static final String DEPENDER_NAME = "version-util-dependent";
    private static final String DEPENDER_VERSION = "dependent-test-version";
    private static final String DEPENDENCY_NAME = "version-util-dependency";
    private static final String EXPECTED_DEPENDENCY_VERSION = "expected-dependency-test-version";

    @Test
    void getArtifactVersionBySelfDeclaredPropertiesReadsPackageRelativeResource() {
        String version = VersionUtil.getArtifactVersionBySelfDeclaredProperties(
                VersionUtilTest.class, SELF_DECLARED_MODULE_NAME);

        assertThat(version).isEqualTo(SELF_DECLARED_MODULE_VERSION);
    }

    @Test
    void compareExpectedAndFoundVersionReadsDependencyVersionResourceFromClassLoader() {
        ContextBase context = new ContextBase();

        VersionUtil.compareExpectedAndFoundVersion(context, EXPECTED_DEPENDENCY_VERSION, VersionUtilTest.class,
                DEPENDER_VERSION, DEPENDER_NAME, DEPENDENCY_NAME);

        List<Status> statuses = context.getStatusManager().getCopyOfStatusList();
        assertThat(statuses)
                .extracting(Status::getMessage)
                .contains("Found " + DEPENDENCY_NAME + " version " + EXPECTED_DEPENDENCY_VERSION,
                        "Found " + DEPENDER_NAME + " version " + DEPENDER_VERSION);
        assertThat(statuses)
                .extracting(Status::getLevel)
                .doesNotContain(Status.WARN, Status.ERROR);
    }
}
