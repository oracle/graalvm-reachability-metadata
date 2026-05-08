/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud.google_cloud_monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.Metric;
import com.google.api.MetricDescriptor;
import com.google.api.MonitoredResource;
import com.google.api.MonitoredResourceDescriptor;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.gax.core.BackgroundResource;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcCallContext;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.ClientSettings;
import com.google.api.gax.rpc.PageContext;
import com.google.api.gax.rpc.PagedListDescriptor;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.cloud.monitoring.v3.AlertPolicyServiceClient;
import com.google.cloud.monitoring.v3.AlertPolicyServiceSettings;
import com.google.cloud.monitoring.v3.GroupServiceClient;
import com.google.cloud.monitoring.v3.GroupServiceSettings;
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.cloud.monitoring.v3.MetricServiceSettings;
import com.google.cloud.monitoring.v3.NotificationChannelServiceClient;
import com.google.cloud.monitoring.v3.NotificationChannelServiceSettings;
import com.google.cloud.monitoring.v3.QueryServiceClient;
import com.google.cloud.monitoring.v3.QueryServiceSettings;
import com.google.cloud.monitoring.v3.ServiceMonitoringServiceClient;
import com.google.cloud.monitoring.v3.ServiceMonitoringServiceSettings;
import com.google.cloud.monitoring.v3.SnoozeServiceClient;
import com.google.cloud.monitoring.v3.SnoozeServiceSettings;
import com.google.cloud.monitoring.v3.UptimeCheckServiceClient;
import com.google.cloud.monitoring.v3.UptimeCheckServiceSettings;
import com.google.cloud.monitoring.v3.stub.AlertPolicyServiceStub;
import com.google.cloud.monitoring.v3.stub.GroupServiceStub;
import com.google.cloud.monitoring.v3.stub.MetricServiceStub;
import com.google.cloud.monitoring.v3.stub.NotificationChannelServiceStub;
import com.google.cloud.monitoring.v3.stub.QueryServiceStub;
import com.google.cloud.monitoring.v3.stub.ServiceMonitoringServiceStub;
import com.google.cloud.monitoring.v3.stub.SnoozeServiceStub;
import com.google.cloud.monitoring.v3.stub.UptimeCheckServiceStub;
import com.google.monitoring.v3.AlertPolicy;
import com.google.monitoring.v3.CreateAlertPolicyRequest;
import com.google.monitoring.v3.CreateGroupRequest;
import com.google.monitoring.v3.CreateMetricDescriptorRequest;
import com.google.monitoring.v3.CreateNotificationChannelRequest;
import com.google.monitoring.v3.CreateServiceLevelObjectiveRequest;
import com.google.monitoring.v3.CreateServiceRequest;
import com.google.monitoring.v3.CreateSnoozeRequest;
import com.google.monitoring.v3.CreateTimeSeriesRequest;
import com.google.monitoring.v3.CreateUptimeCheckConfigRequest;
import com.google.monitoring.v3.DeleteAlertPolicyRequest;
import com.google.monitoring.v3.DeleteGroupRequest;
import com.google.monitoring.v3.DeleteMetricDescriptorRequest;
import com.google.monitoring.v3.DeleteNotificationChannelRequest;
import com.google.monitoring.v3.DeleteServiceLevelObjectiveRequest;
import com.google.monitoring.v3.DeleteServiceRequest;
import com.google.monitoring.v3.DeleteUptimeCheckConfigRequest;
import com.google.monitoring.v3.GetAlertPolicyRequest;
import com.google.monitoring.v3.GetGroupRequest;
import com.google.monitoring.v3.GetMetricDescriptorRequest;
import com.google.monitoring.v3.GetNotificationChannelVerificationCodeRequest;
import com.google.monitoring.v3.GetNotificationChannelVerificationCodeResponse;
import com.google.monitoring.v3.GetServiceRequest;
import com.google.monitoring.v3.GetSnoozeRequest;
import com.google.monitoring.v3.GetUptimeCheckConfigRequest;
import com.google.monitoring.v3.Group;
import com.google.monitoring.v3.ListGroupMembersRequest;
import com.google.monitoring.v3.ListGroupMembersResponse;
import com.google.monitoring.v3.ListGroupsRequest;
import com.google.monitoring.v3.ListGroupsResponse;
import com.google.monitoring.v3.ListMetricDescriptorsRequest;
import com.google.monitoring.v3.ListMetricDescriptorsResponse;
import com.google.monitoring.v3.ListMonitoredResourceDescriptorsRequest;
import com.google.monitoring.v3.ListMonitoredResourceDescriptorsResponse;
import com.google.monitoring.v3.ListTimeSeriesRequest;
import com.google.monitoring.v3.ListTimeSeriesResponse;
import com.google.monitoring.v3.ListUptimeCheckIpsRequest;
import com.google.monitoring.v3.ListUptimeCheckIpsResponse;
import com.google.monitoring.v3.NotificationChannel;
import com.google.monitoring.v3.ProjectName;
import com.google.monitoring.v3.QueryTimeSeriesRequest;
import com.google.monitoring.v3.QueryTimeSeriesResponse;
import com.google.monitoring.v3.SendNotificationChannelVerificationCodeRequest;
import com.google.monitoring.v3.Service;
import com.google.monitoring.v3.ServiceLevelObjective;
import com.google.monitoring.v3.Snooze;
import com.google.monitoring.v3.TimeInterval;
import com.google.monitoring.v3.TimeSeries;
import com.google.monitoring.v3.UpdateAlertPolicyRequest;
import com.google.monitoring.v3.UpdateGroupRequest;
import com.google.monitoring.v3.UpdateNotificationChannelRequest;
import com.google.monitoring.v3.UpdateServiceLevelObjectiveRequest;
import com.google.monitoring.v3.UpdateSnoozeRequest;
import com.google.monitoring.v3.UpdateUptimeCheckConfigRequest;
import com.google.monitoring.v3.UptimeCheckConfig;
import com.google.monitoring.v3.VerifyNotificationChannelRequest;
import com.google.protobuf.Empty;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Timestamp;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

public class Google_cloud_monitoringTest {
    private static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";
    private static final String LOCAL_ENDPOINT = "localhost:1";
    private static final String PROJECT_ID = "monitoring-test-project";
    private static final String PROJECT_NAME = "projects/" + PROJECT_ID;
    private static final String METRIC_TYPE = "custom.googleapis.com/test/latency";
    private static final String METRIC_DESCRIPTOR_NAME = PROJECT_NAME + "/metricDescriptors/" + METRIC_TYPE;
    private static final String ALERT_POLICY_NAME = PROJECT_NAME + "/alertPolicies/alert-policy-1";
    private static final String GROUP_NAME = PROJECT_NAME + "/groups/group-1";
    private static final String NOTIFICATION_CHANNEL_NAME = PROJECT_NAME + "/notificationChannels/channel-1";
    private static final String UPTIME_CHECK_CONFIG_NAME = PROJECT_NAME + "/uptimeCheckConfigs/uptime-1";
    private static final String SNOOZE_NAME = PROJECT_NAME + "/snoozes/snooze-1";
    private static final String SERVICE_NAME = PROJECT_NAME + "/services/service-1";
    private static final String SLO_NAME = SERVICE_NAME + "/serviceLevelObjectives/slo-1";

