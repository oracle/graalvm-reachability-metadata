/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_external.management_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.management.MBeanServerNotification;
import javax.management.Notification;
import javax.management.ObjectName;
import org.glassfish.external.amx.AMX;
import org.glassfish.external.amx.AMXGlassfish;
import org.glassfish.external.amx.AMXUtil;
import org.glassfish.external.amx.MBeanListener;
import org.glassfish.external.arc.Stability;
import org.glassfish.external.probe.provider.PluginPoint;
import org.glassfish.external.probe.provider.StatsProviderInfo;
import org.glassfish.external.probe.provider.StatsProviderManager;
import org.glassfish.external.probe.provider.StatsProviderManagerDelegate;
import org.glassfish.external.probe.provider.annotations.Probe;
import org.glassfish.external.probe.provider.annotations.ProbeParam;
import org.glassfish.external.probe.provider.annotations.ProbeProvider;
import org.glassfish.external.statistics.AverageRangeStatistic;
import org.glassfish.external.statistics.BoundaryStatistic;
import org.glassfish.external.statistics.BoundedRangeStatistic;
import org.glassfish.external.statistics.CountStatistic;
import org.glassfish.external.statistics.RangeStatistic;
import org.glassfish.external.statistics.StringStatistic;
import org.glassfish.external.statistics.TimeStatistic;
import org.glassfish.external.statistics.impl.AverageRangeStatisticImpl;
import org.glassfish.external.statistics.impl.BoundaryStatisticImpl;
import org.glassfish.external.statistics.impl.BoundedRangeStatisticImpl;
import org.glassfish.external.statistics.impl.CountStatisticImpl;
import org.glassfish.external.statistics.impl.RangeStatisticImpl;
import org.glassfish.external.statistics.impl.StringStatisticImpl;
import org.glassfish.external.statistics.impl.TimeStatisticImpl;
import org.junit.jupiter.api.Test;

class Management_apiTest {

