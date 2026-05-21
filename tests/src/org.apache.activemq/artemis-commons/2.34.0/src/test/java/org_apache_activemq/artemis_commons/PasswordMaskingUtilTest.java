/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.apache.activemq.artemis.utils.DefaultSensitiveStringCodec;
import org.apache.activemq.artemis.utils.PasswordMaskingUtil;
import org.apache.activemq.artemis.utils.SensitiveDataCodec;
import org.junit.jupiter.api.Test;

public class PasswordMaskingUtilTest {
    private static final String CONTEXT_ONLY_CODEC_NAME = "context.only.PasswordMaskingUtilCodec";

    @Test
    public void createsCodecDiscoveredByServiceLoader() throws Exception {
        SensitiveDataCodec<String> codec = PasswordMaskingUtil.getCodec(
                ServiceLoadedCodec.class.getCanonicalName() + ";prefix=service");

        assertThat(codec).isInstanceOf(ServiceLoadedCodec.class);
        assertThat(codec.decode("secret")).isEqualTo("service:secret");
    }

    @Test
    public void createsCodecWithPasswordMaskingUtilClassLoader() throws Exception {
        SensitiveDataCodec<String> codec = PasswordMaskingUtil.getCodec(DefaultSensitiveStringCodec.class.getName());

        assertThat(codec).isInstanceOf(DefaultSensitiveStringCodec.class);
    }

    @Test
    public void createsCodecWithContextClassLoaderFallback() throws Exception {
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new ContextOnlyCodecClassLoader(originalLoader));
        try {
            SensitiveDataCodec<String> codec = PasswordMaskingUtil.getCodec(CONTEXT_ONLY_CODEC_NAME);

            assertThat(codec).isInstanceOf(DefaultSensitiveStringCodec.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }

    public static class ServiceLoadedCodec implements SensitiveDataCodec<String> {
        private String prefix = "codec";

        public ServiceLoadedCodec() {
        }

        @Override
        public String decode(Object encodedValue) {
            return prefix + ":" + encodedValue;
        }

        @Override
        public String encode(Object value) {
            return prefix + ":" + value;
        }

        @Override
        public void init(Map<String, String> params) {
            prefix = params.getOrDefault("prefix", prefix);
        }
    }

    private static final class ContextOnlyCodecClassLoader extends ClassLoader {
        private ContextOnlyCodecClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (CONTEXT_ONLY_CODEC_NAME.equals(name)) {
                return DefaultSensitiveStringCodec.class;
            }
            return super.loadClass(name);
        }
    }
}