    @Test
    void settingsBuildersUseMonitoringDefaultsAndAllowOfflineCredentials() throws IOException {
        assertOfflineSettings(
                MetricServiceSettings.newBuilder()
                        .setEndpoint(LOCAL_ENDPOINT)
                        .setCredentialsProvider(NoCredentialsProvider.create())
                        .build(),
                MetricServiceSettings.getDefaultEndpoint(),
                MetricServiceSettings.getDefaultServiceScopes());
        assertOfflineSettings(
                AlertPolicyServiceSettings.newBuilder()
                        .setEndpoint(LOCAL_ENDPOINT)
                        .setCredentialsProvider(NoCredentialsProvider.create())
                        .build(),
                AlertPolicyServiceSettings.getDefaultEndpoint(),
                AlertPolicyServiceSettings.getDefaultServiceScopes());
        assertOfflineSettings(
                GroupServiceSettings.newBuilder()
                        .setEndpoint(LOCAL_ENDPOINT)
                        .setCredentialsProvider(NoCredentialsProvider.create())
                        .build(),
                GroupServiceSettings.getDefaultEndpoint(),
                GroupServiceSettings.getDefaultServiceScopes());
        assertOfflineSettings(
                NotificationChannelServiceSettings.newBuilder()
                        .setEndpoint(LOCAL_ENDPOINT)
                        .setCredentialsProvider(NoCredentialsProvider.create())
                        .build(),
                NotificationChannelServiceSettings.getDefaultEndpoint(),
                NotificationChannelServiceSettings.getDefaultServiceScopes());
        assertOfflineSettings(
                UptimeCheckServiceSettings.newBuilder()
                        .setEndpoint(LOCAL_ENDPOINT)
                        .setCredentialsProvider(NoCredentialsProvider.create())
                        .build(),
                UptimeCheckServiceSettings.getDefaultEndpoint(),
                UptimeCheckServiceSettings.getDefaultServiceScopes());
        assertOfflineSettings(
                QueryServiceSettings.newBuilder()
                        .setEndpoint(LOCAL_ENDPOINT)
                        .setCredentialsProvider(NoCredentialsProvider.create())
                        .build(),
                QueryServiceSettings.getDefaultEndpoint(),
                QueryServiceSettings.getDefaultServiceScopes());
        assertOfflineSettings(
                SnoozeServiceSettings.newBuilder()
                        .setEndpoint(LOCAL_ENDPOINT)
                        .setCredentialsProvider(NoCredentialsProvider.create())
                        .build(),
                SnoozeServiceSettings.getDefaultEndpoint(),
                SnoozeServiceSettings.getDefaultServiceScopes());
        assertOfflineSettings(
                ServiceMonitoringServiceSettings.newBuilder()
                        .setEndpoint(LOCAL_ENDPOINT)
                        .setCredentialsProvider(NoCredentialsProvider.create())
                        .build(),
                ServiceMonitoringServiceSettings.getDefaultEndpoint(),
                ServiceMonitoringServiceSettings.getDefaultServiceScopes());
    }

    @Test
    void metricServiceClientBuildsRequestsForMetricDescriptorsAndTimeSeries() {
        FakeMetricServiceStub stub = new FakeMetricServiceStub();
        MetricDescriptor descriptor = metricDescriptor();
        TimeSeries timeSeries = timeSeries();

        try (MetricServiceClient client = MetricServiceClient.create(stub)) {
            assertThat(client.getStub()).isSameAs(stub);
            assertThat(client.getMetricDescriptor(METRIC_DESCRIPTOR_NAME)).isEqualTo(descriptor);
            assertThat(stub.getMetricDescriptor.getLastRequest().getName()).isEqualTo(METRIC_DESCRIPTOR_NAME);

            assertThat(client.createMetricDescriptor(PROJECT_NAME, descriptor)).isEqualTo(descriptor);
            CreateMetricDescriptorRequest createRequest = stub.createMetricDescriptor.getLastRequest();
            assertThat(createRequest.getName()).isEqualTo(PROJECT_NAME);
            assertThat(createRequest.getMetricDescriptor()).isEqualTo(descriptor);

            client.deleteMetricDescriptor(METRIC_DESCRIPTOR_NAME);
            assertThat(stub.deleteMetricDescriptor.getLastRequest().getName()).isEqualTo(METRIC_DESCRIPTOR_NAME);

            client.createTimeSeries(PROJECT_NAME, Collections.singletonList(timeSeries));
            CreateTimeSeriesRequest createTimeSeriesRequest = stub.createTimeSeries.getLastRequest();
            assertThat(createTimeSeriesRequest.getName()).isEqualTo(PROJECT_NAME);
            assertThat(createTimeSeriesRequest.getTimeSeriesList()).containsExactly(timeSeries);

            client.createServiceTimeSeries(ProjectName.of(PROJECT_ID), Collections.singletonList(timeSeries));
            CreateTimeSeriesRequest createServiceTimeSeriesRequest = stub.createServiceTimeSeries.getLastRequest();
            assertThat(createServiceTimeSeriesRequest.getName()).isEqualTo(PROJECT_NAME);
            assertThat(createServiceTimeSeriesRequest.getTimeSeriesList()).containsExactly(timeSeries);
        }

        assertThat(stub.closed).isTrue();
    }

    @Test
    void metricServiceClientIteratesPagedMetricListResponses() {
        FakeMetricServiceStub stub = new FakeMetricServiceStub();
        MetricDescriptor descriptor = metricDescriptor();
        MonitoredResourceDescriptor resourceDescriptor = monitoredResourceDescriptor();
        TimeSeries timeSeries = timeSeries();
        TimeInterval interval = TimeInterval.newBuilder()
                .setStartTime(Timestamp.newBuilder().setSeconds(10).build())
                .setEndTime(Timestamp.newBuilder().setSeconds(20).build())
                .build();
        String filter = "metric.type = \"" + METRIC_TYPE + "\"";

        try (MetricServiceClient client = MetricServiceClient.create(stub)) {
            assertThat(client.listMetricDescriptors(PROJECT_NAME).iterateAll()).containsExactly(descriptor);
            assertThat(stub.listMetricDescriptors.getLastRequest().getName()).isEqualTo(PROJECT_NAME);

            assertThat(client.listMonitoredResourceDescriptors(ProjectName.of(PROJECT_ID)).iterateAll())
                    .containsExactly(resourceDescriptor);
            assertThat(stub.listMonitoredResourceDescriptors.getLastRequest().getName()).isEqualTo(PROJECT_NAME);

            assertThat(client.listTimeSeries(PROJECT_NAME, filter, interval, ListTimeSeriesRequest.TimeSeriesView.FULL)
                            .iterateAll())
                    .containsExactly(timeSeries);
            ListTimeSeriesRequest listTimeSeriesRequest = stub.listTimeSeries.getLastRequest();
            assertThat(listTimeSeriesRequest.getName()).isEqualTo(PROJECT_NAME);
            assertThat(listTimeSeriesRequest.getFilter()).isEqualTo(filter);
            assertThat(listTimeSeriesRequest.getInterval()).isEqualTo(interval);
            assertThat(listTimeSeriesRequest.getView()).isEqualTo(ListTimeSeriesRequest.TimeSeriesView.FULL);
        }
    }

