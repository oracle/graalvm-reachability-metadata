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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Parallel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ParallelTest {
    @Test
    void computesThreadCountFromAvailableProcessorsWhenThreadsPerProcessorIsConfigured() throws Throwable {
        Project project = newProject();
        AtomicInteger executionCount = new AtomicInteger();
        Parallel parallel = new Parallel();
        configureTask(parallel, project, "parallel");
        parallel.setThreadsPerProcessor(1);
        parallel.addTask(countingTask(project, executionCount));
        parallel.addTask(countingTask(project, executionCount));
        parallel.addTask(countingTask(project, executionCount));

        ExposedParallel.clearCachedRuntimeClass();
        assertThat(ExposedParallel.lookupCompilerGeneratedClass(Runtime.class.getName()))
                .isSameAs(Runtime.class);
        ExposedParallel.clearCachedRuntimeClass();
        parallel.execute();

        assertThat(executionCount.get()).isEqualTo(3);
    }

    private Project newProject() {
        Project project = new Project();
        project.init();
        return project;
    }

    private CountingTask countingTask(Project project, AtomicInteger executionCount) {
        CountingTask task = new CountingTask(executionCount);
        configureTask(task, project, "counting");
        return task;
    }

    private void configureTask(Task task, Project project, String taskName) {
        task.setProject(project);
        task.setTaskName(taskName);
    }

    private static final class ExposedParallel {
        private static final MethodHandle CLASS_LOOKUP = classLookupMethod();
        private static final VarHandle RUNTIME_CLASS = staticField(
                "class$java$lang$Runtime",
                Class.class);

        static Class<?> lookupCompilerGeneratedClass(String className) throws Throwable {
            return (Class<?>) CLASS_LOOKUP.invoke(className);
        }

        static void clearCachedRuntimeClass() {
            RUNTIME_CLASS.set(null);
        }

        private static MethodHandle classLookupMethod() {
            try {
                return MethodHandles.privateLookupIn(Parallel.class, MethodHandles.lookup())
                        .findStatic(
                                Parallel.class,
                                "class$",
                                MethodType.methodType(Class.class, String.class));
            } catch (NoSuchMethodException | IllegalAccessException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }

        private static VarHandle staticField(String fieldName, Class<?> fieldType) {
            try {
                return MethodHandles.privateLookupIn(Parallel.class, MethodHandles.lookup())
                        .findStaticVarHandle(Parallel.class, fieldName, fieldType);
            } catch (NoSuchFieldException | IllegalAccessException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }
    }

    private static final class CountingTask extends Task {
        private final AtomicInteger executionCount;

        private CountingTask(AtomicInteger executionCount) {
            this.executionCount = executionCount;
        }

        @Override
        public void execute() {
            executionCount.incrementAndGet();
        }
    }
}
