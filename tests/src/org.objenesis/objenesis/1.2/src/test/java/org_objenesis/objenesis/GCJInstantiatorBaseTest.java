/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_objenesis.objenesis;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisException;
import org.objenesis.instantiator.gcj.GCJInstantiatorBase;

public class GCJInstantiatorBaseTest {

    @Test
    void initializesGcjObjectInputStreamLookupWhenConstructed() {
        TestGCJInstantiator.resetInitializationState();

        Assertions.assertThatThrownBy(() -> new TestGCJInstantiator(Object.class))
            .isInstanceOf(ObjenesisException.class)
            .hasCauseInstanceOf(NoSuchMethodException.class);
    }

    private static final class TestGCJInstantiator extends GCJInstantiatorBase {

        private TestGCJInstantiator(Class<?> type) {
            super(type);
        }

        @Override
        public Object newInstance() {
            return null;
        }

        private static void resetInitializationState() {
            newObjectMethod = null;
            dummyStream = null;
        }
    }
}
