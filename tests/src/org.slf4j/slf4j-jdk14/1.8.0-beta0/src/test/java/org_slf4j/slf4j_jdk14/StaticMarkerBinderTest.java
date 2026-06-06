/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.slf4j_jdk14;

import org.junit.jupiter.api.Test;
import org.slf4j.IMarkerFactory;
import org.slf4j.Marker;
import org.slf4j.impl.StaticMarkerBinder;

import static org.assertj.core.api.Assertions.assertThat;

public class StaticMarkerBinderTest {

    @Test
    void singletonExposesBasicMarkerFactoryInformation() {
        StaticMarkerBinder binder = StaticMarkerBinder.SINGLETON;

        IMarkerFactory markerFactory = binder.getMarkerFactory();
        Marker marker = markerFactory.getMarker("coverage-marker");
        String markerFactoryClassName = binder.getMarkerFactoryClassStr();

        assertThat(markerFactory).isNotNull();
        assertThat(markerFactoryClassName).isEqualTo(markerFactory.getClass().getName());
        assertThat(marker.getName()).isEqualTo("coverage-marker");
        assertThat(markerFactory.getMarker("coverage-marker")).isSameAs(marker);
    }
}
