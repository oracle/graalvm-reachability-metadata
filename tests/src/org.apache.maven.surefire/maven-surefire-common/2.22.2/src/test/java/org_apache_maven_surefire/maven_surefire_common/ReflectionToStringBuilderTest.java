/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.maven_surefire_common;

import org.apache.maven.surefire.shade.org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.maven.surefire.shade.org.apache.commons.lang3.builder.ToStringStyle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionToStringBuilderTest {
    @Test
    void toStringReadsPrivateFieldsDeclaredAcrossHierarchy() {
        ReportedRun run = new ReportedRun("suite-1", 3, "fork-7", "cached");

        String description = ReflectionToStringBuilder.toString(run, ToStringStyle.SHORT_PREFIX_STYLE);

        assertThat(description)
                .contains("suiteId=suite-1")
                .contains("executedTests=3")
                .doesNotContain("forkId=")
                .doesNotContain("cacheState=");
    }

    @Test
    void toStringCanIncludeTransientAndStaticFieldsWhenRequested() {
        ReportedRun run = new ReportedRun("suite-2", 5, "fork-9", "fresh");

        String description = ReflectionToStringBuilder.toString(
                run, ToStringStyle.SHORT_PREFIX_STYLE, true, true);

        assertThat(description)
                .contains("suiteId=suite-2")
                .contains("executedTests=5")
                .contains("forkId=fork-9")
                .contains("CATEGORY=integration")
                .contains("cacheState=fresh");
    }

    private static class BaseRun {
        private final String suiteId;
        private transient String forkId;

        private BaseRun(String suiteId, String forkId) {
            this.suiteId = suiteId;
            this.forkId = forkId;
        }
    }

    private static final class ReportedRun extends BaseRun {
        private static final String CATEGORY = "integration";

        private final int executedTests;
        private transient String cacheState;

        private ReportedRun(String suiteId, int executedTests, String forkId, String cacheState) {
            super(suiteId, forkId);
            this.executedTests = executedTests;
            this.cacheState = cacheState;
        }
    }
}
