/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_ant

import groovy.ant.AntBuilder
import groovy.ant.FileNameFinder
import org.apache.tools.ant.Location
import org.apache.tools.ant.Project
import org.apache.tools.ant.Target
import org.apache.tools.ant.types.FileSet
import org.apache.tools.ant.types.Path as AntPath
import org.codehaus.groovy.ant.AntProjectPropertiesDelegate
import org.codehaus.groovy.ant.FileScanner
import org.codehaus.groovy.ant.GenerateStubsTask
import org.codehaus.groovy.ant.Groovy
import org.codehaus.groovy.ant.Groovyc
import org.codehaus.groovy.ant.Groovydoc
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path as NioPath

import static org.assertj.core.api.Assertions.assertThat

public class Groovy_antTest {
    @TempDir
    NioPath temporaryDirectory

    @Test
    void antBuilderRunsStandardAntTasksAgainstConfiguredProject() {
        Project project = newProject()
        project.setNewProperty('sample.property', 'configured')
        AntBuilder ant = new AntBuilder(project)
        File sourceFile = temporaryDirectory.resolve('source.txt').toFile()
        File copiedFile = temporaryDirectory.resolve('copy/target.txt').toFile()

        ant.mkdir(dir: copiedFile.parentFile)
        ant.echo(file: sourceFile, message: 'created by AntBuilder', encoding: 'UTF-8')
        ant.copy(file: sourceFile, tofile: copiedFile)

        assertThat(ant.project).isSameAs(project)
        assertThat(ant.antProject.getProperty('sample.property')).isEqualTo('configured')
        assertThat(Files.readString(sourceFile.toPath(), StandardCharsets.UTF_8)).isEqualTo('created by AntBuilder')
        assertThat(Files.readString(copiedFile.toPath(), StandardCharsets.UTF_8)).isEqualTo('created by AntBuilder')
    }

    @Test
    void fileNameFinderHonorsIncludesExcludesAndMapArguments() {
        File groovySource = writeFile('src/main/groovy/example/App.groovy', 'class App {}')
        writeFile('src/test/groovy/example/AppSpec.groovy', 'class AppSpec {}')
        writeFile('src/main/java/example/App.java', 'class App {}')
        writeFile('README.md', '# sample')
        FileNameFinder finder = new FileNameFinder()

        List<String> productionGroovyNames = finder.getFileNames(
                temporaryDirectory.toString(),
                'src/**/*.groovy',
                'src/test/**'
        )
        List<String> sourceNames = finder.getFileNames([
                dir     : temporaryDirectory.toString(),
                includes: 'src/main/**/*.*',
                excludes: '**/*.java'
        ])

        assertThat(fileNames(productionGroovyNames)).containsExactly('App.groovy')
        assertThat(productionGroovyNames).contains(groovySource.absolutePath)
        assertThat(fileNames(sourceNames)).containsExactly('App.groovy')
    }

    @Test
    void fileScannerIteratesMatchingFilesAndCanBeCleared() {
        Project project = newProject()
        File groovySource = writeFile('scripts/build.groovy', 'println "build"')
        writeFile('scripts/notes.txt', 'ignore')
        FileSet fileSet = new FileSet()
        fileSet.setDir(temporaryDirectory.toFile())
        fileSet.setIncludes('**/*.groovy')
        FileScanner scanner = new FileScanner(project)

        scanner.addFileset(fileSet)
        List<File> scannedFiles = collectFiles(scanner.iterator())

        assertThat(scanner.hasFiles()).isTrue()
        assertThat(scannedFiles).containsExactly(groovySource)

        scanner.clear()

        assertThat(scanner.hasFiles()).isFalse()
        assertThat(collectFiles(scanner.iterator())).isEmpty()
    }

    @Test
    void antProjectPropertiesDelegateExposesAndMutatesAntProjectPropertiesAsAMap() {
        Project project = newProject()
        project.setNewProperty('existing', 'value')
        AntProjectPropertiesDelegate properties = new AntProjectPropertiesDelegate(project)

        Object previous = properties.put('created.by.delegate', 'created')

        assertThat(previous).isNull()
        assertThat(properties.get('existing')).isEqualTo('value')
        assertThat(properties.containsKey('created.by.delegate')).isTrue()
        assertThat(project.getProperty('created.by.delegate')).isEqualTo('created')
        assertThat(properties.keySet()).contains('existing', 'created.by.delegate')
        assertThat(properties.values()).contains('value', 'created')
    }

