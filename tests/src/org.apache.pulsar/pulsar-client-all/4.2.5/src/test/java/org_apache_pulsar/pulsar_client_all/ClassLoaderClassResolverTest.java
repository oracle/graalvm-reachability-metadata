/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.shade.io.netty.handler.codec.serialization.ClassResolver;
import org.apache.pulsar.shade.io.netty.handler.codec.serialization.ClassResolvers;
import org.junit.jupiter.api.Test;

public class ClassLoaderClassResolverTest {
    @Test
    void fallsBackToClassForNameAfterInitialLoaderFailure() throws Exception {
        FirstAttemptFailingClassLoader loader =
                new FirstAttemptFailingClassLoader(getClass().getClassLoader());
        ClassResolver resolver = ClassResolvers.cacheDisabled(loader);

        Class<?> resolved = resolver.resolve(PulsarClient.class.getName());

        assertThat(resolved).isEqualTo(PulsarClient.class);
        assertThat(loader.attempts).isEqualTo(2);
    }

    private static final class FirstAttemptFailingClassLoader extends ClassLoader {
        private int attempts;

        private FirstAttemptFailingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (PulsarClient.class.getName().equals(name)) {
                attempts++;
                if (attempts == 1) {
                    throw new ClassNotFoundException(name);
                }
            }
            return super.loadClass(name);
        }
    }
}
