/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/// Common convention-plugin fixture for layered binary configuration tests.
final class LayerBuildOptionsTestFixture {
    private LayerBuildOptionsTestFixture() {
    }

    static Path create(Path tempDir, String binaries, boolean dedicated) throws IOException {
        Path project = Files.createDirectories(tempDir.resolve("tests/src/com.example/demo/1.0"));
        Files.createDirectories(tempDir.resolve("tests/tck-build-logic"));
        Path metadata = Files.createDirectories(tempDir.resolve("metadata/com.example/demo/1.0"));
        Files.writeString(tempDir.resolve("LICENSE"), "fixture");
        Files.writeString(tempDir.resolve("ci.json"), """
                {"buildArgs":["--verbose"],"nativeImageModes":{"current-defaults":[]}}
                """);
        Files.writeString(project.resolve("settings.gradle"), """
                rootProject.name = 'layer-options-fixture'
                dependencyResolutionManagement {
                    versionCatalogs {
                        libs {
                            version('scala2', '2.13.17')
                            version('scala3', '3.7.4')
                            library('test-junit-bom', 'org.junit', 'junit-bom').version('5.9.2')
                            library('test-junit-jupiter-core', 'org.junit.jupiter', 'junit-jupiter').withoutVersion()
                            library('test-junit-vintage', 'org.junit.vintage', 'junit-vintage-engine').withoutVersion()
                        }
                    }
                }
                """);
        Files.writeString(project.resolve("gradle.properties"), """
                library.coordinates=com.example:demo:1.0
                library.version=1.0
                metadata.dir=%s
                tck.dedicatedLayer=%s
                tck.baseLayerFile=%s
                """.formatted(metadata, dedicated, tempDir.resolve("common/base-layer.nil")));
        Files.writeString(project.resolve("build.gradle"), """
                import groovy.json.JsonOutput
                plugins { id 'org.graalvm.internal.tck' }
                graalvmNative {
                    binaries {
                %s
                    }
                }
                tasks.register('inspectLayerOptions') {
                    doLast {
                        // Simulate an output created after project evaluation.
                        file('build/native/generated/dedicatedLayerConfiguration/late-config').mkdirs()
                        println 'layerOptions=' + JsonOutput.toJson([
                            base: graalvmNative.binaries.libDedicated.buildArgs.get(),
                            test: graalvmNative.binaries.test.buildArgs.get()
                        ])
                    }
                }
                """.formatted(binaries));
        return project;
    }
}
