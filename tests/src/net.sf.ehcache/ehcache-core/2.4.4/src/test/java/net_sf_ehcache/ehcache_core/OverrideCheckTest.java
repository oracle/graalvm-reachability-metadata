/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.sf.ehcache.constructs.nonstop.util.OverrideCheck;
import org.junit.jupiter.api.Test;

public class OverrideCheckTest {
    @Test
    void acceptsClassDeclaringAllMethodsRequiredByInterface() {
        assertThatCode(() -> OverrideCheck.check(TrackedOperations.class, CompleteTrackedOperations.class))
                .doesNotThrowAnyException();
    }

    @Test
    void reportsMissingMethodOverridesForInterfaceMethods() {
        assertThatThrownBy(() -> OverrideCheck.check(TrackedOperations.class, IncompleteTrackedOperations.class))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(IncompleteTrackedOperations.class.getName())
                .hasMessageContaining("remove(java.lang.Object)");
    }
}

interface TrackedOperations {
    Object get(Object key);

    void put(Object key, Object value);

    boolean remove(Object key);
}

class CompleteTrackedOperations implements TrackedOperations {
    @Override
    public Object get(Object key) {
        return key;
    }

    @Override
    public void put(Object key, Object value) {
    }

    @Override
    public boolean remove(Object key) {
        return true;
    }
}

abstract class IncompleteTrackedOperations implements TrackedOperations {
    @Override
    public Object get(Object key) {
        return key;
    }

    @Override
    public void put(Object key, Object value) {
    }
}
