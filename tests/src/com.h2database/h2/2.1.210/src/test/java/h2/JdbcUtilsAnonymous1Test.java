/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.util.JdbcUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JdbcUtilsAnonymous1Test {
    @Test
    void deserializationFallsBackWhenThreadContextClassLoaderCannotResolveSerializedClass() {
        byte[] serialized = JdbcUtils.serialize(new ArrayList<>(List.of("h2")), null);
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new RefusingClassLoader());
        try {
            assertThat(JdbcUtils.deserialize(serialized, null)).isEqualTo(List.of("h2"));
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }

    private static final class RefusingClassLoader extends ClassLoader {
        private RefusingClassLoader() {
            super(null);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }
    }
}
