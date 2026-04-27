/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Parallel;
import org.junit.jupiter.api.Test;

public class ParallelTest {
    @Test
    void scalesThreadCountFromAvailableProcessorsAndExecutesNestedTasks() throws ReflectiveOperationException {
        resetRuntimeClassCache();

        AtomicInteger executions = new AtomicInteger();
        Project project = new Project();
        project.init();

        Parallel parallel = new Parallel();
        parallel.setProject(project);
        parallel.setTaskName("parallel");
        parallel.setThreadsPerProcessor(1);
        parallel.addTask(new CountingTask(project, executions));
        parallel.addTask(new CountingTask(project, executions));

        parallel.execute();

        assertThat(executions).hasValue(2);
    }

    private static void resetRuntimeClassCache() throws ReflectiveOperationException {
        Field runtimeClassCache = Parallel.class.getDeclaredField("class$java$lang$Runtime");
        runtimeClassCache.setAccessible(true);
        runtimeClassCache.set(null, null);
    }

    private static final class CountingTask extends Task {
        private final AtomicInteger executions;

        private CountingTask(Project project, AtomicInteger executions) {
            this.executions = executions;
            setProject(project);
            setTaskName("counting");
        }

        @Override
        public void execute() {
            executions.incrementAndGet();
        }
    }
}
