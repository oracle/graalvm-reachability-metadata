/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_adapter_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.sundr.adapter.api.Adapter;
import io.sundr.adapter.api.AdapterContext;
import io.sundr.adapter.api.AdapterFactory;
import io.sundr.adapter.api.Adapters;
import io.sundr.adapter.api.TypeLookup;
import io.sundr.model.AttributeKey;
import io.sundr.model.ClassRef;
import io.sundr.model.Kind;
import io.sundr.model.Method;
import io.sundr.model.PrimitiveRef;
import io.sundr.model.Property;
import io.sundr.model.TypeDef;
import io.sundr.model.TypeDefBuilder;
import io.sundr.model.TypeRef;
import io.sundr.model.repo.DefinitionRepository;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

public class Sundr_adapter_apiTest {
    @Test
    void adapterContextStoresRepositoryAttributesAndGlobalContext() {
        DefinitionRepository repository = DefinitionRepository.createRepository();
        AttributeKey<String> sourceAttribute = new AttributeKey<>("source", String.class);
        Map<AttributeKey, Object> attributes = new HashMap<>();
        attributes.put(sourceAttribute, "test-suite");

        AdapterContext context = AdapterContext.create(repository, attributes);

        assertThat(context.getDefinitionRepository()).isSameAs(repository);
        assertThat(context.getAdapterContext()).isSameAs(context);
        assertThat(context.hasAttribute(sourceAttribute)).isTrue();
        assertThat(context.getAttribute(sourceAttribute)).isEqualTo("test-suite");
        assertThat(AdapterContext.getContext()).isSameAs(context);
    }

    @Test
    void adapterContextCreateReplacesGlobalContext() {
        DefinitionRepository firstRepository = DefinitionRepository.createRepository();
        AdapterContext firstContext = AdapterContext.create(firstRepository);
        DefinitionRepository secondRepository = DefinitionRepository.createRepository();

        AdapterContext secondContext = AdapterContext.create(secondRepository);

        assertThat(secondContext).isNotSameAs(firstContext);
        assertThat(secondContext.getDefinitionRepository()).isSameAs(secondRepository);
        assertThat(AdapterContext.getContext()).isSameAs(secondContext);
    }

    @Test
    void adapterDefaultMethodsDelegateToConfiguredFunctions() {
        TestAdapter adapter = new TestAdapter();

        TypeDef adaptedType = adapter.adaptType(new SourceType("Widget"));
        TypeRef adaptedReference = adapter.adaptReference(new SourceReference("example.model.Widget"));
        Property adaptedProperty = adapter.adaptProperty(new SourceProperty("enabled"));
        Method adaptedMethod = adapter.adaptMethod(new SourceMethod("count"));

        assertThat(adaptedType.getKind()).isEqualTo(Kind.CLASS);
        assertThat(adaptedType.getFullyQualifiedName()).isEqualTo("example.model.Widget");
        assertThat(adaptedReference).isInstanceOf(ClassRef.class);
        assertThat(((ClassRef) adaptedReference).getFullyQualifiedName()).isEqualTo("example.model.Widget");
        assertThat(adaptedProperty.getName()).isEqualTo("enabled");
        assertThat(adaptedProperty.getTypeRef()).isEqualTo(booleanRef());
        assertThat(adaptedMethod.getName()).isEqualTo("count");
        assertThat(adaptedMethod.getReturnType()).isEqualTo(intRef());
    }

    @Test
    void adapterFactoryDescribesSupportedSourceTypesAndCreatesAdaptersWithContext() {
        DefinitionRepository repository = DefinitionRepository.createRepository();
        AdapterContext context = AdapterContext.create(repository);
        TestAdapterFactory factory = new TestAdapterFactory();

        Adapter<SourceType, SourceReference, SourceProperty, SourceMethod> adapter = factory.create(context);

        assertThat(factory.getTypeAdapterType()).isSameAs(SourceType.class);
        assertThat(factory.getReferenceAdapterType()).isSameAs(SourceReference.class);
        assertThat(factory.getPropertyAdapterType()).isSameAs(SourceProperty.class);
        assertThat(factory.getMethodAdapterType()).isSameAs(SourceMethod.class);
        assertThat(factory.getContext()).isSameAs(context);
        assertThat(adapter.adaptType(new SourceType("FactoryProduct")).getName()).isEqualTo("FactoryProduct");
    }

