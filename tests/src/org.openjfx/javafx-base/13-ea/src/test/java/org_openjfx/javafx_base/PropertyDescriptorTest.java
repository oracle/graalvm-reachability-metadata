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
import javafx.beans.property.adapter.JavaBeanIntegerProperty;
import javafx.beans.property.adapter.JavaBeanIntegerPropertyBuilder;
import org.junit.jupiter.api.Test;

public class PropertyDescriptorTest {

    @Test
    void javaBeanPropertyRegistersAndRemovesNamedVetoableChangeListener() throws NoSuchMethodException {
        NamedVetoableBean bean = new NamedVetoableBean(7);

        JavaBeanIntegerProperty property = JavaBeanIntegerPropertyBuilder.create()
                .bean(bean)
                .name("score")
                .build();

        assertThat(property.get()).isEqualTo(7);
        assertThat(bean.addedPropertyNames).containsExactly("score");
        assertThat(bean.vetoableChangeListeners).hasSize(1);

        property.dispose();

        assertThat(bean.removedPropertyNames).containsExactly("score");
        assertThat(bean.vetoableChangeListeners).isEmpty();
    }

    @Test
    void javaBeanPropertyRegistersAndRemovesGlobalVetoableChangeListener() throws NoSuchMethodException {
        GlobalVetoableBean bean = new GlobalVetoableBean(13);

        JavaBeanIntegerProperty property = JavaBeanIntegerPropertyBuilder.create()
                .bean(bean)
                .name("score")
                .build();

        assertThat(property.get()).isEqualTo(13);
        assertThat(bean.addedListeners).hasSize(1);

        property.dispose();

        assertThat(bean.removedListeners).hasSize(1);
        assertThat(bean.addedListeners).isEmpty();
    }

    public static final class NamedVetoableBean {
        private final List<String> addedPropertyNames = new ArrayList<>();
        private final List<String> removedPropertyNames = new ArrayList<>();
        private final List<VetoableChangeListener> vetoableChangeListeners = new ArrayList<>();
        private int score;

        public NamedVetoableBean(int score) {
            this.score = score;
        }

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            this.score = score;
        }

        public void addVetoableChangeListener(String propertyName, VetoableChangeListener listener) {
            addedPropertyNames.add(propertyName);
            vetoableChangeListeners.add(listener);
        }

        public void removeVetoableChangeListener(String propertyName, VetoableChangeListener listener) {
            removedPropertyNames.add(propertyName);
            vetoableChangeListeners.remove(listener);
        }
    }

    public static final class GlobalVetoableBean {
        private final List<VetoableChangeListener> addedListeners = new ArrayList<>();
        private final List<VetoableChangeListener> removedListeners = new ArrayList<>();
        private int score;

        public GlobalVetoableBean(int score) {
            this.score = score;
        }

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            this.score = score;
        }

        public void addVetoableChangeListener(VetoableChangeListener listener) {
            addedListeners.add(listener);
        }

        public void removeVetoableChangeListener(VetoableChangeListener listener) {
            removedListeners.add(listener);
            addedListeners.remove(listener);
        }
    }
}
