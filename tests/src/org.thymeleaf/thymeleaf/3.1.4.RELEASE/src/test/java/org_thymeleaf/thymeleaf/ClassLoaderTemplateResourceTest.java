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

import org.junit.jupiter.api.Test;
import org.thymeleaf.templateresource.ClassLoaderTemplateResource;
import org.thymeleaf.templateresource.ITemplateResource;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoaderTemplateResourceTest {

    private static final String TEMPLATE_RESOURCE_PATH = "org_thymeleaf/thymeleaf/classloader-template-resource/template.txt";

    @Test
    void existsUsesProvidedClassLoaderForClasspathResources() {
        ClassLoader classLoader = new ClassLoader(ClassLoaderTemplateResourceTest.class.getClassLoader()) {
        };
        ClassLoaderTemplateResource templateResource =
                new ClassLoaderTemplateResource(classLoader, "/" + TEMPLATE_RESOURCE_PATH, "UTF-8");

        assertThat(templateResource.getDescription()).isEqualTo(TEMPLATE_RESOURCE_PATH);
        assertThat(templateResource.exists()).isTrue();
    }

    @Test
    void readerUsesProvidedClassLoaderForClasspathResources() throws IOException {
        ClassLoader classLoader = new ClassLoader(ClassLoaderTemplateResourceTest.class.getClassLoader()) {
        };
        ITemplateResource templateResource =
                new ClassLoaderTemplateResource(classLoader, TEMPLATE_RESOURCE_PATH, "UTF-8");

        try (Reader reader = templateResource.reader(); StringWriter writer = new StringWriter()) {
            reader.transferTo(writer);
            assertThat(writer.toString()).isEqualTo("Hello from the classloader template resource.\n");
        }
    }

}