    @Test
    void alertPolicyGroupAndNotificationClientsBuildMutationRequests() {
        FieldMask displayNameMask = FieldMask.newBuilder().addPaths("display_name").build();
        AlertPolicy alertPolicy = AlertPolicy.newBuilder()
                .setName(ALERT_POLICY_NAME)
                .setDisplayName("CPU saturation")
                .build();
        Group group = Group.newBuilder().setName(GROUP_NAME).setDisplayName("frontend").build();
        NotificationChannel channel = NotificationChannel.newBuilder()
                .setName(NOTIFICATION_CHANNEL_NAME)
                .setType("email")
                .setDisplayName("operations")
                .putLabels("email_address", "ops@example.com")
                .build();

        FakeAlertPolicyServiceStub alertStub = new FakeAlertPolicyServiceStub(alertPolicy);
        try (AlertPolicyServiceClient client = AlertPolicyServiceClient.create(alertStub)) {
            assertThat(client.getAlertPolicy(ALERT_POLICY_NAME)).isEqualTo(alertPolicy);
            assertThat(alertStub.getAlertPolicy.getLastRequest().getName()).isEqualTo(ALERT_POLICY_NAME);

            assertThat(client.createAlertPolicy(PROJECT_NAME, alertPolicy)).isEqualTo(alertPolicy);
            CreateAlertPolicyRequest createRequest = alertStub.createAlertPolicy.getLastRequest();
            assertThat(createRequest.getName()).isEqualTo(PROJECT_NAME);
            assertThat(createRequest.getAlertPolicy()).isEqualTo(alertPolicy);

            assertThat(client.updateAlertPolicy(displayNameMask, alertPolicy)).isEqualTo(alertPolicy);
            UpdateAlertPolicyRequest updateRequest = alertStub.updateAlertPolicy.getLastRequest();
            assertThat(updateRequest.getUpdateMask()).isEqualTo(displayNameMask);
            assertThat(updateRequest.getAlertPolicy()).isEqualTo(alertPolicy);

            client.deleteAlertPolicy(ALERT_POLICY_NAME);
            assertThat(alertStub.deleteAlertPolicy.getLastRequest().getName()).isEqualTo(ALERT_POLICY_NAME);
        }

        FakeGroupServiceStub groupStub = new FakeGroupServiceStub(group);
        try (GroupServiceClient client = GroupServiceClient.create(groupStub)) {
            assertThat(client.getGroup(GROUP_NAME)).isEqualTo(group);
            assertThat(groupStub.getGroup.getLastRequest().getName()).isEqualTo(GROUP_NAME);

            assertThat(client.createGroup(PROJECT_NAME, group)).isEqualTo(group);
            assertThat(groupStub.createGroup.getLastRequest().getName()).isEqualTo(PROJECT_NAME);

            assertThat(client.updateGroup(group)).isEqualTo(group);
            assertThat(groupStub.updateGroup.getLastRequest().getGroup()).isEqualTo(group);

            client.deleteGroup(GROUP_NAME);
            assertThat(groupStub.deleteGroup.getLastRequest().getName()).isEqualTo(GROUP_NAME);
        }

        FakeNotificationChannelServiceStub notificationStub = new FakeNotificationChannelServiceStub(channel);
        try (NotificationChannelServiceClient client = NotificationChannelServiceClient.create(notificationStub)) {
            assertThat(client.createNotificationChannel(PROJECT_NAME, channel)).isEqualTo(channel);
            CreateNotificationChannelRequest createRequest = notificationStub.createNotificationChannel.getLastRequest();
            assertThat(createRequest.getName()).isEqualTo(PROJECT_NAME);
            assertThat(createRequest.getNotificationChannel()).isEqualTo(channel);

            assertThat(client.updateNotificationChannel(displayNameMask, channel)).isEqualTo(channel);
            assertThat(notificationStub.updateNotificationChannel.getLastRequest().getUpdateMask()).isEqualTo(displayNameMask);

            client.deleteNotificationChannel(NOTIFICATION_CHANNEL_NAME, true);
            DeleteNotificationChannelRequest deleteRequest = notificationStub.deleteNotificationChannel.getLastRequest();
            assertThat(deleteRequest.getName()).isEqualTo(NOTIFICATION_CHANNEL_NAME);
            assertThat(deleteRequest.getForce()).isTrue();

            client.sendNotificationChannelVerificationCode(NOTIFICATION_CHANNEL_NAME);
            SendNotificationChannelVerificationCodeRequest sendRequest =
                    notificationStub.sendVerificationCode.getLastRequest();
            assertThat(sendRequest.getName()).isEqualTo(NOTIFICATION_CHANNEL_NAME);

            assertThat(client.getNotificationChannelVerificationCode(NOTIFICATION_CHANNEL_NAME).getCode()).isEqualTo("123456");
            GetNotificationChannelVerificationCodeRequest verificationCodeRequest =
                    notificationStub.getVerificationCode.getLastRequest();
            assertThat(verificationCodeRequest.getName()).isEqualTo(NOTIFICATION_CHANNEL_NAME);

            assertThat(client.verifyNotificationChannel(NOTIFICATION_CHANNEL_NAME, "123456")).isEqualTo(channel);
            VerifyNotificationChannelRequest verifyRequest = notificationStub.verifyNotificationChannel.getLastRequest();
            assertThat(verifyRequest.getName()).isEqualTo(NOTIFICATION_CHANNEL_NAME);
            assertThat(verifyRequest.getCode()).isEqualTo("123456");
        }
    }

