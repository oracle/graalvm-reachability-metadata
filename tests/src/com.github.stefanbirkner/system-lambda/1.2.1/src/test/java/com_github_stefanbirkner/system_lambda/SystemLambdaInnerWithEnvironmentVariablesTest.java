/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_stefanbirkner.system_lambda;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static com.github.stefanbirkner.systemlambda.SystemLambda.withEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SystemLambdaInnerWithEnvironmentVariablesTest {
    @Test
    public void executesCallableWithConfiguredEnvironmentVariables() throws Exception {
        String firstName = "SYSTEM_LAMBDA_TEST_FIRST";
        String secondName = "SYSTEM_LAMBDA_TEST_SECOND";
        String originalFirst = System.getenv(firstName);
        String originalSecond = System.getenv(secondName);

        List<String> values = withEnvironmentVariable(firstName, "first-value")
                .and(secondName, "second-value")
                .execute(() -> Arrays.asList(
                        System.getenv(firstName),
                        System.getenv(secondName)));

        assertEquals(Arrays.asList("first-value", "second-value"), values);
        assertEquals(originalFirst, System.getenv(firstName));
        assertEquals(originalSecond, System.getenv(secondName));
    }
}
