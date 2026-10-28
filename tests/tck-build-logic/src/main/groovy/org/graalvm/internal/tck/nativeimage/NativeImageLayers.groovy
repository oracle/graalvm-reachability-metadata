/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.nativeimage

import groovy.json.JsonSlurper
import org.gradle.api.Project
import org.gradle.api.GradleException
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitivity
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.process.ExecOperations
import org.graalvm.buildtools.gradle.dsl.GraalVMExtension
import org.graalvm.buildtools.gradle.dsl.NativeImageOptions
import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask
import org.graalvm.buildtools.gradle.tasks.CreateLayerOptions
import org.graalvm.internal.tck.harness.TckExtension
import org.graalvm.internal.tck.utils.BaseLayerUtils
import org.graalvm.internal.tck.utils.LayeredBuildArgsUtils
import org.graalvm.internal.tck.utils.NativeImageConfigUtils
import org.graalvm.internal.tck.utils.SharedLayerBuildUtils

/// Owns layer selection, construction and policy validation. §FS-tests.3.2, §FS-tests.3.3
/// Module selection follows §FS-tests.3.4; construction is described by §AR-native-test-layers.
class NativeImageLayers {
    final Project project
    final String libraryGAV
    final Closure<String> nativeImageTool
    final GraalVMExtension nativeExtension
    final File metadataDirectory
    File ciJsonFile
    String selectedNativeImageMode
    List<String> nativeImageArgs
    private List<String> baseLayerNativeImageArgs
    private String baseLayerFilePath
    boolean layeredNativeImage
    boolean dedicatedLayer
    private File baseLayerFile
    private File customSharedLayerFile
    private boolean customSharedLayer
    private DedicatedLayerMetadata metadata

    NativeImageLayers(Project project, String libraryGAV, String libraryVersion,
                      File metadataDirectory, Closure<String> nativeImageTool) {
        this.project = project
        this.libraryGAV = libraryGAV
        this.metadataDirectory = metadataDirectory
        this.nativeImageTool = nativeImageTool
        nativeExtension = project.extensions.getByType(GraalVMExtension)
        // Determine native-image build arguments from ci.json.
        ciJsonFile = project.extensions.getByType(TckExtension).repoRoot.file("ci.json").get().asFile
        nativeImageArgs = []
        baseLayerNativeImageArgs = []
        selectedNativeImageMode = NativeImageConfigUtils.resolveSelectedMode(
                System.getenv("GVM_TCK_NATIVE_IMAGE_MODE"),
                project.providers.gradleProperty("tck.nativeImageMode").getOrElse(null)
        )
        if (ciJsonFile.exists()) {
            try {
                def parsed = new JsonSlurper().parse(ciJsonFile)
                nativeImageArgs = NativeImageConfigUtils.resolvedBuildArgs(
                        parsed as Map<String, Object>,
                        selectedNativeImageMode,
                        [
                                "{{library.version}}"    : libraryVersion,
                                "{{library.coordinates}}": libraryGAV
                        ]
                )
                baseLayerNativeImageArgs = NativeImageConfigUtils.resolvedBuildArgs(
                        parsed as Map<String, Object>,
                        selectedNativeImageMode,
                        [
                                "{{library.version}}"    : "",
                                "{{library.coordinates}}": ""
                        ]
                )
            } catch (GradleException e) {
                throw e
            } catch (Exception e) {
                throw new GradleException("Failed to resolve native-image args from ci.json", e)
            }
        } else {
            throw new GradleException("Required configuration file ci.json not found at expected path: ${ciJsonFile.absolutePath}\"")
        }

        baseLayerFilePath = project.providers.gradleProperty("tck.baseLayerFile").getOrElse("").trim()
        layeredNativeImage = !baseLayerFilePath.isEmpty()
        dedicatedLayer = project.providers.gradleProperty("tck.dedicatedLayer")
                .map { String value -> value.toBoolean() }
                .getOrElse(false)
        baseLayerFile = layeredNativeImage ? project.file(baseLayerFilePath) : null
        customSharedLayerFile = BaseLayerUtils.resolveCustomSharedLayerFile(project, libraryGAV)
        customSharedLayer = false
        if (dedicatedLayer && !layeredNativeImage) {
            throw new GradleException("Property 'tck.dedicatedLayer=true' requires 'tck.baseLayerFile'.")
        }
        if (layeredNativeImage) {
            // GR-80256: discover network interfaces at runtime in both layers. §FS-tests.3.1
            nativeImageArgs.addAll([
                    "--initialize-at-run-time=io.netty.util.NetUtil",
                    "--initialize-at-run-time=io.grpc.netty.shaded.io.netty.util.NetUtil"
            ])
        }
    }

