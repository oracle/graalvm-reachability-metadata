/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tools.ant.Location;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.TaskAdapter;
import org.junit.jupiter.api.Test;

public class TaskAdapterTest {
    @Test
    void adaptsTasksThatAcceptProjectAndLocation() {
        Project project = new Project();
        project.initProperties();
        Location location = new Location("build.xml");
        AdaptedTask adaptedTask = new AdaptedTask();
        TaskAdapter adapter = new TaskAdapter(adaptedTask);
        adapter.setProject(project);
        adapter.setLocation(location);

        adapter.checkProxyClass(AdaptedTask.class);
        adapter.execute();

        assertThat(adaptedTask.project).isSameAs(project);
        assertThat(adaptedTask.location).isSameAs(location);
        assertThat(adaptedTask.executed).isTrue();
    }

    public static class AdaptedTask {
        private Project project;
        private Location location;
        private boolean executed;

        public void setProject(Project project) {
            this.project = project;
        }

        public void setLocation(Location location) {
            this.location = location;
        }

        public void execute() {
            executed = true;
        }
    }
}
