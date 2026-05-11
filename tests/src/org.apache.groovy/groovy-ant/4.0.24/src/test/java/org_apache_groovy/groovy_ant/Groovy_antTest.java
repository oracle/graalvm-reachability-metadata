/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_ant;

import groovy.ant.AntBuilder;
import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.BuildListener;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Echo;
import org.apache.tools.ant.types.FileSet;
import org.codehaus.groovy.ant.AntProjectPropertiesDelegate;
import org.codehaus.groovy.ant.FileScanner;
import org.codehaus.groovy.ant.GenerateStubsTask;
import org.codehaus.groovy.ant.Groovyc;
import org.codehaus.groovy.ant.LoggingHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public final class Groovy_antTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void antBuilderRunsStandardAntTasksAgainstConfiguredProject() throws Exception {
        Project project = newProject();
        project.setNewProperty("sample.property", "configured");
        AntBuilder ant = new AntBuilder(project);
        File sourceFile = temporaryDirectory.resolve("source.txt").toFile();
        File copiedFile = temporaryDirectory.resolve("copy/target.txt").toFile();

        ant.invokeMethod("mkdir", Map.of("dir", copiedFile.getParentFile()));
        ant.invokeMethod("echo", Map.of("file", sourceFile, "message", "created by AntBuilder", "encoding", "UTF-8"));
        ant.invokeMethod("copy", Map.of("file", sourceFile, "tofile", copiedFile));

        assertThat(ant.getProject()).isSameAs(project);
        assertThat(ant.getAntProject().getProperty("sample.property")).isEqualTo("configured");
        assertThat(Files.readString(sourceFile.toPath(), StandardCharsets.UTF_8)).isEqualTo("created by AntBuilder");
        assertThat(Files.readString(copiedFile.toPath(), StandardCharsets.UTF_8)).isEqualTo("created by AntBuilder");
    }

    @Test
    void fileScannerIteratesMatchingFilesAndCanBeCleared() throws Exception {
        Project project = newProject();
        File groovySource = writeFile("scripts/build.groovy", "println \"build\"");
        writeFile("scripts/notes.txt", "ignore");
        FileSet fileSet = new FileSet();
        fileSet.setDir(temporaryDirectory.toFile());
        fileSet.setIncludes("**/*.groovy");
        FileScanner scanner = new FileScanner(project);

        scanner.addFileset(fileSet);
        List<File> scannedFiles = collectFiles(scanner.iterator());

        assertThat(scanner.hasFiles()).isTrue();
        assertThat(scannedFiles).containsExactly(groovySource);

        scanner.clear();

        assertThat(scanner.hasFiles()).isFalse();
        assertThat(collectFiles(scanner.iterator())).isEmpty();
    }

    @Test
    void antProjectPropertiesDelegateExposesAndMutatesAntProjectPropertiesAsAMap() {
        Project project = newProject();
        project.setNewProperty("existing", "value");
        AntProjectPropertiesDelegate properties = new AntProjectPropertiesDelegate(project);

        Object previous = properties.put("created.by.delegate", "created");

        assertThat(previous).isNull();
        assertThat(properties.get("existing")).isEqualTo("value");
        assertThat(properties.containsKey("created.by.delegate")).isTrue();
        assertThat(project.getProperty("created.by.delegate")).isEqualTo("created");
        assertThat(properties.keySet()).contains("existing", "created.by.delegate");
        assertThat(properties.values()).contains("value", "created");
    }

    @Test
    void groovycCompilesSources() throws Exception {
        Project project = newProject();
        File sourceDirectory = temporaryDirectory.resolve("compile-src").toFile();
        File destinationDirectory = temporaryDirectory.resolve("compile-dest").toFile();
        Files.createDirectories(destinationDirectory.toPath());
        writeFile("compile-src/example/Greeter.groovy", """
                package example

                class Greeter {
                    String greet(String name) {
                        "Hello, ${name}"
                    }
                }
                """);
        Groovyc compiler = new Groovyc();
        compiler.setProject(project);
        compiler.setTaskName("groovyc");
        compiler.setSrcdir(new org.apache.tools.ant.types.Path(project, sourceDirectory.getAbsolutePath()));
        compiler.setDestdir(destinationDirectory);
        compiler.setFailonerror(true);
        compiler.setFork(false);
        compiler.setParameters(true);

        compiler.execute();

        assertThat(compiler.getTaskSuccess()).isTrue();
        assertThat(destinationDirectory.toPath().resolve("example/Greeter.class")).exists();
    }

    @Test
    void generateStubsTaskCreatesJavaStubsForGroovySources() throws Exception {
        Project project = newProject();
        File sourceDirectory = temporaryDirectory.resolve("stubs-src").toFile();
        File stubDirectory = temporaryDirectory.resolve("stubs-out").toFile();
        Files.createDirectories(stubDirectory.toPath());
        writeFile("stubs-src/example/StubbedService.groovy", """
                package example

                class StubbedService {
                    int answer() {
                        42
                    }
                }
                """);
        GenerateStubsTask stubs = new GenerateStubsTask();
        stubs.setProject(project);
        stubs.setTaskName("generatestubs");
        stubs.setSrcdir(new org.apache.tools.ant.types.Path(project, sourceDirectory.getAbsolutePath()));
        stubs.setDestdir(stubDirectory);
        stubs.setFailonerror(true);

        stubs.execute();

        Path stubFile = stubDirectory.toPath().resolve("example/StubbedService.java");
        assertThat(stubFile).exists();
        String stubSource = Files.readString(stubFile, StandardCharsets.UTF_8);
        assertThat(stubSource).contains("public class StubbedService");
        assertThat(stubSource).containsPattern("public\\s+int\\s+answer\\(\\)");
    }

    @Test
    void loggingHelperEmitsAntMessagesAtRequestedPriorities() {
        Project project = newProject();
        CapturingBuildListener listener = new CapturingBuildListener();
        project.addBuildListener(listener);
        Echo task = new Echo();
        task.setProject(project);
        task.setTaskName("sample-task");
        LoggingHelper logging = new LoggingHelper(task);
        IllegalStateException cause = new IllegalStateException("broken state");

        logging.error("error message");
        logging.error("error with cause", cause);
        logging.warn("warning message");
        logging.info("info message");
        logging.verbose("verbose message");
        logging.debug("debug message");

        assertThat(listener.prioritiesByMessage).containsEntry("error message", Project.MSG_ERR);
        assertThat(listener.prioritiesByMessage).containsEntry("error with cause", Project.MSG_ERR);
        assertThat(listener.exceptionsByMessage).containsEntry("error with cause", cause);
        assertThat(listener.prioritiesByMessage).containsEntry("warning message", Project.MSG_WARN);
        assertThat(listener.prioritiesByMessage).containsEntry("info message", Project.MSG_INFO);
        assertThat(listener.prioritiesByMessage).containsEntry("verbose message", Project.MSG_VERBOSE);
        assertThat(listener.prioritiesByMessage).containsEntry("debug message", Project.MSG_DEBUG);
    }

    private static Project newProject() {
        Project project = new Project();
        project.init();
        return project;
    }

    private File writeFile(String relativePath, String content) throws Exception {
        Path file = temporaryDirectory.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content.stripIndent().trim(), StandardCharsets.UTF_8);
        return file.toFile();
    }

    private static List<File> collectFiles(Iterator<File> iterator) {
        List<File> files = new ArrayList<>();
        while (iterator.hasNext()) {
            files.add(iterator.next());
        }
        return files;
    }

    private static final class CapturingBuildListener implements BuildListener {
        private final Map<String, Integer> prioritiesByMessage = new HashMap<>();
        private final Map<String, Throwable> exceptionsByMessage = new HashMap<>();

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
        public void taskStarted(BuildEvent event) {
        }

        @Override
        public void taskFinished(BuildEvent event) {
        }

        @Override
        public void messageLogged(BuildEvent event) {
            prioritiesByMessage.put(event.getMessage(), event.getPriority());
            if (event.getException() != null) {
                exceptionsByMessage.put(event.getMessage(), event.getException());
            }
        }
    }
}