    void configure(TestedLibraryArtifacts artifacts, Provider<List<String>> testHarnessArguments) {
        metadata = new DedicatedLayerMetadata(project, metadataDirectory, dedicatedLayer)
        if (dedicatedLayer) {
            NativeImageOptions base = nativeExtension.binaries.create("libDedicated")
            base.classpath.from(project.providers.provider { artifacts.resolveDedicatedLayerClasspath() })
            base.buildArgs.addAll(nativeImageArgs)
            base.createLayer { CreateLayerOptions layer ->
                layer.modules.set(project.providers.provider { resolveDedicatedLayerModules() })
                layer.jars.from(project.providers.provider { artifacts.resolveDedicatedLayerJars() })
            }
            nativeExtension.binaries.getByName("test").useLayer("libDedicated")
        }
        if (layeredNativeImage && !dedicatedLayer) {
            // This coordinate's base is a local build output, never a restored CI cache. §FS-tests.3.3
            project.tasks.register("buildCustomSharedLayer") { task ->
                task.description = "Builds a JDK-only shared base with this coordinate's build options"
                task.group = LifecycleBasePlugin.BUILD_GROUP
                task.onlyIf { customSharedLayer }
                task.outputs.file(customSharedLayerFile)
                task.outputs.upToDateWhen { false }
                task.outputs.cacheIf { false }
                task.doLast {
                    SharedLayerBuildUtils.build(project, project.services.get(ExecOperations),
                            customSharedLayerFile, nativeImageTool("native-image"),
                            selectedNativeImageMode, baseLayerNativeImageArgs, false)
                }
            }
        }

        project.tasks.named("nativeTestCompile", BuildNativeImageTask).configure { BuildNativeImageTask task ->
            if (dedicatedLayer) {
                task.dependsOn(project.tasks.named("nativeLibDedicatedCompile"))
            } else if (layeredNativeImage) {
                task.dependsOn(project.tasks.named("buildCustomSharedLayer"))
                // Rebuilt sidecar libraries invalidate consumers as well. §FS-tests.3.3
                task.inputs.dir(project.providers.provider { baseLayerFile.parentFile })
                        .withPropertyName("sharedLayerFiles").withPathSensitivity(PathSensitivity.RELATIVE)
            }
            task.doFirst { validateBaseLayer() }
        }
        if (layeredNativeImage) {
            // Reuse the configured test policy, including binaries.all, exactly once.
            // Generated configuration paths stay lazy until the preparation tasks have run. §FS-tests.3.2
            project.afterEvaluate {
                NativeImageOptions testOptions = nativeExtension.binaries.getByName("test")
                if (dedicatedLayer) {
                    NativeImageOptions baseOptions = nativeExtension.binaries.getByName("libDedicated")
                    baseOptions.buildArgs.set(project.providers.provider {
                        LayeredBuildArgsUtils.dedicatedLayerArguments(
                                testOptions.buildArgs.get(),
                                testHarnessArguments.get(),
                                metadata.resolveDedicatedLayerNativeImageArgs())
                    })
                } else {
                    List<String> policy = LayeredBuildArgsUtils.libraryArguments(
                            testOptions.buildArgs.get(), testHarnessArguments.get())
                    if (policy != nativeImageArgs) {
                        customSharedLayer = true
                        baseLayerNativeImageArgs = LayeredBuildArgsUtils.sharedLayerArguments(policy)
                        baseLayerFile = customSharedLayerFile
                        project.logger.lifecycle("Using an uncached custom shared JDK base for {}", libraryGAV)
                    }
                }
            }
        }
        if (dedicatedLayer) {
            project.tasks.named("nativeLibDedicatedCompile", BuildNativeImageTask).configure { task ->
                task.dependsOn(project.tasks.named("generateDedicatedLayerJUnitTestList"))
                task.dependsOn(project.tasks.named("prepareDedicatedLayerConfiguration"))
                task.outputDirectory.set(baseLayerFile.parentFile)
                task.doFirst {
                    File workingDirectory = task.workingDirectory.get().asFile
                    if (!workingDirectory.isDirectory() && !workingDirectory.mkdirs()) {
                        throw new GradleException("Failed to create native-image working directory: " +
                                workingDirectory.absolutePath)
                    }
                }
            }
        }
    }

