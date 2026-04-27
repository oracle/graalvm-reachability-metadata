/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.compilers.CompilerAdapter;
import org.apache.tools.ant.taskdefs.compilers.CompilerAdapterFactory;
import org.apache.tools.ant.taskdefs.compilers.Javac13;
import org.apache.tools.ant.taskdefs.compilers.Jikes;
import org.junit.jupiter.api.Test;

public class CompilerAdapterFactoryTest {
    @Test
    void createsAdapterFromFullyQualifiedCompilerAdapterClassName() {
        CompilerAdapter adapter = CompilerAdapterFactory.getCompiler(Jikes.class.getName(), newTask());

        assertThat(adapter).isInstanceOf(Jikes.class);
    }

    @Test
    void probesModernCompilerBeforeCreatingModernAdapter() {
        try {
            CompilerAdapter adapter = CompilerAdapterFactory.getCompiler("modern", newTask());

            assertThat(adapter).isInstanceOf(Javac13.class);
        } catch (BuildException exception) {
            assertThat(exception).hasMessageContaining("Unable to find a javac compiler");
        }
    }

    @Test
    void resolvesCompilerFactoryClassThroughLegacyClassLiteralHelper() throws Throwable {
        MethodHandle classLiteralResolver = MethodHandles.privateLookupIn(
                CompilerAdapterFactory.class,
                MethodHandles.lookup())
            .findStatic(
                CompilerAdapterFactory.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Object resolvedClass = classLiteralResolver.invoke(CompilerAdapterFactory.class.getName());

        assertThat(resolvedClass).isSameAs(CompilerAdapterFactory.class);
    }

    private static Task newTask() {
        Project project = new Project();
        project.init();

        Task task = new LoggingTask();
        task.setProject(project);
        task.setTaskName("javac");
        return task;
    }

    private static final class LoggingTask extends Task {
    }
}
