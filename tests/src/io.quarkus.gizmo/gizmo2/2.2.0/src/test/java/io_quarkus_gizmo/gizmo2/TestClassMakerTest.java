/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus_gizmo.gizmo2;

import static org.assertj.core.api.Assertions.assertThat;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import io.quarkus.gizmo2.testing.TestClassMaker;

public class TestClassMakerTest {
    private static final String GENERATED_CLASS_NAME = "io_quarkus_gizmo.gizmo2.generated.TestClassMakerTarget";

    @Test
    void loadsInitializesAndInvokesGeneratedClassMembers() {
        try {
            exerciseGeneratedClassMembers();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static void exerciseGeneratedClassMembers() {
        TestClassMaker maker = TestClassMaker.create();

        maker.gizmo().class_(GENERATED_CLASS_NAME, classCreator -> {
            classCreator.defaultConstructor();
            classCreator.staticMethod("staticGreeting", methodCreator -> {
                methodCreator.returning(String.class);
                methodCreator.body(block -> block.return_("static-result"));
            });
            classCreator.method("instanceGreeting", methodCreator -> {
                methodCreator.returning(String.class);
                methodCreator.body(block -> block.return_("instance-result"));
            });
        });

        Class<?> generatedClass = maker.loadClass(GENERATED_CLASS_NAME);
        maker.initializeClass(generatedClass);

        StringSupplier staticGreeting = maker.staticMethod(generatedClass, "staticGreeting", StringSupplier.class);
        assertThat(staticGreeting.get()).isEqualTo("static-result");

        ObjectFactory factory = maker.constructor(generatedClass, ObjectFactory.class);
        Object constructedByProxy = factory.create();
        assertThat(constructedByProxy).isInstanceOf(generatedClass);

        Object constructedDirectly = maker.newInstance(generatedClass);
        assertThat(constructedDirectly).isInstanceOf(generatedClass);

        InstanceStringFunction instanceGreeting = maker.virtualMethod(generatedClass, "instanceGreeting",
                InstanceStringFunction.class);
        assertThat(instanceGreeting.apply(constructedDirectly)).isEqualTo("instance-result");
    }

    public interface StringSupplier {
        String get();
    }

    public interface ObjectFactory {
        Object create();
    }

    public interface InstanceStringFunction {
        String apply(Object target);
    }
}
