/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import java.awt.Component;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.SwingUtilities;

import junit.framework.TestCase;
import junit.framework.TestFailure;
import junit.framework.TestResult;
import junit.runner.FailureDetailView;
import junit.runner.TestCollector;
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
                "FailureViewClass=" + ConfiguredFailureView.class.getName() + "\n");
        String originalHome = System.getProperty("user.home");
        System.setProperty("user.home", temporaryHome.toString());
        try {
            ExposedTestRunner runner = new ExposedTestRunner();

            FailureDetailView failureView = runner.newFailureDetailView();
            JLabel logo = runner.newLogo();

            assertThat(failureView).isInstanceOf(ConfiguredFailureView.class);
            assertThat(logo.getIcon()).isNotNull();
        } finally {
            if (originalHome == null) {
                System.clearProperty("user.home");
            } else {
                System.setProperty("user.home", originalHome);
            }
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void browsesTestsWithConfiguredCollector(@TempDir Path temporaryHome) throws Exception {
        Files.writeString(
                temporaryHome.resolve("junit.properties"),
                "TestCollectorClass=" + ConfiguredTestCollector.class.getName() + "\n");
        String originalHome = System.getProperty("user.home");
        System.setProperty("user.home", temporaryHome.toString());
        ConfiguredTestCollector.resetState();
        ExposedTestRunner runner = null;
        try {
            runner = new ExposedTestRunner();
            runner.start(new String[0]);
            ExposedTestRunner startedRunner = runner;

            SwingUtilities.invokeLater(startedRunner::browseTestClasses);
            assertThat(ConfiguredTestCollector.collected.await(10, TimeUnit.SECONDS)).isTrue();
            SwingUtilities.invokeAndWait(SwingTestRunnerTest::selectDiscoveredTest);
            SwingUtilities.invokeAndWait(() -> {});

            assertThat(ConfiguredTestCollector.creationCount).isEqualTo(1);
            assertThat(runner.suiteText()).isEqualTo(RerunnableTestCase.class.getName());
        } finally {
            dispose(runner);
            restoreUserHome(originalHome);
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void rerunsSelectedFailureWithStringConstructor(@TempDir Path temporaryHome) throws Exception {
        String originalHome = System.getProperty("user.home");
        System.setProperty("user.home", temporaryHome.toString());
        RerunnableTestCase.resetState();
        ExposedTestRunner runner = null;
        try {
            runner = new ExposedTestRunner();
            runner.setLoading(false);
            runner.start(new String[] {RerunnableTestCase.class.getName()});

            assertThat(RerunnableTestCase.initialRunFinished.await(10, TimeUnit.SECONDS)).isTrue();
            SwingUtilities.invokeAndWait(() -> {});
            assertThat(RerunnableTestCase.constructionCount).isEqualTo(1);
            assertThat(RerunnableTestCase.runCount).isEqualTo(1);

            ExposedTestRunner startedRunner = runner;
            SwingUtilities.invokeAndWait(() -> {
                JButton rerunButton = findRerunButton(startedRunner.frame());
                assertThat(rerunButton).isNotNull();
                assertThat(rerunButton.isEnabled()).isTrue();
                rerunButton.doClick();
            });

            assertThat(RerunnableTestCase.constructionCount).isEqualTo(2);
            assertThat(RerunnableTestCase.runCount).isEqualTo(2);
        } finally {
            dispose(runner);
            restoreUserHome(originalHome);
        }
    }

    private static void selectDiscoveredTest() {
        JDialog selector = null;
        for (Window window : Window.getWindows()) {
            if (window instanceof JDialog && "Test Selector".equals(((JDialog) window).getTitle())) {
                selector = (JDialog) window;
                break;
            }
        }
        assertThat(selector).isNotNull();

        JList<?> testList = findComponent(selector, JList.class);
        JButton okButton = findButton(selector, "OK");
        assertThat(testList).isNotNull();
        assertThat(okButton).isNotNull();
        testList.setSelectedIndex(0);
        okButton.doClick();
    }

    private static JButton findRerunButton(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof JButton) {
                JButton button = (JButton) component;
                if ("Run".equals(button.getText()) && button.getParent().getLayout() instanceof GridLayout) {
                    return button;
                }
            }
            if (component instanceof Container) {
                JButton button = findRerunButton((Container) component);
                if (button != null) {
                    return button;
                }
            }
        }
        return null;
    }

    private static JButton findButton(Container container, String text) {
        for (Component component : container.getComponents()) {
            if (component instanceof JButton && text.equals(((JButton) component).getText())) {
                return (JButton) component;
            }
            if (component instanceof Container) {
                JButton button = findButton((Container) component, text);
                if (button != null) {
                    return button;
                }
            }
        }
        return null;
    }

    private static <T extends Component> T findComponent(Container container, Class<T> componentType) {
        for (Component component : container.getComponents()) {
            if (componentType.isInstance(component)) {
                return componentType.cast(component);
            }
            if (component instanceof Container) {
                T match = findComponent((Container) component, componentType);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static void dispose(ExposedTestRunner runner) throws Exception {
        if (runner == null || runner.frame() == null) {
            return;
        }
        JFrame frame = runner.frame();
        SwingUtilities.invokeAndWait(() -> {
            for (Window window : frame.getOwnedWindows()) {
                window.dispose();
            }
            frame.dispose();
        });
    }

    private static void restoreUserHome(String originalHome) {
        if (originalHome == null) {
            System.clearProperty("user.home");
        } else {
            System.setProperty("user.home", originalHome);
        }
    }

    public static class ConfiguredTestCollector implements TestCollector {
        private static CountDownLatch collected;
        private static int creationCount;

        public ConfiguredTestCollector() {
            creationCount++;
        }

        @Override
        public Enumeration<?> collectTests() {
            collected.countDown();
            return Collections.enumeration(Collections.singletonList(RerunnableTestCase.class.getName()));
        }

        private static void resetState() {
            collected = new CountDownLatch(1);
            creationCount = 0;
        }
    }

    public static class RerunnableTestCase extends TestCase {
        private static CountDownLatch initialRunFinished;
        private static int constructionCount;
        private static int runCount;

        public RerunnableTestCase(String name) {
            super(name);
            constructionCount++;
        }

        @Override
        public void run(TestResult result) {
            super.run(result);
            initialRunFinished.countDown();
        }

        public void testRerunnableSelection() {
            runCount++;
            if (runCount == 1) {
                fail("Select this test for rerun");
            }
        }

        private static void resetState() {
            initialRunFinished = new CountDownLatch(1);
            constructionCount = 0;
            runCount = 0;
        }
    }

    public static class ConfiguredFailureView implements FailureDetailView {
        private final JLabel component = new JLabel("Failure details");

        @Override
        public Component getComponent() {
            return component;
        }

        @Override
        public void showFailure(TestFailure failure) {
            component.setText(failure.toString());
        }

        @Override
        public void clear() {
            component.setText("");
        }
    }

    public static class ExposedTestRunner extends TestRunner {
        public FailureDetailView newFailureDetailView() {
            return createFailureDetailView();
        }

        public JLabel newLogo() {
            return createLogo();
        }

        public JFrame frame() {
            return fFrame;
        }

        public String suiteText() {
            return getSuiteText();
        }
    }
}
