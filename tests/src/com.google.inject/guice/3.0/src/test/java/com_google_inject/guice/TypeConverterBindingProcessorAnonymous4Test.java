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
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

public class TypeConverterBindingProcessorAnonymous4Test {
    @Test
    void convertsAClassNameConstantToAClassBinding() {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bindConstant().annotatedWith(Names.named("implementation")).to("java.lang.StringBuilder");
            }
        });
        Key<Class<?>> key = Key.get(new TypeLiteral<Class<?>>() { }, Names.named("implementation"));
        assertSame(StringBuilder.class, injector.getInstance(key));
        assertSame(StringBuilder.class, injector.getProvider(key).get());
    }
}
