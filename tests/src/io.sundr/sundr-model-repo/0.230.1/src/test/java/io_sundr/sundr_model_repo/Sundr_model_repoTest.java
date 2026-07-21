/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_model_repo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.sundr.model.AttributeKey;
import io.sundr.model.Block;
import io.sundr.model.BlockBuilder;
import io.sundr.model.Method;
import io.sundr.model.MethodBuilder;
import io.sundr.model.MethodCall;
import io.sundr.model.MethodCallBuilder;
import io.sundr.model.TypeDef;
import io.sundr.model.TypeDefBuilder;
import io.sundr.model.repo.DefinitionRepository;
import io.sundr.model.repo.MethodCallCollector;
import io.sundr.model.repo.MethodReference;
import org.junit.jupiter.api.Test;

public class Sundr_model_repoTest {

    @Test
    void storesFiltersAndLazilyResolvesDefinitions() {
        DefinitionRepository repository = DefinitionRepository.createRepository();
        TypeDef included = type("example", "Included");
        TypeDef excluded = type("example", "Excluded");
        AttributeKey<Boolean> generated = new AttributeKey<>("generated", Boolean.class);
        AttributeKey<Boolean> other = new AttributeKey<>("other", Boolean.class);
        AtomicInteger supplierCalls = new AtomicInteger();

        assertThat(repository.register(included, generated)).isNotSameAs(included);
        repository.register(excluded, other);
        repository.registerIfAbsent("example.Lazy", () -> {
            supplierCalls.incrementAndGet();
            return type("example", "Lazy");
        });
        repository.registerIfAbsent("example.Lazy", () -> type("example", "Replacement"));

        assertThat(repository.hasDefinition("example.Lazy")).isTrue();
        assertThat(supplierCalls).hasValue(0);
        assertThat(repository.getDefinitions(generated)).extracting(TypeDef::getName)
                .containsExactly("Included");
        assertThat(repository.getDefinitions(other)).extracting(TypeDef::getName)
                .containsExactly("Excluded");

        TypeDef lazy = repository.getDefinition("example.Lazy");
        assertThat(lazy.getName()).isEqualTo("Lazy");
        assertThat(repository.getDefinition("example.Lazy", false)).isSameAs(lazy);
        assertThat(supplierCalls).hasValue(1);
        assertThat(repository.getDefinition(lazy.toReference())).isSameAs(lazy);
        assertThat(repository.getDefinitions()).extracting(TypeDef::getName)
                .containsExactlyInAnyOrder("Included", "Excluded", "Lazy");

        repository.clear();
        assertThat(repository.getDefinitions()).isEmpty();
        assertThat(repository.hasDefinition("example.Lazy")).isFalse();
    }

    @Test
    void createsAnIsolatedRepositoryForCallableWork() throws Exception {
        DefinitionRepository globalRepository = DefinitionRepository.getRepository();
        TypeDef scopedType = type("scoped", "OnlyHere");

        TypeDef resolved = DefinitionRepository.withNewRepository().call(() -> {
            DefinitionRepository scopedRepository = DefinitionRepository.getRepository();
            scopedRepository.register(scopedType);
            return scopedRepository.getDefinition("scoped.OnlyHere");
        });

        assertThat(resolved).isSameAs(scopedType);
        assertThat(DefinitionRepository.getRepository()).isSameAs(globalRepository);
        assertThat(globalRepository.hasDefinition("scoped.OnlyHere")).isFalse();
    }

    @Test
    void preservesTheFirstDirectlyRegisteredDefinition() {
        DefinitionRepository repository = DefinitionRepository.createRepository();
        TypeDef original = type("stable", "Definition");
        TypeDef replacement = type("stable", "Definition", method("replacement", null));

        repository.registerIfAbsent(original);
        repository.registerIfAbsent(replacement);

        assertThat(repository.getDefinition("stable.Definition")).isSameAs(original);
        assertThat(repository.getDefinitions()).containsExactly(original);
    }

    @Test
    void createsReferenceMapAndTemporarilyScopesRepository() {
        DefinitionRepository repository = DefinitionRepository.createRepository();
        repository.register(type("one", "Duplicate"));
        repository.register(type("two", "Duplicate"));
        repository.register(type("three", "Unique"));

        Map<String, String> references = repository.getReferenceMap();
        assertThat(references).containsEntry("Duplicate", "one.Duplicate")
                .containsEntry("Unique", "three.Unique");

        repository.register(type("four", "Later"));
        assertThat(repository.getReferenceMap()).doesNotContainKey("Later");
        repository.updateReferenceMap();
        assertThat(repository.getReferenceMap()).containsEntry("Later", "four.Later");

        DefinitionRepository original = DefinitionRepository.getRepository();
        DefinitionRepository scoped = DefinitionRepository.withRepository(repository)
                .apply(ignored -> DefinitionRepository.getRepository());
        assertThat(scoped).isSameAs(repository);
        assertThat(DefinitionRepository.getRepository()).isSameAs(original);
    }

    @Test
    void collectsCallsAndResolvesDirectAndTransitiveMethodRelationships() {
        Method leaf = method("leaf", null);
        TypeDef leafType = type("graph", "Leaf", leaf);
        Method middle = method("middle", call("leaf", leafType));
        TypeDef middleType = type("graph", "Middle", middle);
        Method entry = method("entry", call("middle", middleType));
        TypeDef entryType = type("graph", "Entry", entry);
        DefinitionRepository repository = DefinitionRepository.createRepository();
        repository.register(leafType);
        repository.register(middleType);
        repository.register(entryType);
        MethodReference leafReference = new MethodReference(leaf, leafType);
        MethodReference middleReference = new MethodReference(middle, middleType);

        MethodCallCollector collector = new MethodCallCollector();
        MethodCall middleCall = call("middle", middleType);
        collector.visit(new MethodCallBuilder(middleCall));
        collector.visit(new MethodCallBuilder(middleCall));
        assertThat(collector.getMethodCalls()).extracting(MethodCall::getName)
                .containsExactly("middle", "middle");
        collector.clear();
        assertThat(collector.getMethodCalls()).isEmpty();

        assertThat(MethodReference.getDirectMethodReferences(entry, repository))
                .containsExactly(middleReference);
        assertThat(MethodReference.getMethodReferences(entry, repository))
                .containsExactlyInAnyOrder(middleReference, leafReference);
        assertThat(MethodReference.getDirectMethodCallers(leafReference, repository))
                .containsExactly(middleReference);
        assertThat(MethodReference.getMethodCallers(leafReference, repository))
                .containsExactlyInAnyOrder(middleReference, new MethodReference(entry, entryType));
        MethodReference.clearCache();
    }

    private static TypeDef type(String packageName, String name, Method... methods) {
        return new TypeDefBuilder()
                .withPackageName(packageName)
                .withName(name)
                .withMethods(methods)
                .build();
    }

    private static Method method(String name, MethodCall call) {
        MethodBuilder builder = new MethodBuilder().withName(name);
        if (call != null) {
            Block block = new BlockBuilder().withStatements(call).build();
            builder.withBlock(block);
        }
        return builder.build();
    }

    private static MethodCall call(String name, TypeDef scope) {
        return new MethodCall(name, scope.toReference());
    }
}
