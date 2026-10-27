/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.slf4j_log4j12;

import org.junit.jupiter.api.Test;
import org.slf4j.IMarkerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.impl.StaticMarkerBinder;

import static org.assertj.core.api.Assertions.assertThat;

public class StaticMarkerBinderTest {

    @Test
    void singletonExposesTheBasicMarkerFactoryUsedByMarkerFactory() {
        StaticMarkerBinder binder = StaticMarkerBinder.SINGLETON;
        IMarkerFactory markerFactory = binder.getMarkerFactory();
        Marker marker = MarkerFactory.getMarker("coverage-marker");

        assertThat(markerFactory).isInstanceOf(BasicMarkerFactory.class);
        assertThat(binder.getMarkerFactoryClassStr()).isEqualTo(BasicMarkerFactory.class.getName());
        assertThat(MarkerFactory.getIMarkerFactory()).isSameAs(markerFactory);
        assertThat(markerFactory.getMarker("coverage-marker")).isSameAs(marker);
    }
}
