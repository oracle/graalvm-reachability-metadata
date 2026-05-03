/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_openjfx.javafx_base;

import static org.assertj.core.api.Assertions.assertThat;

import java.beans.VetoableChangeListener;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.adapter.JavaBeanStringProperty;
import javafx.beans.property.adapter.JavaBeanStringPropertyBuilder;
import org.junit.jupiter.api.Test;

public class PropertyDescriptorTest {

    @Test
    void propertySpecificVetoableListenersAreRegisteredAndRemoved() throws NoSuchMethodException {
        PropertySpecificVetoableBean bean = new PropertySpecificVetoableBean("initial");

        JavaBeanStringProperty property = JavaBeanStringPropertyBuilder.create()
                .bean(bean)
                .name("title")
                .build();
        try {
            assertThat(bean.addedListeners).hasSize(1);
            assertThat(bean.addedProperties).isEmpty();
            assertThat(property.get()).isEqualTo("initial");
        } finally {
            property.dispose();
        }

        assertThat(bean.removedListeners).containsExactlyElementsOf(bean.addedListeners);
        assertThat(bean.removedProperties).isEmpty();
    }

    @Test
    void namedVetoableListenersAreRegisteredWithPropertyNameAndRemoved() throws NoSuchMethodException {
        NamedVetoableBean bean = new NamedVetoableBean("named");

        JavaBeanStringProperty property = JavaBeanStringPropertyBuilder.create()
                .bean(bean)
                .name("title")
                .build();
        try {
            assertThat(bean.addedListeners).hasSize(1);
            assertThat(bean.addedProperties).containsExactly("title");
            assertThat(property.get()).isEqualTo("named");
        } finally {
            property.dispose();
        }

        assertThat(bean.removedListeners).containsExactlyElementsOf(bean.addedListeners);
        assertThat(bean.removedProperties).containsExactly("title");
    }

    @Test
    void globalVetoableListenersAreRegisteredAndRemoved() throws NoSuchMethodException {
        GlobalVetoableBean bean = new GlobalVetoableBean("global");

        JavaBeanStringProperty property = JavaBeanStringPropertyBuilder.create()
                .bean(bean)
                .name("title")
                .build();
        try {
            assertThat(bean.addedListeners).hasSize(1);
            assertThat(bean.addedProperties).isEmpty();
            assertThat(property.get()).isEqualTo("global");
        } finally {
            property.dispose();
        }

        assertThat(bean.removedListeners).containsExactlyElementsOf(bean.addedListeners);
        assertThat(bean.removedProperties).isEmpty();
    }

    public static final class PropertySpecificVetoableBean extends VetoableStringBean {
        public PropertySpecificVetoableBean(String title) {
            super(title);
        }

        public void addTitleListener(VetoableChangeListener listener) {
            recordAdded(null, listener);
        }

        public void removeTitleListener(VetoableChangeListener listener) {
            recordRemoved(null, listener);
        }
    }

    public static final class NamedVetoableBean extends VetoableStringBean {
        public NamedVetoableBean(String title) {
            super(title);
        }

        public void addVetoableChangeListener(String propertyName, VetoableChangeListener listener) {
            recordAdded(propertyName, listener);
        }

        public void removeVetoableChangeListener(String propertyName, VetoableChangeListener listener) {
            recordRemoved(propertyName, listener);
        }
    }

    public static final class GlobalVetoableBean extends VetoableStringBean {
        public GlobalVetoableBean(String title) {
            super(title);
        }

        public void addVetoableChangeListener(VetoableChangeListener listener) {
            recordAdded(null, listener);
        }

        public void removeVetoableChangeListener(VetoableChangeListener listener) {
            recordRemoved(null, listener);
        }
    }

    public abstract static class VetoableStringBean {
        final List<String> addedProperties = new ArrayList<>();
        final List<String> removedProperties = new ArrayList<>();
        final List<VetoableChangeListener> addedListeners = new ArrayList<>();
        final List<VetoableChangeListener> removedListeners = new ArrayList<>();
        private String title;

        VetoableStringBean(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        void recordAdded(String propertyName, VetoableChangeListener listener) {
            if (propertyName != null) {
                addedProperties.add(propertyName);
            }
            addedListeners.add(listener);
        }

        void recordRemoved(String propertyName, VetoableChangeListener listener) {
            if (propertyName != null) {
                removedProperties.add(propertyName);
            }
            removedListeners.add(listener);
        }
    }
}
