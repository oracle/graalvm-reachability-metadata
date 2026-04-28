/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_dom4j.dom4j;

import static org.assertj.core.api.Assertions.assertThat;

import org.dom4j.dom.DOMDocumentFactory;
import org.dom4j.util.SimpleSingleton;
import org.junit.jupiter.api.Test;

public class SimpleSingletonTest {
    @Test
    void fallsBackToClassForNameWhenContextClassLoaderCannotLoadClass() {
        String className = DOMDocumentFactory.class.getName();
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(new RejectingContextClassLoader(originalClassLoader, className));

        try {
            SimpleSingleton<DOMDocumentFactory> singleton = new SimpleSingleton<>();
            singleton.setSingletonClassName(className);

            DOMDocumentFactory instance = singleton.instance();

            assertThat(instance).isInstanceOf(DOMDocumentFactory.class);
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }

    private static final class RejectingContextClassLoader extends ClassLoader {
        private final String rejectedClassName;

        private RejectingContextClassLoader(ClassLoader parent, String rejectedClassName) {
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
}
