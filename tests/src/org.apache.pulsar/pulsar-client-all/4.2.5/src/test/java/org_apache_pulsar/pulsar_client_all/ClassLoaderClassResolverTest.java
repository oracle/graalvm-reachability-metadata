/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.io.netty.handler.codec.serialization.ClassResolver;
import org.apache.pulsar.shade.io.netty.handler.codec.serialization.ClassResolvers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
@SuppressWarnings("deprecation")
public class ClassLoaderClassResolverTest {
    @Test
    void resolvesClassThroughConfiguredLoader() throws Exception {
        ClassResolver resolver = ClassResolvers.cacheDisabled(getClass().getClassLoader());

        Class<?> resolved = resolver.resolve(String.class.getName());

        assertThat(resolved).isEqualTo(String.class);
    }
}
