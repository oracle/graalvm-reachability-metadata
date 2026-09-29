/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_stefanbirkner.system_lambda;

import org.junit.jupiter.api.Test;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SystemLambdaTest {
    @Test
    public void capturesTextWrittenToSystemOut() throws Exception {
        String output = tapSystemOut(() -> System.out.print("captured output"));

        assertEquals("captured output", output);
    }
}
