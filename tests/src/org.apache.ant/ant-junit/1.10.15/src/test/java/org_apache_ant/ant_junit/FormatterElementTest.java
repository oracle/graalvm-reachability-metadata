/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant_junit;

import static org.assertj.core.api.Assertions.assertThat;

import junit.framework.TestCase;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.optional.junit.FormatterElement;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTask;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTest;
import org.apache.tools.ant.taskdefs.optional.junit.PlainJUnitResultFormatter;
import org.junit.jupiter.api.Test;

public class FormatterElementTest {
    @Test
    void createsFormatterWithDefaultClassLoaderAndExistingProjectField() {
        ExistingProjectFormatter.constructorCalls = 0;

        executeJUnitTask(ExistingProjectFormatter.class, false);

        assertThat(ExistingProjectFormatter.constructorCalls).isPositive();
    }

    @Test
    void createsFormatterWithAntClassLoaderAndProjectSetter() {
        ProjectReceivingFormatter.receivedProject = null;

        Project project = executeJUnitTask(ProjectReceivingFormatter.class, true);

        assertThat(ProjectReceivingFormatter.receivedProject).isSameAs(project);
    }

    private static Project executeJUnitTask(Class<?> formatterClass, boolean createTaskClasspath) {
        Project project = new Project();
        project.init();

        JUnitTask task = new JUnitTask();
        task.setProject(project);
        task.init();
        task.setFork(false);
        task.setHaltonerror(true);
        task.setHaltonfailure(true);
        if (createTaskClasspath) {
            task.createClasspath();
        }

        FormatterElement formatter = new FormatterElement();
        formatter.setClassname(formatterClass.getName());
        formatter.setUseFile(false);
        formatter.setProject(project);
        task.addFormatter(formatter);
        task.addTest(new JUnitTest(PassingJUnit3Test.class.getName()));

        task.execute();
        return project;
    }

    public static class ExistingProjectFormatter extends PlainJUnitResultFormatter {
        static int constructorCalls;

        public Project project = new Project();

        public ExistingProjectFormatter() {
            constructorCalls++;
        }
    }

    public static class ProjectReceivingFormatter extends PlainJUnitResultFormatter {
        static Project receivedProject;

        public void setProject(Project project) {
            receivedProject = project;
        }
    }

    public static class PassingJUnit3Test extends TestCase {
        public void testPasses() {
        }
    }
}
