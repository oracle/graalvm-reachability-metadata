/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static io.restassured.matcher.RestAssuredMatchers.matchesXsdInClasspath;
import static org.hamcrest.MatcherAssert.assertThat;

public class LoadFromClasspathSupportTest {
    private static final String XML = """
            <greeting>
                <text>Hello from REST Assured</text>
            </greeting>
            """;

    private static final String XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="greeting">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="text" type="xs:string"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
            """;

    @Test
    void loadsClasspathResourceWithLeadingSlashWhenValidatingXmlAgainstXsd() {
        assertThat(XML, matchesXsdInClasspath("/load-from-classpath-support-test.xsd"));
    }

    @Test
    void loadsNormalizedPathFromContextClassLoaderWhenAbsolutePathIsNotAClassResource() {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(new InMemorySchemaClassLoader(originalClassLoader));

        try {
            assertThat(XML, matchesXsdInClasspath("/in-memory-load-from-classpath-support-test.xsd"));
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }

    private static final class InMemorySchemaClassLoader extends ClassLoader {
        private static final String SCHEMA_PATH = "in-memory-load-from-classpath-support-test.xsd";

        private InMemorySchemaClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (!SCHEMA_PATH.equals(name)) {
                return null;
            }
            return new ByteArrayInputStream(XSD.getBytes(StandardCharsets.UTF_8));
        }
    }
}
