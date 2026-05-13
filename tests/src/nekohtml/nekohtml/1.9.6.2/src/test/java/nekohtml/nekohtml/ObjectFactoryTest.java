/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package nekohtml.nekohtml;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import org.apache.xerces.util.SymbolTable;
import org.cyberneko.html.HTMLConfiguration;
import org.junit.jupiter.api.Test;

public class ObjectFactoryTest {
    private static final String SYMBOL_TABLE_PROPERTY = "http://apache.org/xml/properties/internal/symbol-table";
    private static final String SYMBOL_TABLE_CLASS_NAME = "org.apache.xerces.util.SymbolTable";
    private static final String OBJECT_FACTORY_CLASS_NAME = "org.cyberneko.html.ObjectFactory";
    private static final String OBJECT_FACTORY_CLASS_CACHE_FIELD = "class$org$cyberneko$html$ObjectFactory";

    @Test
    void htmlConfigurationFallsBackToObjectFactoryClassLoaderWhenContextLoaderCannotLoadProvider()
                    throws Exception {
        VersionFlags versionFlags = TestHTMLConfiguration.enableSymbolTableFactoryPath();
        clearObjectFactoryClassCache();
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        ClassLoader blockingClassLoader = new BlockingClassLoader(SYMBOL_TABLE_CLASS_NAME);

        try {
            thread.setContextClassLoader(blockingClassLoader);

            HTMLConfiguration configuration = new TestHTMLConfiguration();

            Object symbolTable = configuration.getProperty(SYMBOL_TABLE_PROPERTY);
            assertNotNull(symbolTable);
            assertTrue(SymbolTable.class.isInstance(symbolTable));
        } finally {
            thread.setContextClassLoader(originalClassLoader);
            TestHTMLConfiguration.restoreVersionFlags(versionFlags);
        }
    }

    @Test
    void htmlConfigurationUsesClassForNameWhenContextClassLoaderIsUnavailable() throws Exception {
        VersionFlags versionFlags = TestHTMLConfiguration.enableSymbolTableFactoryPath();
        clearObjectFactoryClassCache();
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();

        try {
            thread.setContextClassLoader(null);

            HTMLConfiguration configuration = new TestHTMLConfiguration();

            Object symbolTable = configuration.getProperty(SYMBOL_TABLE_PROPERTY);
            assertNotNull(symbolTable);
            assertTrue(SymbolTable.class.isInstance(symbolTable));
        } finally {
            thread.setContextClassLoader(originalClassLoader);
            TestHTMLConfiguration.restoreVersionFlags(versionFlags);
        }
    }

    private static void clearObjectFactoryClassCache() throws ReflectiveOperationException {
        Class<?> objectFactoryClass = Class.forName(OBJECT_FACTORY_CLASS_NAME);
        Field field = objectFactoryClass.getDeclaredField(OBJECT_FACTORY_CLASS_CACHE_FIELD);
        field.setAccessible(true);
        field.set(null, null);
    }

    private static final class TestHTMLConfiguration extends HTMLConfiguration {
        static VersionFlags enableSymbolTableFactoryPath() {
            VersionFlags flags = new VersionFlags(XERCES_2_0_0, XERCES_2_0_1, XML4J_4_0_x);
            XERCES_2_0_0 = true;
            XERCES_2_0_1 = false;
            XML4J_4_0_x = false;
            return flags;
        }

        static void restoreVersionFlags(VersionFlags flags) {
            XERCES_2_0_0 = flags.xerces200;
            XERCES_2_0_1 = flags.xerces201;
            XML4J_4_0_x = flags.xml4j40x;
        }
    }

    private static final class VersionFlags {
        private final boolean xerces200;
        private final boolean xerces201;
        private final boolean xml4j40x;

        VersionFlags(boolean xerces200, boolean xerces201, boolean xml4j40x) {
            this.xerces200 = xerces200;
            this.xerces201 = xerces201;
            this.xml4j40x = xml4j40x;
        }
    }

    private static final class BlockingClassLoader extends ClassLoader {
        private final String blockedClassName;

        BlockingClassLoader(String blockedClassName) {
            super(null);
            this.blockedClassName = blockedClassName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (blockedClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }
}
