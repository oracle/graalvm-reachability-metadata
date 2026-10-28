/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.common.util.ClassLoaderUtils;
import org.junit.jupiter.api.Test;

public class ClassLoaderUtilsTest {
    private static final String ALIAS = "example.PulsarClientAlias";

    @Test
    void fallsBackToProvidedLoaderForAnApplicationAlias() throws Exception {
        AliasClassLoader loader = new AliasClassLoader(getClass().getClassLoader());

        Class<?> loaded = ClassLoaderUtils.loadClass(ALIAS, loader);

        assertThat(loaded).isEqualTo(PulsarClient.class);
        assertThat(loader.requested).isTrue();
    }

    private static final class AliasClassLoader extends ClassLoader {
        private boolean requested;

        private AliasClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (ALIAS.equals(name)) {
                requested = true;
                return PulsarClient.class;
            }
            return super.loadClass(name);
        }
    }
}
