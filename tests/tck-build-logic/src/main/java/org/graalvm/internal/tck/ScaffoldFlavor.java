/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck;

import org.graalvm.internal.tck.model.LibraryLanguage;

/// The test-project shape for a library language: its build template, test template, and test
/// source directory. The `scaffold` task and the real-use scaffold share it, so a project of either
/// suite builds its language the same way (§FS-test-contract.1.1,
/// §real-use-tests/FS-real-use-test-contract.1.5).
public enum ScaffoldFlavor {
    JAVA("/scaffold/build.gradle.template", "/scaffold/Test.java.template", "java", "java"),
    KOTLIN("/scaffold/build.gradle.kotlin.template", "/scaffold/Test.kt.template", "kotlin", "kt"),
    GROOVY("/scaffold/build.gradle.groovy.template", "/scaffold/Test.groovy.template", "groovy", "groovy"),
    SCALA2("/scaffold/build.gradle.scala2.template", "/scaffold/Test.scala.template", "scala", "scala"),
    SCALA3("/scaffold/build.gradle.scala3.template", "/scaffold/Test.scala.template", "scala", "scala");

    private final String buildTemplatePath;
    private final String testTemplatePath;
    private final String sourceDirectoryName;
    private final String fileExtension;

    ScaffoldFlavor(String buildTemplatePath, String testTemplatePath, String sourceDirectoryName, String fileExtension) {
        this.buildTemplatePath = buildTemplatePath;
        this.testTemplatePath = testTemplatePath;
        this.sourceDirectoryName = sourceDirectoryName;
        this.fileExtension = fileExtension;
    }

    /// The flavor of `language`; a library without one is Java.
    public static ScaffoldFlavor fromLanguage(LibraryLanguage language) {
        if (language == null) {
            return JAVA;
        }
        if (language.isKotlin()) {
            return KOTLIN;
        }
        if (language.isGroovy()) {
            return GROOVY;
        }
        if (language.isScala2()) {
            return SCALA2;
        }
        if (language.isScala3()) {
            return SCALA3;
        }
        if (language.isScala()) {
            throw new IllegalStateException("Unsupported Scala version for scaffold generation: " + language.version());
        }
        throw new IllegalStateException("Unsupported language for scaffold generation: " + language.name());
    }

    public String buildTemplatePath() {
        return buildTemplatePath;
    }

    public String testTemplatePath() {
        return testTemplatePath;
    }

    /// The test source directory, relative to the project: `src/test/<language>`.
    public String testSourceDirectory() {
        return "src/test/" + sourceDirectoryName;
    }

    /// The scaffolded test file, relative to the project, with coordinate placeholders.
    public String testFileTemplatePath() {
        return testSourceDirectory() + "/$sanitizedGroup$/$sanitizedArtifact$/$capitalizedSanitizedArtifact$Test." + fileExtension;
    }
}
