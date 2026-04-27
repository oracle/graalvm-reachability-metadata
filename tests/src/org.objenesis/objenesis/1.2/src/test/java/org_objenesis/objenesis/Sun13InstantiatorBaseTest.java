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
import org.objenesis.instantiator.sun.Sun13InstantiatorBase;

public class Sun13InstantiatorBaseTest {

    @Test
    void initializesSun13ObjectInputStreamLookupWhenConstructed() {
        TestSun13Instantiator.resetInitializationState();

        try {
            TestSun13Instantiator instantiator = new TestSun13Instantiator(Object.class);

            Assertions.assertThat(instantiator.newInstance()).isNull();
            Assertions.assertThat(TestSun13Instantiator.initialized()).isTrue();
        }
        catch (ObjenesisException e) {
            Assertions.assertThat(e).hasCauseInstanceOf(NoSuchMethodException.class);
        }
    }

    private static final class TestSun13Instantiator extends Sun13InstantiatorBase {

        private TestSun13Instantiator(Class<?> type) {
            super(type);
        }

        @Override
        public Object newInstance() {
            return null;
        }

        private static void resetInitializationState() {
            allocateNewObjectMethod = null;
        }

        private static boolean initialized() {
            return allocateNewObjectMethod != null;
        }
    }
}
