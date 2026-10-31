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

public class TypeConverterBindingProcessorAnonymous5Test {
    @Test
    void convertsStringConstantsWithEveryPrimitiveParser() {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bindConstant().annotatedWith(Names.named("int")).to("42");
                bindConstant().annotatedWith(Names.named("long")).to("4294967296");
                bindConstant().annotatedWith(Names.named("boolean")).to("true");
                bindConstant().annotatedWith(Names.named("byte")).to("12");
                bindConstant().annotatedWith(Names.named("short")).to("32000");
                bindConstant().annotatedWith(Names.named("float")).to("1.25");
                bindConstant().annotatedWith(Names.named("double")).to("2.5");
            }
        });
        assertEquals(Integer.valueOf(42), injector.getInstance(Key.get(Integer.class, Names.named("int"))));
        assertEquals(Long.valueOf(4294967296L), injector.getInstance(Key.get(Long.class, Names.named("long"))));
        assertEquals(Boolean.TRUE, injector.getInstance(Key.get(Boolean.class, Names.named("boolean"))));
        assertEquals(Byte.valueOf((byte) 12), injector.getInstance(Key.get(Byte.class, Names.named("byte"))));
        assertEquals(Short.valueOf((short) 32000), injector.getInstance(Key.get(Short.class, Names.named("short"))));
        assertEquals(Float.valueOf(1.25F), injector.getInstance(Key.get(Float.class, Names.named("float"))));
        assertEquals(Double.valueOf(2.5), injector.getInstance(Key.get(Double.class, Names.named("double"))));
    }
}