    @Test
    void groupServiceClientIteratesPagedGroupsAndMembers() {
        Group group = Group.newBuilder().setName(GROUP_NAME).setDisplayName("frontend").build();
        MonitoredResource member = groupMember();
        TimeInterval interval = TimeInterval.newBuilder()
                .setStartTime(Timestamp.newBuilder().setSeconds(100).build())
                .setEndTime(Timestamp.newBuilder().setSeconds(200).build())
                .build();
        ListGroupsRequest groupsRequest = ListGroupsRequest.newBuilder()
                .setName(PROJECT_NAME)
                .setChildrenOfGroup(GROUP_NAME)
                .setPageSize(2)
                .build();
        ListGroupMembersRequest membersRequest = ListGroupMembersRequest.newBuilder()
                .setName(GROUP_NAME)
                .setFilter("resource.type = \"global\"")
                .setInterval(interval)
                .setPageSize(1)
                .build();

        FakeGroupServiceStub stub = new FakeGroupServiceStub(group);
        try (GroupServiceClient client = GroupServiceClient.create(stub)) {
            assertThat(client.listGroups(groupsRequest).iterateAll()).containsExactly(group);
            ListGroupsRequest capturedGroupsRequest = stub.listGroups.getLastRequest();
            assertThat(capturedGroupsRequest.getName()).isEqualTo(PROJECT_NAME);
            assertThat(capturedGroupsRequest.getChildrenOfGroup()).isEqualTo(GROUP_NAME);
            assertThat(capturedGroupsRequest.getPageSize()).isEqualTo(2);

            assertThat(client.listGroupMembers(membersRequest).iterateAll()).containsExactly(member);
            ListGroupMembersRequest capturedMembersRequest = stub.listGroupMembers.getLastRequest();
            assertThat(capturedMembersRequest.getName()).isEqualTo(GROUP_NAME);
            assertThat(capturedMembersRequest.getFilter()).isEqualTo("resource.type = \"global\"");
            assertThat(capturedMembersRequest.getInterval()).isEqualTo(interval);
            assertThat(capturedMembersRequest.getPageSize()).isEqualTo(1);
        }
    }

    @Test
    void uptimeSnoozeServiceMonitoringAndQueryClientsBuildRequests() {
        FieldMask displayNameMask = FieldMask.newBuilder().addPaths("display_name").build();
        UptimeCheckConfig uptimeCheckConfig = UptimeCheckConfig.newBuilder()
                .setName(UPTIME_CHECK_CONFIG_NAME)
                .setDisplayName("homepage")
                .build();
        Snooze snooze = Snooze.newBuilder().setName(SNOOZE_NAME).setDisplayName("maintenance").build();
        Service service = Service.newBuilder().setName(SERVICE_NAME).setDisplayName("checkout").build();
        ServiceLevelObjective objective = ServiceLevelObjective.newBuilder()
                .setName(SLO_NAME)
                .setDisplayName("availability")
                .build();

        FakeUptimeCheckServiceStub uptimeStub = new FakeUptimeCheckServiceStub(uptimeCheckConfig);
        try (UptimeCheckServiceClient client = UptimeCheckServiceClient.create(uptimeStub)) {
            assertThat(client.getUptimeCheckConfig(UPTIME_CHECK_CONFIG_NAME)).isEqualTo(uptimeCheckConfig);
            assertThat(uptimeStub.getUptimeCheckConfig.getLastRequest().getName()).isEqualTo(UPTIME_CHECK_CONFIG_NAME);

            assertThat(client.createUptimeCheckConfig(PROJECT_NAME, uptimeCheckConfig)).isEqualTo(uptimeCheckConfig);
            assertThat(uptimeStub.createUptimeCheckConfig.getLastRequest().getParent()).isEqualTo(PROJECT_NAME);

            assertThat(client.updateUptimeCheckConfig(uptimeCheckConfig)).isEqualTo(uptimeCheckConfig);
            UpdateUptimeCheckConfigRequest updateRequest = uptimeStub.updateUptimeCheckConfig.getLastRequest();
            assertThat(updateRequest.getUptimeCheckConfig()).isEqualTo(uptimeCheckConfig);

            client.deleteUptimeCheckConfig(UPTIME_CHECK_CONFIG_NAME);
            assertThat(uptimeStub.deleteUptimeCheckConfig.getLastRequest().getName()).isEqualTo(UPTIME_CHECK_CONFIG_NAME);

            ListUptimeCheckIpsResponse ipResponse = client.listUptimeCheckIpsCallable()
                    .call(ListUptimeCheckIpsRequest.newBuilder().setPageSize(1).build());
            assertThat(ipResponse).isEqualTo(ListUptimeCheckIpsResponse.getDefaultInstance());
            assertThat(uptimeStub.listUptimeCheckIps.getLastRequest().getPageSize()).isEqualTo(1);
        }

        FakeSnoozeServiceStub snoozeStub = new FakeSnoozeServiceStub(snooze);
        try (SnoozeServiceClient client = SnoozeServiceClient.create(snoozeStub)) {
            assertThat(client.createSnooze(PROJECT_NAME, snooze)).isEqualTo(snooze);
            CreateSnoozeRequest createRequest = snoozeStub.createSnooze.getLastRequest();
            assertThat(createRequest.getParent()).isEqualTo(PROJECT_NAME);
            assertThat(createRequest.getSnooze()).isEqualTo(snooze);

            assertThat(client.getSnooze(SNOOZE_NAME)).isEqualTo(snooze);
            assertThat(snoozeStub.getSnooze.getLastRequest().getName()).isEqualTo(SNOOZE_NAME);

            assertThat(client.updateSnooze(snooze, displayNameMask)).isEqualTo(snooze);
            UpdateSnoozeRequest updateRequest = snoozeStub.updateSnooze.getLastRequest();
            assertThat(updateRequest.getSnooze()).isEqualTo(snooze);
            assertThat(updateRequest.getUpdateMask()).isEqualTo(displayNameMask);
        }

        FakeServiceMonitoringServiceStub serviceStub = new FakeServiceMonitoringServiceStub(service, objective);
        try (ServiceMonitoringServiceClient client = ServiceMonitoringServiceClient.create(serviceStub)) {
            assertThat(client.createService(PROJECT_NAME, service)).isEqualTo(service);
            assertThat(serviceStub.createService.getLastRequest().getParent()).isEqualTo(PROJECT_NAME);

            assertThat(client.getService(SERVICE_NAME)).isEqualTo(service);
            assertThat(serviceStub.getService.getLastRequest().getName()).isEqualTo(SERVICE_NAME);

            assertThat(client.createServiceLevelObjective(SERVICE_NAME, objective)).isEqualTo(objective);
            assertThat(serviceStub.createServiceLevelObjective.getLastRequest().getParent()).isEqualTo(SERVICE_NAME);

            assertThat(client.updateServiceLevelObjective(objective)).isEqualTo(objective);
            assertThat(serviceStub.updateServiceLevelObjective.getLastRequest().getServiceLevelObjective()).isEqualTo(objective);

            client.deleteServiceLevelObjective(SLO_NAME);
            assertThat(serviceStub.deleteServiceLevelObjective.getLastRequest().getName()).isEqualTo(SLO_NAME);

            client.deleteService(SERVICE_NAME);
            assertThat(serviceStub.deleteService.getLastRequest().getName()).isEqualTo(SERVICE_NAME);
        }

        FakeQueryServiceStub queryStub = new FakeQueryServiceStub();
        try (QueryServiceClient client = QueryServiceClient.create(queryStub)) {
            QueryTimeSeriesRequest request = QueryTimeSeriesRequest.newBuilder()
                    .setName(PROJECT_NAME)
                    .setQuery("fetch gce_instance::compute.googleapis.com/instance/cpu/utilization")
                    .build();
            assertThat(client.queryTimeSeriesCallable().call(request)).isEqualTo(QueryTimeSeriesResponse.getDefaultInstance());
            assertThat(queryStub.queryTimeSeries.getLastRequest()).isEqualTo(request);
        }
    }

