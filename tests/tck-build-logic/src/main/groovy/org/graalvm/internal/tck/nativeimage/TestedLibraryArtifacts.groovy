/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.nativeimage

import org.gradle.api.Project
import org.gradle.api.GradleException
import org.gradle.api.artifacts.ResolvedArtifact
import org.graalvm.internal.tck.utils.BaseLayerUtils
import org.graalvm.internal.tck.utils.JarUtils

/// Resolves one library consistently for coverage, diagnostics, and layers. §FS-tests.3
class TestedLibraryArtifacts {
    final Project project
    final String libraryGAV

    TestedLibraryArtifacts(Project project, String libraryGAV) {
        this.project = project
        this.libraryGAV = libraryGAV
    }

    List<ResolvedArtifact> resolveTestedLibraryArtifacts() {
        String targetGroup = ""
        String targetArtifact = ""
        String targetVersion = ""
        String[] coordsParts = (libraryGAV ?: "").split(":")
        if (coordsParts.length >= 3) {
            targetGroup = coordsParts[0]
            targetArtifact = coordsParts[1]
            targetVersion = coordsParts[2]
        }

        Set<ResolvedArtifact> artifacts = project.configurations.testRuntimeClasspath.resolvedConfiguration.resolvedArtifacts
        Set<ResolvedArtifact> matched = new LinkedHashSet<>(artifacts.findAll { ResolvedArtifact artifact ->
            (artifact.moduleVersion.id.group == targetGroup) && (artifact.moduleVersion.id.name == targetArtifact)
        })

        if (matched.isEmpty()) {
            // Fallback for relocated or substituted modules: the requested G:A coordinates may resolve
            // to a different group in published metadata, so no resolved artifact carries the original group.
            matched.addAll(artifacts.findAll { ResolvedArtifact artifact ->
                (artifact.moduleVersion.id.name == targetArtifact) && (artifact.moduleVersion.id.version == targetVersion)
            })
        }

        if (matched.isEmpty()) {
            throw new GradleException("No eligible artifacts found on the classpath for ${libraryGAV}.\nPlease verify that the provided artifact exists.")
        }

        return matched.toList().sort { ResolvedArtifact artifact -> artifact.file.absolutePath }
    }

    List<File> resolveTestedLibraryJars() {
        return resolveTestedLibraryArtifacts().collect { ResolvedArtifact artifact -> artifact.file }
    }

    List<File> resolveTestedLibraryMainJars() {
        String requestedVersion = libraryGAV.split(":")[2]
        List<ResolvedArtifact> mainArtifacts = resolveTestedLibraryArtifacts().findAll { ResolvedArtifact artifact ->
            JarUtils.isMainJar(artifact.extension, artifact.classifier, requestedVersion, artifact.moduleVersion.id.version)
        }
        if (mainArtifacts.isEmpty()) {
            throw new GradleException(
                    "No main JAR found on the classpath for ${libraryGAV}.\n" +
                            "Please verify that the coordinate publishes a main JAR."
            )
        }
        return mainArtifacts.collect { ResolvedArtifact artifact -> artifact.file }
    }

    List<File> resolveJUnitRuntimeJars() {
        Set<File> matched = new LinkedHashSet<>(
                project.configurations.nativeImageTestClasspath.resolvedConfiguration.resolvedArtifacts.findAll { artifact ->
                    BaseLayerUtils.isJUnitRuntimeArtifact(
                            artifact.moduleVersion.id.group,
                            artifact.moduleVersion.id.name)
                }.collect { artifact ->
                    artifact.file
                })
        if (matched.isEmpty()) {
            throw new GradleException("No JUnit runtime artifacts found on the test runtime classpath.")
        }
        return matched.toList().sort { File file -> file.absolutePath }
    }

    List<File> resolveDedicatedLayerJars() {
        Set<File> includedJars = new LinkedHashSet<>(resolveTestedLibraryJars())
        includedJars.addAll(resolveJUnitRuntimeJars())
        return includedJars.toList().sort { File file -> file.absolutePath }
    }
    List<File> resolveDedicatedLayerClasspath() {
        Set<File> classpath = new LinkedHashSet<>(project.configurations.testRuntimeClasspath
                .resolvedConfiguration.resolvedArtifacts.collect { artifact -> artifact.file })
        classpath.addAll(project.configurations.nativeImageTestClasspath
                .resolvedConfiguration.resolvedArtifacts.collect { artifact -> artifact.file })
        return classpath
                .findAll { File file -> file.isFile() }
                .toList()
                .sort { File file -> file.absolutePath }
    }
}
