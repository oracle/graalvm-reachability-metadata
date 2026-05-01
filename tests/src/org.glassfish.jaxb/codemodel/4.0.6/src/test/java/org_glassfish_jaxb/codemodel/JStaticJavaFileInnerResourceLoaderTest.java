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
import com.sun.codemodel.writer.SingleStreamCodeWriter;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class JStaticJavaFileInnerResourceLoaderTest {
    @Test
    void loadsStaticJavaSourceFromJavaResource() throws Exception {
        String generatedSource = buildStaticJavaSource("StaticJavaSource");

        assertThat(generatedSource)
                .contains("example.generated.StaticJavaSource.java")
                .contains("package example.generated;")
                .contains("public class StaticJavaSource")
                .contains("loaded from .java")
                .doesNotContain("package original.resource;");
    }

    @Test
    void fallsBackToUnderscoreJavaResource() throws Exception {
        String generatedSource = buildStaticJavaSource("StaticJavaSourceFallback");

        assertThat(generatedSource)
                .contains("example.generated.StaticJavaSourceFallback.java")
                .contains("package example.generated;")
                .contains("public class StaticJavaSourceFallback")
                .contains("loaded from .java_")
                .doesNotContain("package original.resource;");
    }

    private static String buildStaticJavaSource(String className) throws Exception {
        JCodeModel codeModel = new JCodeModel();
        JPackage generatedPackage = codeModel._package("example.generated");
        generatedPackage.addResourceFile(new JStaticJavaFile(
                generatedPackage,
                className,
                JStaticJavaFileInnerResourceLoaderTest.class,
                null));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        codeModel.build(new SingleStreamCodeWriter(output));
        return output.toString(StandardCharsets.UTF_8);
    }
}
