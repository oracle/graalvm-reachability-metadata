/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_ant;

import groovy.ant.AntBuilder;
import org.apache.tools.ant.BuildListener;
import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.Property;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(30)
public class AntBuilderTest {
    @Test
    void executesPropertyTaskAndNotifiesListeners() {
        AntBuilder builder = new AntBuilder();
        TaskEvents events = new TaskEvents();
        builder.getProject().addBuildListener(events);

        Object task = builder.invokeMethod("property", new Object[]{
                Map.of("name", "build.message", "value", "ready to package")
        });

        assertInstanceOf(Property.class, task);
        assertEquals("ready to package", builder.getProject().getProperty("build.message"));
        assertEquals(1, events.started.size());
        assertEquals(1, events.finished.size());
        assertEquals("property", events.started.getFirst().getTask().getTaskName());
        assertSame(events.started.getFirst().getTask(), events.finished.getFirst().getTask());
        assertNull(events.finished.getFirst().getException());
    }

    @Test
    void reportsExplicitBuildFailureToListeners() {
        AntBuilder builder = new AntBuilder();
        TaskEvents events = new TaskEvents();
        builder.getProject().addBuildListener(events);

        BuildException failure = assertThrows(BuildException.class, () ->
                builder.invokeMethod("fail", new Object[]{Map.of("message", "required input is absent")}));

        assertEquals("required input is absent", failure.getMessage());
        assertEquals(1, events.started.size());
        assertEquals(1, events.finished.size());
        assertEquals("fail", events.started.getFirst().getTask().getTaskName());
        assertSame(events.started.getFirst().getTask(), events.finished.getFirst().getTask());
        assertSame(failure, events.finished.getFirst().getException());
    }

    private static class TaskEvents implements BuildListener {
        private final List<BuildEvent> started = new ArrayList<>();
        private final List<BuildEvent> finished = new ArrayList<>();

        @Override
        public void taskStarted(BuildEvent event) {
            started.add(event);
        }

        @Override
        public void taskFinished(BuildEvent event) {
            finished.add(event);
        }

        @Override
        public void buildStarted(BuildEvent event) {
        }

        @Override
        public void buildFinished(BuildEvent event) {
        }

        @Override
        public void targetStarted(BuildEvent event) {
        }

        @Override
        public void targetFinished(BuildEvent event) {
        }

        @Override
        public void messageLogged(BuildEvent event) {
        }
    }
}
