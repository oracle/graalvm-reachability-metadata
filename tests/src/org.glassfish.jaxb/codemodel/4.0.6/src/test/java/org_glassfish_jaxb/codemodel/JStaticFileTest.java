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
import com.sun.codemodel.writer.SingleStreamCodeWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class JStaticFileTest {
    @Test
    void copiesStaticResourceFromConfiguredClassLoader() throws Exception {
        String resourceName = "org_glassfish_jaxb/codemodel/jstaticfile-resource.txt";
        String resourceContent = "static resource content loaded through JStaticFile";
        InMemoryResourceClassLoader classLoader = new InMemoryResourceClassLoader(resourceName, resourceContent);
        JCodeModel codeModel = new JCodeModel();
        JPackage generatedPackage = codeModel._package("example.generated.resources");
        generatedPackage.addResourceFile(new JStaticFile(classLoader, resourceName, true));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        codeModel.build(new SingleStreamCodeWriter(output));
        String generatedResource = output.toString(StandardCharsets.UTF_8);

        assertThat(classLoader.requestedResourceName()).isEqualTo(resourceName);
        assertThat(generatedResource)
                .contains("example.generated.resources.jstaticfile-resource.txt")
                .contains(resourceContent);
    }

    private static final class InMemoryResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final byte[] resourceContent;
        private String requestedResourceName;

        private InMemoryResourceClassLoader(String resourceName, String resourceContent) {
            super(JStaticFileTest.class.getClassLoader());
            this.resourceName = resourceName;
            this.resourceContent = resourceContent.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            requestedResourceName = name;
            if (!resourceName.equals(name)) {
                return null;
            }
            return new ByteArrayInputStream(resourceContent);
        }

        private String requestedResourceName() {
            return requestedResourceName;
        }
    }
}
