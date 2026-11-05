/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import org.apache.commons.beanutils.converters.ClassConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ClassConverterTest {
    @Test
    void resolvesTypeNamesWithoutAThreadContextClassLoader() {
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(null);
            ClassConverter converter = new ClassConverter();

            assertSame(String.class, converter.convert(Class.class, "java.lang.String"));
            assertSame(SampleBean.class,
                    converter.convert(Class.class, "commons_beanutils.commons_beanutils.SampleBean"));
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void resolvesConfiguredTypeNamesForConversion() {
        ClassConverter converter = new ClassConverter();

        assertEquals(String.class, converter.convert(Class.class, "java.lang.String"));
        assertEquals(SampleBean.class,
                converter.convert(Class.class, "commons_beanutils.commons_beanutils.SampleBean"));
        assertSame(Integer.class, converter.convert(Class.class, Integer.class));
        assertEquals(Object.class, new ClassConverter(Object.class).convert(Class.class, null));
    }
}
