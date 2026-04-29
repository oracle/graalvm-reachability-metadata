/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.slf4j_ext;

import java.security.ProtectionDomain;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.instrumentation.LogTransformer;

import static org.assertj.core.api.Assertions.assertThat;

public class LogTransformerTest {
    private static final String LOGGER_FACTORY_CLASS_NAME = LoggerFactory.class.getName();
    private static final String INSTRUMENTED_INTERFACE_NAME =
            "org_slf4j/slf4j_ext/LogTransformerInstrumentedInterface";

    @Test
    void transformUsesTheProtectionDomainClassLoaderToFindSlf4j() {
        final LogTransformer transformer = new LogTransformer.Builder()
                .ignore(new String[0])
                .build();
        final TrackingClassLoader trackingClassLoader = new TrackingClassLoader(
                LogTransformerTest.class.getClassLoader()
        );
        final ProtectionDomain protectionDomain = new ProtectionDomain(
                null,
                null,
                trackingClassLoader,
                null
        );
        final byte[] interfaceBytes = minimalInterfaceClassBytes();

        final byte[] transformedBytes = transformer.transform(
                trackingClassLoader,
                INSTRUMENTED_INTERFACE_NAME,
                null,
                protectionDomain,
                interfaceBytes
        );

        assertThat(trackingClassLoader.loadedLoggerFactory()).isTrue();
        assertThat(transformedBytes).isSameAs(interfaceBytes);
    }

    private static byte[] minimalInterfaceClassBytes() {
        // Java 5 bytecode keeps this fixture parseable by the artifact's old Javassist version.
        return new byte[] {
                (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE,
                0x00, 0x00,
                0x00, 0x31,
                0x00, 0x05,
                0x07, 0x00, 0x02,
                0x01, 0x00, 0x37,
                0x6F, 0x72, 0x67, 0x5F, 0x73, 0x6C, 0x66, 0x34,
                0x6A, 0x2F, 0x73, 0x6C, 0x66, 0x34, 0x6A, 0x5F,
                0x65, 0x78, 0x74, 0x2F, 0x4C, 0x6F, 0x67, 0x54,
                0x72, 0x61, 0x6E, 0x73, 0x66, 0x6F, 0x72, 0x6D,
                0x65, 0x72, 0x49, 0x6E, 0x73, 0x74, 0x72, 0x75,
                0x6D, 0x65, 0x6E, 0x74, 0x65, 0x64, 0x49, 0x6E,
                0x74, 0x65, 0x72, 0x66, 0x61, 0x63, 0x65,
                0x07, 0x00, 0x04,
                0x01, 0x00, 0x10,
                0x6A, 0x61, 0x76, 0x61, 0x2F, 0x6C, 0x61, 0x6E,
                0x67, 0x2F, 0x4F, 0x62, 0x6A, 0x65, 0x63, 0x74,
                0x06, 0x01,
                0x00, 0x01,
                0x00, 0x03,
                0x00, 0x00,
                0x00, 0x00,
                0x00, 0x00,
                0x00, 0x00
        };
    }

    private static final class TrackingClassLoader extends ClassLoader {
        private boolean loadedLoggerFactory;

        private TrackingClassLoader(final ClassLoader parent) {
            super(parent);
        }

        boolean loadedLoggerFactory() {
            return loadedLoggerFactory;
        }

        @Override
        public Class<?> loadClass(final String name) throws ClassNotFoundException {
            if (LOGGER_FACTORY_CLASS_NAME.equals(name)) {
                loadedLoggerFactory = true;
            }
            return super.loadClass(name);
        }
    }
}
