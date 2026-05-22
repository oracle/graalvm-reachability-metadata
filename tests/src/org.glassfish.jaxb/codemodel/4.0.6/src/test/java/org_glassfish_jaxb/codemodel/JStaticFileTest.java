/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.codemodel;

import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.fmt.JStaticFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class JStaticFileTest {
    private static final String RESOURCE_NAME = "org_glassfish_jaxb/codemodel/static-file.txt";

    @TempDir
    Path tempDir;

    @Test
    void copiesClasspathResourceAsPackageResource() throws Exception {
        Path sourceDirectory = Files.createDirectory(tempDir.resolve("generated-sources"));
        Path resourceDirectory = Files.createDirectory(tempDir.resolve("generated-resources"));
        JCodeModel codeModel = new JCodeModel();
        JPackage packageModel = codeModel._package("example.staticfiles");
        JStaticFile staticFile = new JStaticFile(
                JStaticFileTest.class.getClassLoader(),
                RESOURCE_NAME,
                true);
        packageModel.addResourceFile(staticFile);

        codeModel.build(sourceDirectory.toFile(), resourceDirectory.toFile(), null);

        Path copiedResource = resourceDirectory.resolve("example/staticfiles/static-file.txt");
        assertThat(copiedResource).exists();
        assertThat(Files.readString(copiedResource, StandardCharsets.UTF_8))
                .isEqualTo("JStaticFile classpath resource payload\n");
    }
}
