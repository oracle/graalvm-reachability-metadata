/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hadoop.util.ProgramDriver;
import org.junit.jupiter.api.Test;

public class ProgramDriverInnerProgramDescriptionTest {
    private static final String RESULT_PROPERTY = ProgramDriverInnerProgramDescriptionTest.class.getName() + ".result";

    @Test
    void runInvokesRegisteredProgramMainMethod() throws Throwable {
        ProgramDriver driver = new ProgramDriver();
        String previousResult = System.getProperty(RESULT_PROPERTY);
        System.clearProperty(RESULT_PROPERTY);

        String result = null;
        try {
            driver.addClass("registered", RegisteredProgram.class, "Registered program");
            int exitCode = driver.run(new String[] {"registered", "alpha", "beta"});

            result = System.getProperty(RESULT_PROPERTY);
            assertThat(exitCode).isZero();
        } finally {
            restoreProperty(previousResult);
        }

        assertThat(result).isEqualTo("alpha,beta");
    }

    private static void restoreProperty(String previousResult) {
        if (previousResult == null) {
            System.clearProperty(RESULT_PROPERTY);
        } else {
            System.setProperty(RESULT_PROPERTY, previousResult);
        }
    }

    public static class RegisteredProgram {
        public static void main(String[] args) {
            System.setProperty(RESULT_PROPERTY, String.join(",", args));
        }
    }
}
