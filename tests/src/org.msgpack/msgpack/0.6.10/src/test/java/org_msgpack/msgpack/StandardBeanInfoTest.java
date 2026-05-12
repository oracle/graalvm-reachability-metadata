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

import org.junit.jupiter.api.Test;
import org.msgpack.template.builder.beans.BeanInfo;
import org.msgpack.template.builder.beans.EventSetDescriptor;
import org.msgpack.template.builder.beans.IntrospectionException;
import org.msgpack.template.builder.beans.Introspector;
import org.msgpack.template.builder.beans.PropertyDescriptor;

public class StandardBeanInfoTest {
    @Test
    void mergesIndexedBooleanPropertyWithSuperclassBooleanGetter() throws IntrospectionException {
        final BeanInfo beanInfo = introspect(IndexedBooleanFlagBean.class);

        final PropertyDescriptor descriptor = property(beanInfo, "flag");

        assertThat(descriptor.getReadMethod().getDeclaringClass()).isSameAs(BooleanFlagBase.class);
        assertThat(descriptor.getReadMethod().getName()).isEqualTo("isFlag");
        assertThat(descriptor.getWriteMethod().getDeclaringClass()).isSameAs(IndexedBooleanFlagBean.class);
        assertThat(descriptor.getWriteMethod().getName()).isEqualTo("setFlag");
        assertThat(descriptor.getWriteMethod().getParameterTypes()).containsExactly(Boolean.TYPE);
    }

    @Test
    void mergesBooleanReadMethodDeclaredOnIndexedSuperclass() throws IntrospectionException {
        final BeanInfo beanInfo = introspect(BooleanReadyBean.class);

        final PropertyDescriptor descriptor = property(beanInfo, "ready");

        assertThat(descriptor.getReadMethod().getDeclaringClass()).isSameAs(IndexedBooleanReadyBase.class);
        assertThat(descriptor.getReadMethod().getName()).isEqualTo("isReady");
        assertThat(descriptor.getWriteMethod().getDeclaringClass()).isSameAs(BooleanReadyBean.class);
    }

    @Test
    void mergesObjectReadMethodDeclaredOnIndexedSuperclass() throws IntrospectionException {
        final BeanInfo beanInfo = introspect(TitleBean.class);

        final PropertyDescriptor descriptor = property(beanInfo, "title");

        assertThat(descriptor.getReadMethod().getDeclaringClass()).isSameAs(IndexedTitleBase.class);
        assertThat(descriptor.getReadMethod().getName()).isEqualTo("getTitle");
        assertThat(descriptor.getWriteMethod().getDeclaringClass()).isSameAs(TitleBean.class);
    }

    @Test
    void mergesWriteMethodDeclaredOnIndexedSuperclass() throws IntrospectionException {
        final BeanInfo beanInfo = introspect(CountBean.class);

        final PropertyDescriptor descriptor = property(beanInfo, "count");

        assertThat(descriptor.getReadMethod().getDeclaringClass()).isSameAs(CountBean.class);
        assertThat(descriptor.getWriteMethod().getDeclaringClass()).isSameAs(IndexedCountBase.class);
        assertThat(descriptor.getWriteMethod().getName()).isEqualTo("setCount");
        assertThat(descriptor.getWriteMethod().getParameterTypes()).containsExactly(Integer.TYPE);
    }

    @Test
    void introspectsDeclaredListenerMethodsForEventSets() throws IntrospectionException {
        final BeanInfo beanInfo = introspect(SignalSource.class);

        final EventSetDescriptor descriptor = event(beanInfo, "signal");

        assertThat(descriptor.getListenerType()).isSameAs(SignalListener.class);
        assertThat(descriptor.getListenerMethods()).hasSize(1);
        assertThat(descriptor.getListenerMethods()[0].getName()).isEqualTo("signalChanged");
        assertThat(descriptor.getAddListenerMethod().getName()).isEqualTo("addSignalListener");
        assertThat(descriptor.getRemoveListenerMethod().getName()).isEqualTo("removeSignalListener");
    }

    private static BeanInfo introspect(Class<?> beanClass) throws IntrospectionException {
        return Introspector.getBeanInfo(beanClass, Introspector.IGNORE_ALL_BEANINFO);
    }

    private static PropertyDescriptor property(BeanInfo beanInfo, String name) {
        for (PropertyDescriptor descriptor : beanInfo.getPropertyDescriptors()) {
            if (name.equals(descriptor.getName())) {
                return descriptor;
            }
        }
        throw new AssertionError("Missing property descriptor: " + name);
    }

    private static EventSetDescriptor event(BeanInfo beanInfo, String name) {
        for (EventSetDescriptor descriptor : beanInfo.getEventSetDescriptors()) {
            if (name.equals(descriptor.getName())) {
                return descriptor;
            }
        }
        throw new AssertionError("Missing event set descriptor: " + name);
    }

    public static class BooleanFlagBase {
        public boolean isFlag() {
            return true;
        }
    }

    public static class IndexedBooleanFlagBean extends BooleanFlagBase {
        public void setFlag(int index, boolean flag) {
        }

        public void setFlag(boolean flag) {
        }
    }

    public static class IndexedBooleanReadyBase {
        public boolean isReady() {
            return true;
        }

        public String getReady(int index) {
            return "ready";
        }

        public void setReady(int index, String value) {
        }
    }

    public static class BooleanReadyBean extends IndexedBooleanReadyBase {
        public void setReady(boolean ready) {
        }
    }

    public static class IndexedTitleBase {
        public String getTitle() {
            return "title";
        }

        public int getTitle(int index) {
            return index;
        }

        public void setTitle(int index, int value) {
        }
    }

    public static class TitleBean extends IndexedTitleBase {
        public void setTitle(String title) {
        }
    }

    public static class IndexedCountBase {
        public void setCount(int count) {
        }

        public String getCount(int index) {
            return Integer.toString(index);
        }

        public void setCount(int index, String value) {
        }
    }

    public static class CountBean extends IndexedCountBase {
        public int getCount() {
            return 1;
        }
    }

    public static class SignalSource {
        public void addSignalListener(SignalListener listener) {
        }

        public void removeSignalListener(SignalListener listener) {
        }
    }

    public interface SignalListener extends EventListener {
        void signalChanged(SignalEvent event);
    }

    public static class SignalEvent extends EventObject {
        public SignalEvent(Object source) {
            super(source);
        }
    }
}
