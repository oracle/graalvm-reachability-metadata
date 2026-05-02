/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Parallel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ParallelTest {
    @Test
    void computesThreadCountFromAvailableProcessorsWhenThreadsPerProcessorIsConfigured() {
        Project project = newProject();
        AtomicInteger executionCount = new AtomicInteger();
        Parallel parallel = new Parallel();
        configureTask(parallel, project, "parallel");
        parallel.setThreadsPerProcessor(1);
        parallel.addTask(countingTask(project, executionCount));
        parallel.addTask(countingTask(project, executionCount));
        parallel.addTask(countingTask(project, executionCount));

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
