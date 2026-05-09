/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_mapreduce_client_app;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapred.JobID;
import org.apache.hadoop.mapred.TaskUmbilicalProtocol;
import org.apache.hadoop.mapred.WrappedJvmID;
import org.apache.hadoop.mapreduce.MRJobConfig;
import org.apache.hadoop.mapreduce.TaskCounter;
import org.apache.hadoop.mapreduce.v2.api.MRClientProtocolPB;
import org.apache.hadoop.mapreduce.v2.api.records.JobId;
import org.apache.hadoop.mapreduce.v2.api.records.Phase;
import org.apache.hadoop.mapreduce.v2.api.records.TaskAttemptId;
import org.apache.hadoop.mapreduce.v2.api.records.TaskAttemptState;
import org.apache.hadoop.mapreduce.v2.api.records.TaskId;
import org.apache.hadoop.mapreduce.v2.api.records.TaskType;
import org.apache.hadoop.mapreduce.v2.app.AppContext;
import org.apache.hadoop.mapreduce.v2.app.ClusterInfo;
import org.apache.hadoop.mapreduce.v2.app.MRClientSecurityInfo;
import org.apache.hadoop.mapreduce.v2.app.job.Job;
import org.apache.hadoop.mapreduce.v2.app.job.event.JobCounterUpdateEvent;
import org.apache.hadoop.mapreduce.v2.app.job.event.JobDiagnosticsUpdateEvent;
import org.apache.hadoop.mapreduce.v2.app.job.event.JobEventType;
import org.apache.hadoop.mapreduce.v2.app.job.event.TaskAttemptDiagnosticsUpdateEvent;
import org.apache.hadoop.mapreduce.v2.app.job.event.TaskAttemptEventType;
import org.apache.hadoop.mapreduce.v2.app.job.event.TaskAttemptKillEvent;
import org.apache.hadoop.mapreduce.v2.app.job.event.TaskAttemptStatusUpdateEvent.TaskAttemptStatus;
import org.apache.hadoop.mapreduce.v2.app.launcher.ContainerLauncher;
import org.apache.hadoop.mapreduce.v2.app.launcher.ContainerLauncherEvent;
import org.apache.hadoop.mapreduce.v2.app.rm.ContainerAllocator;
import org.apache.hadoop.mapreduce.v2.app.rm.ContainerFailedEvent;
import org.apache.hadoop.mapreduce.v2.app.rm.ContainerRequestEvent;
import org.apache.hadoop.mapreduce.v2.app.rm.ResourceCalculatorUtils;
import org.apache.hadoop.mapreduce.v2.app.security.authorize.MRAMPolicyProvider;
import org.apache.hadoop.mapreduce.v2.app.speculate.DataStatistics;
import org.apache.hadoop.mapreduce.v2.app.speculate.Speculator;
import org.apache.hadoop.mapreduce.v2.app.speculate.SpeculatorEvent;
import org.apache.hadoop.mapreduce.v2.app.webapp.dao.BlacklistedNodesInfo;
import org.apache.hadoop.mapreduce.v2.app.webapp.dao.ConfEntryInfo;
import org.apache.hadoop.mapreduce.v2.util.MRBuilderUtils;
import org.apache.hadoop.security.authorize.Service;
import org.apache.hadoop.security.token.TokenInfo;
import org.apache.hadoop.yarn.api.records.ApplicationAttemptId;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.event.EventHandler;
import org.apache.hadoop.yarn.proto.YarnServiceProtos.SchedulerResourceTypes;
import org.apache.hadoop.yarn.security.client.ClientToAMTokenSecretManager;
import org.apache.hadoop.yarn.security.client.ClientToAMTokenSelector;
import org.apache.hadoop.yarn.util.Clock;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

public class Hadoop_mapreduce_client_appTest {
    @Test
    void dataStatisticsTracksAggregatesAndOutlierThresholds() {
        DataStatistics statistics = new DataStatistics(2.0);

        statistics.add(4.0);
        statistics.add(8.0);
        statistics.updateStatistics(8.0, 10.0);

        assertThat(statistics.count()).isEqualTo(3.0);
        assertThat(statistics.mean()).isCloseTo(16.0 / 3.0, within(0.000001));
        assertThat(statistics.var()).isCloseTo(104.0 / 9.0, within(0.000001));
        assertThat(statistics.std()).isCloseTo(Math.sqrt(104.0 / 9.0), within(0.000001));
        assertThat(statistics.outlier(2.0f)).isCloseTo(statistics.mean() + 2.0 * statistics.std(), within(0.000001));
        assertThat(statistics.toString()).contains("count is 3", "sum is 16.0", "mean is");
    }

