/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf.thymeleaf;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.thymeleaf.templateresource.ClassLoaderTemplateResource;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoaderTemplateResourceTest {

    private static final String RESOURCE_PATH = "/org_thymeleaf/thymeleaf/class-loader-template-resource.txt";
    private static final String RESOURCE_CONTENT = "Loaded through the explicit class loader";

    @Test
    void existsUsesProvidedClassLoaderToResolveResource() {
        ClassLoaderTemplateResource resource = new ClassLoaderTemplateResource(
                ClassLoaderTemplateResourceTest.class.getClassLoader(),
                RESOURCE_PATH,
                StandardCharsets.UTF_8.name());

        assertThat(resource.exists()).isTrue();
    }

    @Test
    void readerUsesProvidedClassLoaderToOpenResourceStream() throws IOException {
        ClassLoaderTemplateResource resource = new ClassLoaderTemplateResource(
                ClassLoaderTemplateResourceTest.class.getClassLoader(),
                RESOURCE_PATH,
                StandardCharsets.UTF_8.name());

        try (Reader reader = resource.reader()) {
            assertThat(readAll(reader)).isEqualTo(RESOURCE_CONTENT);
        }
    }

    private static String readAll(Reader reader) throws IOException {
        StringWriter writer = new StringWriter();
        reader.transferTo(writer);
        return writer.toString();
    }
}
