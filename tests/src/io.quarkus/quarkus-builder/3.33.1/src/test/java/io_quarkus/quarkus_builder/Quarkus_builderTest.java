/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.quarkus.builder.BuildChain;
import io.quarkus.builder.BuildChainBuilder;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildException;
import io.quarkus.builder.BuildExecutionBuilder;
import io.quarkus.builder.BuildResult;
import io.quarkus.builder.BuildStep;
import io.quarkus.builder.BuildStepBuilder;
import io.quarkus.builder.ChainBuildException;
import io.quarkus.builder.ConsumeFlag;
import io.quarkus.builder.ConsumeFlags;
import io.quarkus.builder.ProduceFlag;
import io.quarkus.builder.Version;
import io.quarkus.builder.diag.Diagnostic;
import io.quarkus.builder.item.MultiBuildItem;
import io.quarkus.builder.item.SimpleBuildItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

public class Quarkus_builderTest {
    @Test
    void executesBuildChainWithInitialItemsMultiItemsDiagnosticsAndResourceCleanup() throws Exception {
        List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean closed = new AtomicBoolean(false);
        ClassLoader deploymentClassLoader = new MarkerClassLoader(getClass().getClassLoader());
        BuildChainBuilder builder = BuildChain.builder();
        builder.setClassLoader(deploymentClassLoader);
        builder.addInitial(ConfigurationItem.class);

        builder.addBuildStep(new NamedStep("setup", context -> {
            assertThat(context.getBuildTargetName()).isEqualTo("integration-build");
            assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(deploymentClassLoader);
            ConfigurationItem configuration = context.consume(ConfigurationItem.class);
            context.note(null, "building %s", configuration.name());
            context.warn(null, "warning for %s", configuration.name());
            executionOrder.add("setup");
            context.produce(new BaseItem(configuration.name() + ":base"));
            context.produce(List.of(new GeneratedItem("base-one"), new GeneratedItem("base-two")));
            context.produce(new CloseableResourceItem(closed));
        })).consumes(ConfigurationItem.class)
                .produces(BaseItem.class)
                .produces(GeneratedItem.class)
                .produces(CloseableResourceItem.class)
                .build();

        builder.addBuildStep(new NamedStep("feature", context -> {
            BaseItem base = context.consume(BaseItem.class);
            executionOrder.add("feature:" + base.value());
            context.produce(new FeatureItem(base.value() + ":feature"));
            context.produce(new GeneratedItem("feature"));
        })).consumes(BaseItem.class)
                .produces(FeatureItem.class)
                .produces(GeneratedItem.class)
                .build();

        builder.addBuildStep(new NamedStep("summary", context -> {
            BaseItem base = context.consume(BaseItem.class);
            FeatureItem feature = context.consume(FeatureItem.class);
            List<GeneratedItem> generatedItems = context.consumeMulti(GeneratedItem.class);
            assertThatThrownBy(() -> generatedItems.add(new GeneratedItem("not-allowed")))
                    .isInstanceOf(UnsupportedOperationException.class);
            executionOrder.add("summary");
            context.produce(new SummaryItem(base.value(), feature.value(), generatedItems.stream()
                    .map(GeneratedItem::value)
                    .toList()));
        })).consumes(BaseItem.class)
                .consumes(FeatureItem.class)
                .consumes(GeneratedItem.class)
                .produces(SummaryItem.class)
                .build();

        builder.addFinal(SummaryItem.class);
        builder.addFinal(CloseableResourceItem.class);
        BuildChain chain = builder.build();
        BuildExecutionBuilder executionBuilder = chain.createExecutionBuilder("integration-build");
        assertThat(executionBuilder.getBuildTargetName()).isEqualTo("integration-build");
        BuildResult result = executionBuilder.produce(new ConfigurationItem("app")).execute();

        SummaryItem summary = result.consume(SummaryItem.class);
        assertThat(summary.base()).isEqualTo("app:base");
        assertThat(summary.feature()).isEqualTo("app:base:feature");
        assertThat(summary.generated()).containsExactly("base-one", "base-two", "feature");
        assertThat(result.consumeMulti(GeneratedItem.class))
                .extracting(GeneratedItem::value)
                .containsExactly("base-one", "base-two", "feature");
        assertThat(result.consumeOptional(UnusedItem.class)).isNull();
        assertThat(result.getDiagnostics())
                .extracting(Diagnostic::toString)
                .containsExactly("[note]: building app", "[warning]: warning for app");
        assertThat(result.getDeploymentClassLoader()).isSameAs(deploymentClassLoader);
        assertThat(result.getDuration(java.util.concurrent.TimeUnit.NANOSECONDS)).isGreaterThanOrEqualTo(0L);
        assertThat(executionOrder).containsExactly("setup", "feature:app:base", "summary");

        CloseableResourceItem closeable = result.consume(CloseableResourceItem.class);
        assertThat(closeable.closed()).isFalse();

        BuildChain closeOnlyChain = BuildChain.builder()
                .addBuildStep(new NamedStep("closeable",
                        context -> context.produce(new CloseableResourceItem(closed))))
                .produces(CloseableResourceItem.class)
                .build()
                .addFinal(CloseableResourceItem.class)
                .build();
        BuildResult closeOnlyResult = closeOnlyChain.createExecutionBuilder("close-only-build").execute();
        CloseableResourceItem closeOnlyItem = closeOnlyResult.consume(CloseableResourceItem.class);
        closeOnlyResult.closeAll();
        assertThat(closeOnlyItem.closed()).isTrue();
        assertThat(closed).isTrue();
    }

