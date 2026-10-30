/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.nativeimage

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.Project
import org.gradle.api.GradleException
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask
import org.graalvm.internal.tck.utils.LayeredBuildArgsUtils

/// Owns base selectors and staged configuration, keeping test classes out. §FS-tests.3.2
class DedicatedLayerMetadata {
    final Project project
    final SourceSetContainer sourceSets
    final File coordinateMetadataDirectory
    final String coordinateMetadataArgument
    final TaskProvider<BuildNativeImageTask> nativeTestCompileTask
    final Provider<Directory> dedicatedLayerJUnitTestListDirectory
    final Provider<Directory> dedicatedLayerConfigurationDirectory
    final Provider<Directory> dedicatedLayerReachabilityMetadataDirectory

    DedicatedLayerMetadata(Project project, File coordinateMetadata, boolean dedicatedLayer) {
        this.project = project
        sourceSets = project.extensions.getByType(SourceSetContainer)
        coordinateMetadataDirectory = coordinateMetadata.absoluteFile
        coordinateMetadataArgument = "-H:ConfigurationFileDirectories=${coordinateMetadataDirectory.absolutePath}"
        nativeTestCompileTask = project.tasks.named("nativeTestCompile", BuildNativeImageTask)
        dedicatedLayerJUnitTestListDirectory = project.layout.buildDirectory.dir("native/generated/dedicatedLayerJUnitTestList")
        dedicatedLayerConfigurationDirectory = project.layout.buildDirectory.dir("native/generated/dedicatedLayerConfiguration")
        dedicatedLayerReachabilityMetadataDirectory = project.layout.buildDirectory.dir("native/generated/dedicatedLayerReachabilityMetadata")
        if (dedicatedLayer) {
            project.tasks.named("processTestResources").configure { task ->
                task.exclude("META-INF/native-image/**/reachability-metadata.json")
            }
            project.tasks.named("generateTestResourcesConfigFile").configure { task ->
                task.dependsOn(project.tasks.named("processTestResources"))
            }
        }

        project.tasks.register("generateDedicatedLayerJUnitTestList") { task ->
            task.setDescription("Generates engine-root JUnit selectors for the dedicated layer")
            task.setGroup("build")
            task.outputs.dir(dedicatedLayerJUnitTestListDirectory)
            task.doLast {
                File outputDirectory = dedicatedLayerJUnitTestListDirectory.get().asFile
                project.delete(outputDirectory)
                if (!outputDirectory.mkdirs()) {
                    throw new GradleException("Failed to create base-layer JUnit test-list directory: " +
                            outputDirectory.absolutePath)
                }
                File selectorsFile = new File(outputDirectory, "junit-platform-unique-ids-base")
                List<String> engineRootSelectors = [
                        "[engine:junit-jupiter]",
                        "[engine:junit-vintage]"
                ]
                selectorsFile.text = engineRootSelectors.join(System.lineSeparator()) + System.lineSeparator()
            }
        }

        project.tasks.register("prepareDedicatedLayerReachabilityMetadata") { task ->
            task.setDescription("Prepares test-scoped reachability metadata for dedicated-layer builds")
            task.setGroup("build")
            task.inputs.files(project.providers.provider { resolveTestReachabilityMetadataFiles() })
                    .withPropertyName("testReachabilityMetadata")
                    .withPathSensitivity(PathSensitivity.RELATIVE)
            task.outputs.dir(dedicatedLayerReachabilityMetadataDirectory)
            task.doLast {
                File outputDirectory = dedicatedLayerReachabilityMetadataDirectory.get().asFile
                project.delete(outputDirectory)
                if (!outputDirectory.mkdirs()) {
                    throw new GradleException("Failed to create dedicated-layer reachability metadata directory: " +
                            outputDirectory.absolutePath)
                }
                resolveTestReachabilityMetadataFiles().eachWithIndex { File metadataFile, int index ->
                    File metadataDirectory = new File(outputDirectory, "entry-${index}")
                    if (!metadataDirectory.mkdirs()) {
                        throw new GradleException("Failed to create dedicated-layer metadata entry: " +
                                metadataDirectory.absolutePath)
                    }
                    Object metadata = new JsonSlurper().parse(metadataFile)
                    String json = JsonOutput.prettyPrint(JsonOutput.toJson(withoutReachabilityConditions(metadata)))
                    new File(metadataDirectory, "reachability-metadata.json").text =
                            json + System.lineSeparator()
                }
            }
        }

        project.tasks.register("prepareDedicatedLayerConfiguration") { task ->
            task.setDescription("Prepares Native Image configuration for the dedicated layer")
            task.setGroup("build")
            task.dependsOn(project.tasks.named("generateTestResourcesConfigFile"))
            task.dependsOn(project.tasks.named("prepareDedicatedLayerReachabilityMetadata"))
            task.inputs.dir(dedicatedLayerReachabilityMetadataDirectory)
                    .withPropertyName("dedicatedLayerReachabilityMetadata")
                    .withPathSensitivity(PathSensitivity.RELATIVE)
            task.outputs.dir(dedicatedLayerConfigurationDirectory)
            task.outputs.upToDateWhen { false }
            task.doLast {
                File outputDirectory = dedicatedLayerConfigurationDirectory.get().asFile
                project.delete(outputDirectory)
                if (!outputDirectory.mkdirs()) {
                    throw new GradleException("Failed to create base-layer configuration directory: " +
                            outputDirectory.absolutePath)
                }
                resolveNativeTestConfigurationDirectories().eachWithIndex { File directory, int index ->
                    project.copy {
                        from(directory)
                        into(new File(outputDirectory, "entry-${index}"))
                    }
                }
            }
        }

    }

