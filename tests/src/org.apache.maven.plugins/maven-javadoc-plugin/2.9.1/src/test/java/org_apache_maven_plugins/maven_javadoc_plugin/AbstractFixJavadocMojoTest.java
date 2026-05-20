/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_plugins.maven_javadoc_plugin;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.javadoc.AbstractFixJavadocMojo;
import org.apache.maven.plugin.javadoc.TestFixJavadocMojo;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractFixJavadocMojoTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void fixesInheritedMethodJavadocUsingProjectClassLoader() throws Exception {
        Path sourceRoot = temporaryDirectory.resolve("src/main/java");
        Path arrayListSource = sourceRoot.resolve("java/util/ArrayList.java");
        Files.createDirectories(arrayListSource.getParent());
        Files.writeString(arrayListSource, """
            package java.util;

            public class ArrayList {
                public Object get(int index) {
                    return null;
                }
            }
            """, StandardCharsets.UTF_8);

        TestFixJavadocMojo mojo = new TestFixJavadocMojo();
        setField(mojo, "project", createProject(sourceRoot));
        setField(mojo, "defaultSince", "1.0");
        setField(mojo, "encoding", StandardCharsets.UTF_8.name());
        setField(mojo, "fixTags", "all");
        setField(mojo, "force", true);
        setField(mojo, "includes", "**/*.java");
        setField(mojo, "level", "public");
        setField(mojo, "fixClassComment", false);
        setField(mojo, "fixFieldComment", false);
        setField(mojo, "fixMethodComment", true);
        setField(mojo, "projectClassLoader", AbstractFixJavadocMojoTest.class.getClassLoader());

        mojo.execute();

        assertThat(arrayListSource)
            .content(StandardCharsets.UTF_8)
            .contains("/** {@inheritDoc} */")
            .contains("public Object get(int index)");
    }

    @Test
    void buildsFullClirrGoalFromBundledPomPropertiesWhenAvailable() throws Exception {
        String goal = invokeStatic("getFullClirrGoal");

        assertThat(goal)
            .startsWith("org.codehaus.mojo:clirr-maven-plugin:")
            .endsWith(":check");
    }

    private static MavenProject createProject(Path sourceRoot) {
        Build build = new Build();
        build.setSourceDirectory(sourceRoot.toString());
        build.setDirectory(sourceRoot.getParent().getParent().resolve("target").toString());

        Model model = new Model();
        model.setGroupId("org.example");
        model.setArtifactId("javadoc-fix-test-project");
        model.setVersion("1.0-SNAPSHOT");
        model.setPackaging("jar");
        model.setBuild(build);

        MavenProject project = new MavenProject(model);
        project.addTestCompileSourceRoot(sourceRoot.toString());
        return project;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = AbstractFixJavadocMojo.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T invokeStatic(String name) throws Exception {
        Method method = AbstractFixJavadocMojo.class.getDeclaredMethod(name);
        method.setAccessible(true);
        try {
            return (T) method.invoke(null);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }
}