    @Test
    @SuppressWarnings("unchecked")
    void statisticsExposeSnapshotsThroughProxyInterfaces() {
        CountStatisticImpl count = new CountStatisticImpl(2, "requests", "count", "Processed requests", 100L, 200L);
        CountStatistic countView = count.getStatistic();

        count.increment(3);

        assertThat(countView.getName()).isEqualTo("requests");
        assertThat(countView.getUnit()).isEqualTo("count");
        assertThat(countView.getDescription()).isEqualTo("Processed requests");
        assertThat(countView.getCount()).isEqualTo(5);
        assertThat(count.getStaticAsMap()).containsEntry("count", 5L);

        RangeStatisticImpl queueDepth = new RangeStatisticImpl(4, 7, 2, "queueDepth", "count", "Queue depth", 300L, 400L);
        RangeStatistic queueDepthView = queueDepth.getStatistic();

        queueDepth.setCurrent(9);

        assertThat(queueDepthView.getCurrent()).isEqualTo(9);
        assertThat(queueDepthView.getHighWaterMark()).isEqualTo(9);
        assertThat(queueDepthView.getLowWaterMark()).isEqualTo(2);
        assertThat(queueDepth.getStaticAsMap()).containsEntry("highwatermark", 9L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void stringAndTimeStatisticsTrackCurrentValuesAndReset() {
        StringStatisticImpl status = new StringStatisticImpl("starting", "status", "text", "Service status", 500L, 600L);
        StringStatistic statusView = status.getStatistic();

        status.setCurrent("running");

        assertThat(statusView.getCurrent()).isEqualTo("running");
        assertThat(status.getStaticAsMap()).containsEntry("current", "running");

        status.reset();

        assertThat(statusView.getCurrent()).isEqualTo("starting");

        TimeStatisticImpl latency = new TimeStatisticImpl(0, 0, 0, 0, "latency", "millisecond", "Request latency", 700L, 800L);
        TimeStatistic latencyView = latency.getStatistic();

        latency.incrementCount(42);
        latency.incrementCount(12);

        assertThat(latencyView.getCount()).isEqualTo(2);
        assertThat(latencyView.getMaxTime()).isEqualTo(42);
        assertThat(latencyView.getMinTime()).isEqualTo(12);
        assertThat(latencyView.getTotalTime()).isEqualTo(54);
        assertThat(latency.getStaticAsMap()).containsEntry("totaltime", 54L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rangeBoundaryStatisticsExposeSnapshotsThroughProxyInterfaces() {
        AverageRangeStatisticImpl average =
                new AverageRangeStatisticImpl(10, 14, 6, "averageQueueDepth", "count", "Average queue depth", 900L, 1000L);
        AverageRangeStatistic averageView = average.getStatistic();

        average.setCurrent(20);

        assertThat(averageView.getCurrent()).isEqualTo(20);
        assertThat(averageView.getHighWaterMark()).isEqualTo(20);
        assertThat(averageView.getLowWaterMark()).isEqualTo(6);
        assertThat(averageView.getAverage()).isEqualTo(20);
        assertThat(average.getStaticAsMap()).containsEntry("average", 20L);

        BoundaryStatisticImpl boundary =
                new BoundaryStatisticImpl(2, 25, "connectionLimit", "count", "Connection limits", 1100L, 1200L);
        BoundaryStatistic boundaryView = boundary.getStatistic();

        assertThat(boundaryView.getLowerBound()).isEqualTo(2);
        assertThat(boundaryView.getUpperBound()).isEqualTo(25);
        assertThat(boundary.getStaticAsMap()).containsEntry("upperbound", 25L);

        BoundedRangeStatisticImpl bounded =
                new BoundedRangeStatisticImpl(4, 7, 3, 10, 1, "activeConnections", "count", "Active connections", 1300L, 1400L);
        BoundedRangeStatistic boundedView = bounded.getStatistic();

        bounded.setCurrent(8);

        assertThat(boundedView.getCurrent()).isEqualTo(8);
        assertThat(boundedView.getHighWaterMark()).isEqualTo(8);
        assertThat(boundedView.getLowWaterMark()).isEqualTo(3);
        assertThat(boundedView.getLowerBound()).isEqualTo(1);
        assertThat(boundedView.getUpperBound()).isEqualTo(10);
        assertThat(bounded.getStaticAsMap()).containsEntry("current", 8L);
    }

    @Test
    void amxUtilitiesBuildObjectNamesAndFilterMBeanNotifications() throws Exception {
        ObjectName directName = AMXUtil.newObjectName("amx:type=server,name=server");
        ObjectName typedName = AMXUtil.newObjectName(
                "amx", AMXUtil.prop(AMX.TYPE_KEY, "server") + "," + AMXUtil.prop(AMX.NAME_KEY, "server"));
        RecordingMBeanCallback callback = new RecordingMBeanCallback();
        MBeanListener<RecordingMBeanCallback> listener = new MBeanListener<>(
                null, "amx", "server", "server", callback);

        listener.handleNotification(
                new MBeanServerNotification(MBeanServerNotification.REGISTRATION_NOTIFICATION, this, 1, directName), null);
        listener.handleNotification(
                new MBeanServerNotification(MBeanServerNotification.UNREGISTRATION_NOTIFICATION, this, 2, directName), null);
        listener.handleNotification(new Notification("ignored", this, 3), null);

        assertThat(directName).isEqualTo(typedName);
        assertThat(callback.registered).containsExactly(directName);
        assertThat(callback.unregistered).containsExactly(directName);
        assertThat(AMXGlassfish.DEFAULT.amxJMXDomain()).isEqualTo("amx");
        assertThat(AMXGlassfish.DEFAULT.dasName()).isEqualTo("server");
        assertThat(AMXGlassfish.DEFAULT.getBootAMXMBeanObjectName().getDomain()).isEqualTo("amx-support");
    }

    @Test
    void statsProviderManagerDelegatesRegistrationsAndListenerQueries() {
        RecordingStatsProviderDelegate delegate = new RecordingStatsProviderDelegate();
        Object provider = new Object();

        StatsProviderManager.setStatsProviderManagerDelegate(delegate);

        assertThat(StatsProviderManager.register("http", PluginPoint.SERVER, "server/http", provider, "server", "invoker-1"))
                .isTrue();
        assertThat(StatsProviderManager.hasListeners("server/http")).isTrue();
        assertThat(StatsProviderManager.unregister(provider)).isTrue();

        StatsProviderInfo registered = delegate.registered.get(0);
        assertThat(registered.getConfigElement()).isEqualTo("http");
        assertThat(registered.getPluginPoint()).isEqualTo(PluginPoint.SERVER);
        assertThat(registered.getSubTreeRoot()).isEqualTo("server/http");
        assertThat(registered.getStatsProvider()).isSameAs(provider);
        assertThat(registered.getConfigLevel()).isEqualTo("server");
        assertThat(registered.getInvokerId()).isEqualTo("invoker-1");
        assertThat(delegate.unregistered).containsExactly(provider);
        assertThat(PluginPoint.APPLICATIONS.getName()).isEqualTo("applications");
        assertThat(Stability.COMMITTED.toString()).isEqualTo("Committed");
    }

    @Test
    @SuppressWarnings("checkstyle:annotationAccess")
    void probeAnnotationsRetainProviderAndParameterMetadata() throws Exception {
        ProbeProvider provider = AnnotatedProbeProvider.class.getAnnotation(ProbeProvider.class);
        Method requestStarted = AnnotatedProbeProvider.class.getDeclaredMethod("requestStarted", String.class, long.class);
        Probe probe = requestStarted.getAnnotation(Probe.class);
        ProbeParam path = requestStarted.getParameters()[0].getAnnotation(ProbeParam.class);
        ProbeParam duration = requestStarted.getParameters()[1].getAnnotation(ProbeParam.class);

        assertThat(provider.moduleProviderName()).isEqualTo("glassfish");
        assertThat(provider.moduleName()).isEqualTo("web");
        assertThat(provider.probeProviderName()).isEqualTo("requests");
        assertThat(provider.providerName()).isEqualTo("glassfish:web:requests");
        assertThat(probe.name()).isEqualTo("requestStarted");
        assertThat(probe.moduleName()).isEqualTo("web");
        assertThat(probe.providerName()).isEqualTo("requests");
        assertThat(probe.hidden()).isFalse();
        assertThat(probe.self()).isTrue();
        assertThat(path.value()).isEqualTo("path");
        assertThat(duration.value()).isEqualTo("durationMillis");
    }

    @ProbeProvider(
            moduleProviderName = "glassfish",
            moduleName = "web",
            probeProviderName = "requests",
            providerName = "glassfish:web:requests")
    private static final class AnnotatedProbeProvider {

        @Probe(name = "requestStarted", moduleName = "web", providerName = "requests", self = true)
        void requestStarted(@ProbeParam("path") String path, @ProbeParam("durationMillis") long durationMillis) {
        }
    }

    @SuppressWarnings("rawtypes")
    private static final class RecordingMBeanCallback implements MBeanListener.Callback {
        private final List<ObjectName> registered = new ArrayList<>();
        private final List<ObjectName> unregistered = new ArrayList<>();

        @Override
        public void mbeanRegistered(ObjectName objectName, MBeanListener listener) {
            registered.add(objectName);
        }

        @Override
        public void mbeanUnregistered(ObjectName objectName, MBeanListener listener) {
            unregistered.add(objectName);
        }
    }

    private static final class RecordingStatsProviderDelegate implements StatsProviderManagerDelegate {
        private final List<StatsProviderInfo> registered = new ArrayList<>();
        private final List<Object> unregistered = new ArrayList<>();

        @Override
        public void register(StatsProviderInfo statsProviderInfo) {
            registered.add(statsProviderInfo);
        }

        @Override
        public void unregister(Object statsProvider) {
            unregistered.add(statsProvider);
        }

        @Override
        public boolean hasListeners(String probeStr) {
            return registered.stream()
                    .map(StatsProviderInfo::getSubTreeRoot)
                    .anyMatch(probeStr::equals);
        }
    }
}
