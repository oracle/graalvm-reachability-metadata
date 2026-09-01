/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javax.swing.JLabel;

import junit.runner.FailureDetailView;
import junit.swingui.DefaultFailureDetailView;
import junit.swingui.TestRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class SwingTestRunnerTest {
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void createsConfiguredFailureViewAndLogo(@TempDir Path temporaryHome) throws Exception {
        Files.writeString(
                temporaryHome.resolve("junit.properties"),
                "FailureViewClass=junit.swingui.DefaultFailureDetailView\n");
        String originalHome = System.getProperty("user.home");
        System.setProperty("user.home", temporaryHome.toString());
        try {
            ExposedTestRunner runner = new ExposedTestRunner();

            FailureDetailView failureView = runner.newFailureDetailView();
            JLabel logo = runner.newLogo();

            assertThat(failureView).isInstanceOf(DefaultFailureDetailView.class);
            assertThat(logo.getIcon()).isNotNull();
        } finally {
            if (originalHome == null) {
                System.clearProperty("user.home");
            } else {
                System.setProperty("user.home", originalHome);
            }
        }
    }

    public static class ExposedTestRunner extends TestRunner {
        public FailureDetailView newFailureDetailView() {
            return createFailureDetailView();
        }

        public JLabel newLogo() {
            return createLogo();
        }
    }
}
