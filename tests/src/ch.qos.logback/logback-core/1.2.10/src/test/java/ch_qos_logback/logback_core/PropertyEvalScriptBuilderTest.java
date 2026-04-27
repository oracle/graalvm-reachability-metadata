/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.joran.conditional.IfAction;
import ch.qos.logback.core.joran.spi.InterpretationContext;
import ch.qos.logback.core.status.Status;
import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.AttributesImpl;

public class PropertyEvalScriptBuilderTest {

    @Test
    void buildsConditionFromIfActionUsingLocalAndContextProperties() throws Exception {
        ContextBase context = new ContextBase();
        context.putProperty("contextFlag", "enabled");
        InterpretationContext interpretationContext = new InterpretationContext(context, null);
        interpretationContext.addSubstitutionProperty("localFlag", "enabled");
        IfAction action = new IfAction();
        action.setContext(context);

        action.begin(interpretationContext, "if", ifAttributes(
                "isDefined(\"localFlag\") && property(\"contextFlag\").equals(\"enabled\")"));

        assertThat(action.isActive()).isTrue();
        assertThat(interpretationContext.peekObject()).isSameAs(action);
        assertThat(context.getStatusManager().getCopyOfStatusList())
                .noneMatch(status -> status.getLevel() == Status.ERROR);
    }

    private static AttributesImpl ifAttributes(String condition) {
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "condition", "condition", "CDATA", condition);
        return attributes;
    }
}
