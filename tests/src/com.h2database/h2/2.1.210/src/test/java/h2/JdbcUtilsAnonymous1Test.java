/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.api.JavaObjectSerializer;
import org.h2.engine.SysProperties;
import org.h2.util.JdbcUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JdbcUtilsAnonymous1Test {
    @Test
    void resolvesSerializedClassWithContextClassLoader() {
        assertThat(SysProperties.USE_THREAD_CONTEXT_CLASS_LOADER).isTrue();
        List<String> payload = new ArrayList<String>(Arrays.asList("gamma", "delta"));
        JavaObjectSerializer configuredSerializer = JdbcUtils.serializer;
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        JdbcUtils.serializer = null;
        try {
            byte[] serialized = JdbcUtils.serialize(payload, null);

            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            Object decoded = JdbcUtils.deserialize(serialized, null);

            assertThat(decoded).isEqualTo(payload);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
            JdbcUtils.serializer = configuredSerializer;
        }
    }

    @Test
    void fallsBackToObjectInputStreamWhenContextClassLoaderRejectsSerializedClass() {
        assertThat(SysProperties.USE_THREAD_CONTEXT_CLASS_LOADER).isTrue();
        List<String> payload = new ArrayList<String>(Arrays.asList("alpha", "beta"));
        JavaObjectSerializer configuredSerializer = JdbcUtils.serializer;
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        JdbcUtils.serializer = null;
        try {
            byte[] serialized = JdbcUtils.serialize(payload, null);

            Thread.currentThread().setContextClassLoader(new RejectingClassLoader());
            Object decoded = JdbcUtils.deserialize(serialized, null);

            assertThat(decoded).isEqualTo(payload);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
            JdbcUtils.serializer = configuredSerializer;
        }
    }

    public static final class RejectingClassLoader extends ClassLoader {
        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }
    }
}
