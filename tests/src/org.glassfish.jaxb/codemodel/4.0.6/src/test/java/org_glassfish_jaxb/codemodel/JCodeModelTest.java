/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.codemodel;

import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JCodeModelTest {
    @Test
    void resolvesReferenceWithContextClassLoader() {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(JCodeModelTest.class.getClassLoader());
        try {
            JCodeModel codeModel = new JCodeModel();

            JClass referencedClass = codeModel.ref("java.util.ArrayList");

            assertThat(referencedClass.fullName()).isEqualTo("java.util.ArrayList");
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void resolvesReferenceWithClassForNameFallback() {
        String rejectedClassName = "java.lang.String";
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader rejectingClassLoader = new RejectingClassLoader(
                JCodeModelTest.class.getClassLoader(), rejectedClassName);
        Thread.currentThread().setContextClassLoader(rejectingClassLoader);
        try {
            JCodeModel codeModel = new JCodeModel();

            JClass referencedClass = codeModel.ref(rejectedClassName);

            assertThat(referencedClass.fullName()).isEqualTo(rejectedClassName);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private final String rejectedClassName;

        RejectingClassLoader(ClassLoader parent, String rejectedClassName) {
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
