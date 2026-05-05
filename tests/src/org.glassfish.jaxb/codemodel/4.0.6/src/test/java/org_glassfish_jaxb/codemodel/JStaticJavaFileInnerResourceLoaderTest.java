/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.codemodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.fmt.JStaticJavaFile;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JStaticJavaFileInnerResourceLoaderTest {
    @TempDir
    Path outputDirectory;

    @Test
    void staticJavaFileLoadsJavaResourceBesideLoadingClass() throws IOException {
        Path generatedFile = buildStaticJavaFile("StaticJavaResource");

        assertThat(Files.readString(generatedFile, StandardCharsets.UTF_8))
                .contains("package generated.example;")
                .contains("public class StaticJavaResource")
                .contains("java-resource");
    }

    @Test
    void staticJavaFileFallsBackToJavaUnderscoreResourceBesideLoadingClass() throws IOException {
        Path generatedFile = buildStaticJavaFile("StaticJavaUnderscoreResource");

        assertThat(Files.readString(generatedFile, StandardCharsets.UTF_8))
                .contains("package generated.example;")
                .contains("public class StaticJavaUnderscoreResource")
                .contains("java-underscore-resource");
    }

    private Path buildStaticJavaFile(String className) throws IOException {
        JCodeModel codeModel = new JCodeModel();
        JPackage generatedPackage = codeModel._package("generated.example");
        generatedPackage.addResourceFile(new JStaticJavaFile(
                generatedPackage,
                className,
                JStaticJavaFileInnerResourceLoaderTest.class,
                null));

        File output = outputDirectory.toFile();
        codeModel.build(output, output, null);
        return outputDirectory.resolve("generated/example/" + className + ".java");
    }
}
