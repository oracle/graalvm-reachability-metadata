/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_inject.guice;

import com.google.inject.AbstractModule;
import com.google.inject.BindingAnnotation;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnnotationsTest {
    @Test
    void matchesMarkerAnnotationInstancesToAnnotationTypeBindings() {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(String.class).annotatedWith(Primary.class).toInstance("primary");
                bind(String.class).annotatedWith(Secondary.class).toInstance("secondary");
            }
        });
        Client client = new Client();
        injector.injectMembers(client);
        assertEquals("primary", client.primary);
        assertEquals("secondary", client.secondary);
        assertEquals("primary", injector.getInstance(Key.get(String.class, Primary.class)));
    }

    @BindingAnnotation
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Primary {
    }

    @BindingAnnotation
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Secondary {
    }

    static final class Client {
        @Inject @Primary private String primary;
        @Inject @Secondary private String secondary;
    }
}
