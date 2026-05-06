/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_spi;

import org.jboss.weld.bootstrap.api.Singleton;
import org.jboss.weld.bootstrap.api.SingletonProvider;
import org.jboss.weld.bootstrap.api.helpers.RegistrySingletonProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SingletonProviderTest {
    private static final String SINGLETON_ID = "test-deployment";

    @BeforeEach
    void resetBeforeTest() {
        SingletonProvider.reset();
    }

    @AfterEach
    void resetAfterTest() {
        SingletonProvider.reset();
    }

    @Test
    void instanceInitializesDefaultRegistryProvider() {
        SingletonProvider provider = SingletonProvider.instance();

        assertThat(provider).isInstanceOf(RegistrySingletonProvider.class);

        Singleton<String> singleton = provider.create(String.class);
        assertThat(singleton.isSet(SINGLETON_ID)).isFalse();
        assertThatThrownBy(() -> singleton.get(SINGLETON_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Singleton not set for " + SINGLETON_ID);

        singleton.set(SINGLETON_ID, "stored value");
        assertThat(singleton.isSet(SINGLETON_ID)).isTrue();
        assertThat(singleton.get(SINGLETON_ID)).isEqualTo("stored value");

        singleton.clear(SINGLETON_ID);
        assertThat(singleton.isSet(SINGLETON_ID)).isFalse();
    }
}
