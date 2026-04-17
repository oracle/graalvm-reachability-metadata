/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.joran.action.NestedComplexPropertyIA;
import ch.qos.logback.core.joran.spi.ElementPath;
import ch.qos.logback.core.joran.spi.InterpretationContext;
import ch.qos.logback.core.joran.util.beans.BeanDescriptionCache;
import ch.qos.logback.core.spi.ContextAwareBase;
import ch.qos.logback.core.spi.LifeCycle;
import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.AttributesImpl;

public class NestedComplexPropertyIATest {

    @Test
    void instantiatesImplicitNestedComponentAndAttachesItToParent() {
        ContextBase context = new ContextBase();
        NestedComplexPropertyIA action = new NestedComplexPropertyIA(new BeanDescriptionCache(context));
        InterpretationContext interpretationContext = new InterpretationContext(context, null);
        ParentTarget parent = new ParentTarget();
        ElementPath elementPath = new ElementPath("component");
        AttributesImpl attributes = new AttributesImpl();

        action.setContext(context);
        interpretationContext.pushObject(parent);

        assertThat(action.isApplicable(elementPath, attributes, interpretationContext)).isTrue();

        action.begin(interpretationContext, "component", attributes);

        assertThat(interpretationContext.peekObject()).isInstanceOf(NestedComponent.class);

        action.end(interpretationContext, "component");

        assertThat(parent.getComponent()).isNotNull();
        assertThat(parent.getComponent().getParent()).isSameAs(parent);
        assertThat(parent.getComponent().getContext()).isSameAs(context);
        assertThat(parent.getComponent().isStarted()).isTrue();
        assertThat(interpretationContext.peekObject()).isSameAs(parent);
    }

    public static final class ParentTarget {

        private NestedComponent component;

        public NestedComponent getComponent() {
            return component;
        }

        public void setComponent(NestedComponent component) {
            this.component = component;
        }
    }

    public static final class NestedComponent extends ContextAwareBase implements LifeCycle {

        private ParentTarget parent;
        private boolean started;

        public NestedComponent() {
        }

        public ParentTarget getParent() {
            return parent;
        }

        public void setParent(ParentTarget parent) {
            this.parent = parent;
        }

        @Override
        public void start() {
            started = true;
        }

        @Override
        public void stop() {
            started = false;
        }

        @Override
        public boolean isStarted() {
            return started;
        }
    }
}
