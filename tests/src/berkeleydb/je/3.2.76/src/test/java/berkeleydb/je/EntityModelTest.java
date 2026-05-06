/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.persist.model.EntityModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EntityModelTest {

    @Test
    void classForNameFallsBackWhenContextClassLoaderCannotLoadClass() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        RejectingClassLoader rejectingClassLoader = new RejectingClassLoader(String.class.getName());
        try {
            Thread.currentThread().setContextClassLoader(rejectingClassLoader);

            Class<?> loadedClass = EntityModel.classForName(String.class.getName());

            assertThat(loadedClass).isEqualTo(String.class);
            assertThat(rejectingClassLoader.hasRejectedClass()).isTrue();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private final String rejectedClassName;
        private boolean rejectedClass;

        private RejectingClassLoader(String rejectedClassName) {
            super(null);
            this.rejectedClassName = rejectedClassName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(rejectedClassName)) {
                rejectedClass = true;
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }

        private boolean hasRejectedClass() {
            return rejectedClass;
        }
    }
}
