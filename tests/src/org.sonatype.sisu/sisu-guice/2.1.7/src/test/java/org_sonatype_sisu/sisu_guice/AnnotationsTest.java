/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_guice;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.BindingAnnotation;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provides;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.jupiter.api.Test;

public class AnnotationsTest {
    @Test
    void acceptsMarkerBindingAnnotationsOnProviderMethodsAndInjectionPoints() {
        Injector injector = Guice.createInjector(new MarkerModule());

        MarkerConsumer consumer = injector.getInstance(MarkerConsumer.class);

        assertThat(consumer.value).isEqualTo("marker-value");
    }

    static class MarkerModule extends AbstractModule {
        @Override
        protected void configure() {
        }

        @Provides
        @MarkerBinding
        String markerValue() {
            return "marker-value";
        }
    }

    static class MarkerConsumer {
        @Inject
        @MarkerBinding
        String value;
    }

    @BindingAnnotation
    @Retention(RetentionPolicy.RUNTIME)
    @interface MarkerBinding {
    }
}
