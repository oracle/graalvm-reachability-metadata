/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2_external.aopalliance_repackaged;

import org.aopalliance.instrument.Instrumentation;
import org.aopalliance.instrument.InstrumentationError;
import org.aopalliance.instrument.UndoNotSupportedException;
import org.aopalliance.reflect.Locator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InstrumentationExceptionsTest {
    @Test
    void instrumentationErrorIdentifiesTheOperationAndPreservesItsCause() {
        final Instrumentation operation = new MetadataAddition();
        final IllegalArgumentException cause = new IllegalArgumentException("Invalid metadata key");
        final InstrumentationError error = new InstrumentationError(operation, cause);

        assertThat(error).hasMessage("Error while instrumenting add audit metadata").hasCause(cause);
    }

    @Test
    void unsupportedUndoIdentifiesTheOperation() {
        final Instrumentation operation = new MetadataAddition();
        final UndoNotSupportedException failure = new UndoNotSupportedException(operation);

        assertThat(failure).hasMessage("Undo not supported for instrumentation: add audit metadata").hasNoCause();
    }

    private static final class MetadataAddition implements Instrumentation {
        private final Locator location = new Locator() { };

        @Override
        public Locator getLocation() {
            return location;
        }

        @Override
        public int getType() {
            return ADD_METADATA;
        }

        @Override
        public String toString() {
            return "add audit metadata";
        }
    }
}
