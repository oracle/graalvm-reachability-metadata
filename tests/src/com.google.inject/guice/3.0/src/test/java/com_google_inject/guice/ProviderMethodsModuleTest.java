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
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProviderMethodsModuleTest {
    @Test
    void discoversInheritedGenericAndQualifiedProviderMethods() {
        Injector injector = Guice.createInjector(new ChildModule());
        assertEquals("inherited", injector.getInstance(String.class));
        assertEquals(List.of("inherited"), injector.getInstance(Key.get(new TypeLiteral<List<String>>() { })));
        assertEquals("child", injector.getInstance(Key.get(String.class, Names.named("label"))));
    }

    static class BaseModule<T> extends AbstractModule {
        private final T value;

        BaseModule(T value) {
            this.value = value;
        }

        @Override
        protected void configure() {
        }

        @Provides
        private T provideValue() {
            return value;
        }

        @Provides
        protected List<T> provideValues(T item) {
            return List.of(item);
        }
    }

    static final class ChildModule extends BaseModule<String> {
        ChildModule() {
            super("inherited");
        }

        @Provides
        @Named("label")
        private String provideLabel() {
            return "child";
        }
    }
}
