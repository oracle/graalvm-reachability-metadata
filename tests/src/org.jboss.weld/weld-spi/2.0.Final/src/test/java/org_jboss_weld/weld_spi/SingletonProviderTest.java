/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_spi;

import org.jboss.weld.bootstrap.api.Singleton;
import org.jboss.weld.bootstrap.api.SingletonProvider;
import org.jboss.weld.bootstrap.api.helpers.IsolatedStaticSingletonProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SingletonProviderTest {
    @BeforeEach
    void resetBeforeTest() {
        SingletonProvider.reset();
    }

    @AfterEach
    void resetAfterTest() {
        SingletonProvider.reset();
    }

    @Test
    void instanceInitializesDefaultIsolatedStaticProvider() {
        SingletonProvider provider = SingletonProvider.instance();

        assertThat(provider).isInstanceOf(IsolatedStaticSingletonProvider.class);

        Singleton<String> singleton = provider.create(String.class);
        String singletonId = "test-application";
        assertThat(singleton.isSet(singletonId)).isFalse();
        assertThatThrownBy(() -> singleton.get(singletonId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Singleton is not set");

        singleton.set(singletonId, "stored value");
        assertThat(singleton.isSet(singletonId)).isTrue();
        assertThat(singleton.get(singletonId)).isEqualTo("stored value");

        singleton.clear(singletonId);
        assertThat(singleton.isSet(singletonId)).isFalse();
    }
}
