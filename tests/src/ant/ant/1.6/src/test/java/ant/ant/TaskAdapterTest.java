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

import org.apache.tools.ant.Project;
import org.apache.tools.ant.TaskAdapter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class TaskAdapterTest {
    @Test
    void compilerGeneratedClassLookupResolvesProjectType() throws Throwable {
        Class<?> resolvedClass = ExposedTaskAdapter.lookupCompilerGeneratedClass(
                Project.class.getName());

        assertThat(resolvedClass).isSameAs(Project.class);
    }

    @Test
    void validatesAndExecutesBeanTaskThroughAdapter() {
        Project project = new Project();
        AdaptedBeanTask proxy = new AdaptedBeanTask();
        TaskAdapter adapter = new TaskAdapter();
        adapter.setProject(project);
        adapter.setProxy(proxy);

        assertThatCode(() -> TaskAdapter.checkTaskClass(AdaptedBeanTask.class, project))
                .doesNotThrowAnyException();
        assertThatCode(() -> adapter.checkProxyClass(AdaptedBeanTask.class))
                .doesNotThrowAnyException();

        ExposedTaskAdapter.clearCachedProjectClass();
        adapter.execute();

        assertThat(adapter.getProxy()).isSameAs(proxy);
        assertThat(proxy.project).isSameAs(project);
        assertThat(proxy.executed).isTrue();
    }

    private static final class ExposedTaskAdapter {
        private static final MethodHandle CLASS_LOOKUP = classLookupMethod();
        private static final VarHandle PROJECT_CLASS = staticField(
                "class$org$apache$tools$ant$Project",
                Class.class);

        static Class<?> lookupCompilerGeneratedClass(String className) throws Throwable {
            return (Class<?>) CLASS_LOOKUP.invoke(className);
        }

        static void clearCachedProjectClass() {
            PROJECT_CLASS.set(null);
        }

        private static MethodHandle classLookupMethod() {
            try {
                return taskAdapterLookup().findStatic(
                        TaskAdapter.class,
                        "class$",
                        MethodType.methodType(Class.class, String.class));
            } catch (NoSuchMethodException | IllegalAccessException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }

        private static VarHandle staticField(String fieldName, Class<?> fieldType) {
            try {
                return taskAdapterLookup()
                        .findStaticVarHandle(TaskAdapter.class, fieldName, fieldType);
            } catch (NoSuchFieldException | IllegalAccessException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }

        private static MethodHandles.Lookup taskAdapterLookup() throws IllegalAccessException {
            return MethodHandles.privateLookupIn(TaskAdapter.class, MethodHandles.lookup());
        }
    }

    public static class AdaptedBeanTask {
        private Project project;
        private boolean executed;

        public void setProject(Project project) {
            this.project = project;
        }

        public void execute() {
            executed = true;
        }
    }
}
