/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_groovydoc;

import org.codehaus.groovy.tools.groovydoc.ClasspathResourceManager;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class ClasspathResourceManagerTest {
    private static final String STYLESHEET =
            "org/codehaus/groovy/tools/groovydoc/gstringTemplates/topLevel/stylesheet.css";

    @Test
    void loadsBundledStylesheetAsStream() throws Exception {
        ClasspathResourceManager resources = new ClasspathResourceManager();

        try (InputStream stream = resources.getInputStream(STYLESHEET)) {
            assertThat(stream).isNotNull();
            String stylesheet = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(stylesheet).contains("body {", "font-family: sans-serif;", ".container {");
        }
    }

    @Test
    void readsBundledStylesheetWithSuppliedClassLoader() throws Exception {
        ClasspathResourceManager resources =
                new ClasspathResourceManager(ClasspathResourceManagerTest.class.getClassLoader());

        try (Reader reader = resources.getReader(STYLESHEET)) {
            StringWriter output = new StringWriter();
            reader.transferTo(output);

            assertThat(output.toString()).contains("body {", "font-family: sans-serif;", ".container {");
        }
    }
}
