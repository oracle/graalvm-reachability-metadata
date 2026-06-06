/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_model_repo;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.model.AttributeKey;
import io.sundr.model.Block;
import io.sundr.model.ClassRef;
import io.sundr.model.Expression;
import io.sundr.model.Kind;
import io.sundr.model.Method;
import io.sundr.model.MethodBuilder;
import io.sundr.model.MethodCall;
import io.sundr.model.MethodCallBuilder;
import io.sundr.model.Property;
import io.sundr.model.TypeDef;
import io.sundr.model.TypeDefBuilder;
import io.sundr.model.repo.DefinitionRepository;
import io.sundr.model.repo.MethodCallCollector;
import io.sundr.model.repo.MethodReference;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class Sundr_model_repoTest {
    private static final AttributeKey<Boolean> AGGREGATE = new AttributeKey<>("aggregate", Boolean.class);
    private static final ClassRef STRING = ClassRef.forName("java.lang.String");

    @BeforeEach
    void resetSharedState() {
        DefinitionRepository.getRepository().clear();
        MethodReference.clearCache();
    }

    @Test
    void registersDefinitionsSuppliersAttributesAndReferenceMaps() {
        DefinitionRepository repository = DefinitionRepository.createRepository();
        TypeDef user = type("example.repo", "User");
        TypeDef duplicateUser = type("example.repo", "User");
        TypeDef order = type("example.repo", "Order");
        TypeDef lazy = type("example.repo", "Lazy");
        TypeDef aggregateRoot = type("example.repo", "AggregateRoot");
        AtomicInteger supplierCalls = new AtomicInteger();

        repository.registerIfAbsent(user);
        repository.registerIfAbsent(duplicateUser);
        repository.registerIfAbsent(lazy.getFullyQualifiedName(), () -> {
            supplierCalls.incrementAndGet();
            return lazy;
        });
        TypeDef taggedOrder = repository.register(order, AGGREGATE);
        TypeDef taggedAggregateRoot = repository.register(aggregateRoot, AGGREGATE);

        assertThat(repository.hasDefinition(user.getFullyQualifiedName())).isTrue();
        assertThat(repository.getDefinition(user.getFullyQualifiedName())).isSameAs(user);
        assertThat(repository.getDefinition(user.toReference())).isSameAs(user);
        assertThat(repository.getDefinition(lazy.getFullyQualifiedName(), false)).isNull();
        assertThat(repository.hasDefinition(lazy.getFullyQualifiedName())).isTrue();
        assertThat(repository.getDefinition(lazy.getFullyQualifiedName())).isSameAs(lazy);
        assertThat(supplierCalls).hasValue(1);

        assertThat(repository.getDefinitions(AGGREGATE)).containsExactlyInAnyOrder(taggedOrder, taggedAggregateRoot);
        assertThat(repository.getDefinitions()).contains(user, lazy, taggedOrder, taggedAggregateRoot);

        assertThat(repository.getReferenceMap()).containsEntry("User", "example.repo.User");
        repository.register(type("example.repo", "AfterSnapshot"));
        assertThat(repository.getReferenceMap()).doesNotContainKey("AfterSnapshot");
        repository.updateReferenceMap();
        assertThat(repository.getReferenceMap()).containsEntry("AfterSnapshot", "example.repo.AfterSnapshot");

        repository.clear();
        assertThat(repository.getDefinitions()).isEmpty();
        assertThat(repository.hasDefinition(user.getFullyQualifiedName())).isFalse();
    }

    @Test
    void scopesTheSharedRepositoryForFunctionAndCallableWork() {
        TypeDef scoped = type("example.scope", "Scoped");
        TypeDef called = type("example.scope", "Called");

        Boolean foundInScopedFunction = DefinitionRepository.withNewRepository().apply(repository -> {
            repository.register(scoped);
            return DefinitionRepository.getRepository().hasDefinition(scoped.getFullyQualifiedName());
        });
        String callableResult = DefinitionRepository.withRepository(DefinitionRepository.createRepository())
                .call(() -> {
                    DefinitionRepository.getRepository().register(called);
                    return DefinitionRepository.getRepository().getDefinition(called.getFullyQualifiedName()).getName();
                });

        assertThat(foundInScopedFunction).isTrue();
        assertThat(callableResult).isEqualTo("Called");
        assertThat(DefinitionRepository.getRepository().hasDefinition(scoped.getFullyQualifiedName())).isFalse();
        assertThat(DefinitionRepository.getRepository().hasDefinition(called.getFullyQualifiedName())).isFalse();
    }

    @Test
    void methodCallCollectorCopiesVisitedCallsAndCanBeCleared() {
        MethodCall load = call("load", null);
        MethodCall refresh = call(
                "refresh",
                Property.newProperty(type("example.collect", "Cache").toReference(), "cache"));
        MethodCallCollector collector = new MethodCallCollector();

        collector.visit(new MethodCallBuilder(load));
        collector.visit(new MethodCallBuilder(refresh));
        Set<MethodCall> collectedSnapshot = collector.getMethodCalls();
        collector.clear();

        assertThat(collectedSnapshot).extracting(MethodCall::getName).containsExactlyInAnyOrder("load", "refresh");
        assertThat(collector.getMethodCalls()).isEmpty();
    }

    @Test
    void resolvesDirectAndTransitiveMethodReferencesFromMethodBodies() {
        MethodReferenceGraph graph = createMethodReferenceGraph();

        Set<MethodReference> directReferences = MethodReference.getDirectMethodReferences(
                graph.load(), graph.repository());
        Set<MethodReference> transitiveReferences = MethodReference.getMethodReferences(
                graph.load(), graph.repository());

        assertThat(referenceNames(directReferences))
                .containsExactlyInAnyOrder("example.calls.Facade#helper", "example.calls.Service#fetch");
        assertThat(referenceNames(transitiveReferences))
                .containsExactlyInAnyOrder(
                        "example.calls.Facade#helper",
                        "example.calls.Service#fetch",
                        "example.calls.Service#normalize");
    }

    @Test
    void discoversDirectAndTransitiveMethodCallers() {
        MethodReferenceGraph graph = createMethodReferenceGraph();
        MethodReference normalizeReference = new MethodReference(graph.normalize(), graph.service());

        Set<MethodReference> directCallers = MethodReference.getDirectMethodCallers(
                normalizeReference, graph.repository());
        Set<MethodReference> transitiveCallers = MethodReference.getMethodCallers(
                normalizeReference, graph.repository());

        assertThat(referenceNames(directCallers)).containsExactly("example.calls.Facade#helper");
        assertThat(referenceNames(transitiveCallers))
                .containsExactlyInAnyOrder("example.calls.Facade#helper", "example.calls.Facade#load");
    }

    @Test
    void methodReferencesUseMethodErasureAndOwningTypeForIdentity() {
        TypeDef service = type("example.identity", "Service");
        Method byString = Method.newMethod("find", service.toReference(), Property.newProperty(String.class, "id"));
        Method sameErasure = Method.newMethod(
                "find", service.toReference(), Property.newProperty(String.class, "other"));
        Method differentMethod = Method.newMethod("save", service.toReference());
        MethodReference first = new MethodReference(byString, service);
        MethodReference second = new MethodReference(sameErasure, service);
        MethodReference different = new MethodReference(differentMethod, service);

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first).isNotEqualTo(different);
        assertThat(first.toString()).contains("Service", "find");
        assertThat(first.getMethod()).isSameAs(byString);
        assertThat(first.getOwningType()).isSameAs(service);
    }

    private static MethodReferenceGraph createMethodReferenceGraph() {
        DefinitionRepository repository = DefinitionRepository.createRepository();
        Method fetch = Method.newMethod("fetch", STRING);
        Method normalize = Method.newMethod("normalize", STRING, Property.newProperty(String.class, "value"));
        TypeDef service = type("example.calls", "Service", fetch, normalize);
        Property serviceProperty = Property.newProperty(service.toReference(), "service");
        Method helper = new MethodBuilder(Method.newMethod("helper", STRING))
                .withBlock(new Block(call("normalize", serviceProperty)))
                .build();
        Method load = new MethodBuilder(Method.newMethod("load", STRING))
                .withBlock(new Block(call("fetch", serviceProperty), call("helper", null)))
                .build();
        TypeDef facade = type("example.calls", "Facade", load, helper);

        repository.register(service);
        repository.register(facade);
        return new MethodReferenceGraph(repository, service, load, normalize);
    }

    private static Set<String> referenceNames(Set<MethodReference> references) {
        return references.stream()
                .map(reference -> reference.getOwningType().getFullyQualifiedName()
                        + "#" + reference.getMethod().getName())
                .collect(Collectors.toSet());
    }

    private static MethodCall call(String name, Expression scope) {
        return new MethodCall(name, scope);
    }

    private static TypeDef type(String packageName, String name, Method... methods) {
        return new TypeDefBuilder()
                .withKind(Kind.CLASS)
                .withPackageName(packageName)
                .withName(name)
                .withMethods(methods)
                .build();
    }

    private record MethodReferenceGraph(
            DefinitionRepository repository,
            TypeDef service,
            Method load,
            Method normalize) {
    }
}
