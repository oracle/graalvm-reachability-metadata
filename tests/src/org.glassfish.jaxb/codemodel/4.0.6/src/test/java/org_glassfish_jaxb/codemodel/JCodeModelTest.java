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
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class JCodeModelTest {
    @Test
    void refFallsBackToClassForNameWhenContextClassLoaderCannotLoadClass() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(new RejectingClassLoader());

            JCodeModel codeModel = new JCodeModel();
            JClass referencedClass = codeModel.ref("java.math.BigDecimal");

            assertThat(referencedClass.fullName()).isEqualTo("java.math.BigDecimal");
            assertThat(referencedClass.binaryName()).isEqualTo("java.math.BigDecimal");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private RejectingClassLoader() {
            super(null);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }
    }
}
