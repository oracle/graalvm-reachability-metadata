/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package args4j.args4j;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kohsuke.args4j.Starter;

import static org.assertj.core.api.Assertions.assertThat;

public class StarterTest {
    private String originalMainClass;

    @AfterEach
    void restoreMainClassProperty() {
        if (originalMainClass == null) {
            System.clearProperty(Starter.PARAMETER_NAME);
        } else {
            System.setProperty(Starter.PARAMETER_NAME, originalMainClass);
        }
    }

    @Test
    void invokesNoArgumentRunMethodOnConfiguredClass() {
        NoArgumentRunApplication.reset();

        start(NoArgumentRunApplication.class);

        assertThat(NoArgumentRunApplication.invoked).isTrue();
        assertThat(NoArgumentRunApplication.instanceCreated).isTrue();
    }

    @Test
    void invokesStringArrayRunMethodWhenNoArgumentRunMethodIsAbsent() {
        StringArrayRunApplication.reset();

        start(StringArrayRunApplication.class);

        assertThat(StringArrayRunApplication.invoked).isTrue();
        assertThat(StringArrayRunApplication.receivedArguments).isEmpty();
    }

    private void start(Class<?> applicationClass) {
        originalMainClass = System.getProperty(Starter.PARAMETER_NAME);
        System.setProperty(Starter.PARAMETER_NAME, applicationClass.getName());
        Starter.main(new String[0]);
    }

    public static class NoArgumentRunApplication {
        private static boolean instanceCreated;
        private static boolean invoked;

        public String publicField;

        public NoArgumentRunApplication() {
            instanceCreated = true;
        }

        public static void reset() {
            instanceCreated = false;
            invoked = false;
        }

        public void run() {
            invoked = true;
        }
    }

    public static class StringArrayRunApplication {
        private static boolean invoked;
        private static String[] receivedArguments;

        public String publicField;

        public StringArrayRunApplication() {
        }

        public static void reset() {
            invoked = false;
            receivedArguments = null;
        }

        public void run(String[] arguments) {
            invoked = true;
            receivedArguments = arguments.clone();
        }
    }
}
