/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.surefire_api;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.maven.surefire.api.suite.RunResult;
import org.apache.maven.surefire.booter.SurefireReflector;
import org.junit.jupiter.api.Test;

public class SurefireReflectorInnerClassLoaderProxyTest {

    @Test
    public void convertsRunResultLoadedThroughProviderApiTypes() {
        final SurefireReflector reflector = new SurefireReflector(getClass().getClassLoader());
        final RunResult runResult = new RunResult(7, 1, 2, 3);

        final Object converted = reflector.convertIfRunResult(runResult);
        final Object unchanged = reflector.convertIfRunResult("not-a-run-result");

        assertThat(converted)
                .isInstanceOfSatisfying(RunResult.class, convertedRunResult -> {
                    assertThat(convertedRunResult.getCompletedCount()).isEqualTo(7);
                    assertThat(convertedRunResult.getErrors()).isEqualTo(1);
                    assertThat(convertedRunResult.getFailures()).isEqualTo(2);
                    assertThat(convertedRunResult.getSkipped()).isEqualTo(3);
                });
        assertThat(unchanged).isEqualTo("not-a-run-result");
    }
}
