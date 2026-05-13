/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_inject_plexus;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.TypeLiteral;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.sonatype.guice.plexus.converters.PlexusXmlBeanConverter;

public class PlexusXmlBeanConverterTest {
    @Test
    void convertsXmlElementsToArray() {
        PlexusXmlBeanConverter converter = newConverter();

        String[] values = (String[]) converter.convert(TypeLiteral.get(String[].class), """
                <values>
                    <value>alpha</value>
                    <value>beta</value>
                </values>
                """);

        assertThat(values).containsExactly("alpha", "beta");
    }

    @Test
    void loadsImplementationFromThreadContextClassLoader() {
        PlexusXmlBeanConverter converter = newConverter();
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(PlexusXmlBeanConverterTest.class.getClassLoader());
        try {
            EmptyRole role = (EmptyRole) converter.convert(TypeLiteral.get(EmptyRole.class), """
                    <role implementation="%s"> </role>
                    """.formatted(TcclLoadedRole.class.getName()));

            assertThat(role).isInstanceOf(TcclLoadedRole.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void fallsBackToPeerClassLoaderForImplementation() {
        PlexusXmlBeanConverter converter = newConverter();
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new RejectingClassLoader(originalClassLoader));
        try {
            EmptyRole role = (EmptyRole) converter.convert(TypeLiteral.get(EmptyRole.class), """
                    <role implementation="%s"> </role>
                    """.formatted(PeerLoadedRole.class.getName()));

            assertThat(role).isInstanceOf(PeerLoadedRole.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void fallsBackToClassForNameForBootstrapDefaultImplementation() {
        PlexusXmlBeanConverter converter = newConverter();
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(null);
        try {
            Properties properties = (Properties) converter.convert(TypeLiteral.get(Properties.class), """
                    <properties implementation="java.util.Properties">
                        <property>
                            <name>server</name>
                            <value>native-image</value>
                        </property>
                    </properties>
                    """);

            assertThat(properties).containsEntry("server", "native-image");
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void convertsPlainTextWithStringConstructor() {
        PlexusXmlBeanConverter converter = newConverter();

        ConstructedValue value = (ConstructedValue) converter.convert(
                TypeLiteral.get(ConstructedValue.class), "from text");

        assertThat(value.value()).isEqualTo("from text");
    }

    private static PlexusXmlBeanConverter newConverter() {
        return Guice.createInjector().getInstance(PlexusXmlBeanConverter.class);
    }

    public static class EmptyRole {
        public EmptyRole() {
        }
    }

    public static final class TcclLoadedRole extends EmptyRole {
        public TcclLoadedRole() {
        }
    }

    public static final class PeerLoadedRole extends EmptyRole {
        public PeerLoadedRole() {
        }
    }

    public static final class ConstructedValue {
        private final String value;

        public ConstructedValue(final String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {
        RejectingClassLoader(final ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(final String name) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }
    }
}
