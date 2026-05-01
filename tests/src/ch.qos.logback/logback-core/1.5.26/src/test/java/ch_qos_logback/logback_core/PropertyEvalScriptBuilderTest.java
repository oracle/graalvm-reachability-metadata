/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.joran.conditional.Condition;
import ch.qos.logback.core.joran.conditional.PropertyEvalScriptBuilder;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyEvalScriptBuilderTest {
    @Test
    void buildCreatesConditionBackedByLocalAndContextProperties() throws Exception {
        try {
            ContextBase localProperties = new ContextBase();
            localProperties.putProperty("localKey", "local-value");

            ContextBase context = new ContextBase();
            context.putProperty("contextKey", "context-value");

            PropertyEvalScriptBuilder builder = new PropertyEvalScriptBuilder(localProperties);
            builder.setContext(context);

            Condition condition = builder.build("isDefined(\"localKey\")"
                    + " && property(\"localKey\").equals(\"local-value\")"
                    + " && p(\"contextKey\").equals(\"context-value\")"
                    + " && isNull(\"missingKey\")");

            assertThat(condition.evaluate()).isTrue();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }
}
