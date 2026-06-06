/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_builder.helidon_builder_api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.BuilderSupport;
import io.helidon.builder.api.Description;
import io.helidon.builder.api.GeneratedBuilder;
import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.builder.api.RuntimeType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Helidon_builder_apiTest {
    @Test
    void optionalCharArrayEqualityUsesArrayContents() {
        Optional<char[]> first = Optional.of(new char[] {'s', 'e', 'c', 'r', 'e', 't'});
        Optional<char[]> sameContent = Optional.of(new char[] {'s', 'e', 'c', 'r', 'e', 't'});
        Optional<char[]> differentContent = Optional.of(new char[] {'p', 'u', 'b', 'l', 'i', 'c'});

        assertThat(GeneratedBuilder.EqualityUtil.optionalCharArrayEquals(Optional.empty(), Optional.empty()))
                .isTrue();
        assertThat(GeneratedBuilder.EqualityUtil.optionalCharArrayEquals(first, sameContent))
                .isTrue();
        assertThat(GeneratedBuilder.EqualityUtil.optionalCharArrayEquals(first, differentContent))
                .isFalse();
        assertThat(GeneratedBuilder.EqualityUtil.optionalCharArrayEquals(first, Optional.empty()))
                .isFalse();
        assertThat(GeneratedBuilder.EqualityUtil.optionalCharArrayEquals(Optional.empty(), first))
                .isFalse();
    }

    @Test
    void optionalCharArrayHashUsesArrayContentsWhenPresent() {
        char[] value = new char[] {'a', 'b', 'c'};
        Optional<char[]> optionalValue = Optional.of(value);

        assertThat(GeneratedBuilder.EqualityUtil.optionalCharArrayHash(optionalValue))
                .isEqualTo(Arrays.hashCode(value));
        assertThat(GeneratedBuilder.EqualityUtil.optionalCharArrayHash(Optional.empty()))
                .isEqualTo(Optional.empty().hashCode());
    }

    @Test
    void builderSupportKeepsExplicitServiceWhenDiscoveryIsDisabledOrEnabled() {
        ServiceContract provided = new FirstService("provided");
        Optional<ServiceContract> providedService = Optional.of(provided);

        assertThat(BuilderSupport.discoverService(ServiceContract.class, false, providedService))
                .contains(provided);
        assertThat(BuilderSupport.discoverService(ServiceContract.class, true, providedService))
                .contains(provided);
        assertThat(BuilderSupport.discoverServices(ServiceContract.class, false, List.of(provided)))
                .isEmpty();
    }

    @Test
    void builderSupportHandlesDiscoveryWhenNoProvidersAreRegistered() {
        assertThat(BuilderSupport.discoverService(ServiceContract.class, false, Optional.empty()))
                .isEmpty();
        assertThat(BuilderSupport.discoverService(ServiceContract.class, true, Optional.empty()))
                .isEmpty();
        assertThat(BuilderSupport.discoverServices(ServiceContract.class, true, List.of(new FirstService("known"))))
                .isEmpty();
    }

    @Test
    void prototypeBuilderFactoryAndRuntimeTypeContractsCanBeImplemented() {
        SimplePrototypeBuilder builder = new SimplePrototypeBuilder().value("configured");
        Prototype.Factory<SimplePrototype> factory = () -> new SimplePrototype("factory");
        RuntimeType.Api<SimplePrototype> runtimeType = new SimpleRuntimeType(builder.buildPrototype());

        assertThat(builder.self()).isSameAs(builder);
        assertThat(builder.buildPrototype()).isEqualTo(new SimplePrototype("configured"));
        assertThat(factory.build()).isEqualTo(new SimplePrototype("factory"));
        assertThat(runtimeType.prototype()).isEqualTo(new SimplePrototype("configured"));
    }

    @Test
    void builderAndOptionDecoratorsCanCustomizeMutableTargets() {
        List<String> builderTarget = new ArrayList<>();
        RecordingBuilderDecorator builderDecorator = new RecordingBuilderDecorator("builder");
        RecordingOptionDecorator optionDecorator = new RecordingOptionDecorator();

        builderDecorator.decorate(builderTarget);
        optionDecorator.decorate(builderTarget, "direct");
        optionDecorator.decorateSetList(builderTarget, List.of("set-list"));
        optionDecorator.decorateAddList(builderTarget, List.of("add-list"));
        optionDecorator.decorateSetSet(builderTarget, Set.of("set-set"));
        optionDecorator.decorateAddSet(builderTarget, Set.of("add-set"));

        assertThat(builderTarget).containsExactly("builder", "direct");
    }

    @Test
    void annotatedBlueprintUsesBuilderApiAnnotationsAndProducesValues() {
        AnnotatedBlueprint blueprint = new AnnotatedBlueprintImplementation();
        RuntimeComponent runtimeComponent = new RuntimeComponent(new SimplePrototype(blueprint.name()));

        assertThat(blueprint.name()).isEqualTo("alpha");
        assertThat(blueprint.tags()).containsExactly("one", "two");
        assertThat(blueprint.secret()).containsExactly('s', '3', 'c', 'r', '3', 't');
        assertThat(blueprint.service().name()).isEqualTo("service");
        assertThat(new BlueprintCustomMethods().factoryMethod()).isEqualTo(new SimplePrototype("factory-method"));
        assertThat(runtimeComponent.prototype()).isEqualTo(new SimplePrototype("alpha"));
    }

    @Test
    void optionDecoratorCollectionHooksCanCustomizeListAndSetOperations() {
        List<String> target = new ArrayList<>();
        CollectionRecordingOptionDecorator decorator = new CollectionRecordingOptionDecorator();

        decorator.decorateSetList(target, List.of("red", "blue"));
        decorator.decorateAddList(target, List.of("green"));
        decorator.decorateSetSet(target, Set.of("cyan"));
        decorator.decorateAddSet(target, Set.of("magenta"));

        assertThat(target)
                .containsExactly(
                        "set-list:red,blue",
                        "add-list:green",
                        "set-set:cyan",
                        "add-set:magenta");
    }

    public interface ServiceContract {
        String name();
    }

    private record FirstService(String name) implements ServiceContract {
    }

    private record SimplePrototype(String value) implements Prototype.Api {
    }

    private static final class SimpleRuntimeType implements RuntimeType.Api<SimplePrototype> {
        private final SimplePrototype prototype;

        private SimpleRuntimeType(SimplePrototype prototype) {
            this.prototype = prototype;
        }

        @Override
        public SimplePrototype prototype() {
            return prototype;
        }
    }

    @RuntimeType.PrototypedBy(SimplePrototype.class)
    private static final class RuntimeComponent implements RuntimeType.Api<SimplePrototype> {
        private final SimplePrototype prototype;

        private RuntimeComponent(SimplePrototype prototype) {
            this.prototype = prototype;
        }

        @Override
        public SimplePrototype prototype() {
            return prototype;
        }
    }

    private static final class SimplePrototypeBuilder
            implements Prototype.Builder<SimplePrototypeBuilder, SimplePrototype> {
        private String value;

        private SimplePrototypeBuilder value(String value) {
            this.value = value;
            return this;
        }

        @Override
        public SimplePrototype buildPrototype() {
            return new SimplePrototype(value);
        }
    }

    private static final class RecordingBuilderDecorator implements Prototype.BuilderDecorator<List<String>> {
        private final String value;

        private RecordingBuilderDecorator(String value) {
            this.value = value;
        }

        @Override
        public void decorate(List<String> target) {
            target.add(value);
        }
    }

    private static final class RecordingOptionDecorator implements Prototype.OptionDecorator<List<String>, String> {
        @Override
        public void decorate(List<String> target, String value) {
            target.add(value);
        }
    }

    private static final class CollectionRecordingOptionDecorator
            implements Prototype.OptionDecorator<List<String>, String> {
        @Override
        public void decorate(List<String> target, String value) {
            target.add("value:" + value);
        }

        @Override
        public void decorateSetList(List<String> target, List<String> values) {
            target.add("set-list:" + String.join(",", values));
        }

        @Override
        public void decorateAddList(List<String> target, List<String> values) {
            target.add("add-list:" + String.join(",", values));
        }

        @Override
        public void decorateSetSet(List<String> target, Set<String> values) {
            target.add("set-set:" + String.join(",", values));
        }

        @Override
        public void decorateAddSet(List<String> target, Set<String> values) {
            target.add("add-set:" + String.join(",", values));
        }
    }

    @Description("Blueprint exercising Helidon builder annotations")
    @Prototype.Annotated("jakarta.inject.Singleton")
    @Prototype.Blueprint(
            isPublic = true,
            builderPublic = true,
            createFromConfigPublic = false,
            createEmptyPublic = false,
            beanStyle = true,
            decorator = AnnotatedBuilderDecorator.class)
    @Prototype.Configured(value = "test.blueprint", root = false)
    @Prototype.CustomMethods(BlueprintCustomMethods.class)
    @Prototype.Implement({"java.lang.AutoCloseable"})
    @Prototype.IncludeDefaultMethods({"defaultGreeting"})
    @Prototype.Provides({ServiceContract.class})
    @Prototype.RegistrySupport(false)
    interface AnnotatedBlueprint extends Prototype.Api {
        @Description("Primary name")
        @Option.Access("public")
        @Option.AllowedValue(value = "alpha", description = "First value")
        @Option.AllowedValue(value = "beta", description = "Second value")
        @Option.Configured(value = "name", merge = true)
        @Option.Default("alpha")
        @Option.Required
        String name();

        @Option.DefaultInt({1, 2, 3})
        int[] numbers();

        @Option.DefaultLong({4L, 5L})
        long[] sizes();

        @Option.DefaultDouble({1.5D, 2.5D})
        double[] ratios();

        @Option.DefaultBoolean({true, false})
        boolean[] flags();

        @Option.DefaultCode("java.time.Duration.ofSeconds(5)")
        @Option.DefaultMethod(type = Defaults.class, value = "codeDefault")
        String generatedCode();

        @Option.Decorator(AnnotatedOptionDecorator.class)
        @Option.Redundant(equality = false, stringValue = false)
        @Option.Singular(value = "tag", withPrefix = false)
        List<String> tags();

        @Option.Provider(value = FirstService.class, discoverServices = false)
        @Option.RegistryService
        ServiceContract service();

        @Option.Confidential
        @Option.Deprecated("Use replacementSecret")
        char[] secret();

        @Option.SameGeneric
        @Option.TraverseConfig(false)
        @Option.Type("java.util.List<java.lang.String>")
        List<String> genericValues();

        @Prototype.PrototypeMethod
        default String defaultGreeting() {
            return "hello " + name();
        }
    }

    private static final class AnnotatedBlueprintImplementation implements AnnotatedBlueprint {
        @Override
        public String name() {
            return "alpha";
        }

        @Override
        public int[] numbers() {
            return new int[] {1, 2, 3};
        }

        @Override
        public long[] sizes() {
            return new long[] {4L, 5L};
        }

        @Override
        public double[] ratios() {
            return new double[] {1.5D, 2.5D};
        }

        @Override
        public boolean[] flags() {
            return new boolean[] {true, false};
        }

        @Override
        public String generatedCode() {
            return Defaults.codeDefault();
        }

        @Override
        public List<String> tags() {
            return List.of("one", "two");
        }

        @Override
        public ServiceContract service() {
            return new FirstService("service");
        }

        @Override
        public char[] secret() {
            return new char[] {'s', '3', 'c', 'r', '3', 't'};
        }

        @Override
        public List<String> genericValues() {
            return List.of("generic");
        }
    }

    private static final class Defaults {
        private Defaults() {
        }

        private static String codeDefault() {
            return "java.time.Duration.ofSeconds(5)";
        }
    }

    private static final class AnnotatedBuilderDecorator implements Prototype.BuilderDecorator<Object> {
        @Override
        public void decorate(Object target) {
        }
    }

    private static final class AnnotatedOptionDecorator implements Prototype.OptionDecorator<Object, String> {
        @Override
        public void decorate(Object builder, String value) {
        }
    }

    private static final class BlueprintCustomMethods {
        @Prototype.Constant
        private static final String CONSTANT = "constant";

        @Prototype.FactoryMethod
        private SimplePrototype factoryMethod() {
            return new SimplePrototype("factory-method");
        }

        @Prototype.BuilderMethod
        private String builderMethod() {
            return CONSTANT;
        }
    }
}