    private static void assertOfflineSettings(
            ClientSettings<?> settings, String defaultEndpoint, List<String> defaultServiceScopes) throws IOException {
        assertThat(settings.getEndpoint()).isEqualTo(LOCAL_ENDPOINT);
        assertThat(defaultEndpoint).isEqualTo("monitoring.googleapis.com:443");
        assertThat(defaultServiceScopes).contains(CLOUD_PLATFORM_SCOPE);
        assertThat(settings.getCredentialsProvider().getCredentials()).isNull();
        assertThat(settings.getTransportChannelProvider()).isNotNull();
        assertThat(settings.toBuilder().build().getEndpoint()).isEqualTo(LOCAL_ENDPOINT);
    }

    private static MetricDescriptor metricDescriptor() {
        return MetricDescriptor.newBuilder()
                .setName(METRIC_DESCRIPTOR_NAME)
                .setType(METRIC_TYPE)
                .setMetricKind(MetricDescriptor.MetricKind.GAUGE)
                .setValueType(MetricDescriptor.ValueType.DOUBLE)
                .setDisplayName("Latency")
                .build();
    }

    private static MonitoredResourceDescriptor monitoredResourceDescriptor() {
        return MonitoredResourceDescriptor.newBuilder()
                .setName(PROJECT_NAME + "/monitoredResourceDescriptors/global")
                .setType("global")
                .setDisplayName("Global")
                .build();
    }

    private static TimeSeries timeSeries() {
        TimeInterval interval = TimeInterval.newBuilder()
                .setStartTime(Timestamp.newBuilder().setSeconds(1).build())
                .setEndTime(Timestamp.newBuilder().setSeconds(2).build())
                .build();
        return TimeSeries.newBuilder()
                .setMetric(Metric.newBuilder().setType(METRIC_TYPE).build())
                .setResource(MonitoredResource.newBuilder()
                        .setType("global")
                        .putLabels("project_id", PROJECT_ID)
                        .build())
                .setMetricKind(MetricDescriptor.MetricKind.GAUGE)
                .setValueType(MetricDescriptor.ValueType.DOUBLE)
                .setUnit("ms")
                .setDescription(interval.toString())
                .build();
    }

    private static MonitoredResource groupMember() {
        return MonitoredResource.newBuilder()
                .setType("global")
                .putLabels("project_id", PROJECT_ID)
                .build();
    }

    private static PagedListDescriptor<ListGroupsRequest, ListGroupsResponse, Group> groupsPageDescriptor() {
        return new SimplePagedListDescriptor<>(
                (request, token) -> request.toBuilder().setPageToken(token).build(),
                (request, pageSize) -> request.toBuilder().setPageSize(pageSize).build(),
                ListGroupsRequest::getPageSize,
                ListGroupsResponse::getNextPageToken,
                ListGroupsResponse::getGroupList);
    }

    private static PagedListDescriptor<ListGroupMembersRequest, ListGroupMembersResponse, MonitoredResource>
            groupMembersPageDescriptor() {
        return new SimplePagedListDescriptor<>(
                (request, token) -> request.toBuilder().setPageToken(token).build(),
                (request, pageSize) -> request.toBuilder().setPageSize(pageSize).build(),
                ListGroupMembersRequest::getPageSize,
                ListGroupMembersResponse::getNextPageToken,
                ListGroupMembersResponse::getMembersList);
    }

    private static PagedListDescriptor<ListMonitoredResourceDescriptorsRequest,
                    ListMonitoredResourceDescriptorsResponse,
                    MonitoredResourceDescriptor>
            monitoredResourceDescriptorsPageDescriptor() {
        return new SimplePagedListDescriptor<>(
                (request, token) -> request.toBuilder().setPageToken(token).build(),
                (request, pageSize) -> request.toBuilder().setPageSize(pageSize).build(),
                ListMonitoredResourceDescriptorsRequest::getPageSize,
                ListMonitoredResourceDescriptorsResponse::getNextPageToken,
                ListMonitoredResourceDescriptorsResponse::getResourceDescriptorsList);
    }

    private static PagedListDescriptor<ListMetricDescriptorsRequest, ListMetricDescriptorsResponse, MetricDescriptor>
            metricDescriptorsPageDescriptor() {
        return new SimplePagedListDescriptor<>(
                (request, token) -> request.toBuilder().setPageToken(token).build(),
                (request, pageSize) -> request.toBuilder().setPageSize(pageSize).build(),
                ListMetricDescriptorsRequest::getPageSize,
                ListMetricDescriptorsResponse::getNextPageToken,
                ListMetricDescriptorsResponse::getMetricDescriptorsList);
    }

    private static PagedListDescriptor<ListTimeSeriesRequest, ListTimeSeriesResponse, TimeSeries>
            timeSeriesPageDescriptor() {
        return new SimplePagedListDescriptor<>(
                (request, token) -> request.toBuilder().setPageToken(token).build(),
                (request, pageSize) -> request.toBuilder().setPageSize(pageSize).build(),
                ListTimeSeriesRequest::getPageSize,
                ListTimeSeriesResponse::getNextPageToken,
                ListTimeSeriesResponse::getTimeSeriesList);
    }

    private static final class CapturingUnaryCallable<RequestT, ResponseT> extends UnaryCallable<RequestT, ResponseT> {
        private final ResponseT response;
        private RequestT lastRequest;

        private CapturingUnaryCallable(ResponseT response) {
            this.response = response;
        }

        @Override
        public ApiFuture<ResponseT> futureCall(RequestT request, ApiCallContext context) {
            lastRequest = request;
            return ApiFutures.immediateFuture(response);
        }

        private RequestT getLastRequest() {
            return lastRequest;
        }
    }

    @FunctionalInterface
    private interface PagedResponseFactory<RequestT, ResponseT, ResourceT, PagedResponseT> {
        ApiFuture<PagedResponseT> create(
                PageContext<RequestT, ResponseT, ResourceT> pageContext, ApiFuture<ResponseT> responseFuture);
    }

