/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.codemodel;

import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.fmt.JStaticJavaFile;
import com.sun.codemodel.writer.SingleStreamCodeWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class JStaticJavaFileInnerResourceLoaderTest {
    @Test
    void copiesStaticJavaSourceResourceWithJavaExtension() throws IOException {
        String generatedSource = buildStaticJavaFile("StaticTemplate");

        assertThat(generatedSource)
                .contains("package example.generated;")
                .contains("public final class StaticTemplate")
                .contains("return \"loaded from .java\";");
    }

    @Test
    void copiesStaticJavaSourceResourceWithJavaUnderscoreExtension() throws IOException {
        String generatedSource = buildStaticJavaFile("StaticTemplateUnderscore");

        assertThat(generatedSource)
                .contains("package example.generated;")
                .contains("public final class StaticTemplateUnderscore")
                .contains("return \"loaded from .java_\";");
    }

    private static String buildStaticJavaFile(String className) throws IOException {
        JCodeModel codeModel = new JCodeModel();
        JPackage generatedPackage = codeModel._package("example.generated");
        JStaticJavaFile staticJavaFile = new JStaticJavaFile(
                generatedPackage,
                className,
                JStaticJavaFileInnerResourceLoaderTest.class,
                null);
        generatedPackage.addResourceFile(staticJavaFile);

        ByteArrayOutputStream generatedSource = new ByteArrayOutputStream();
        codeModel.build(new SingleStreamCodeWriter(generatedSource));
        return generatedSource.toString(StandardCharsets.UTF_8);
    }
}
