/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus_gizmo.gizmo2;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.constant.ClassDesc;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import io.quarkus.gizmo2.testing.TestClassMaker;

public class TestClassMakerTest {
    private static final String GENERATED_CLASS_NAME = "io_quarkus_gizmo.gizmo2.generated.TestClassMakerTarget";

    @Test
    void createsLoadsAndInvokesGeneratedClass() {
        try {
            exerciseTestClassMaker();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static void exerciseTestClassMaker() {
        TestClassMaker maker = TestClassMaker.create();
        ClassDesc generatedClassDesc = maker.gizmo().class_(GENERATED_CLASS_NAME, classCreator -> {
            classCreator.defaultConstructor();
            classCreator.staticMethod("staticGreeting", methodCreator -> {
                methodCreator.returning(String.class);
                methodCreator.body(block -> block.return_("hello from static"));
            });
            classCreator.method("virtualGreeting", methodCreator -> {
                methodCreator.returning(String.class);
                methodCreator.body(block -> block.return_("hello from virtual"));
            });
        });

        Class<?> generatedClass = maker.loadClass(generatedClassDesc);
        maker.initializeClass(generatedClass);

        StaticStringSupplier staticGreeting = maker.staticMethod(generatedClass, "staticGreeting", StaticStringSupplier.class);
        assertThat(staticGreeting.get()).isEqualTo("hello from static");

        Object instanceFromConstructorProxy = maker.constructor(generatedClass, ObjectFactory.class).create();
        assertThat(instanceFromConstructorProxy).isInstanceOf(generatedClass);

        Object instanceFromNewInstance = maker.newInstance(generatedClass);
        assertThat(instanceFromNewInstance).isInstanceOf(generatedClass);

        InstanceStringFunction virtualGreeting = maker.virtualMethod(
                generatedClass, "virtualGreeting", InstanceStringFunction.class);
        assertThat(virtualGreeting.apply(instanceFromConstructorProxy)).isEqualTo("hello from virtual");
        assertThat(virtualGreeting.apply(instanceFromNewInstance)).isEqualTo("hello from virtual");
    }

    public interface StaticStringSupplier {
        String get();
    }

    public interface ObjectFactory {
        Object create();
    }

    public interface InstanceStringFunction {
        String apply(Object instance);
    }
}