    List<String> getApplicationBuildArgs() {
        if (dedicatedLayer) {
            return metadata.applicationBuildArgs
        }
        if (layeredNativeImage) {
            return ["-H:+UnlockExperimentalVMOptions", "-H:LayerUse=${baseLayerFile.absolutePath}",
                    "-H:LinkerRPath=${baseLayerFile.parentFile.absolutePath}", "-H:-UnlockExperimentalVMOptions"]
        }
        return []
    }

    List<String> resolveDedicatedLayerModules() {
        NativeImageOptions baseOptions = nativeExtension.binaries.getByName("libDedicated")
        BuildNativeImageTask baseTask = project.tasks.named("nativeLibDedicatedCompile", BuildNativeImageTask).get()
        Provider<String> environmentHome = project.providers.environmentVariable("GRAALVM_HOME")
                .orElse(project.providers.environmentVariable("JAVA_HOME"))
        // Match Native Build Tools' environment-vs-launcher selection. §FS-tests.3.4
        File javaHome = (baseTask.disableToolchainDetection.get() || !baseOptions.javaLauncher.isPresent()) &&
                environmentHome.isPresent() ? project.file(environmentHome.get()) :
                baseOptions.javaLauncher.get().metadata.installationPath.asFile
        List<String> modules = BaseLayerUtils.dedicatedLayerModules(
                baseOptions.classpath.files.findAll { File entry -> entry.name.endsWith(".jar") }
                        .collect { File entry -> entry.toPath() }, javaHome.toPath())
        List<String> omitted = BaseLayerUtils.layerModules() - modules
        if (!omitted.isEmpty()) {
            project.logger.lifecycle("Omitting dedicated-layer JDK module selectors for {} due to dependency class overlaps: {}",
                    libraryGAV, omitted.join(", "))
        }
        return modules
    }
    Void validateBaseLayer() {
        if (!layeredNativeImage) {
            return
        }
        if (!baseLayerFile.isAbsolute()) {
            throw new GradleException("Property 'tck.baseLayerFile' must contain an absolute path. Got: ${baseLayerFilePath}")
        }
        if (!baseLayerFile.isFile()) {
            throw new GradleException("Native Image base layer does not exist: ${baseLayerFile.absolutePath}")
        }
        if (dedicatedLayer) {
            return
        }
        File manifestFile = new File(baseLayerFile.parentFile, "manifest.json")
        if (!manifestFile.isFile()) {
            throw new GradleException("Native Image base-layer manifest does not exist: ${manifestFile.absolutePath}")
        }
        def currentManifest = new JsonSlurper().parse(manifestFile)
        def expectedManifest = BaseLayerUtils.expectedManifest(
                baseLayerFile,
                project.providers.exec {
                    commandLine(nativeImageTool("native-image"), "--version")
                }.standardOutput.asText.get().trim(),
                selectedNativeImageMode,
                baseLayerNativeImageArgs
        )
        if (currentManifest != expectedManifest) {
            throw new GradleException("Native Image base-layer manifest does not match the current inputs: ${manifestFile.absolutePath}")
        }
    }
}
