/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.joran.action.Action;
import ch.qos.logback.core.joran.action.NestedComplexPropertyIA;
import ch.qos.logback.core.joran.spi.ElementPath;
import ch.qos.logback.core.joran.spi.InterpretationContext;
import ch.qos.logback.core.joran.util.beans.BeanDescriptionCache;
import ch.qos.logback.core.status.OnConsoleStatusListener;
import ch.qos.logback.core.status.StatusListener;
import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.AttributesImpl;

public class NestedComplexPropertyIATest {

    @Test
    void createsAndAttachesNestedComponentFromClassAttribute() throws Exception {
        ContextBase context = new ContextBase();
        NestedComplexPropertyIA action = new NestedComplexPropertyIA(new BeanDescriptionCache(context));
        action.setContext(context);
        InterpretationContext interpretationContext = new InterpretationContext(context, null);
        StatusListenerParent parent = new StatusListenerParent();
        interpretationContext.pushObject(parent);
        AttributesImpl attributes = statusListenerAttributes();

        boolean applicable = action.isApplicable(
                new ElementPath("configuration/statusListener"),
                attributes,
                interpretationContext);
        assertThat(applicable).isTrue();

        action.begin(interpretationContext, "statusListener", attributes);
        assertThat(interpretationContext.peekObject()).isInstanceOf(OnConsoleStatusListener.class);

        action.end(interpretationContext, "statusListener");

        assertThat(interpretationContext.peekObject()).isSameAs(parent);
        assertThat(parent.getStatusListener()).isInstanceOf(OnConsoleStatusListener.class);
        OnConsoleStatusListener statusListener = (OnConsoleStatusListener) parent.getStatusListener();
        assertThat(statusListener.getContext()).isSameAs(context);
        assertThat(statusListener.isStarted()).isTrue();
    }

    private static AttributesImpl statusListenerAttributes() {
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute(
                "",
                Action.CLASS_ATTRIBUTE,
                Action.CLASS_ATTRIBUTE,
                "CDATA",
                OnConsoleStatusListener.class.getName());
        return attributes;
    }

    public static final class StatusListenerParent {

        private StatusListener statusListener;

        public StatusListener getStatusListener() {
            return statusListener;
        }

        public void setStatusListener(StatusListener statusListener) {
            this.statusListener = statusListener;
        }
    }
}