    @Test
    void resourceCalculationsRespectMemoryOnlyAndCpuAwareScheduling() {
        Resource available = Resource.newInstance(8192, 8);
        Resource required = Resource.newInstance(2048, 3);
        EnumSet<SchedulerResourceTypes> memoryOnly = EnumSet.noneOf(SchedulerResourceTypes.class);
        EnumSet<SchedulerResourceTypes> cpuAware = EnumSet.of(SchedulerResourceTypes.CPU);

        assertThat(ResourceCalculatorUtils.divideAndCeil(5, 2)).isEqualTo(3);
        assertThat(ResourceCalculatorUtils.divideAndCeil(10, 0)).isZero();
        assertThat(ResourceCalculatorUtils.computeAvailableContainers(available, required, memoryOnly)).isEqualTo(4);
        assertThat(ResourceCalculatorUtils.computeAvailableContainers(available, required, cpuAware)).isEqualTo(2);
        assertThat(ResourceCalculatorUtils.divideAndCeilContainers(required, Resource.newInstance(1024, 2), memoryOnly))
                .isEqualTo(2);
        assertThat(ResourceCalculatorUtils.divideAndCeilContainers(required, Resource.newInstance(1024, 2), cpuAware))
                .isEqualTo(2);
    }

    @Test
    void clusterInfoExposesMutableMaximumContainerCapability() {
        ClusterInfo clusterInfo = new ClusterInfo();
        Resource originalCapability = clusterInfo.getMaxContainerCapability();
        Resource updatedCapability = Resource.newInstance(4096, 4);

        clusterInfo.setMaxContainerCapability(updatedCapability);

        assertThat(originalCapability).isNotNull();
        assertThat(clusterInfo.getMaxContainerCapability()).isSameAs(updatedCapability);
        assertThat(new ClusterInfo(updatedCapability).getMaxContainerCapability()).isSameAs(updatedCapability);
    }

    @Test
    void containerRequestEventsExposeLocalityCapabilityAndFailureRetryState() {
        TaskAttemptId attemptId = newTaskAttemptId();
        Resource capability = Resource.newInstance(1536, 2);
        ContainerRequestEvent request = new ContainerRequestEvent(
                attemptId,
                capability,
                new String[] {"node1.example.test", "node2.example.test"},
                new String[] {"/rack-a"});
        ContainerRequestEvent retry = ContainerRequestEvent.createContainerRequestEventForFailedContainer(
                attemptId,
                capability);
        ContainerFailedEvent failed = new ContainerFailedEvent(attemptId, "node1.example.test:8041");

        assertThat(request.getType()).isEqualTo(ContainerAllocator.EventType.CONTAINER_REQ);
        assertThat(request.getAttemptID()).isSameAs(attemptId);
        assertThat(request.getCapability()).isSameAs(capability);
        assertThat(request.getHosts()).containsExactly("node1.example.test", "node2.example.test");
        assertThat(request.getRacks()).containsExactly("/rack-a");
        assertThat(request.getEarlierAttemptFailed()).isFalse();
        assertThat(retry.getEarlierAttemptFailed()).isTrue();
        assertThat(retry.getHosts()).isEmpty();
        assertThat(failed.getType()).isEqualTo(ContainerAllocator.EventType.CONTAINER_FAILED);
        assertThat(failed.getContMgrAddress()).isEqualTo("node1.example.test:8041");
    }

    @Test
    void jobAndTaskAttemptEventsKeepTheirIdentifiersTypesAndPayloads() {
        JobId jobId = newJobId();
        TaskAttemptId attemptId = newTaskAttemptId();
        JobCounterUpdateEvent counterUpdate = new JobCounterUpdateEvent(jobId);
        JobDiagnosticsUpdateEvent jobDiagnostics = new JobDiagnosticsUpdateEvent(jobId, "job diagnostic");
        TaskAttemptDiagnosticsUpdateEvent attemptDiagnostics = new TaskAttemptDiagnosticsUpdateEvent(
                attemptId,
                "attempt diagnostic");
        TaskAttemptKillEvent kill = new TaskAttemptKillEvent(attemptId, "preempted", true);

        counterUpdate.addCounterUpdate(TaskCounter.MAP_INPUT_RECORDS, 37L);

        assertThat(counterUpdate.getType()).isEqualTo(JobEventType.JOB_COUNTER_UPDATE);
        assertThat(counterUpdate.getJobId()).isSameAs(jobId);
        assertThat(counterUpdate.getCounterUpdates()).hasSize(1);
        assertThat(counterUpdate.getCounterUpdates().get(0).getCounterKey()).isEqualTo(TaskCounter.MAP_INPUT_RECORDS);
        assertThat(counterUpdate.getCounterUpdates().get(0).getIncrementValue()).isEqualTo(37L);
        assertThat(jobDiagnostics.getType()).isEqualTo(JobEventType.JOB_DIAGNOSTIC_UPDATE);
        assertThat(jobDiagnostics.getDiagnosticUpdate()).isEqualTo("job diagnostic");
        assertThat(attemptDiagnostics.getType()).isEqualTo(TaskAttemptEventType.TA_DIAGNOSTICS_UPDATE);
        assertThat(attemptDiagnostics.getDiagnosticInfo()).isEqualTo("attempt diagnostic");
        assertThat(kill.getType()).isEqualTo(TaskAttemptEventType.TA_KILL);
        assertThat(kill.getTaskAttemptID()).isSameAs(attemptId);
        assertThat(kill.getMessage()).isEqualTo("preempted");
        assertThat(kill.getRescheduleAttempt()).isTrue();
    }