    @Test
    void prioritizesMultiItemProducerOrderForConsumers() throws Exception {
        List<String> consumedOrder = new ArrayList<>();
        BuildChainBuilder builder = newPriorityChain(consumedOrder);
        builder.addPriorityItem(LoggingSetupItem.class);

        BuildResult result = builder.build().createExecutionBuilder("priority-build").execute();

        assertThat(result.consume(BundleItem.class)).isNotNull();
        assertThat(consumedOrder).containsExactly("bytecode-from-base", "bytecode-from-logging", "bytecode-from-feature");
    }

    @Test
    void honorsOrderOnlyConstraintsAroundItemConsumersAndProducers() throws Exception {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean inputProduced = new AtomicBoolean(false);
        AtomicBoolean beforeConsumerRan = new AtomicBoolean(false);
        AtomicBoolean afterProducerRan = new AtomicBoolean(false);
        BuildChainBuilder builder = BuildChain.builder();

        builder.addBuildStep(new NamedStep("before-consume", context -> {
            beforeConsumerRan.set(true);
            events.add("before-consume");
        })).beforeConsume(OrderedInputItem.class).build();
        builder.addBuildStep(new NamedStep("producer", context -> {
            inputProduced.set(true);
            events.add("producer");
            context.produce(new OrderedInputItem("configured"));
        })).produces(OrderedInputItem.class).build();
        builder.addBuildStep(new NamedStep("after-produce", context -> {
            assertThat(inputProduced).isTrue();
            afterProducerRan.set(true);
            events.add("after-produce");
            context.produce(new OrderedGateItem());
        })).afterProduce(OrderedInputItem.class).produces(OrderedGateItem.class).build();
        builder.addBuildStep(new NamedStep("consumer", context -> {
            assertThat(beforeConsumerRan).isTrue();
            assertThat(afterProducerRan).isTrue();
            OrderedInputItem input = context.consume(OrderedInputItem.class);
            context.consume(OrderedGateItem.class);
            events.add("consumer");
            context.produce(new OrderedResultItem(input.value() + ":consumed"));
        })).consumes(OrderedInputItem.class)
                .consumes(OrderedGateItem.class)
                .produces(OrderedResultItem.class)
                .build();
        builder.addFinal(OrderedResultItem.class);

        BuildResult result = builder.build().createExecutionBuilder("order-only-build").execute();

        assertThat(result.consume(OrderedResultItem.class).value()).isEqualTo("configured:consumed");
        assertThat(events).containsExactlyInAnyOrder("before-consume", "producer", "after-produce", "consumer");
        assertThat(events.indexOf("before-consume")).isLessThan(events.indexOf("consumer"));
        assertThat(events.indexOf("producer")).isLessThan(events.indexOf("after-produce"));
        assertThat(events.indexOf("after-produce")).isLessThan(events.indexOf("consumer"));
    }

