/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_inject_extensions.guice_assistedinject;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

public class FactoryProvider2InnerPrivateLookupTest {
    @Test
    void fallsBackToPrivateLookupForGeneratedBridgeDefaultMethod() {
        Injector injector = createBridgeWidgetInjector("-bridge");

        BridgeWidgetFactory factory = injector.getInstance(BridgeWidgetFactory.class);
        BridgeWidget directWidget = factory.create("direct");
        GenericBridgeFactory<BridgeWidget> genericFactory = factory;
        BridgeWidget genericWidget = genericFactory.create("generic");

        assertThat(directWidget.description()).isEqualTo("direct-bridge");
        assertThat(genericWidget.description()).isEqualTo("generic-bridge");
    }

    @Test
    void triesTwoArgumentPrivateLookupConstructorBeforeMethodSignatureWorkaround()
            throws ReflectiveOperationException {
        Unsafe unsafe = getUnsafe();
        Field constructorField = privateLookupConstructorField();
        Object originalConstructor = readStaticField(unsafe, constructorField);
        Constructor<TwoArgumentLookupFailure> twoArgumentConstructor =
                TwoArgumentLookupFailure.class.getDeclaredConstructor(Class.class, int.class);
        twoArgumentConstructor.setAccessible(true);

        try {
            writeStaticField(unsafe, constructorField, twoArgumentConstructor);

            Injector injector = createBridgeWidgetInjector("-two-argument");
            BridgeWidget widget = injector.getInstance(BridgeWidgetFactory.class).create("widget");

            assertThat(widget.description()).isEqualTo("widget-two-argument");
        } finally {
            writeStaticField(unsafe, constructorField, originalConstructor);
        }
    }

    private static Injector createBridgeWidgetInjector(String suffix) {
        return Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(BridgeSuffixService.class).toInstance(new BridgeSuffixService(suffix));
                install(new FactoryModuleBuilder()
                        .withLookups(MethodHandles.publicLookup())
                        .build(BridgeWidgetFactory.class));
            }
        });
    }

    private static Field privateLookupConstructorField() throws ReflectiveOperationException {
        Class<?> privateLookupClass =
                Class.forName("com.google.inject.assistedinject.FactoryProvider2$PrivateLookup");
        Field field = privateLookupClass.getDeclaredField("privateLookupCxtor");
        field.setAccessible(true);
        return field;
    }

    private static Unsafe getUnsafe() throws ReflectiveOperationException {
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        return (Unsafe) theUnsafe.get(null);
    }

    private static Object readStaticField(Unsafe unsafe, Field field) {
        return unsafe.getObject(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field));
    }

    private static void writeStaticField(Unsafe unsafe, Field field, Object value) {
        unsafe.putObject(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field), value);
    }

    static final class TwoArgumentLookupFailure {
        TwoArgumentLookupFailure(Class<?> declaringClass, int modes) throws NoSuchMethodException {
            throw new NoSuchMethodException(declaringClass.getName() + " with modes " + modes);
        }
    }

    private interface GenericBridgeFactory<T> {
        T create(@Assisted String name);
    }

    private interface BridgeWidgetFactory extends GenericBridgeFactory<BridgeWidget> {
        @Override
        BridgeWidget create(@Assisted String name);
    }

    static final class BridgeWidget {
        private final BridgeSuffixService suffixService;
        private final String name;

        @Inject
        BridgeWidget(BridgeSuffixService suffixService, @Assisted String name) {
            this.suffixService = suffixService;
            this.name = name;
        }

        String description() {
            return name + suffixService.value();
        }
    }

    static final class BridgeSuffixService {
        private final String value;

        BridgeSuffixService(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }
    }
}
