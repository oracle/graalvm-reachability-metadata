/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_inject;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Iterator;

import javax.inject.Qualifier;

import org.eclipse.sisu.BeanEntry;
import org.eclipse.sisu.inject.DefaultBeanLocator;
import org.eclipse.sisu.inject.MutableBeanLocator;
import org.junit.jupiter.api.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;

public class QualifyingStrategyAnonymous4Test {
    @Test
    void locateUpgradesMarkerOnlyBindingToQualifierInstance() {
        MarkerBoundService service = new MarkerBoundService();
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(MarkerBoundService.class).annotatedWith(Marker.class).toInstance(service);
            }
        });
        MutableBeanLocator locator = new DefaultBeanLocator();

        locator.add(injector, 0);

        Iterable<? extends BeanEntry<Annotation, MarkerBoundService>> entries = locator.locate(
            Key.get(MarkerBoundService.class, Marker.class));
        Iterator<? extends BeanEntry<Annotation, MarkerBoundService>> iterator = entries.iterator();

        assertThat(iterator.hasNext()).isTrue();
        BeanEntry<Annotation, MarkerBoundService> entry = iterator.next();
        assertThat(entry.getKey().annotationType()).isEqualTo(Marker.class);
        assertThat(entry.getValue()).isSameAs(service);
        assertThat(iterator.hasNext()).isFalse();
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    private @interface Marker {
    }

    private static final class MarkerBoundService {
    }
}