    @Test
    void speculatorEventsCaptureJobAttemptStatusAndContainerDemandChanges() {
        JobId jobId = newJobId();
        TaskAttemptId attemptId = newTaskAttemptId();
        TaskId taskId = attemptId.getTaskId();
        long timestamp = 1_714_000_123_000L;
        TaskAttemptStatus status = new TaskAttemptStatus();
        status.id = attemptId;
        status.progress = 0.75f;
        status.stateString = "shuffle in progress";
        status.phase = Phase.SHUFFLE;
        status.taskState = TaskAttemptState.RUNNING;

        SpeculatorEvent jobCreated = new SpeculatorEvent(jobId, timestamp);
        SpeculatorEvent attemptStarted = new SpeculatorEvent(attemptId, false, timestamp + 1);
        SpeculatorEvent statusUpdated = new SpeculatorEvent(status, timestamp + 2);
        SpeculatorEvent containerDemandChanged = new SpeculatorEvent(taskId, -1);

        assertThat(jobCreated.getType()).isEqualTo(Speculator.EventType.JOB_CREATE);
        assertThat(jobCreated.getJobID()).isSameAs(jobId);
        assertThat(jobCreated.getTimestamp()).isEqualTo(timestamp);
        assertThat(attemptStarted.getType()).isEqualTo(Speculator.EventType.ATTEMPT_START);
        assertThat(attemptStarted.getTaskID()).isEqualTo(taskId);
        assertThat(attemptStarted.getReportedStatus().id).isSameAs(attemptId);
        assertThat(attemptStarted.getTimestamp()).isEqualTo(timestamp + 1);
        assertThat(statusUpdated.getType()).isEqualTo(Speculator.EventType.ATTEMPT_STATUS_UPDATE);
        assertThat(statusUpdated.getReportedStatus()).isSameAs(status);
        assertThat(statusUpdated.getReportedStatus().progress).isEqualTo(0.75f);
        assertThat(statusUpdated.getReportedStatus().stateString).isEqualTo("shuffle in progress");
        assertThat(statusUpdated.getReportedStatus().phase).isEqualTo(Phase.SHUFFLE);
        assertThat(statusUpdated.getReportedStatus().taskState).isEqualTo(TaskAttemptState.RUNNING);
        assertThat(statusUpdated.getTimestamp()).isEqualTo(timestamp + 2);
        assertThat(containerDemandChanged.getType()).isEqualTo(Speculator.EventType.TASK_CONTAINER_NEED_UPDATE);
        assertThat(containerDemandChanged.getTaskID()).isSameAs(taskId);
        assertThat(containerDemandChanged.containersNeededChange()).isEqualTo(-1);
    }

    @Test
    void containerLauncherEventsCompareByLaunchTargetAndExposePayload() {
        TaskAttemptId attemptId = newTaskAttemptId();
        ContainerLauncherEvent launch = new ContainerLauncherEvent(
                attemptId,
                null,
                "node-manager.example.test:1234",
                null,
                ContainerLauncher.EventType.CONTAINER_REMOTE_LAUNCH);
        ContainerLauncherEvent equivalentLaunch = new ContainerLauncherEvent(
                attemptId,
                null,
                "node-manager.example.test:1234",
                null,
                ContainerLauncher.EventType.CONTAINER_REMOTE_LAUNCH);
        ContainerLauncherEvent cleanup = new ContainerLauncherEvent(
                attemptId,
                null,
                "other-node-manager.example.test:1234",
                null,
                ContainerLauncher.EventType.CONTAINER_REMOTE_CLEANUP);

        assertThat(launch.getType()).isEqualTo(ContainerLauncher.EventType.CONTAINER_REMOTE_LAUNCH);
        assertThat(launch.getTaskAttemptID()).isSameAs(attemptId);
        assertThat(launch.getContainerMgrAddress()).isEqualTo("node-manager.example.test:1234");
        assertThat(launch.getContainerID()).isNull();
        assertThat(launch.getContainerToken()).isNull();
        assertThat(launch).isEqualTo(equivalentLaunch).hasSameHashCodeAs(equivalentLaunch);
        assertThat(launch).isNotEqualTo(cleanup);
        assertThat(launch.toString()).contains("CONTAINER_REMOTE_LAUNCH", attemptId.toString());
    }

