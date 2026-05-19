/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_data_commons;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.data.autoconfigure.metrics.DataMetricsProperties;
import org.springframework.boot.data.autoconfigure.metrics.PropertiesAutoTimer;
import org.springframework.boot.data.autoconfigure.web.DataWebProperties;
import org.springframework.boot.data.metrics.AutoTimer;
import org.springframework.boot.data.metrics.DefaultRepositoryTagsProvider;
import org.springframework.boot.data.metrics.MetricsRepositoryMethodInvocationListener;
import org.springframework.boot.data.metrics.TimedAnnotations;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.RepositoryMethodInvocationListener.RepositoryMethodInvocation;
import org.springframework.data.repository.core.support.RepositoryMethodInvocationListener.RepositoryMethodInvocationResult;
import org.springframework.data.repository.core.support.RepositoryMethodInvocationListener.RepositoryMethodInvocationResult.State;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_data_commonsTest {

    @Test
    void dataWebPropertiesExposeDocumentedDefaults() {
        DataWebProperties properties = new DataWebProperties();

        assertThat(properties.getPageable().getPageParameter()).isEqualTo("page");
        assertThat(properties.getPageable().getSizeParameter()).isEqualTo("size");
        assertThat(properties.getPageable().isOneIndexedParameters()).isFalse();
        assertThat(properties.getPageable().getPrefix()).isEmpty();
        assertThat(properties.getPageable().getQualifierDelimiter()).isEqualTo("_");
        assertThat(properties.getPageable().getDefaultPageSize()).isEqualTo(20);
        assertThat(properties.getPageable().getMaxPageSize()).isEqualTo(2000);
        assertThat(properties.getPageable().getSerializationMode()).isEqualTo(PageSerializationMode.DIRECT);
        assertThat(properties.getSort().getSortParameter()).isEqualTo("sort");
    }

    @Test
    void bindsDataWebPropertiesUsingSpringBootBinder() {
        DataWebProperties properties = bind("spring.data.web", DataWebProperties.class, Map.of(
                "spring.data.web.pageable.page-parameter", "p",
                "spring.data.web.pageable.size-parameter", "s",
                "spring.data.web.pageable.one-indexed-parameters", "true",
                "spring.data.web.pageable.prefix", "search",
                "spring.data.web.pageable.qualifier-delimiter", ".",
                "spring.data.web.pageable.default-page-size", "25",
                "spring.data.web.pageable.max-page-size", "250",
                "spring.data.web.pageable.serialization-mode", "via-dto",
                "spring.data.web.sort.sort-parameter", "order"));

        assertThat(properties.getPageable().getPageParameter()).isEqualTo("p");
        assertThat(properties.getPageable().getSizeParameter()).isEqualTo("s");
        assertThat(properties.getPageable().isOneIndexedParameters()).isTrue();
        assertThat(properties.getPageable().getPrefix()).isEqualTo("search");
        assertThat(properties.getPageable().getQualifierDelimiter()).isEqualTo(".");
        assertThat(properties.getPageable().getDefaultPageSize()).isEqualTo(25);
        assertThat(properties.getPageable().getMaxPageSize()).isEqualTo(250);
        assertThat(properties.getPageable().getSerializationMode()).isEqualTo(PageSerializationMode.VIA_DTO);
        assertThat(properties.getSort().getSortParameter()).isEqualTo("order");
    }

    @Test
    void dataMetricsPropertiesExposeRepositoryDefaults() {
        DataMetricsProperties properties = new DataMetricsProperties();

        assertThat(properties.getRepository().getMetricName()).isEqualTo("spring.data.repository.invocations");
        assertThat(properties.getRepository().getAutotime().isEnabled()).isTrue();
        assertThat(properties.getRepository().getAutotime().isPercentilesHistogram()).isFalse();
        assertThat(properties.getRepository().getAutotime().getPercentiles()).isNull();
    }

    @Test
    void bindsDataMetricsPropertiesAndConfiguresPropertiesAutoTimer() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("management.metrics.data.repository.metric-name", "custom.repository.calls");
        values.put("management.metrics.data.repository.autotime.enabled", "true");
        values.put("management.metrics.data.repository.autotime.percentiles-histogram", "true");
        values.put("management.metrics.data.repository.autotime.percentiles[0]", "0.5");
        values.put("management.metrics.data.repository.autotime.percentiles[1]", "0.95");
        DataMetricsProperties properties = bind("management.metrics.data", DataMetricsProperties.class, values);

        assertThat(properties.getRepository().getMetricName()).isEqualTo("custom.repository.calls");
        assertThat(properties.getRepository().getAutotime().isEnabled()).isTrue();
        assertThat(properties.getRepository().getAutotime().isPercentilesHistogram()).isTrue();
        assertThat(properties.getRepository().getAutotime().getPercentiles()).containsExactly(0.5, 0.95);

        PropertiesAutoTimer autoTimer = new PropertiesAutoTimer(properties.getRepository().getAutotime());
        List<Timer.Builder> builders = new ArrayList<>();
        AutoTimer.apply(autoTimer, properties.getRepository().getMetricName(), Collections.emptySet(), builders::add);

        assertThat(autoTimer.isEnabled()).isTrue();
        assertThat(builders).hasSize(1);
    }

    @Test
    void autoTimerStaticApplyHonorsDisabledAndEnabledTimers() {
        List<Timer.Builder> disabledBuilders = new ArrayList<>();
        AutoTimer.apply(AutoTimer.DISABLED, "repository.calls", Collections.emptySet(), disabledBuilders::add);

        List<Timer.Builder> enabledBuilders = new ArrayList<>();
        AutoTimer.apply(AutoTimer.ENABLED, "repository.calls", Collections.emptySet(), enabledBuilders::add);

        assertThat(AutoTimer.DISABLED.isEnabled()).isFalse();
        assertThat(AutoTimer.ENABLED.isEnabled()).isTrue();
        assertThat(disabledBuilders).isEmpty();
        assertThat(enabledBuilders).hasSize(1);
    }

    @Test
    void defaultRepositoryTagsProviderAddsFailureTags() {
        DefaultRepositoryTagsProvider provider = new DefaultRepositoryTagsProvider();
        RepositoryMethodInvocation invocation = repositoryInvocation(State.ERROR, new IllegalStateException("failed"),
                0);

        Iterable<Tag> tags = provider.repositoryTags(invocation);

        assertThat(tags).contains(Tag.of("repository", "SampleRepository"), Tag.of("state", "ERROR"),
                Tag.of("exception", "IllegalStateException"));
        assertThat(tags).doesNotContain(Tag.of("exception", "None"));
    }

    @Test
    void metricsRepositoryMethodInvocationListenerRecordsSuccessfulInvocations() {
        String metricName = "test.repository.invocations";
        long duration = TimeUnit.MILLISECONDS.toNanos(25);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MetricsRepositoryMethodInvocationListener listener = new MetricsRepositoryMethodInvocationListener(
                () -> registry, new DefaultRepositoryTagsProvider(), metricName, AutoTimer.ENABLED);

        listener.afterInvocation(repositoryInvocation(State.SUCCESS, null, duration));

        Timer timer = registry.get(metricName)
            .tags("repository", "SampleRepository", "state", "SUCCESS", "exception", "None")
            .timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(25.0);
    }

    @Test
    void timedAnnotationsPreferRepositoryMethodAnnotationsAndFallBackToTypeAnnotations() throws NoSuchMethodException {
        Method annotatedMethod = TimedRepository.class.getMethod("findOneByName", String.class);
        Method unannotatedMethod = TimedRepository.class.getMethod("countByName", String.class);

        assertThat(TimedAnnotations.get(annotatedMethod, TimedRepository.class)).singleElement()
            .satisfies((annotation) -> assertTimedAnnotation(annotation, "method.repository.invocations", "method",
                    "findOneByName"));
        assertThat(TimedAnnotations.get(unannotatedMethod, TimedRepository.class)).singleElement()
            .satisfies((annotation) -> assertTimedAnnotation(annotation, "type.repository.invocations", "level",
                    "repository"));
    }

    private static <T> T bind(String prefix, Class<T> type, Map<String, String> values) {
        Binder binder = new Binder(new MapConfigurationPropertySource(values));
        return binder.bind(prefix, type).get();
    }

    private static RepositoryMethodInvocation repositoryInvocation(State state, Throwable error, long durationNs) {
        return new RepositoryMethodInvocation(SampleRepository.class, null, new TestInvocationResult(state, error),
                durationNs);
    }

    private static void assertTimedAnnotation(Timed annotation, String metricName, String... extraTags) {
        assertThat(annotation.value()).isEqualTo(metricName);
        assertThat(annotation.extraTags()).containsExactly(extraTags);
    }

    private interface SampleRepository extends Repository<SampleEntity, Long> {

    }

    @Timed(value = "type.repository.invocations", extraTags = { "level", "repository" })
    private interface TimedRepository extends Repository<SampleEntity, Long> {

        @Timed(value = "method.repository.invocations", extraTags = { "method", "findOneByName" })
        SampleEntity findOneByName(String name);

        long countByName(String name);

    }

    private static final class SampleEntity {

    }

    private static final class TestInvocationResult implements RepositoryMethodInvocationResult {

        private final State state;

        private final Throwable error;

        private TestInvocationResult(State state, Throwable error) {
            this.state = state;
            this.error = error;
        }

        @Override
        public State getState() {
            return this.state;
        }

        @Override
        public Throwable getError() {
            return this.error;
        }

    }

}
