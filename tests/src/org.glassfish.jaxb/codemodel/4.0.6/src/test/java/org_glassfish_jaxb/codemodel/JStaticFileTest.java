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
import com.sun.codemodel.fmt.JStaticFile;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JStaticFileTest {
    private static final String STATIC_RESOURCE = "org_glassfish_jaxb/codemodel/StaticJavaResource.java";

    @TempDir
    Path outputDirectory;

    @Test
    void packageBuildCopiesStaticClasspathResource() throws IOException {
        JCodeModel codeModel = new JCodeModel();
        JPackage generatedPackage = codeModel._package("generated.example");
        generatedPackage.addResourceFile(new JStaticFile(
                JStaticFileTest.class.getClassLoader(),
                STATIC_RESOURCE,
                true));

        File output = outputDirectory.toFile();
        codeModel.build(output, output, null);

        Path staticFile = outputDirectory.resolve("generated/example/StaticJavaResource.java");
        assertThat(staticFile).exists();
        assertThat(Files.readString(staticFile, StandardCharsets.UTF_8))
                .contains("package org_glassfish_jaxb.codemodel;")
                .contains("public class StaticJavaResource")
                .contains("java-resource");
    }
}