    @Test
    void groovyTaskExecutesInlineScriptWithAntProjectBinding() {
        Project project = newProject()
        Groovy task = new Groovy()
        Target target = new Target()
        target.setProject(project)
        target.setName('test-target')
        task.setProject(project)
        task.setOwningTarget(target)
        task.setTaskName('groovy')
        task.setTaskType('groovy')
        task.setLocation(new Location(temporaryDirectory.resolve('build.xml').toString()))
        task.setFork(false)
        task.setUseGroovyShell(true)
        task.addText("""
            project.setNewProperty('groovy.task.answer', String.valueOf(6 * 7))
        """)

        boolean executed = executeAllowingNativeUnsupportedDynamicClassLoading { task.execute() }

        if (executed) {
            assertThat(project.getProperty('groovy.task.answer')).isEqualTo('42')
        }
    }

    @Test
    void groovycCompilesSources() {
        Project project = newProject()
        File sourceDirectory = temporaryDirectory.resolve('compile-src').toFile()
        File destinationDirectory = temporaryDirectory.resolve('compile-dest').toFile()
        Files.createDirectories(destinationDirectory.toPath())
        writeFile('compile-src/example/Greeter.groovy', '''
            package example

            class Greeter {
                String greet(String name) {
                    "Hello, ${name}"
                }
            }
        ''')
        Groovyc compiler = new Groovyc()
        compiler.setProject(project)
        compiler.setTaskName('groovyc')
        compiler.setSrcdir(new AntPath(project, sourceDirectory.absolutePath))
        compiler.setDestdir(destinationDirectory)
        compiler.setFailonerror(true)
        compiler.setFork(false)
        compiler.setParameters(true)

        compiler.execute()

        assertThat(compiler.taskSuccess).isTrue()
        assertThat(destinationDirectory.toPath().resolve('example/Greeter.class')).exists()

    }

    @Test
    void generateStubsTaskCreatesJavaStubsForGroovySources() {
        Project project = newProject()
        File sourceDirectory = temporaryDirectory.resolve('stubs-src').toFile()
        File stubDirectory = temporaryDirectory.resolve('stubs-out').toFile()
        Files.createDirectories(stubDirectory.toPath())
        writeFile('stubs-src/example/StubbedService.groovy', '''
            package example

            class StubbedService {
                int answer() {
                    42
                }
            }
        ''')
        GenerateStubsTask stubs = new GenerateStubsTask()
        stubs.setProject(project)
        stubs.setTaskName('generatestubs')
        stubs.setSrcdir(new AntPath(project, sourceDirectory.absolutePath))
        stubs.setDestdir(stubDirectory)
        stubs.setFailonerror(true)

        stubs.execute()

        NioPath stubFile = stubDirectory.toPath().resolve('example/StubbedService.java')
        assertThat(stubFile).exists()
        String stubSource = Files.readString(stubFile, StandardCharsets.UTF_8)
        assertThat(stubSource).contains('public class StubbedService')
        assertThat(stubSource).containsPattern(/public\s+int\s+answer\(\)/)
    }

    @Test
    void groovydocGeneratesDocumentationForGroovySources() {
        Project project = newProject()
        File sourceDirectory = temporaryDirectory.resolve('docs-src').toFile()
        File documentationDirectory = temporaryDirectory.resolve('docs-out').toFile()
        writeFile('docs-src/example/DocumentedGreeter.groovy', '''
            package example

            /** Greets users for documentation generation. */
            class DocumentedGreeter {
                /** Returns a friendly greeting. */
                String greet(String name) {
                    "Hello, ${name}"
                }
            }
        ''')
        Groovydoc groovydoc = new Groovydoc()
        groovydoc.setProject(project)
        groovydoc.setTaskName('groovydoc')
        groovydoc.setSourcepath(new AntPath(project, sourceDirectory.absolutePath))
        groovydoc.setDestdir(documentationDirectory)
        groovydoc.setPackagenames('example')
        groovydoc.setWindowtitle('Groovy Ant Test Docs')
        groovydoc.setDoctitle('Groovy Ant Test Docs')
        groovydoc.setNoTimestamp(true)
        groovydoc.setNoVersionStamp(true)

        groovydoc.execute()

        assertThat(documentationDirectory.toPath().resolve('index.html')).exists()
        assertThat(documentationDirectory.toPath().resolve('example/DocumentedGreeter.html')).exists()
    }

    private static Project newProject() {
        Project project = new Project()
        project.init()
        project
    }

    private File writeFile(String relativePath, String content) {
        NioPath file = temporaryDirectory.resolve(relativePath)
        Files.createDirectories(file.parent)
        Files.writeString(file, content.stripIndent().trim(), StandardCharsets.UTF_8)
        file.toFile()
    }

    private static List<String> fileNames(List<String> paths) {
        paths.collect { String path -> new File(path).name }.sort()
    }

    private static List<File> collectFiles(Iterator<File> iterator) {
        List<File> files = []
        while (iterator.hasNext()) {
            files.add(iterator.next())
        }
        files
    }

    private static boolean executeAllowingNativeUnsupportedDynamicClassLoading(Closure<?> action) {
        try {
            action.call()
            return true
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error
            }
            return false
        }
    }
}
