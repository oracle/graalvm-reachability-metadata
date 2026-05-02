/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package antlr.antlr;

import antlr.build.Tool;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ToolTest {
    @Test
    void syntheticClassLookupResolvesTheToolClass() throws Throwable {
        MethodHandle classLookup = syntheticClassLookup();

        Class<?> resolvedClass = invokeSyntheticClassLookup(classLookup, Tool.class.getName());

        assertThat(resolvedClass).isSameAs(Tool.class);
    }

    @Test
    void syntheticClassLookupReportsMissingClassAsNoClassDefinition() throws Exception {
        MethodHandle classLookup = syntheticClassLookup();

        NoClassDefFoundError error = assertThrows(NoClassDefFoundError.class,
                () -> invokeSyntheticClassLookup(classLookup, "antlr.build.DoesNotExist"));

        assertThat(error).hasMessage("antlr.build.DoesNotExist");
    }

    @Test
    void performAttemptsToResolveShortApplicationName() {
        RecordingTool tool = new RecordingTool();

        tool.perform("ANTLR", "build");

        assertThat(tool.errors).hasSize(1);
        RecordedError error = tool.errors.get(0);
        assertThat(error.message).isEqualTo("no such application ANTLR");
        assertThat(error.exception).isInstanceOf(ClassNotFoundException.class);
        assertThat(tool.commands).isEmpty();
    }

    @Test
    void performLoadsFullyQualifiedApplicationNameWithoutRunningBuildCommands() {
        RecordingTool tool = new RecordingTool();

        tool.perform("antlr.build.ANTLR", "build");

        assertThat(tool.errors).isEmpty();
        assertThat(tool.commands).isEmpty();
    }

    private static MethodHandle syntheticClassLookup() throws NoSuchMethodException, IllegalAccessException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(Tool.class, MethodHandles.lookup());
        return lookup.findStatic(Tool.class, "class$", MethodType.methodType(Class.class, String.class));
    }

    private static Class<?> invokeSyntheticClassLookup(MethodHandle classLookup, String className) throws Throwable {
        return (Class<?>) classLookup.invokeExact(className);
    }

    private static final class RecordingTool extends Tool {
        private final List<String> commands = new ArrayList<>();
        private final List<RecordedError> errors = new ArrayList<>();

        @Override
        public void system(String cmd) {
            commands.add(cmd);
        }

        @Override
        public void error(String msg) {
            errors.add(new RecordedError(msg, null));
        }

        @Override
        public void error(String msg, Exception e) {
            errors.add(new RecordedError(msg, e));
        }
    }

    private static final class RecordedError {
        private final String message;
        private final Exception exception;

        private RecordedError(String message, Exception exception) {
            this.message = message;
            this.exception = exception;
        }
    }
}
