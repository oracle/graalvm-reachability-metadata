/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dom4j.dom4j;

import static org.assertj.core.api.Assertions.assertThat;

import org.dom4j.dtd.AttributeDecl;
import org.dom4j.dtd.ElementDecl;
import org.dom4j.util.PerThreadSingleton;
import org.junit.jupiter.api.Test;

public class PerThreadSingletonTest {
    @Test
    void createsAndCachesInstanceLoadedByContextClassLoader() {
        PerThreadSingleton singleton = new PerThreadSingleton();
        singleton.setSingletonClassName(ElementDecl.class.getName());

        Object first = singleton.instance();
        Object second = singleton.instance();

        assertThat(first).isInstanceOf(ElementDecl.class);
        assertThat(second).isSameAs(first);
    }

    @Test
    void fallsBackToClassForNameWhenContextClassLoaderCannotLoadClass() {
        String className = AttributeDecl.class.getName();
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(new RejectingContextClassLoader(originalClassLoader, className));

        try {
            PerThreadSingleton singleton = new PerThreadSingleton();
            singleton.setSingletonClassName(className);

            Object instance = singleton.instance();

            assertThat(instance).isInstanceOf(AttributeDecl.class);
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }
}

final class RejectingContextClassLoader extends ClassLoader {
    private final String rejectedClassName;

    RejectingContextClassLoader(ClassLoader parent, String rejectedClassName) {
        super(parent);
        this.rejectedClassName = rejectedClassName;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (rejectedClassName.equals(name)) {
            throw new ClassNotFoundException(name);
        }
        return super.loadClass(name);
    }
}
