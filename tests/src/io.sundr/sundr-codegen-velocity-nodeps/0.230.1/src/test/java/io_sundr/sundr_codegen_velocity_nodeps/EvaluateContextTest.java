/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.sundr.deps.org.apache.velocity.VelocityContext;
import io.sundr.deps.org.apache.velocity.context.EvaluateContext;
import io.sundr.deps.org.apache.velocity.context.InternalContextAdapterImpl;
import io.sundr.deps.org.apache.velocity.runtime.RuntimeConstants;
import io.sundr.deps.org.apache.velocity.runtime.RuntimeInstance;
import io.sundr.deps.org.apache.velocity.runtime.log.Log;
import io.sundr.deps.org.apache.velocity.runtime.log.NullLogChute;
import org.junit.jupiter.api.Test;

public class EvaluateContextTest {
    @Test
    void rejectsConfiguredEvaluateContextClassThatDoesNotImplementContext() {
        RuntimeInstance runtime = new RuntimeWithEvaluateContextClass(String.class.getName());
        InternalContextAdapterImpl context = new InternalContextAdapterImpl(new VelocityContext());

        assertThatThrownBy(() -> new EvaluateContext(context, runtime))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("does not implement")
                .hasMessageContaining("io.sundr.deps.org.apache.velocity.context.Context");
    }

    private static final class RuntimeWithEvaluateContextClass extends RuntimeInstance {
        private static final Log LOG = new Log(new NullLogChute());

        private final String evaluateContextClass;

        private RuntimeWithEvaluateContextClass(String evaluateContextClass) {
            this.evaluateContextClass = evaluateContextClass;
        }

        @Override
        public String getString(String key) {
            if (RuntimeConstants.EVALUATE_CONTEXT_CLASS.equals(key)) {
                return evaluateContextClass;
            }
            return super.getString(key);
        }

        @Override
        public Log getLog() {
            return LOG;
        }
    }
}
