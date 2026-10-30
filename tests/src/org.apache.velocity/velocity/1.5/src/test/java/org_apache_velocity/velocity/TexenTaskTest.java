/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import java.nio.file.Path;

import org.apache.tools.ant.Project;
import org.apache.velocity.texen.ant.TexenTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TexenTaskTest {
    @TempDir
    Path projectDirectory;

    @Test
    void loadsGenerationContextPropertiesFromClasspath() {
        Project project = new Project();
        project.setBaseDir(projectDirectory.toFile());
        TexenTask task = new TexenTask();
        task.setProject(project);
        task.setContextProperties("velocity/settings.properties");
        assertEquals("Atlas", task.getContextProperties().getString("projectName"));
        assertEquals(4, task.getContextProperties().getInt("itemCount"));
    }
}
