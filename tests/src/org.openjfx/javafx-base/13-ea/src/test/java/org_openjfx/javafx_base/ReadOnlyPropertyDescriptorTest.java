/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_openjfx.javafx_base;

import static org.assertj.core.api.Assertions.assertThat;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.adapter.ReadOnlyJavaBeanIntegerProperty;
import javafx.beans.property.adapter.ReadOnlyJavaBeanIntegerPropertyBuilder;
import org.junit.jupiter.api.Test;

public class ReadOnlyPropertyDescriptorTest {

    @Test
    void readOnlyPropertyRegistersAndRemovesSpecificPropertyChangeListener() throws NoSuchMethodException {
        SpecificPropertyChangeBean bean = new SpecificPropertyChangeBean(3);

        ReadOnlyJavaBeanIntegerProperty property = ReadOnlyJavaBeanIntegerPropertyBuilder.create()
                .bean(bean)
                .name("score")
                .build();
        List<Object> invalidatedObservables = observeInvalidations(property);

        assertThat(bean.getScore()).isEqualTo(3);
        assertThat(bean.scoreListeners).hasSize(1);

        bean.setScore(5);
        assertThat(bean.getScore()).isEqualTo(5);
        assertThat(invalidatedObservables).containsExactly(property);

        property.dispose();
        assertThat(bean.scoreListeners).isEmpty();
    }

    @Test
    void readOnlyPropertyRegistersAndRemovesNamedPropertyChangeListener() throws NoSuchMethodException {
        NamedPropertyChangeBean bean = new NamedPropertyChangeBean(7);

        ReadOnlyJavaBeanIntegerProperty property = ReadOnlyJavaBeanIntegerPropertyBuilder.create()
                .bean(bean)
                .name("score")
                .build();
        List<Object> invalidatedObservables = observeInvalidations(property);

        assertThat(bean.getScore()).isEqualTo(7);
        assertThat(bean.addedPropertyNames).containsExactly("score");
        assertThat(bean.propertyChangeListeners).hasSize(1);

        bean.setScore(11);
        assertThat(bean.getScore()).isEqualTo(11);
        assertThat(invalidatedObservables).containsExactly(property);

        property.dispose();
        assertThat(bean.removedPropertyNames).containsExactly("score");
        assertThat(bean.propertyChangeListeners).isEmpty();
    }

    @Test
    void readOnlyPropertyRegistersAndRemovesGlobalPropertyChangeListener() throws NoSuchMethodException {
        GlobalPropertyChangeBean bean = new GlobalPropertyChangeBean(13);

        ReadOnlyJavaBeanIntegerProperty property = ReadOnlyJavaBeanIntegerPropertyBuilder.create()
                .bean(bean)
                .name("score")
                .build();
        List<Object> invalidatedObservables = observeInvalidations(property);

        assertThat(bean.getScore()).isEqualTo(13);
        assertThat(bean.propertyChangeListeners).hasSize(1);

        bean.setScore(17);
        assertThat(bean.getScore()).isEqualTo(17);
        assertThat(invalidatedObservables).containsExactly(property);

        property.dispose();
        assertThat(bean.removedListeners).hasSize(1);
        assertThat(bean.propertyChangeListeners).isEmpty();
    }

    private static List<Object> observeInvalidations(ReadOnlyJavaBeanIntegerProperty property) {
        List<Object> invalidatedObservables = new ArrayList<>();
        property.addListener(invalidatedObservables::add);
        return invalidatedObservables;
    }

    public static final class SpecificPropertyChangeBean {
        private final List<PropertyChangeListener> scoreListeners = new ArrayList<>();
        private int score;

        public SpecificPropertyChangeBean(int score) {
            this.score = score;
        }

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            int oldScore = this.score;
            this.score = score;
            PropertyChangeEvent event = new PropertyChangeEvent(this, "score", oldScore, score);
            for (PropertyChangeListener listener : List.copyOf(scoreListeners)) {
                listener.propertyChange(event);
            }
        }

        public void addScoreListener(PropertyChangeListener listener) {
            scoreListeners.add(listener);
        }

        public void removeScoreListener(PropertyChangeListener listener) {
            scoreListeners.remove(listener);
        }
    }

    public static final class NamedPropertyChangeBean {
        private final List<String> addedPropertyNames = new ArrayList<>();
        private final List<String> removedPropertyNames = new ArrayList<>();
        private final List<PropertyChangeListener> propertyChangeListeners = new ArrayList<>();
        private int score;

        public NamedPropertyChangeBean(int score) {
            this.score = score;
        }

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            int oldScore = this.score;
            this.score = score;
            PropertyChangeEvent event = new PropertyChangeEvent(this, "score", oldScore, score);
            for (PropertyChangeListener listener : List.copyOf(propertyChangeListeners)) {
                listener.propertyChange(event);
            }
        }

        public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
            addedPropertyNames.add(propertyName);
            propertyChangeListeners.add(listener);
        }

        public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
            removedPropertyNames.add(propertyName);
            propertyChangeListeners.remove(listener);
        }
    }

    public static final class GlobalPropertyChangeBean {
        private final PropertyChangeSupport changeSupport = new PropertyChangeSupport(this);
        private final List<PropertyChangeListener> propertyChangeListeners = new ArrayList<>();
        private final List<PropertyChangeListener> removedListeners = new ArrayList<>();
        private int score;

        public GlobalPropertyChangeBean(int score) {
            this.score = score;
        }

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            int oldScore = this.score;
            this.score = score;
            changeSupport.firePropertyChange("score", oldScore, score);
        }

        public void addPropertyChangeListener(PropertyChangeListener listener) {
            propertyChangeListeners.add(listener);
            changeSupport.addPropertyChangeListener(listener);
        }

        public void removePropertyChangeListener(PropertyChangeListener listener) {
            removedListeners.add(listener);
            propertyChangeListeners.remove(listener);
            changeSupport.removePropertyChangeListener(listener);
        }
    }
}