    @Test
    void acceptsMultipleInitialMultiBuildItemsForEachExecution() throws Exception {
        BuildChainBuilder builder = BuildChain.builder();
        builder.addInitial(InitialFeatureItem.class);
        builder.addBuildStep(new NamedStep("initial-feature-summary", context -> {
            List<InitialFeatureItem> featureItems = context.consumeMulti(InitialFeatureItem.class);
            context.produce(new InitialFeatureSummaryItem(featureItems.stream()
                    .map(InitialFeatureItem::name)
                    .toList()));
        })).consumes(InitialFeatureItem.class).produces(InitialFeatureSummaryItem.class).build();
        builder.addFinal(InitialFeatureSummaryItem.class);

        BuildResult result = builder.build()
                .createExecutionBuilder("initial-multi-build")
                .produce(new InitialFeatureItem("security"))
                .produce(InitialFeatureItem.class, new InitialFeatureItem("observability"))
                .execute();

        assertThat(result.consume(InitialFeatureSummaryItem.class).names())
                .containsExactly("security", "observability");
        assertThat(result.consumeMulti(InitialFeatureItem.class))
                .extracting(InitialFeatureItem::name)
                .containsExactly("security", "observability");
    }

    @Test
    void supportsOptionalConsumesOverridableProducersAndFlagSets() throws Exception {
        BuildChainBuilder builder = BuildChain.builder();
        AtomicBoolean overridableRan = new AtomicBoolean(false);

        builder.addBuildStep(new NamedStep("overridable", context -> {
            overridableRan.set(true);
            context.produce(new OptionalOutputItem("overridable"));
        })).produces(OptionalOutputItem.class, ProduceFlag.OVERRIDABLE).build();
        builder.addBuildStep(new NamedStep("real", context -> context.produce(new OptionalOutputItem("real"))))
                .produces(OptionalOutputItem.class)
                .build();
        builder.addBuildStep(new NamedStep("optional-consumer", context -> {
            assertThat(context.consume(MissingOptionalItem.class)).isNull();
            OptionalOutputItem output = context.consume(OptionalOutputItem.class);
            context.produce(new OptionalSummaryItem(output.value()));
        })).consumes(MissingOptionalItem.class, ConsumeFlags.of(ConsumeFlag.OPTIONAL))
                .consumes(OptionalOutputItem.class)
                .produces(OptionalSummaryItem.class)
                .build();
        builder.addFinal(OptionalSummaryItem.class);

        BuildResult result = builder.build().createExecutionBuilder("optional-build").execute();

        assertThat(result.consume(OptionalSummaryItem.class).value()).isEqualTo("real");
        assertThat(overridableRan).isFalse();
        assertThat(ConsumeFlags.NONE.contains(ConsumeFlag.OPTIONAL)).isFalse();
        assertThat(ConsumeFlags.of(ConsumeFlag.OPTIONAL).contains(ConsumeFlag.OPTIONAL)).isTrue();
    }

    @Test
    void reportsContextErrorsAndThrownFailuresAsDiagnostics() throws Exception {
        BuildChainBuilder reportedErrorBuilder = BuildChain.builder();
        reportedErrorBuilder.addBuildStep(new NamedStep("reported-error", context -> {
            context.note(null, "starting");
            context.error(null, "invalid %s", "state");
            context.produce(new ErrorResultItem());
        })).produces(ErrorResultItem.class).build();
        reportedErrorBuilder.addFinal(ErrorResultItem.class);

        assertThatThrownBy(() -> reportedErrorBuilder.build().createExecutionBuilder("reported-error-build").execute())
                .isInstanceOf(BuildException.class)
                .satisfies(error -> {
                    BuildException buildException = (BuildException) error;
                    assertThat(buildException.getDiagnostics())
                            .extracting(Diagnostic::getLevel)
                            .containsExactly(Diagnostic.Level.NOTE, Diagnostic.Level.ERROR);
                    assertThat(buildException.getMessage()).contains("[error]: invalid state");
                });

        BuildChainBuilder thrownFailureBuilder = BuildChain.builder();
        IllegalStateException failure = new IllegalStateException("boom");
        thrownFailureBuilder.addBuildStep(new NamedStep("throwing-step", context -> {
            throw failure;
        })).produces(ErrorResultItem.class).build();
        thrownFailureBuilder.addFinal(ErrorResultItem.class);

        assertThatThrownBy(() -> thrownFailureBuilder.build().createExecutionBuilder("thrown-error-build").execute())
                .isInstanceOf(BuildException.class)
                .satisfies(error -> {
                    BuildException buildException = (BuildException) error;
                    assertThat(buildException.getDiagnostics()).hasSize(1);
                    Diagnostic diagnostic = buildException.getDiagnostics().get(0);
                    assertThat(diagnostic.getLevel()).isEqualTo(Diagnostic.Level.ERROR);
                    assertThat(diagnostic.getThrown()).isSameAs(failure);
                    assertThat(diagnostic.toString()).contains("Build step throwing-step threw an exception", "boom");
                });
    }

