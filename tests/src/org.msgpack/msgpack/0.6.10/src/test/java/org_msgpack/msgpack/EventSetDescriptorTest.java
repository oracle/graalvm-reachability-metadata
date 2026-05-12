/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_msgpack.msgpack;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EventListener;
import java.util.EventObject;
import java.util.TooManyListenersException;

import org.junit.jupiter.api.Test;
import org.msgpack.template.builder.beans.EventSetDescriptor;
import org.msgpack.template.builder.beans.IntrospectionException;

public class EventSetDescriptorTest {
    @Test
    void locatesConventionalListenerMethodsFromSourceClass() throws IntrospectionException {
        final EventSetDescriptor descriptor = new EventSetDescriptor(
                ConventionalEventSource.class,
                "descriptor",
                DescriptorListener.class,
                "descriptorChanged");

        assertThat(descriptor.getListenerType()).isSameAs(DescriptorListener.class);
        assertThat(descriptor.getListenerMethods()).hasSize(1);
        assertThat(descriptor.getListenerMethods()[0].getName()).isEqualTo("descriptorChanged");
        assertThat(descriptor.getAddListenerMethod().getName()).isEqualTo("addDescriptorListener");
        assertThat(descriptor.getRemoveListenerMethod().getName()).isEqualTo("removeDescriptorListener");
        assertThat(descriptor.getGetListenerMethod().getName()).isEqualTo("getDescriptorListeners");
        assertThat(descriptor.isUnicast()).isTrue();
    }

    @Test
    void locatesExplicitAddRemoveAndGetListenerMethods() throws IntrospectionException {
        final EventSetDescriptor descriptor = new EventSetDescriptor(
                NamedEventSource.class,
                "descriptor",
                DescriptorListener.class,
                new String[] {"descriptorChanged", "descriptorReset"},
                "registerDescriptorListener",
                "unregisterDescriptorListener",
                "currentDescriptorListeners");

        assertThat(descriptor.getListenerMethods()).hasSize(2);
        assertThat(descriptor.getListenerMethods()[0].getName()).isEqualTo("descriptorChanged");
        assertThat(descriptor.getListenerMethods()[1].getName()).isEqualTo("descriptorReset");
        assertThat(descriptor.getAddListenerMethod().getName()).isEqualTo("registerDescriptorListener");
        assertThat(descriptor.getRemoveListenerMethod().getName()).isEqualTo("unregisterDescriptorListener");
        assertThat(descriptor.getGetListenerMethod().getName()).isEqualTo("currentDescriptorListeners");
        assertThat(descriptor.isUnicast()).isFalse();
    }

    @Test
    void acceptsExplicitAddListenerMethodWithAssignableParameterWhenExactLookupFails()
            throws IntrospectionException {
        final EventSetDescriptor descriptor = new EventSetDescriptor(
                LenientEventSource.class,
                "descriptor",
                DescriptorListener.class,
                new String[] {"descriptorChanged"},
                "addLooseDescriptorListener",
                "removeLooseDescriptorListener");

        assertThat(descriptor.getAddListenerMethod().getName()).isEqualTo("addLooseDescriptorListener");
        assertThat(descriptor.getAddListenerMethod().getParameterTypes()).containsExactly(Object.class);
        assertThat(descriptor.getRemoveListenerMethod().getParameterTypes()).containsExactly(DescriptorListener.class);
        assertThat(descriptor.getGetListenerMethod()).isNull();
    }

    public static class ConventionalEventSource {
        public void addDescriptorListener(DescriptorListener listener) throws TooManyListenersException {
        }

        public void removeDescriptorListener(DescriptorListener listener) {
        }

        public DescriptorListener[] getDescriptorListeners() {
            return new DescriptorListener[0];
        }
    }

    public static class NamedEventSource {
        public void registerDescriptorListener(DescriptorListener listener) {
        }

        public void unregisterDescriptorListener(DescriptorListener listener) {
        }

        public DescriptorListener[] currentDescriptorListeners() {
            return new DescriptorListener[0];
        }
    }

    public static class LenientEventSource {
        public void addLooseDescriptorListener(Object listener) {
        }

        public void removeLooseDescriptorListener(DescriptorListener listener) {
        }
    }
}

interface DescriptorListener extends EventListener {
    void descriptorChanged(DescriptorEvent event);

    void descriptorReset(DescriptorEvent event);
}

class DescriptorEvent extends EventObject {
    DescriptorEvent(Object source) {
        super(source);
    }
}