    List<String> getApplicationBuildArgs() {
        List<String> arguments = [coordinateMetadataArgument]
        List<File> directories = resolveDedicatedLayerReachabilityMetadataDirectories()
        if (!directories.isEmpty()) {
            arguments.add("-H:ConfigurationFileDirectories=" + directories.collect { File directory -> directory.absolutePath }.join(","))
        }
        return arguments
    }

    List<File> resolveTestReachabilityMetadataFiles() {
        return sourceSets.test.resources.srcDirs.collectMany { File resourceDirectory ->
            project.fileTree(resourceDirectory) {
                include("META-INF/native-image/**/reachability-metadata.json")
            }.files
        }.unique().sort { File file -> file.absolutePath }
    }

    Object withoutReachabilityConditions(Object value) {
        if (value instanceof Map) {
            Map<String, Object> result = new LinkedHashMap<>()
            value.each { Object key, Object nestedValue ->
                if (key != "condition") {
                    result.put(key.toString(), withoutReachabilityConditions(nestedValue))
                }
            }
            return result
        }
        if (value instanceof List) {
            return value.collect { Object nestedValue -> withoutReachabilityConditions(nestedValue) }
        }
        return value
    }

    List<File> resolveDedicatedLayerReachabilityMetadataDirectories() {
        File outputDirectory = dedicatedLayerReachabilityMetadataDirectory.get().asFile
        return resolveTestReachabilityMetadataFiles().indices.collect { int index ->
            new File(outputDirectory, "entry-${index}")
        }
    }

    List<File> resolveNativeTestConfigurationDirectories() {
        Set<File> directories = new LinkedHashSet<>(nativeTestCompileTask.get()
                .options.get().configurationFileDirectories.files)
        directories.add(coordinateMetadataDirectory)
        directories.addAll(resolveDedicatedLayerReachabilityMetadataDirectories())
        return directories.findAll { File directory -> directory.isDirectory() }
                .toList()
                .sort { File directory -> directory.absolutePath }
    }

    List<File> resolveDedicatedLayerConfigurationDirectories() {
        File outputDirectory = dedicatedLayerConfigurationDirectory.get().asFile
        File[] directories = outputDirectory.listFiles({ File file -> file.isDirectory() } as FileFilter)
        return directories == null ? [] : directories.toList().sort { File file -> file.absolutePath }
    }

    List<String> resolveDedicatedLayerNativeImageArgs() {
        List<String> arguments = []
        arguments.add("-H:ConfigurationFileDirectories=" +
                resolveDedicatedLayerConfigurationDirectories()
                        .collect { File directory -> directory.absolutePath }
                        .join(","))
        arguments.addAll(LayeredBuildArgsUtils.junitBuildArguments())
        arguments.add("-Djunit.platform.listeners.uid.tracking.output.dir=" +
                dedicatedLayerJUnitTestListDirectory.get().asFile.absolutePath)
        return arguments
    }

}
