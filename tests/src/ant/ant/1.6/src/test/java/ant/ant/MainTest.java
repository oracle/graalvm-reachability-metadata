/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Permission;
import java.util.Vector;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Main;
import org.apache.tools.ant.NoBannerLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.input.DefaultInputHandler;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class MainTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void configuresLoggerAndListenerClassesFromCommandLine() throws Exception {
        Path buildFile = createBuildFile();
        ExposedMain main = new ExposedMain(new String[] {
                "-buildfile", buildFile.toString(),
                "-logger", NoBannerLogger.class.getName(),
                "-listener", NoBannerLogger.class.getName()
        });
        Project project = new Project();

        main.addConfiguredBuildListeners(project);

        Vector listeners = project.getBuildListeners();
        assertThat(listeners).hasSize(2);
        assertThat(listeners.elementAt(0)).isInstanceOf(NoBannerLogger.class);
        assertThat(listeners.elementAt(1)).isInstanceOf(NoBannerLogger.class);
        assertThat(Main.getAntVersion()).contains("Apache Ant version");
    }

    @Test
    void commandLineStartupInstantiatesConfiguredInputHandlerBeforeExiting() throws Exception {
        Path buildFile = createBuildFile();
        SecurityManager previousSecurityManager = System.getSecurityManager();
        ExitInterceptingSecurityManager securityManager = new ExitInterceptingSecurityManager();
        boolean securityManagerInstalled = installSecurityManagerIfSupported(securityManager);

        if (!securityManagerInstalled) {
            assertThat(Main.getAntVersion()).contains("Apache Ant version");
            return;
        }

        try {
            new Main().startAnt(new String[] {
                    "-buildfile", buildFile.toString(),
                    "-logger", NoBannerLogger.class.getName(),
                    "-listener", NoBannerLogger.class.getName(),
                    "-inputhandler", DefaultInputHandler.class.getName(),
                    "-projecthelp"
            }, null, null);
        } catch (ExitInterceptedException exception) {
            assertThat(exception.getStatus()).isEqualTo(0);
            return;
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
            return;
        } finally {
            System.setSecurityManager(previousSecurityManager);
        }

        throw new AssertionError("Ant startup should finish by calling System.exit");
    }

    @Test
    void reportsConfiguredTypesThatDoNotImplementAntExtensionPoints() throws Exception {
        Path buildFile = createBuildFile();
        ExposedMain main = new ExposedMain(new String[] {
                "-buildfile", buildFile.toString(),
                "-logger", String.class.getName(),
                "-listener", NoBannerLogger.class.getName()
        });

        assertThatCode(() -> main.addConfiguredBuildListeners(new Project()))
                .isInstanceOf(RuntimeException.class);

        SecurityManager previousSecurityManager = System.getSecurityManager();
        ExitInterceptingSecurityManager securityManager = new ExitInterceptingSecurityManager();
        boolean securityManagerInstalled = installSecurityManagerIfSupported(securityManager);
        if (!securityManagerInstalled) {
            return;
        }

        try {
            new Main().startAnt(new String[] {
                    "-buildfile", buildFile.toString(),
                    "-inputhandler", String.class.getName(),
                    "-projecthelp"
            }, null, null);
        } catch (ExitInterceptedException exception) {
            assertThat(exception.getStatus()).isEqualTo(1);
            return;
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
            return;
        } finally {
            System.setSecurityManager(previousSecurityManager);
        }

        throw new AssertionError("Invalid input handler startup should call System.exit");
    }

    @Test
    void rejectsMissingBuildFileBeforeDynamicStartupConfiguration() {
        Path missingBuildFile = temporaryDirectory.resolve("missing-build.xml");

        assertThatCode(() -> new ExposedMain(new String[] {
                "-buildfile", missingBuildFile.toString(),
                "-logger", NoBannerLogger.class.getName()
        }))
                .isInstanceOf(BuildException.class);
    }

    private Path createBuildFile() throws Exception {
        Path buildFile = temporaryDirectory.resolve("build.xml");
        Files.writeString(buildFile, """
                <project name=\"main-test\" default=\"noop\">
                    <description>Project used by MainTest.</description>
                    <target name=\"noop\" description=\"No-op target\"/>
                </project>
                """, StandardCharsets.UTF_8);
        return buildFile;
    }

    private static boolean installSecurityManagerIfSupported(SecurityManager securityManager) {
        try {
            System.setSecurityManager(securityManager);
            return System.getSecurityManager() == securityManager;
        } catch (UnsupportedOperationException | SecurityException exception) {
            return false;
        }
    }

    private static final class ExposedMain extends Main {
        ExposedMain(String[] args) throws BuildException {
            super(args);
        }

        void addConfiguredBuildListeners(Project project) {
            addBuildListeners(project);
        }
    }

    private static final class ExitInterceptingSecurityManager extends SecurityManager {
        @Override
        public void checkPermission(Permission permission) {
            // Allow Ant to read files, access system properties, and restore streams.
        }

        @Override
        public void checkExit(int status) {
            throw new ExitInterceptedException(status);
        }
    }

    public static final class ExitInterceptedException extends SecurityException {
        private final int status;

        ExitInterceptedException(int status) {
            super("Intercepted System.exit(" + status + ")");
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }
}