    private static final class CapturingPagedCallable<RequestT, ResponseT, ResourceT, PagedResponseT>
            extends UnaryCallable<RequestT, PagedResponseT> {
        private final CapturingUnaryCallable<RequestT, ResponseT> responseCallable;
        private final PagedListDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor;
        private final PagedResponseFactory<RequestT, ResponseT, ResourceT, PagedResponseT> responseFactory;

        private CapturingPagedCallable(
                ResponseT response,
                PagedListDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor,
                PagedResponseFactory<RequestT, ResponseT, ResourceT, PagedResponseT> responseFactory) {
            this.responseCallable = new CapturingUnaryCallable<>(response);
            this.pageDescriptor = pageDescriptor;
            this.responseFactory = responseFactory;
        }

        @Override
        public ApiFuture<PagedResponseT> futureCall(RequestT request, ApiCallContext context) {
            ApiCallContext callContext = context == null ? GrpcCallContext.createDefault() : context;
            ApiFuture<ResponseT> responseFuture = responseCallable.futureCall(request, callContext);
            PageContext<RequestT, ResponseT, ResourceT> pageContext =
                    PageContext.create(responseCallable, pageDescriptor, request, callContext);
            return responseFactory.create(pageContext, responseFuture);
        }

        private RequestT getLastRequest() {
            return responseCallable.getLastRequest();
        }
    }

    private static final class SimplePagedListDescriptor<RequestT, ResponseT, ResourceT>
            implements PagedListDescriptor<RequestT, ResponseT, ResourceT> {
        private final BiFunction<RequestT, String, RequestT> tokenInjector;
        private final BiFunction<RequestT, Integer, RequestT> pageSizeInjector;
        private final Function<RequestT, Integer> pageSizeExtractor;
        private final Function<ResponseT, String> nextTokenExtractor;
        private final Function<ResponseT, Iterable<ResourceT>> resourcesExtractor;

        private SimplePagedListDescriptor(
                BiFunction<RequestT, String, RequestT> tokenInjector,
                BiFunction<RequestT, Integer, RequestT> pageSizeInjector,
                Function<RequestT, Integer> pageSizeExtractor,
                Function<ResponseT, String> nextTokenExtractor,
                Function<ResponseT, Iterable<ResourceT>> resourcesExtractor) {
            this.tokenInjector = tokenInjector;
            this.pageSizeInjector = pageSizeInjector;
            this.pageSizeExtractor = pageSizeExtractor;
            this.nextTokenExtractor = nextTokenExtractor;
            this.resourcesExtractor = resourcesExtractor;
        }

        @Override
        public String emptyToken() {
            return "";
        }

        @Override
        public RequestT injectToken(RequestT request, String token) {
            return tokenInjector.apply(request, token);
        }

        @Override
        public RequestT injectPageSize(RequestT request, int pageSize) {
            return pageSizeInjector.apply(request, pageSize);
        }

        @Override
        public Integer extractPageSize(RequestT request) {
            return pageSizeExtractor.apply(request);
        }

        @Override
        public String extractNextToken(ResponseT response) {
            return nextTokenExtractor.apply(response);
        }

        @Override
        public Iterable<ResourceT> extractResources(ResponseT response) {
            return resourcesExtractor.apply(response);
        }
    }

    private interface ImmediateBackgroundResource extends BackgroundResource {
        @Override
        default void shutdown() {
        }

        @Override
        default boolean isShutdown() {
            return true;
        }

        @Override
        default boolean isTerminated() {
            return true;
        }

        @Override
        default void shutdownNow() {
        }

        @Override
        default boolean awaitTermination(long duration, TimeUnit unit) {
            return true;
        }
    }

    private static final class FakeMetricServiceStub extends MetricServiceStub implements ImmediateBackgroundResource {
        private final CapturingPagedCallable<ListMonitoredResourceDescriptorsRequest,
                        ListMonitoredResourceDescriptorsResponse,
                        MonitoredResourceDescriptor,
                        MetricServiceClient.ListMonitoredResourceDescriptorsPagedResponse> listMonitoredResourceDescriptors =
                new CapturingPagedCallable<>(
                        ListMonitoredResourceDescriptorsResponse.newBuilder()
                                .addResourceDescriptors(monitoredResourceDescriptor())
                                .build(),
                        monitoredResourceDescriptorsPageDescriptor(),
                        MetricServiceClient.ListMonitoredResourceDescriptorsPagedResponse::createAsync);
        private final CapturingPagedCallable<ListMetricDescriptorsRequest,
                        ListMetricDescriptorsResponse,
                        MetricDescriptor,
                        MetricServiceClient.ListMetricDescriptorsPagedResponse> listMetricDescriptors =
                new CapturingPagedCallable<>(
                        ListMetricDescriptorsResponse.newBuilder().addMetricDescriptors(metricDescriptor()).build(),
                        metricDescriptorsPageDescriptor(),
                        MetricServiceClient.ListMetricDescriptorsPagedResponse::createAsync);
        private final CapturingUnaryCallable<GetMetricDescriptorRequest, MetricDescriptor> getMetricDescriptor =
                new CapturingUnaryCallable<>(metricDescriptor());
        private final CapturingUnaryCallable<CreateMetricDescriptorRequest, MetricDescriptor> createMetricDescriptor =
                new CapturingUnaryCallable<>(metricDescriptor());
        private final CapturingUnaryCallable<DeleteMetricDescriptorRequest, Empty> deleteMetricDescriptor =
                new CapturingUnaryCallable<>(Empty.getDefaultInstance());
        private final CapturingPagedCallable<ListTimeSeriesRequest,
                        ListTimeSeriesResponse,
                        TimeSeries,
                        MetricServiceClient.ListTimeSeriesPagedResponse> listTimeSeries =
                new CapturingPagedCallable<>(
                        ListTimeSeriesResponse.newBuilder().addTimeSeries(timeSeries()).build(),
                        timeSeriesPageDescriptor(),
                        MetricServiceClient.ListTimeSeriesPagedResponse::createAsync);
        private final CapturingUnaryCallable<CreateTimeSeriesRequest, Empty> createTimeSeries =
                new CapturingUnaryCallable<>(Empty.getDefaultInstance());
        private final CapturingUnaryCallable<CreateTimeSeriesRequest, Empty> createServiceTimeSeries =
                new CapturingUnaryCallable<>(Empty.getDefaultInstance());
        private boolean closed;

        @Override
        public UnaryCallable<ListMonitoredResourceDescriptorsRequest,
                        MetricServiceClient.ListMonitoredResourceDescriptorsPagedResponse>
                listMonitoredResourceDescriptorsPagedCallable() {
            return listMonitoredResourceDescriptors;
        }

        @Override
        public UnaryCallable<ListMetricDescriptorsRequest, MetricServiceClient.ListMetricDescriptorsPagedResponse>
                listMetricDescriptorsPagedCallable() {
            return listMetricDescriptors;
        }

        @Override
        public UnaryCallable<GetMetricDescriptorRequest, MetricDescriptor> getMetricDescriptorCallable() {
            return getMetricDescriptor;
        }

        @Override
        public UnaryCallable<CreateMetricDescriptorRequest, MetricDescriptor> createMetricDescriptorCallable() {
            return createMetricDescriptor;
        }

        @Override
        public UnaryCallable<DeleteMetricDescriptorRequest, Empty> deleteMetricDescriptorCallable() {
            return deleteMetricDescriptor;
        }

        @Override
        public UnaryCallable<ListTimeSeriesRequest, MetricServiceClient.ListTimeSeriesPagedResponse>
                listTimeSeriesPagedCallable() {
            return listTimeSeries;
        }

