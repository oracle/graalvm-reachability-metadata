/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.slf4j_nop;

import org.junit.jupiter.api.Test;
import org.slf4j.IMarkerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.impl.StaticMarkerBinder;

import static org.assertj.core.api.Assertions.assertThat;

public class StaticMarkerBinderTest {

    @Test
    void staticMarkerBinderProvidesTheBasicMarkerFactoryUsedBySlf4j() {
        StaticMarkerBinder binder = StaticMarkerBinder.SINGLETON;
        IMarkerFactory binderFactory = binder.getMarkerFactory();
        IMarkerFactory apiFactory = MarkerFactory.getIMarkerFactory();
        Marker binderMarker = binderFactory.getMarker("binder-marker");
        Marker apiMarker = MarkerFactory.getMarker("binder-marker");

        assertThat(binderFactory).isInstanceOf(BasicMarkerFactory.class);
        assertThat(apiFactory).isSameAs(binderFactory);
        assertThat(binder.getMarkerFactoryClassStr()).isEqualTo(BasicMarkerFactory.class.getName());
        assertThat(apiMarker).isSameAs(binderMarker);
        assertThat(StaticMarkerBinder.SINGLETON).isSameAs(binder);
    }
}
