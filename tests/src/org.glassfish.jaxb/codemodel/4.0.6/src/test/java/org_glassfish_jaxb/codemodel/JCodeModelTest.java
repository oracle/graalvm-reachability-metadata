/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.codemodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import java.util.AbstractList;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

public class JCodeModelTest {
    @Test
    void refUsesContextClassLoaderWhenClassIsVisible() {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
        String className = ArrayList.class.getName();
        ClassLoader contextClassLoader = new DelegatingContextClassLoader(originalContextClassLoader);

        currentThread.setContextClassLoader(contextClassLoader);
        try {
            JClass resolvedClass = new JCodeModel().ref(className);

            assertArrayListReference(className, resolvedClass);
        } finally {
            currentThread.setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void refFallsBackToDefaultClassLookupWhenContextClassLoaderCannotLoadClass() {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
        String className = ArrayList.class.getName();
        ClassLoader rejectingContextClassLoader = new DenyingContextClassLoader(originalContextClassLoader, className);

        currentThread.setContextClassLoader(rejectingContextClassLoader);
        try {
            JClass resolvedClass = new JCodeModel().ref(className);

            assertArrayListReference(className, resolvedClass);
        } finally {
            currentThread.setContextClassLoader(originalContextClassLoader);
        }
    }

    private static void assertArrayListReference(String className, JClass resolvedClass) {
        assertThat(resolvedClass.fullName()).isEqualTo(className);
        assertThat(resolvedClass.name()).isEqualTo(ArrayList.class.getSimpleName());
        assertThat(resolvedClass._extends().fullName()).isEqualTo(AbstractList.class.getName());
    }

    private static final class DelegatingContextClassLoader extends ClassLoader {
        DelegatingContextClassLoader(ClassLoader parent) {
            super(parent);
        }
    }

    private static final class DenyingContextClassLoader extends ClassLoader {
        private final String deniedClassName;

        DenyingContextClassLoader(ClassLoader parent, String deniedClassName) {
            super(parent);
            this.deniedClassName = deniedClassName;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (deniedClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }
    }
}
