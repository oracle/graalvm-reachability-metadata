/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness;

import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.assertj.core.api.Assertions.assertThat;

/// Exercises lazy selector wiring through the real dedicated-layer DSL. §FS-tests.3.4
class DedicatedLayerModuleSelectionTests {
    @TempDir
    Path tempDir;

    @Test
    void selectsModulesFromTheConfiguredBaseClasspathWithoutChangingSharedModules() throws IOException {
        Path project = LayerBuildOptionsTestFixture.create(tempDir, "", true);
        Path buildFile = project.resolve("build.gradle");
        Files.writeString(buildFile, """
                import java.util.jar.JarEntry
                import java.util.jar.JarOutputStream
                import org.graalvm.internal.tck.utils.BaseLayerUtils
                """ + Files.readString(buildFile));
        Files.writeString(buildFile, """

                // The overlap JAR does not exist during project evaluation.
                File overlapJar = file('build/generated/dependency.jar')
                graalvmNative.binaries.libDedicated.classpath.setFrom(files(overlapJar))
                tasks.register('generateOverlapJar') {
                    doLast {
                        overlapJar.parentFile.mkdirs()
                        new JarOutputStream(new FileOutputStream(overlapJar)).withCloseable { jar ->
                            ['javax/xml/stream/XMLStreamWriter.class', 'javax/transaction/xa/Xid.class',
                             'org/w3c/dom/css/CSSRule.class', 'java/util/concurrent/ExecutorService.class'].each { name ->
                                jar.putNextEntry(new JarEntry(name))
                                jar.write(0)
                                jar.closeEntry()
                            }
                        }
                    }
                }
                tasks.register('inspectModuleSelection') {
                    dependsOn('generateOverlapJar')
                    doLast {
                        def layer = graalvmNative.binaries.libDedicated.layers.find { it.hasProperty('modules') }
                        println 'selectedModules=' + layer.modules.get().join(',')
                        println 'baseClasspath=' + graalvmNative.binaries.libDedicated.classpath.singleFile.name
                        println 'sharedModules=' + BaseLayerUtils.layerModules().join(',')
                    }
                }
                """, StandardOpenOption.APPEND);

        String output = GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
                .withArguments("inspectModuleSelection", "--offline", "--quiet").build().getOutput();
        String modules = output.lines().filter(line -> line.startsWith("selectedModules="))
                .findFirst().orElseThrow().substring("selectedModules=".length());
        assertThat(modules.split(",")).contains("java.base", "java.sql", "java.desktop")
                .doesNotContain("java.xml", "java.transaction.xa", "jdk.xml.dom");
        assertThat(output).contains("baseClasspath=dependency.jar");
        String shared = output.lines().filter(line -> line.startsWith("sharedModules="))
                .findFirst().orElseThrow().substring("sharedModules=".length());
        assertThat(shared.split(",")).contains("java.xml", "java.transaction.xa", "jdk.xml.dom", "java.base");
    }
}
