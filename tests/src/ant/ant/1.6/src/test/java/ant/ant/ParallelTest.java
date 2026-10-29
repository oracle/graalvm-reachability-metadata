/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Parallel;
import org.junit.jupiter.api.Test;

public class ParallelTest {
    @Test
    void sizesWorkersByProcessorAndRunsAllTasks() {
        Project project = new Project();
        Parallel parallel = new Parallel();
        parallel.setProject(project);
        parallel.setThreadsPerProcessor(1);
        parallel.setTimeout(30000);
        AtomicInteger count = new AtomicInteger();
        for (int i = 0; i < 3; i++) {
            Task task = new Task() {
                @Override
                public void execute() {
                    count.incrementAndGet();
                }
            };
            task.setProject(project);
            parallel.addTask(task);
        }
        parallel.execute();
        assertEquals(3, count.get());
    }
}
