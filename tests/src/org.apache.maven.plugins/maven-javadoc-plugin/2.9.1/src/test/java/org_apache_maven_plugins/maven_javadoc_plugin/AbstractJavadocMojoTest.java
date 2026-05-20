/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_plugins.maven_javadoc_plugin;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.javadoc.AbstractJavadocMojo;
import org.apache.maven.plugin.javadoc.JavadocReport;
import org.apache.maven.plugin.javadoc.options.OfflineLink;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractJavadocMojoTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void copiesDefaultMavenStylesheetFromPluginResources() throws Exception {
        JavadocReport mojo = new JavadocReport();
        setField(mojo, "stylesheet", "maven");

        Path outputDirectory = temporaryDirectory.resolve("javadoc-output");
        Files.createDirectories(outputDirectory);

        invoke(mojo, "copyDefaultStylesheet", new Class<?>[]{ File.class }, outputDirectory.toFile());

        assertThat(outputDirectory.resolve("stylesheet.css"))
            .exists()
            .content(StandardCharsets.UTF_8)
            .contains("body");
    }

    @Test
    void locatesResourcesFromExplicitClasspathEntries() throws Exception {
        Path resourceRoot = temporaryDirectory.resolve("resources");
        Path nestedResource = resourceRoot.resolve("custom/help.html");
        Files.createDirectories(nestedResource.getParent());
        Files.writeString(nestedResource, "<html>custom help</html>", StandardCharsets.UTF_8);

        JavadocReport mojo = new JavadocReport();

        URL resource = invoke(
            mojo,
            "getResource",
            new Class<?>[]{ List.class, String.class },
            Collections.singletonList(resourceRoot.toString()),
            "custom/help.html");

        assertThat(resource).isNotNull();
        assertThat(resource.toString()).endsWith("custom/help.html");
    }

    @Test
    void readsPluginPomPropertiesWhenBuildingFullJavadocGoal() throws Exception {
        JavadocReport mojo = new JavadocReport();

        String goal = invoke(mojo, "getFullJavadocGoal", new Class<?>[0]);

        assertThat(goal)
            .startsWith("org.apache.maven.plugins:maven-javadoc-plugin:")
            .endsWith(":javadoc");
    }

    @Test
    void patchesGeneratedFramePagesUsingBundledFixResource() throws Exception {
        Path outputDirectory = temporaryDirectory.resolve("generated-javadocs");
        Files.createDirectories(outputDirectory);
        Path indexFile = outputDirectory.resolve("index.html");
        Files.writeString(indexFile, """
            <html>
            <script type="text/javascript">
            function loadFrames() {
                targetPage = "" + window.location.search;
            }
            </script>
            </html>
            """, StandardCharsets.UTF_8);

        JavadocReport mojo = new JavadocReport();

        Integer patchedFiles = invoke(
            mojo,
            "fixFrameInjectionBug",
            new Class<?>[]{ File.class, String.class },
            outputDirectory.toFile(),
            StandardCharsets.UTF_8.name());

        assertThat(patchedFiles).isEqualTo(1);
        assertThat(indexFile).content(StandardCharsets.UTF_8).contains("function validURL(url) {");
    }

    @Test
    void createsDefaultJavaApiOfflineLinkFromBundledPackageList() throws Exception {
        JavadocReport mojo = new JavadocReport();
        Path optionsDirectory = temporaryDirectory.resolve("javadoc-options");
        MavenProject project = createProjectWithCompilerSource("1.7");

        setField(mojo, "project", project);
        setField(mojo, "detectJavaApiLink", true);
        setField(mojo, "javaApiLinks", AbstractJavadocMojo.DEFAULT_JAVA_API_LINKS);
        setField(mojo, "javadocOptionsDir", optionsDirectory.toFile());
        setField(mojo, "fJavadocVersion", 1.7f);

        OfflineLink link = invoke(mojo, "getDefaultJavadocApiLink", new Class<?>[0]);

        assertThat(link).isNotNull();
        assertThat(link.getUrl()).isEqualTo("http://docs.oracle.com/javase/7/docs/api/");
        assertThat(optionsDirectory.resolve("package-list"))
            .exists()
            .content(StandardCharsets.US_ASCII)
            .contains("java.lang");
    }

    private static MavenProject createProjectWithCompilerSource(String source) {
        Xpp3Dom configuration = new Xpp3Dom("configuration");
        Xpp3Dom sourceConfiguration = new Xpp3Dom("source");
        sourceConfiguration.setValue(source);
        configuration.addChild(sourceConfiguration);

        Plugin compilerPlugin = new Plugin();
        compilerPlugin.setGroupId("org.apache.maven.plugins");
        compilerPlugin.setArtifactId("maven-compiler-plugin");
        compilerPlugin.setConfiguration(configuration);

        Build build = new Build();
        build.addPlugin(compilerPlugin);

        Model model = new Model();
        model.setGroupId("org.example");
        model.setArtifactId("javadoc-test-project");
        model.setVersion("1.0-SNAPSHOT");
        model.setBuild(build);

        return new MavenProject(model);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = AbstractJavadocMojo.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T invoke(Object target, String name, Class<?>[] parameterTypes, Object... arguments)
        throws Exception {
        Method method = AbstractJavadocMojo.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        try {
            return (T) method.invoke(target, arguments);
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
