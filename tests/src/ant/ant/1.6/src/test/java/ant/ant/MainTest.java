/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Vector;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Main;
import org.apache.tools.ant.NoBannerLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.input.DefaultInputHandler;
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
    }

    @Test
    void configuresInputHandlerClassFromCommandLine() throws Throwable {
        Path buildFile = createBuildFile();
        ExposedMain main = new ExposedMain(new String[] {
                "-buildfile", buildFile.toString(),
                "-inputhandler", DefaultInputHandler.class.getName()
        });
        Project project = new Project();

        main.addConfiguredInputHandler(project);

        assertThat(project.getInputHandler()).isInstanceOf(DefaultInputHandler.class);
    }

    @Test
    void loadsAntVersionFromMainClassResource() throws Throwable {
        ExposedMain.clearCachedAntVersion();

        String antVersion = Main.getAntVersion();

        assertThat(antVersion)
                .contains("Apache Ant version")
                .contains("compiled on");
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

    private static final class ExposedMain extends Main {
        private static final MethodHandle ADD_INPUT_HANDLER = addInputHandlerMethod();
        private static final VarHandle ANT_VERSION = staticField("antVersion", String.class);
        private static final VarHandle MAIN_CLASS = staticField(
                "class$org$apache$tools$ant$Main",
                Class.class);

        ExposedMain(String[] args) throws BuildException {
            super(args);
        }

        void addConfiguredBuildListeners(Project project) {
            addBuildListeners(project);
        }

        void addConfiguredInputHandler(Project project) throws Throwable {
            ADD_INPUT_HANDLER.invoke(this, project);
        }

        static void clearCachedAntVersion() {
            ANT_VERSION.set(null);
            MAIN_CLASS.set(null);
        }

        private static MethodHandle addInputHandlerMethod() {
            try {
                return privateMainLookup().findVirtual(
                        Main.class,
                        "addInputHandler",
                        MethodType.methodType(void.class, Project.class));
            } catch (NoSuchMethodException | IllegalAccessException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }

        private static VarHandle staticField(String fieldName, Class<?> fieldType) {
            try {
                return privateMainLookup().findStaticVarHandle(Main.class, fieldName, fieldType);
            } catch (NoSuchFieldException | IllegalAccessException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }

        private static MethodHandles.Lookup privateMainLookup() throws IllegalAccessException {
            return MethodHandles.privateLookupIn(Main.class, MethodHandles.lookup());
        }
    }
}