    @Test
    void rejectsInvalidDeclarationsAndUndeclaredItems() throws Exception {
        BuildStepBuilder missingProduces = BuildChain.builder()
                .addBuildStep(new NamedStep("missing-produces", context -> assertThat(context).isNotNull()));
        assertThatThrownBy(missingProduces::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Build step 'missing-produces'")
                .hasMessageContaining("does not produce any build item");

        BuildChainBuilder missingRequiredProducer = BuildChain.builder();
        missingRequiredProducer.addBuildStep(new NamedStep("requires-missing", context -> {
            context.consume(MissingRequiredItem.class);
            context.produce(new OptionalSummaryItem("unreachable"));
        })).consumes(MissingRequiredItem.class).produces(OptionalSummaryItem.class).build();
        missingRequiredProducer.addFinal(OptionalSummaryItem.class);
        assertThatThrownBy(missingRequiredProducer::build)
                .isInstanceOf(ChainBuildException.class)
                .hasMessageContaining("No producers for required item");

        BuildChainBuilder duplicateProducer = BuildChain.builder();
        duplicateProducer.addBuildStep(new NamedStep("first", context -> context.produce(new BaseItem("first"))))
                .produces(BaseItem.class)
                .build();
        duplicateProducer.addBuildStep(new NamedStep("second", context -> context.produce(new BaseItem("second"))))
                .produces(BaseItem.class)
                .build();
        duplicateProducer.addFinal(BaseItem.class);
        assertThatThrownBy(duplicateProducer::build)
                .isInstanceOf(ChainBuildException.class)
                .hasMessageContaining("Multiple producers of item");

        BuildChain chain = BuildChain.builder().addInitial(ConfigurationItem.class).build();
        assertThatThrownBy(() -> chain.createExecutionBuilder("undeclared-initial").produce(new BaseItem("bad")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Undeclared build item");
    }

    @Test
    void exposesVersionInformationFromPackagedResources() {
        assertThat(Version.getJarName()).isEqualTo("quarkus-builder");
        assertThat(Version.getVersion()).isNotBlank().isNotEqualTo("(unknown)");
    }

    private static BuildChainBuilder newPriorityChain(List<String> consumedOrder) {
        BuildChainBuilder builder = BuildChain.builder();
        builder.addBuildStep(new NamedStep("base-config", context -> {
            context.produce(new BaseConfigItem());
            context.produce(new GeneratedBytecodeItem("bytecode-from-base"));
        })).produces(BaseConfigItem.class).produces(GeneratedBytecodeItem.class).build();
        builder.addBuildStep(new NamedStep("logging-setup", context -> {
            context.consume(BaseConfigItem.class);
            context.produce(new LoggingSetupItem());
            context.produce(new GeneratedBytecodeItem("bytecode-from-logging"));
        })).consumes(BaseConfigItem.class).produces(LoggingSetupItem.class).produces(GeneratedBytecodeItem.class).build();
        builder.addBuildStep(new NamedStep("feature-init", context -> {
            context.produce(new FeatureMetadataItem());
            context.produce(new GeneratedBytecodeItem("bytecode-from-feature"));
        })).produces(FeatureMetadataItem.class).produces(GeneratedBytecodeItem.class).build();
        builder.addBuildStep(new NamedStep("bundle", context -> {
            context.consume(LoggingSetupItem.class);
            context.consume(FeatureMetadataItem.class);
            context.consumeMulti(GeneratedBytecodeItem.class).forEach(item -> consumedOrder.add(item.label()));
            context.produce(new BundleItem());
        })).consumes(LoggingSetupItem.class)
                .consumes(FeatureMetadataItem.class)
                .consumes(GeneratedBytecodeItem.class)
                .produces(BundleItem.class)
                .build();
        builder.addFinal(BundleItem.class);
        return builder;
    }

    private static final class NamedStep implements BuildStep {
        private final String id;
        private final StepAction action;

        private NamedStep(String id, StepAction action) {
            this.id = id;
            this.action = action;
        }

        @Override
        public void execute(BuildContext context) {
            action.execute(context);
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String toString() {
            return id;
        }
    }

    @FunctionalInterface
    private interface StepAction {
        void execute(BuildContext context);
    }

    private static final class MarkerClassLoader extends ClassLoader {
        private MarkerClassLoader(ClassLoader parent) {
            super(parent);
        }
    }

    private static final class ConfigurationItem extends SimpleBuildItem {
        private final String name;

        private ConfigurationItem(String name) {
            this.name = name;
        }

        private String name() {
            return name;
        }
    }

    private static final class BaseItem extends SimpleBuildItem {
        private final String value;

        private BaseItem(String value) {
            this.value = value;
        }

        private String value() {
            return value;
        }
    }

    private static final class FeatureItem extends SimpleBuildItem {
        private final String value;

        private FeatureItem(String value) {
            this.value = value;
        }

        private String value() {
            return value;
        }
    }

    private static final class SummaryItem extends SimpleBuildItem {
        private final String base;
        private final String feature;
        private final List<String> generated;

        private SummaryItem(String base, String feature, List<String> generated) {
            this.base = base;
            this.feature = feature;
            this.generated = List.copyOf(generated);
        }

        private String base() {
            return base;
        }

        private String feature() {
            return feature;
        }

        private List<String> generated() {
            return generated;
        }
    }

    private static final class GeneratedItem extends MultiBuildItem {
        private final String value;

        private GeneratedItem(String value) {
            this.value = value;
        }

        private String value() {
            return value;
        }
    }

    private static final class CloseableResourceItem extends SimpleBuildItem implements AutoCloseable {
        private final AtomicBoolean closed;

        private CloseableResourceItem(AtomicBoolean closed) {
            this.closed = closed;
        }

        @Override
        public void close() {
            closed.set(true);
        }

        private boolean closed() {
            return closed.get();
        }
    }

    private static final class UnusedItem extends SimpleBuildItem {
    }

    private static final class MissingOptionalItem extends SimpleBuildItem {
    }

    private static final class OrderedInputItem extends SimpleBuildItem {
        private final String value;

        private OrderedInputItem(String value) {
            this.value = value;
        }

        private String value() {
            return value;
        }
    }

    private static final class OrderedGateItem extends SimpleBuildItem {
    }

    private static final class OrderedResultItem extends SimpleBuildItem {
        private final String value;

        private OrderedResultItem(String value) {
            this.value = value;
        }

        private String value() {
            return value;
        }
    }

    private static final class MissingRequiredItem extends SimpleBuildItem {
    }

    private static final class InitialFeatureItem extends MultiBuildItem {
        private final String name;

        private InitialFeatureItem(String name) {
            this.name = name;
        }

        private String name() {
            return name;
        }
    }

    private static final class InitialFeatureSummaryItem extends SimpleBuildItem {
        private final List<String> names;

        private InitialFeatureSummaryItem(List<String> names) {
            this.names = List.copyOf(names);
        }

        private List<String> names() {
            return names;
        }
    }

    private static final class OptionalOutputItem extends SimpleBuildItem {
        private final String value;

        private OptionalOutputItem(String value) {
            this.value = value;
        }

        private String value() {
            return value;
        }
    }

    private static final class OptionalSummaryItem extends SimpleBuildItem {
        private final String value;

        private OptionalSummaryItem(String value) {
            this.value = value;
        }

        private String value() {
            return value;
        }
    }

    private static final class ErrorResultItem extends SimpleBuildItem {
    }

    private static final class BaseConfigItem extends SimpleBuildItem {
    }

    private static final class LoggingSetupItem extends SimpleBuildItem {
    }

    private static final class FeatureMetadataItem extends SimpleBuildItem {
    }

    private static final class BundleItem extends SimpleBuildItem {
    }

    private static final class GeneratedBytecodeItem extends MultiBuildItem {
        private final String label;

        private GeneratedBytecodeItem(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }
    }
}