    @Test
    void wrappedJvmIdPublishesMapReduceJvmIdentity() {
        JobID jobId = new JobID("202405090101", 12);
        WrappedJvmID mapJvm = new WrappedJvmID(jobId, true, 34L);
        WrappedJvmID reduceJvm = new WrappedJvmID(jobId, false, 35L);

        assertThat(mapJvm.getJobId()).isSameAs(jobId);
        assertThat(mapJvm.isMapJVM()).isTrue();
        assertThat(mapJvm.getId()).isEqualTo(34L);
        assertThat(mapJvm.toString()).isEqualTo("jvm_202405090101_0012_m_000034");
        assertThat(mapJvm).isNotEqualTo(reduceJvm);
    }

    @Test
    void webAppDaoObjectsExposeConfigurationEntriesAndBlacklistedNodes() {
        ConfEntryInfo entry = new ConfEntryInfo(
                "mapreduce.job.queuename",
                "analytics",
                new String[] {"programmatic"});
        Set<String> blacklistedNodes = Set.of("worker-a.example.test", "worker-b.example.test");
        BlacklistedNodesInfo nodesInfo = new BlacklistedNodesInfo(new StaticBlacklistedNodesContext(blacklistedNodes));

        assertThat(entry.getName()).isEqualTo("mapreduce.job.queuename");
        assertThat(entry.getValue()).isEqualTo("analytics");
        assertThat(entry.getSource()).containsExactly("programmatic");
        assertThat(nodesInfo.getBlacklistedNodes()).containsExactlyInAnyOrderElementsOf(blacklistedNodes);
    }

    @Test
    void securityInfoAndPolicyProviderExposeMapReduceApplicationProtocols() {
        Configuration configuration = new Configuration(false);
        MRClientSecurityInfo securityInfo = new MRClientSecurityInfo();
        TokenInfo tokenInfo = securityInfo.getTokenInfo(MRClientProtocolPB.class, configuration);
        Service[] services = new MRAMPolicyProvider().getServices();

        assertThat(securityInfo.getKerberosInfo(MRClientProtocolPB.class, configuration)).isNull();
        assertThat(securityInfo.getTokenInfo(String.class, configuration)).isNull();
        assertThat(tokenInfo).isNotNull();
        assertThat(tokenInfo.value()).isEqualTo(ClientToAMTokenSelector.class);
        assertThat(services).hasSize(2);
        assertThat(services).extracting(Service::getServiceKey).containsExactly(
                MRJobConfig.MR_AM_SECURITY_SERVICE_AUTHORIZATION_TASK_UMBILICAL,
                MRJobConfig.MR_AM_SECURITY_SERVICE_AUTHORIZATION_CLIENT);
        assertThat(services).extracting(Service::getProtocol).containsExactly(
                TaskUmbilicalProtocol.class,
                MRClientProtocolPB.class);
    }

    private static final class StaticBlacklistedNodesContext implements AppContext {
        private final Set<String> blacklistedNodes;

        private StaticBlacklistedNodesContext(Set<String> blacklistedNodes) {
            this.blacklistedNodes = blacklistedNodes;
        }

        @Override
        public ApplicationId getApplicationID() {
            return null;
        }

        @Override
        public ApplicationAttemptId getApplicationAttemptId() {
            return null;
        }

        @Override
        public String getApplicationName() {
            return null;
        }

        @Override
        public long getStartTime() {
            return 0L;
        }

        @Override
        public CharSequence getUser() {
            return null;
        }

        @Override
        public Job getJob(JobId jobId) {
            return null;
        }

        @Override
        public Map<JobId, Job> getAllJobs() {
            return Map.of();
        }

        @Override
        @SuppressWarnings("rawtypes")
        public EventHandler getEventHandler() {
            return null;
        }

        @Override
        public Clock getClock() {
            return null;
        }

        @Override
        public ClusterInfo getClusterInfo() {
            return null;
        }

        @Override
        public Set<String> getBlacklistedNodes() {
            return blacklistedNodes;
        }

        @Override
        public ClientToAMTokenSecretManager getClientToAMTokenSecretManager() {
            return null;
        }

        @Override
        public boolean isLastAMRetry() {
            return false;
        }

        @Override
        public boolean hasSuccessfullyUnregistered() {
            return false;
        }

        @Override
        public String getNMHostname() {
            return null;
        }
    }

    private static JobId newJobId() {
        return MRBuilderUtils.newJobId(1_714_000_000_000L, 7, 3);
    }

    private static TaskAttemptId newTaskAttemptId() {
        JobId jobId = newJobId();
        TaskId taskId = MRBuilderUtils.newTaskId(jobId, 42, TaskType.MAP);
        return MRBuilderUtils.newTaskAttemptId(taskId, 2);
    }
}