    @Test
    void serviceBackedAdapterLookupReportsNoProviderWhenClasspathHasNoAdapterFactory() {
        AdapterContext context = AdapterContext.create(DefinitionRepository.createRepository());

        assertThat(Adapters.getAdapterForType(SourceType.class, context)).isEmpty();
        assertThat(Adapters.getAdapterForReference(SourceReference.class, context)).isEmpty();
        assertThat(Adapters.getAdapterForProperty(SourceProperty.class, context)).isEmpty();
        assertThat(Adapters.getAdapterForMethod(SourceMethod.class, context)).isEmpty();
        assertThat(TypeLookup.lookup("example.model.Widget", context)).isEmpty();
    }

    @Test
    void adaptersRejectNullInputsBeforeLookingUpProviders() {
        AdapterContext context = AdapterContext.create(DefinitionRepository.createRepository());
        Adapters.WithContext withContext = Adapters.withContext(context);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Adapters.adaptType(null, context))
                .withMessage("Adapter.adapt(null, ctx) is not allowed!");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> withContext.adaptReference(null))
                .withMessage("Adapter.adapt(null, ctx) is not allowed!");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> withContext.adaptProperty(null))
                .withMessage("Adapter.adapt(null, ctx) is not allowed!");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> withContext.adaptMethod(null))
                .withMessage("Adapter.adapt(null, ctx) is not allowed!");
    }

    @Test
    void adaptersFailFastWhenNoProviderCanAdaptInputType() {
        AdapterContext context = AdapterContext.create(DefinitionRepository.createRepository());
        Adapters.WithContext withContext = Adapters.withContext(context);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> withContext.adaptType(new SourceType("Missing")))
                .withMessageContaining(SourceType.class.getName());
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> withContext.adaptReference(new SourceReference("example.model.Missing")))
                .withMessageContaining(SourceReference.class.getName());
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> withContext.adaptProperty(new SourceProperty("missing")))
                .withMessageContaining(SourceProperty.class.getName());
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> withContext.adaptMethod(new SourceMethod("missing")))
                .withMessageContaining(SourceMethod.class.getName());
    }

    @Test
    void typeLookupDefaultAvailabilityIsTrue() {
        TypeLookup<SourceType> lookup = name -> Optional.of(new SourceType(name));

        assertThat(lookup.isAvailable()).isTrue();
        assertThat(lookup.forName("LookupTarget"))
                .hasValueSatisfying(source -> assertThat(source.name()).isEqualTo("LookupTarget"));
    }

    private static PrimitiveRef intRef() {
        return new PrimitiveRef("int", 0, Collections.emptyMap());
    }

    private static PrimitiveRef booleanRef() {
        return new PrimitiveRef("boolean", 0, Collections.emptyMap());
    }

    private static TypeDef typeDef(String simpleName) {
        return new TypeDefBuilder()
                .withKind(Kind.CLASS)
                .withPackageName("example.model")
                .withName(simpleName)
                .build();
    }

    private static final class TestAdapter
            implements Adapter<SourceType, SourceReference, SourceProperty, SourceMethod> {
        @Override
        public Function<SourceType, TypeDef> getTypeAdapterFunction() {
            return source -> typeDef(source.name());
        }

        @Override
        public Function<SourceReference, TypeRef> getReferenceAdapterFunction() {
            return source -> new ClassRef(
                    source.fullyQualifiedName(),
                    0,
                    Collections.emptyList(),
                    Collections.emptyMap());
        }

        @Override
        public Function<SourceMethod, Method> getMethodAdapterFunction() {
            return source -> Method.newMethod(source.name(), intRef());
        }

        @Override
        public Function<SourceProperty, Property> getPropertyAdapterFunction() {
            return source -> Property.newProperty(booleanRef(), source.name());
        }
    }

    private static final class TestAdapterFactory
            implements AdapterFactory<SourceType, SourceReference, SourceProperty, SourceMethod> {
        private AdapterContext context;

        @Override
        public Class<SourceType> getTypeAdapterType() {
            return SourceType.class;
        }

        @Override
        public Class<SourceReference> getReferenceAdapterType() {
            return SourceReference.class;
        }

        @Override
        public Class<SourceMethod> getMethodAdapterType() {
            return SourceMethod.class;
        }

        @Override
        public Class<SourceProperty> getPropertyAdapterType() {
            return SourceProperty.class;
        }

        @Override
        public Adapter<SourceType, SourceReference, SourceProperty, SourceMethod> create(
                AdapterContext adapterContext) {
            context = adapterContext;
            return new TestAdapter();
        }

        private AdapterContext getContext() {
            return context;
        }
    }

    private record SourceType(String name) {
    }

    private record SourceReference(String fullyQualifiedName) {
    }

    private record SourceProperty(String name) {
    }

    private record SourceMethod(String name) {
    }
}
