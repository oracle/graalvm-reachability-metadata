/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Ant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class AntTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void compilerGeneratedClassLookupResolvesProjectType() throws Throwable {
        Class<?> resolvedClass = ExposedAnt.lookupCompilerGeneratedClass(Project.class.getName());

        assertThat(resolvedClass).isSameAs(Project.class);
    }

    @Test
    void copiesConfiguredReferenceByCloningAndAssigningChildProject() throws IOException {
        Path buildFile = temporaryDirectory.resolve("build.xml");
        Files.writeString(buildFile, """
                <project name="child" default="noop">
                    <target name="noop"/>
                </project>
                """, StandardCharsets.UTF_8);

        Project parentProject = new Project();
        parentProject.init();
        parentProject.setBaseDir(temporaryDirectory.toFile());

        CloneableReference originalReference = new CloneableReference();
        parentProject.addReference("original", originalReference);

        Ant antTask = new Ant();
        antTask.setProject(parentProject);
        antTask.setTaskName("ant");
        antTask.setDir(temporaryDirectory.toFile());
        antTask.setAntfile(buildFile.getFileName().toString());
        antTask.setTarget("noop");
        antTask.addReference(reference("original", "copied"));

        ExposedAnt.clearCachedProjectClass();
        antTask.execute();

        assertThat(originalReference.cloneCount).isEqualTo(1);
        assertThat(originalReference.lastClone).isNotNull();
        assertThat(originalReference.lastClone).isNotSameAs(originalReference);
        assertThat(originalReference.lastClone.assignedProject).isNotNull();
        assertThat(originalReference.assignedProject).isNull();
    }

    private static Ant.Reference reference(String refid, String toRefid) {
        Ant.Reference reference = new Ant.Reference();
        reference.setRefId(refid);
        reference.setToRefid(toRefid);
        return reference;
    }

    private static final class ExposedAnt {
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
                return MethodHandles.privateLookupIn(Ant.class, MethodHandles.lookup())
                        .findStatic(
                                Ant.class,
                                "class$",
                                MethodType.methodType(Class.class, String.class));
            } catch (NoSuchMethodException | IllegalAccessException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }

        private static VarHandle staticField(String fieldName, Class<?> fieldType) {
            try {
                return MethodHandles.privateLookupIn(Ant.class, MethodHandles.lookup())
                        .findStaticVarHandle(Ant.class, fieldName, fieldType);
            } catch (NoSuchFieldException | IllegalAccessException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }
    }

    public static class CloneableReference implements Cloneable {
        private int cloneCount;
        private CloneableReference lastClone;
        private Project assignedProject;

        @Override
        public Object clone() {
            CloneableReference copy = new CloneableReference();
            cloneCount++;
            lastClone = copy;
            return copy;
        }

        public void setProject(Project project) {
            assignedProject = project;
        }
    }
}
