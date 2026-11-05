/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_inject.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TypeConverterBindingProcessorTest {
    @Test
    void installsBuiltInConvertersForNamedConstants() {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bindConstant().annotatedWith(Names.named("port")).to("8123");
                bindConstant().annotatedWith(Names.named("enabled")).to("true");
            }
        });
        assertEquals(Integer.valueOf(8123), injector.getInstance(Key.get(Integer.class, Names.named("port"))));
        assertEquals(Boolean.TRUE, injector.getInstance(Key.get(Boolean.class, Names.named("enabled"))));
    }
}