        @Override
        public UnaryCallable<CreateTimeSeriesRequest, Empty> createTimeSeriesCallable() {
            return createTimeSeries;
        }

        @Override
        public UnaryCallable<CreateTimeSeriesRequest, Empty> createServiceTimeSeriesCallable() {
            return createServiceTimeSeries;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class FakeAlertPolicyServiceStub extends AlertPolicyServiceStub implements ImmediateBackgroundResource {
        private final CapturingUnaryCallable<GetAlertPolicyRequest, AlertPolicy> getAlertPolicy;
        private final CapturingUnaryCallable<CreateAlertPolicyRequest, AlertPolicy> createAlertPolicy;
        private final CapturingUnaryCallable<UpdateAlertPolicyRequest, AlertPolicy> updateAlertPolicy;
        private final CapturingUnaryCallable<DeleteAlertPolicyRequest, Empty> deleteAlertPolicy =
                new CapturingUnaryCallable<>(Empty.getDefaultInstance());

        private FakeAlertPolicyServiceStub(AlertPolicy alertPolicy) {
            getAlertPolicy = new CapturingUnaryCallable<>(alertPolicy);
            createAlertPolicy = new CapturingUnaryCallable<>(alertPolicy);
            updateAlertPolicy = new CapturingUnaryCallable<>(alertPolicy);
        }

        @Override
        public UnaryCallable<GetAlertPolicyRequest, AlertPolicy> getAlertPolicyCallable() {
            return getAlertPolicy;
        }

        @Override
        public UnaryCallable<CreateAlertPolicyRequest, AlertPolicy> createAlertPolicyCallable() {
            return createAlertPolicy;
        }

        @Override
        public UnaryCallable<UpdateAlertPolicyRequest, AlertPolicy> updateAlertPolicyCallable() {
            return updateAlertPolicy;
        }

        @Override
        public UnaryCallable<DeleteAlertPolicyRequest, Empty> deleteAlertPolicyCallable() {
            return deleteAlertPolicy;
        }

        @Override
        public void close() {
        }
    }

    private static final class FakeGroupServiceStub extends GroupServiceStub implements ImmediateBackgroundResource {
        private final CapturingPagedCallable<ListGroupsRequest,
                        ListGroupsResponse,
                        Group,
                        GroupServiceClient.ListGroupsPagedResponse> listGroups;
        private final CapturingUnaryCallable<GetGroupRequest, Group> getGroup;
        private final CapturingUnaryCallable<CreateGroupRequest, Group> createGroup;
        private final CapturingUnaryCallable<UpdateGroupRequest, Group> updateGroup;
        private final CapturingUnaryCallable<DeleteGroupRequest, Empty> deleteGroup =
                new CapturingUnaryCallable<>(Empty.getDefaultInstance());
        private final CapturingPagedCallable<ListGroupMembersRequest,
                        ListGroupMembersResponse,
                        MonitoredResource,
                        GroupServiceClient.ListGroupMembersPagedResponse> listGroupMembers =
                new CapturingPagedCallable<>(
                        ListGroupMembersResponse.newBuilder().addMembers(groupMember()).build(),
                        groupMembersPageDescriptor(),
                        GroupServiceClient.ListGroupMembersPagedResponse::createAsync);

        private FakeGroupServiceStub(Group group) {
            listGroups = new CapturingPagedCallable<>(
                    ListGroupsResponse.newBuilder().addGroup(group).build(),
                    groupsPageDescriptor(),
                    GroupServiceClient.ListGroupsPagedResponse::createAsync);
            getGroup = new CapturingUnaryCallable<>(group);
            createGroup = new CapturingUnaryCallable<>(group);
            updateGroup = new CapturingUnaryCallable<>(group);
        }

        @Override
        public UnaryCallable<ListGroupsRequest, GroupServiceClient.ListGroupsPagedResponse> listGroupsPagedCallable() {
            return listGroups;
        }

        @Override
        public UnaryCallable<GetGroupRequest, Group> getGroupCallable() {
            return getGroup;
        }

        @Override
        public UnaryCallable<CreateGroupRequest, Group> createGroupCallable() {
            return createGroup;
        }

        @Override
        public UnaryCallable<UpdateGroupRequest, Group> updateGroupCallable() {
            return updateGroup;
        }

        @Override
        public UnaryCallable<DeleteGroupRequest, Empty> deleteGroupCallable() {
            return deleteGroup;
        }

        @Override
        public UnaryCallable<ListGroupMembersRequest, GroupServiceClient.ListGroupMembersPagedResponse>
                listGroupMembersPagedCallable() {
            return listGroupMembers;
        }

        @Override
        public void close() {
        }
    }

    private static final class FakeNotificationChannelServiceStub extends NotificationChannelServiceStub implements ImmediateBackgroundResource {
        private final CapturingUnaryCallable<CreateNotificationChannelRequest, NotificationChannel> createNotificationChannel;
        private final CapturingUnaryCallable<UpdateNotificationChannelRequest, NotificationChannel> updateNotificationChannel;
        private final CapturingUnaryCallable<DeleteNotificationChannelRequest, Empty> deleteNotificationChannel =
                new CapturingUnaryCallable<>(Empty.getDefaultInstance());
        private final CapturingUnaryCallable<SendNotificationChannelVerificationCodeRequest, Empty> sendVerificationCode =
                new CapturingUnaryCallable<>(Empty.getDefaultInstance());
        private final CapturingUnaryCallable<GetNotificationChannelVerificationCodeRequest,
                        GetNotificationChannelVerificationCodeResponse> getVerificationCode =
                new CapturingUnaryCallable<>(GetNotificationChannelVerificationCodeResponse.newBuilder()
                        .setCode("123456")
                        .build());
        private final CapturingUnaryCallable<VerifyNotificationChannelRequest, NotificationChannel> verifyNotificationChannel;

        private FakeNotificationChannelServiceStub(NotificationChannel notificationChannel) {
            createNotificationChannel = new CapturingUnaryCallable<>(notificationChannel);
            updateNotificationChannel = new CapturingUnaryCallable<>(notificationChannel);
            verifyNotificationChannel = new CapturingUnaryCallable<>(notificationChannel);
        }

        @Override
        public UnaryCallable<CreateNotificationChannelRequest, NotificationChannel> createNotificationChannelCallable() {
            return createNotificationChannel;
        }

        @Override
        public UnaryCallable<UpdateNotificationChannelRequest, NotificationChannel> updateNotificationChannelCallable() {
            return updateNotificationChannel;
        }

        @Override
        public UnaryCallable<DeleteNotificationChannelRequest, Empty> deleteNotificationChannelCallable() {
            return deleteNotificationChannel;
        }

        @Override
        public UnaryCallable<SendNotificationChannelVerificationCodeRequest, Empty>
                sendNotificationChannelVerificationCodeCallable() {
            return sendVerificationCode;
        }

        @Override
        public UnaryCallable<GetNotificationChannelVerificationCodeRequest, GetNotificationChannelVerificationCodeResponse>
                getNotificationChannelVerificationCodeCallable() {
            return getVerificationCode;
        }

        @Override
        public UnaryCallable<VerifyNotificationChannelRequest, NotificationChannel> verifyNotificationChannelCallable() {
            return verifyNotificationChannel;
        }

        @Override
        public void close() {
        }
    }

    private static final class FakeUptimeCheckServiceStub extends UptimeCheckServiceStub implements ImmediateBackgroundResource {
        private final CapturingUnaryCallable<GetUptimeCheckConfigRequest, UptimeCheckConfig> getUptimeCheckConfig;
        private final CapturingUnaryCallable<CreateUptimeCheckConfigRequest, UptimeCheckConfig> createUptimeCheckConfig;
        private final CapturingUnaryCallable<UpdateUptimeCheckConfigRequest, UptimeCheckConfig> updateUptimeCheckConfig;
        private final CapturingUnaryCallable<DeleteUptimeCheckConfigRequest, Empty> deleteUptimeCheckConfig =
                new CapturingUnaryCallable<>(Empty.getDefaultInstance());
        private final CapturingUnaryCallable<ListUptimeCheckIpsRequest, ListUptimeCheckIpsResponse> listUptimeCheckIps =
                new CapturingUnaryCallable<>(ListUptimeCheckIpsResponse.getDefaultInstance());

        private FakeUptimeCheckServiceStub(UptimeCheckConfig uptimeCheckConfig) {
            getUptimeCheckConfig = new CapturingUnaryCallable<>(uptimeCheckConfig);
            createUptimeCheckConfig = new CapturingUnaryCallable<>(uptimeCheckConfig);
            updateUptimeCheckConfig = new CapturingUnaryCallable<>(uptimeCheckConfig);
        }

        @Override
        public UnaryCallable<GetUptimeCheckConfigRequest, UptimeCheckConfig> getUptimeCheckConfigCallable() {
            return getUptimeCheckConfig;
        }

        @Override
        public UnaryCallable<CreateUptimeCheckConfigRequest, UptimeCheckConfig> createUptimeCheckConfigCallable() {
            return createUptimeCheckConfig;
        }

        @Override
        public UnaryCallable<UpdateUptimeCheckConfigRequest, UptimeCheckConfig> updateUptimeCheckConfigCallable() {
            return updateUptimeCheckConfig;
        }

        @Override
        public UnaryCallable<DeleteUptimeCheckConfigRequest, Empty> deleteUptimeCheckConfigCallable() {
            return deleteUptimeCheckConfig;
        }

        @Override
        public UnaryCallable<ListUptimeCheckIpsRequest, ListUptimeCheckIpsResponse> listUptimeCheckIpsCallable() {
            return listUptimeCheckIps;
        }

        @Override
        public void close() {
        }
    }

    private static final class FakeSnoozeServiceStub extends SnoozeServiceStub implements ImmediateBackgroundResource {
        private final CapturingUnaryCallable<CreateSnoozeRequest, Snooze> createSnooze;
        private final CapturingUnaryCallable<GetSnoozeRequest, Snooze> getSnooze;
        private final CapturingUnaryCallable<UpdateSnoozeRequest, Snooze> updateSnooze;

        private FakeSnoozeServiceStub(Snooze snooze) {
            createSnooze = new CapturingUnaryCallable<>(snooze);
            getSnooze = new CapturingUnaryCallable<>(snooze);
            updateSnooze = new CapturingUnaryCallable<>(snooze);
        }

        @Override
        public UnaryCallable<CreateSnoozeRequest, Snooze> createSnoozeCallable() {
            return createSnooze;
        }

        @Override
        public UnaryCallable<GetSnoozeRequest, Snooze> getSnoozeCallable() {
            return getSnooze;
        }

        @Override
        public UnaryCallable<UpdateSnoozeRequest, Snooze> updateSnoozeCallable() {
            return updateSnooze;
        }

        @Override
        public void close() {
        }
    }

    private static final class FakeServiceMonitoringServiceStub extends ServiceMonitoringServiceStub implements ImmediateBackgroundResource {
        private final CapturingUnaryCallable<CreateServiceRequest, Service> createService;
        private final CapturingUnaryCallable<GetServiceRequest, Service> getService;
        private final CapturingUnaryCallable<DeleteServiceRequest, Empty> deleteService =
                new CapturingUnaryCallable<>(Empty.getDefaultInstance());
        private final CapturingUnaryCallable<CreateServiceLevelObjectiveRequest, ServiceLevelObjective>
                createServiceLevelObjective;
        private final CapturingUnaryCallable<UpdateServiceLevelObjectiveRequest, ServiceLevelObjective>
                updateServiceLevelObjective;
        private final CapturingUnaryCallable<DeleteServiceLevelObjectiveRequest, Empty> deleteServiceLevelObjective =
                new CapturingUnaryCallable<>(Empty.getDefaultInstance());

        private FakeServiceMonitoringServiceStub(Service service, ServiceLevelObjective objective) {
            createService = new CapturingUnaryCallable<>(service);
            getService = new CapturingUnaryCallable<>(service);
            createServiceLevelObjective = new CapturingUnaryCallable<>(objective);
            updateServiceLevelObjective = new CapturingUnaryCallable<>(objective);
        }

        @Override
        public UnaryCallable<CreateServiceRequest, Service> createServiceCallable() {
            return createService;
        }

        @Override
        public UnaryCallable<GetServiceRequest, Service> getServiceCallable() {
            return getService;
        }

        @Override
        public UnaryCallable<DeleteServiceRequest, Empty> deleteServiceCallable() {
            return deleteService;
        }

        @Override
        public UnaryCallable<CreateServiceLevelObjectiveRequest, ServiceLevelObjective> createServiceLevelObjectiveCallable() {
            return createServiceLevelObjective;
        }

        @Override
        public UnaryCallable<UpdateServiceLevelObjectiveRequest, ServiceLevelObjective> updateServiceLevelObjectiveCallable() {
            return updateServiceLevelObjective;
        }

        @Override
        public UnaryCallable<DeleteServiceLevelObjectiveRequest, Empty> deleteServiceLevelObjectiveCallable() {
            return deleteServiceLevelObjective;
        }

        @Override
        public void close() {
        }
    }

    private static final class FakeQueryServiceStub extends QueryServiceStub implements ImmediateBackgroundResource {
        private final CapturingUnaryCallable<QueryTimeSeriesRequest, QueryTimeSeriesResponse> queryTimeSeries =
                new CapturingUnaryCallable<>(QueryTimeSeriesResponse.getDefaultInstance());

        @Override
        public UnaryCallable<QueryTimeSeriesRequest, QueryTimeSeriesResponse> queryTimeSeriesCallable() {
            return queryTimeSeries;
        }

        @Override
        public void close() {
        }
    }
}
