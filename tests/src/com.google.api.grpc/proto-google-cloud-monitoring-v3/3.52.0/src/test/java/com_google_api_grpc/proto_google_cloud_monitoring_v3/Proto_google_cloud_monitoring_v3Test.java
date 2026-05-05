/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api_grpc.proto_google_cloud_monitoring_v3;

import java.util.List;

import com.google.api.LabelDescriptor;
import com.google.api.Metric;
import com.google.api.MetricDescriptor;
import com.google.api.MonitoredResource;
import com.google.monitoring.v3.Aggregation;
import com.google.monitoring.v3.AlertPolicy;
import com.google.monitoring.v3.AlertPolicyName;
import com.google.monitoring.v3.BasicSli;
import com.google.monitoring.v3.ComparisonType;
import com.google.monitoring.v3.CreateGroupRequest;
import com.google.monitoring.v3.CreateServiceLevelObjectiveRequest;
import com.google.monitoring.v3.CreateServiceRequest;
import com.google.monitoring.v3.CreateTimeSeriesRequest;
import com.google.monitoring.v3.CreateUptimeCheckConfigRequest;
import com.google.monitoring.v3.DistributionCut;
import com.google.monitoring.v3.Group;
import com.google.monitoring.v3.GroupName;
import com.google.monitoring.v3.InternalChecker;
import com.google.monitoring.v3.LabelValue;
import com.google.monitoring.v3.ListGroupMembersRequest;
import com.google.monitoring.v3.ListGroupMembersResponse;
import com.google.monitoring.v3.ListGroupsRequest;
import com.google.monitoring.v3.ListGroupsResponse;
import com.google.monitoring.v3.ListServiceLevelObjectivesResponse;
import com.google.monitoring.v3.ListTimeSeriesRequest;
import com.google.monitoring.v3.ListTimeSeriesResponse;
import com.google.monitoring.v3.ListUptimeCheckConfigsResponse;
import com.google.monitoring.v3.MetricDescriptorName;
import com.google.monitoring.v3.NotificationChannel;
import com.google.monitoring.v3.NotificationChannelName;
import com.google.monitoring.v3.Point;
import com.google.monitoring.v3.ProjectName;
import com.google.monitoring.v3.QueryTimeSeriesRequest;
import com.google.monitoring.v3.QueryTimeSeriesResponse;
import com.google.monitoring.v3.Range;
import com.google.monitoring.v3.RequestBasedSli;
import com.google.monitoring.v3.Service;
import com.google.monitoring.v3.ServiceLevelIndicator;
import com.google.monitoring.v3.ServiceLevelObjective;
import com.google.monitoring.v3.ServiceLevelObjectiveName;
import com.google.monitoring.v3.ServiceName;
import com.google.monitoring.v3.Snooze;
import com.google.monitoring.v3.TimeInterval;
import com.google.monitoring.v3.TimeSeries;
import com.google.monitoring.v3.TimeSeriesData;
import com.google.monitoring.v3.TimeSeriesDescriptor;
import com.google.monitoring.v3.TimeSeriesRatio;
import com.google.monitoring.v3.TypedValue;
import com.google.monitoring.v3.UpdateGroupRequest;
import com.google.monitoring.v3.UpdateUptimeCheckConfigRequest;
import com.google.monitoring.v3.UptimeCheckConfig;
import com.google.monitoring.v3.UptimeCheckConfigName;
import com.google.monitoring.v3.UptimeCheckRegion;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Timestamp;
import com.google.rpc.Code;
import com.google.rpc.Status;
import com.google.type.CalendarPeriod;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Proto_google_cloud_monitoring_v3Test {
    private static final String PROJECT_ID = "sample-project";
    private static final String PROJECT_NAME = "projects/" + PROJECT_ID;

    @Test
    void resourceNamesRoundTripAcrossSupportedParents() {
        ProjectName projectName = ProjectName.of(PROJECT_ID);
        MetricDescriptorName projectMetric = MetricDescriptorName.ofProjectMetricDescriptorName(
                PROJECT_ID, "custom.googleapis.com/http/server/latency");
        MetricDescriptorName folderMetric = MetricDescriptorName.ofFolderMetricDescriptorName(
                "123456", "custom.googleapis.com/http/server/latency");
        MetricDescriptorName organizationMetric = MetricDescriptorName.ofOrganizationMetricDescriptorName(
                "789012", "custom.googleapis.com/http/server/latency");

        assertThat(projectName.toString()).isEqualTo(PROJECT_NAME);
        assertThat(MetricDescriptorName.isParsableFrom(projectMetric.toString())).isTrue();
        MetricDescriptorName parsedProjectMetric = MetricDescriptorName.parse(projectMetric.toString());
        assertThat(parsedProjectMetric).isEqualTo(projectMetric);
        assertThat(parsedProjectMetric.hashCode()).isEqualTo(projectMetric.hashCode());
        assertThat(projectMetric.getFieldValuesMap())
                .containsEntry("project", PROJECT_ID)
                .containsEntry("metric_descriptor", "custom.googleapis.com/http/server/latency");
        assertThat(folderMetric.getFolder()).isEqualTo("123456");
        assertThat(organizationMetric.getOrganization()).isEqualTo("789012");

        UptimeCheckConfigName uptimeName = UptimeCheckConfigName.ofProjectUptimeCheckConfigName(PROJECT_ID, "uptime-1");
        NotificationChannelName channelName = NotificationChannelName.ofProjectNotificationChannelName(PROJECT_ID, "channel-1");
        AlertPolicyName alertPolicyName = AlertPolicyName.ofProjectAlertPolicyName(PROJECT_ID, "policy-1");
        ServiceName serviceName = ServiceName.ofProjectServiceName(PROJECT_ID, "checkout-service");
        ServiceLevelObjectiveName objectiveName = ServiceLevelObjectiveName.ofProjectServiceServiceLevelObjectiveName(
                PROJECT_ID, "checkout-service", "availability");

        assertThat(UptimeCheckConfigName.parse(uptimeName.toString()).getUptimeCheckConfig()).isEqualTo("uptime-1");
        assertThat(NotificationChannelName.toStringList(List.of(channelName))).containsExactly(channelName.toString());
        assertThat(AlertPolicyName.parseList(List.of(alertPolicyName.toString()))).containsExactly(alertPolicyName);
        assertThat(serviceName.toBuilder().build()).isEqualTo(serviceName);
        assertThat(objectiveName.getService()).isEqualTo("checkout-service");
        assertThat(objectiveName.getServiceLevelObjective()).isEqualTo("availability");
    }

    @Test
    void timeSeriesRequestsCarryMetricsResourcesPointsAndAggregations() {
        TimeInterval interval = interval(1_700_000_000L, 1_700_000_300L);
        Point point = Point.newBuilder()
                .setInterval(interval)
                .setValue(TypedValue.newBuilder().setDoubleValue(123.45))
                .build();
        TimeSeries timeSeries = TimeSeries.newBuilder()
                .setMetric(Metric.newBuilder()
                        .setType("custom.googleapis.com/http/server/latency")
                        .putLabels("method", "GET")
                        .putLabels("status", "200"))
                .setResource(MonitoredResource.newBuilder()
                        .setType("gce_instance")
                        .putLabels("project_id", PROJECT_ID)
                        .putLabels("instance_id", "1234567890")
                        .putLabels("zone", "us-central1-a"))
                .setMetricKind(MetricDescriptor.MetricKind.GAUGE)
                .setValueType(MetricDescriptor.ValueType.DOUBLE)
                .addPoints(point)
                .setUnit("ms")
                .build();
        Aggregation aggregation = Aggregation.newBuilder()
                .setAlignmentPeriod(duration(60))
                .setPerSeriesAligner(Aggregation.Aligner.ALIGN_MEAN)
                .setCrossSeriesReducer(Aggregation.Reducer.REDUCE_PERCENTILE_95)
                .addGroupByFields("resource.label.zone")
                .build();

        ListTimeSeriesRequest listRequest = ListTimeSeriesRequest.newBuilder()
                .setName(PROJECT_NAME)
                .setFilter("metric.type=\"custom.googleapis.com/http/server/latency\"")
                .setInterval(interval)
                .setAggregation(aggregation)
                .setSecondaryAggregation(aggregation.toBuilder().setPerSeriesAligner(Aggregation.Aligner.ALIGN_MAX))
                .setOrderBy("metric.label.method")
                .setView(ListTimeSeriesRequest.TimeSeriesView.FULL)
                .setPageSize(25)
                .setPageToken("next-page")
                .build();
        ListTimeSeriesResponse listResponse = ListTimeSeriesResponse.newBuilder()
                .addTimeSeries(timeSeries)
                .setNextPageToken("response-page")
                .build();
        CreateTimeSeriesRequest createRequest = CreateTimeSeriesRequest.newBuilder()
                .setName(PROJECT_NAME)
                .addTimeSeries(timeSeries)
                .build();

        assertThat(listRequest.hasInterval()).isTrue();
        assertThat(listRequest.getAggregation().getGroupByFieldsList()).containsExactly("resource.label.zone");
        assertThat(listRequest.getSecondaryAggregation().getPerSeriesAligner()).isEqualTo(Aggregation.Aligner.ALIGN_MAX);
        assertThat(listResponse.getTimeSeries(0).getMetric().getLabelsMap()).containsEntry("status", "200");
        assertThat(listResponse.getTimeSeries(0).getPoints(0).getValue().getDoubleValue()).isEqualTo(123.45);
        assertThat(createRequest.getTimeSeries(0).getResource().getLabelsOrThrow("zone")).isEqualTo("us-central1-a");
    }

    @Test
    void queryTimeSeriesRequestsAndResponsesModelDescriptorsRowsAndPartialErrors() {
        QueryTimeSeriesRequest request = QueryTimeSeriesRequest.newBuilder()
                .setName(PROJECT_NAME)
                .setQuery("""
                        fetch gce_instance
                        | metric 'custom.googleapis.com/http/server/latency'
                        | group_by [resource.zone, metric.healthy],
                            [mean_latency: mean(value.latency), sample_count: count()]
                        """)
                .setPageSize(20)
                .setPageToken("query-page")
                .build();
        TimeSeriesDescriptor descriptor = TimeSeriesDescriptor.newBuilder()
                .addLabelDescriptors(LabelDescriptor.newBuilder()
                        .setKey("resource.zone")
                        .setValueType(LabelDescriptor.ValueType.STRING)
                        .setDescription("Zone label emitted by the query."))
                .addLabelDescriptors(LabelDescriptor.newBuilder()
                        .setKey("metric.healthy")
                        .setValueType(LabelDescriptor.ValueType.BOOL)
                        .setDescription("Whether the backend reported a healthy response."))
                .addPointDescriptors(TimeSeriesDescriptor.ValueDescriptor.newBuilder()
                        .setKey("mean_latency")
                        .setValueType(MetricDescriptor.ValueType.DOUBLE)
                        .setMetricKind(MetricDescriptor.MetricKind.GAUGE)
                        .setUnit("ms"))
                .addPointDescriptors(TimeSeriesDescriptor.ValueDescriptor.newBuilder()
                        .setKey("sample_count")
                        .setValueType(MetricDescriptor.ValueType.INT64)
                        .setMetricKind(MetricDescriptor.MetricKind.CUMULATIVE)
                        .setUnit("1"))
                .build();
        TimeSeriesData data = TimeSeriesData.newBuilder()
                .addLabelValues(LabelValue.newBuilder().setStringValue("us-central1-a"))
                .addLabelValues(LabelValue.newBuilder().setBoolValue(true))
                .addPointData(TimeSeriesData.PointData.newBuilder()
                        .setTimeInterval(interval(1_700_000_300L, 1_700_000_360L))
                        .addValues(TypedValue.newBuilder().setDoubleValue(275.25))
                        .addValues(TypedValue.newBuilder().setInt64Value(42)))
                .build();
        QueryTimeSeriesResponse response = QueryTimeSeriesResponse.newBuilder()
                .setTimeSeriesDescriptor(descriptor)
                .addTimeSeriesData(data)
                .setNextPageToken("query-next")
                .addPartialErrors(Status.newBuilder()
                        .setCode(Code.INVALID_ARGUMENT_VALUE)
                        .setMessage("one query branch could not be evaluated"))
                .build();

        assertThat(request.getQuery()).contains("group_by [resource.zone, metric.healthy]");
        assertThat(request.getPageToken()).isEqualTo("query-page");
        assertThat(response.hasTimeSeriesDescriptor()).isTrue();
        assertThat(response.getTimeSeriesDescriptor().getLabelDescriptorsList())
                .extracting(LabelDescriptor::getKey)
                .containsExactly("resource.zone", "metric.healthy");
        assertThat(response.getTimeSeriesDescriptor().getPointDescriptors(0).getValueType())
                .isEqualTo(MetricDescriptor.ValueType.DOUBLE);
        assertThat(response.getTimeSeriesDescriptor().getPointDescriptors(1).getMetricKind())
                .isEqualTo(MetricDescriptor.MetricKind.CUMULATIVE);
        assertThat(response.getTimeSeriesData(0).getLabelValues(0).getValueCase())
                .isEqualTo(LabelValue.ValueCase.STRING_VALUE);
        assertThat(response.getTimeSeriesData(0).getLabelValues(1).getBoolValue()).isTrue();
        assertThat(response.getTimeSeriesData(0).getPointData(0).getValues(0).getDoubleValue()).isEqualTo(275.25);
        assertThat(response.getTimeSeriesData(0).getPointData(0).getValues(1).getInt64Value()).isEqualTo(42L);
        assertThat(response.getTimeSeriesData(0).getPointData(0).getTimeInterval().getStartTime().getSeconds())
                .isEqualTo(1_700_000_300L);
        assertThat(response.getPartialErrors(0).getCode()).isEqualTo(Code.INVALID_ARGUMENT_VALUE);
        assertThat(response.getNextPageToken()).isEqualTo("query-next");
    }

    @Test
    void uptimeCheckConfigModelsHttpChecksMatchersAndUpdateRequests() {
        UptimeCheckConfig.HttpCheck httpCheck = UptimeCheckConfig.HttpCheck.newBuilder()
                .setRequestMethod(UptimeCheckConfig.HttpCheck.RequestMethod.POST)
                .setUseSsl(true)
                .setPath("/healthz")
                .setPort(443)
                .setAuthInfo(UptimeCheckConfig.HttpCheck.BasicAuthentication.newBuilder()
                        .setUsername("uptime-user")
                        .setPassword("secret"))
                .setMaskHeaders(true)
                .putHeaders("X-Check", "synthetic")
                .setContentType(UptimeCheckConfig.HttpCheck.ContentType.USER_PROVIDED)
                .setCustomContentType("application/json")
                .setValidateSsl(true)
                .setBody(ByteString.copyFromUtf8("{\"ping\":true}"))
                .addAcceptedResponseStatusCodes(UptimeCheckConfig.HttpCheck.ResponseStatusCode.newBuilder()
                        .setStatusClass(UptimeCheckConfig.HttpCheck.ResponseStatusCode.StatusClass.STATUS_CLASS_2XX))
                .build();
        UptimeCheckConfig.ContentMatcher matcher = UptimeCheckConfig.ContentMatcher.newBuilder()
                .setContent("$.status")
                .setMatcher(UptimeCheckConfig.ContentMatcher.ContentMatcherOption.MATCHES_JSON_PATH)
                .setJsonPathMatcher(UptimeCheckConfig.ContentMatcher.JsonPathMatcher.newBuilder()
                        .setJsonPath("$.status")
                        .setJsonMatcher(UptimeCheckConfig.ContentMatcher.JsonPathMatcher.JsonPathMatcherOption.EXACT_MATCH))
                .build();
        UptimeCheckConfig config = UptimeCheckConfig.newBuilder()
                .setName(UptimeCheckConfigName.ofProjectUptimeCheckConfigName(PROJECT_ID, "uptime-1").toString())
                .setDisplayName("Checkout health check")
                .setMonitoredResource(MonitoredResource.newBuilder()
                        .setType("uptime_url")
                        .putLabels("host", "checkout.example.com"))
                .setHttpCheck(httpCheck)
                .setPeriod(duration(60))
                .setTimeout(duration(10))
                .addContentMatchers(matcher)
                .setCheckerType(UptimeCheckConfig.CheckerType.STATIC_IP_CHECKERS)
                .addSelectedRegions(UptimeCheckRegion.USA)
                .addSelectedRegions(UptimeCheckRegion.EUROPE)
                .setIsInternal(true)
                .addInternalCheckers(InternalChecker.newBuilder()
                        .setName("projects/sample-project/internalCheckers/checker-1")
                        .setDisplayName("private checker")
                        .setNetwork("projects/sample-project/global/networks/default")
                        .setGcpZone("us-central1-a")
                        .setPeerProjectId(PROJECT_ID)
                        .setState(InternalChecker.State.RUNNING))
                .putUserLabels("team", "payments")
                .build();
        CreateUptimeCheckConfigRequest createRequest = CreateUptimeCheckConfigRequest.newBuilder()
                .setParent(PROJECT_NAME)
                .setUptimeCheckConfig(config)
                .build();
        UpdateUptimeCheckConfigRequest updateRequest = UpdateUptimeCheckConfigRequest.newBuilder()
                .setUptimeCheckConfig(config.toBuilder().setDisplayName("Checkout health check v2"))
                .setUpdateMask(FieldMask.newBuilder().addPaths("display_name").addPaths("timeout"))
                .build();
        ListUptimeCheckConfigsResponse response = ListUptimeCheckConfigsResponse.newBuilder()
                .addUptimeCheckConfigs(config)
                .setNextPageToken("uptime-next")
                .setTotalSize(1)
                .build();

        assertThat(config.getResourceCase()).isEqualTo(UptimeCheckConfig.ResourceCase.MONITORED_RESOURCE);
        assertThat(config.getCheckRequestTypeCase()).isEqualTo(UptimeCheckConfig.CheckRequestTypeCase.HTTP_CHECK);
        assertThat(config.getHttpCheck().getAcceptedResponseStatusCodes(0).getStatusClass())
                .isEqualTo(UptimeCheckConfig.HttpCheck.ResponseStatusCode.StatusClass.STATUS_CLASS_2XX);
        assertThat(config.getContentMatchers(0).getJsonPathMatcher().getJsonMatcher())
                .isEqualTo(UptimeCheckConfig.ContentMatcher.JsonPathMatcher.JsonPathMatcherOption.EXACT_MATCH);
        assertThat(createRequest.getUptimeCheckConfig().getInternalCheckers(0).getState()).isEqualTo(InternalChecker.State.RUNNING);
        assertThat(updateRequest.getUpdateMask().getPathsList()).containsExactly("display_name", "timeout");
        assertThat(response.getUptimeCheckConfigs(0).getUserLabelsOrThrow("team")).isEqualTo("payments");
    }

    @Test
    void alertPoliciesNotificationChannelsAndSnoozesUseNestedConfiguration() {
        String channelName = NotificationChannelName.ofProjectNotificationChannelName(PROJECT_ID, "channel-1").toString();
        NotificationChannel channel = NotificationChannel.newBuilder()
                .setName(channelName)
                .setType("email")
                .setDisplayName("Primary on-call")
                .setDescription("Sends alerts to the primary on-call rotation")
                .putLabels("email_address", "oncall@example.com")
                .putUserLabels("team", "sre")
                .setVerificationStatus(NotificationChannel.VerificationStatus.VERIFIED)
                .setEnabled(BoolValue.of(true))
                .build();
        AlertPolicy.Condition.MetricThreshold threshold = AlertPolicy.Condition.MetricThreshold.newBuilder()
                .setFilter("metric.type=\"custom.googleapis.com/http/server/latency\"")
                .addAggregations(Aggregation.newBuilder()
                        .setAlignmentPeriod(duration(60))
                        .setPerSeriesAligner(Aggregation.Aligner.ALIGN_PERCENTILE_99))
                .setComparison(ComparisonType.COMPARISON_GT)
                .setThresholdValue(500.0)
                .setDuration(duration(300))
                .setTrigger(AlertPolicy.Condition.Trigger.newBuilder().setPercent(75.0))
                .setEvaluationMissingData(AlertPolicy.Condition.EvaluationMissingData.EVALUATION_MISSING_DATA_INACTIVE)
                .build();
        AlertPolicy policy = AlertPolicy.newBuilder()
                .setName(AlertPolicyName.ofProjectAlertPolicyName(PROJECT_ID, "policy-1").toString())
                .setDisplayName("High checkout latency")
                .setDocumentation(AlertPolicy.Documentation.newBuilder()
                        .setContent("Investigate checkout latency in the service dashboard.")
                        .setMimeType("text/markdown")
                        .setSubject("Checkout latency alert"))
                .putUserLabels("service", "checkout")
                .addConditions(AlertPolicy.Condition.newBuilder()
                        .setDisplayName("99th percentile latency")
                        .setConditionThreshold(threshold))
                .setCombiner(AlertPolicy.ConditionCombinerType.AND)
                .setEnabled(BoolValue.of(true))
                .addNotificationChannels(channelName)
                .setAlertStrategy(AlertPolicy.AlertStrategy.newBuilder()
                        .setNotificationRateLimit(AlertPolicy.AlertStrategy.NotificationRateLimit.newBuilder()
                                .setPeriod(duration(300)))
                        .setAutoClose(duration(3_600))
                        .addNotificationChannelStrategy(AlertPolicy.AlertStrategy.NotificationChannelStrategy.newBuilder()
                                .addNotificationChannelNames(channelName)
                                .setRenotifyInterval(duration(1_800))))
                .setSeverity(AlertPolicy.Severity.CRITICAL)
                .build();
        Snooze snooze = Snooze.newBuilder()
                .setName("projects/sample-project/snoozes/snooze-1")
                .setDisplayName("Maintenance window")
                .setCriteria(Snooze.Criteria.newBuilder().addPolicies(policy.getName()))
                .setInterval(interval(1_700_001_000L, 1_700_004_600L))
                .build();

        assertThat(channel.getLabelsOrThrow("email_address")).isEqualTo("oncall@example.com");
        assertThat(policy.getConditions(0).getConditionCase()).isEqualTo(AlertPolicy.Condition.ConditionCase.CONDITION_THRESHOLD);
        assertThat(policy.getConditions(0).getConditionThreshold().getTrigger().getPercent()).isEqualTo(75.0);
        assertThat(policy.getAlertStrategy().getNotificationChannelStrategy(0).getNotificationChannelNamesList())
                .containsExactly(channelName);
        assertThat(policy.getSeverity()).isEqualTo(AlertPolicy.Severity.CRITICAL);
        assertThat(snooze.getCriteria().getPoliciesList()).containsExactly(policy.getName());
    }

    @Test
    void serviceMonitoringObjectsComposeServicesSlisAndObjectives() {
        Service service = Service.newBuilder()
                .setName(ServiceName.ofProjectServiceName(PROJECT_ID, "checkout-service").toString())
                .setDisplayName("Checkout service")
                .setBasicService(Service.BasicService.newBuilder()
                        .setServiceType("load_balancer")
                        .putServiceLabels("module_id", "checkout"))
                .setTelemetry(Service.Telemetry.newBuilder()
                        .setResourceName("//container.googleapis.com/projects/sample-project/locations/us-central1/clusters/prod"))
                .putUserLabels("tier", "frontend")
                .build();
        RequestBasedSli requestBasedSli = RequestBasedSli.newBuilder()
                .setGoodTotalRatio(TimeSeriesRatio.newBuilder()
                        .setGoodServiceFilter("metric.type=\"custom.googleapis.com/http/server/success_count\"")
                        .setTotalServiceFilter("metric.type=\"custom.googleapis.com/http/server/request_count\""))
                .build();
        BasicSli basicSli = BasicSli.newBuilder()
                .addMethod("GET")
                .addLocation("us-central1")
                .addVersion("v1")
                .setAvailability(BasicSli.AvailabilityCriteria.getDefaultInstance())
                .build();
        ServiceLevelObjective objective = ServiceLevelObjective.newBuilder()
                .setName(ServiceLevelObjectiveName.ofProjectServiceServiceLevelObjectiveName(
                        PROJECT_ID, "checkout-service", "availability").toString())
                .setDisplayName("Checkout availability")
                .setServiceLevelIndicator(ServiceLevelIndicator.newBuilder().setRequestBased(requestBasedSli))
                .setGoal(0.995)
                .setRollingPeriod(duration(2_419_200))
                .putUserLabels("slo", "availability")
                .build();
        ServiceLevelObjective monthlyObjective = objective.toBuilder()
                .setName(ServiceLevelObjectiveName.ofProjectServiceServiceLevelObjectiveName(
                        PROJECT_ID, "checkout-service", "latency").toString())
                .setDisplayName("Checkout latency")
                .setServiceLevelIndicator(ServiceLevelIndicator.newBuilder().setBasicSli(basicSli))
                .setGoal(0.99)
                .setCalendarPeriod(CalendarPeriod.MONTH)
                .build();
        DistributionCut distributionCut = DistributionCut.newBuilder()
                .setDistributionFilter("metric.type=\"custom.googleapis.com/http/server/latency_distribution\"")
                .setRange(Range.newBuilder().setMin(0.0).setMax(500.0))
                .build();

        CreateServiceRequest createServiceRequest = CreateServiceRequest.newBuilder()
                .setParent(PROJECT_NAME)
                .setServiceId("checkout-service")
                .setService(service)
                .build();
        CreateServiceLevelObjectiveRequest createObjectiveRequest = CreateServiceLevelObjectiveRequest.newBuilder()
                .setParent(service.getName())
                .setServiceLevelObjectiveId("availability")
                .setServiceLevelObjective(objective)
                .build();
        ListServiceLevelObjectivesResponse response = ListServiceLevelObjectivesResponse.newBuilder()
                .addServiceLevelObjectives(objective)
                .addServiceLevelObjectives(monthlyObjective)
                .setNextPageToken("slo-next")
                .build();

        assertThat(createServiceRequest.getService().hasBasicService()).isTrue();
        assertThat(createServiceRequest.getService().getTelemetry().getResourceName()).contains("container.googleapis.com");
        assertThat(createObjectiveRequest.getServiceLevelObjective().getServiceLevelIndicator().getTypeCase())
                .isEqualTo(ServiceLevelIndicator.TypeCase.REQUEST_BASED);
        assertThat(response.getServiceLevelObjectives(0).getPeriodCase()).isEqualTo(ServiceLevelObjective.PeriodCase.ROLLING_PERIOD);
        assertThat(response.getServiceLevelObjectives(1).getPeriodCase()).isEqualTo(ServiceLevelObjective.PeriodCase.CALENDAR_PERIOD);
        assertThat(response.getServiceLevelObjectives(1).getServiceLevelIndicator().getBasicSli().getMethodList())
                .containsExactly("GET");
        assertThat(distributionCut.getRange().getMax()).isEqualTo(500.0);
    }

    @Test
    void groupsRequestsAndMembershipResponsesModelHierarchies() {
        String parentGroupName = GroupName.ofProjectGroupName(PROJECT_ID, "checkout-parent").toString();
        String childGroupName = GroupName.ofProjectGroupName(PROJECT_ID, "checkout-workers").toString();
        Group parentGroup = Group.newBuilder()
                .setName(parentGroupName)
                .setDisplayName("Checkout production")
                .setFilter("resource.type = starts_with(\"gce_\") AND metadata.user_labels.service = \"checkout\"")
                .setIsCluster(true)
                .build();
        Group childGroup = Group.newBuilder()
                .setName(childGroupName)
                .setDisplayName("Checkout workers")
                .setParentName(parentGroupName)
                .setFilter("resource.labels.zone = \"us-central1-a\"")
                .build();
        CreateGroupRequest createRequest = CreateGroupRequest.newBuilder()
                .setName(PROJECT_NAME)
                .setGroup(childGroup)
                .setValidateOnly(true)
                .build();
        UpdateGroupRequest updateRequest = UpdateGroupRequest.newBuilder()
                .setGroup(childGroup.toBuilder().setDisplayName("Checkout workers in us-central1-a"))
                .build();
        ListGroupsRequest childrenRequest = ListGroupsRequest.newBuilder()
                .setName(PROJECT_NAME)
                .setChildrenOfGroup(parentGroupName)
                .setPageSize(10)
                .setPageToken("groups-page")
                .build();
        ListGroupsResponse groupsResponse = ListGroupsResponse.newBuilder()
                .addGroup(parentGroup)
                .addGroup(childGroup)
                .setNextPageToken("groups-next")
                .build();
        MonitoredResource member = MonitoredResource.newBuilder()
                .setType("gce_instance")
                .putLabels("project_id", PROJECT_ID)
                .putLabels("instance_id", "1234567890")
                .putLabels("zone", "us-central1-a")
                .build();
        ListGroupMembersRequest membersRequest = ListGroupMembersRequest.newBuilder()
                .setName(childGroupName)
                .setFilter("resource.type = \"gce_instance\"")
                .setInterval(interval(1_700_002_000L, 1_700_002_300L))
                .setPageSize(5)
                .setPageToken("members-page")
                .build();
        ListGroupMembersResponse membersResponse = ListGroupMembersResponse.newBuilder()
                .addMembers(member)
                .setNextPageToken("members-next")
                .setTotalSize(1)
                .build();

        assertThat(parentGroup.getIsCluster()).isTrue();
        assertThat(createRequest.getValidateOnly()).isTrue();
        assertThat(createRequest.getGroup().getParentName()).isEqualTo(parentGroupName);
        assertThat(updateRequest.getGroup().getDisplayName()).isEqualTo("Checkout workers in us-central1-a");
        assertThat(childrenRequest.getFilterCase()).isEqualTo(ListGroupsRequest.FilterCase.CHILDREN_OF_GROUP);
        assertThat(childrenRequest.getChildrenOfGroup()).isEqualTo(parentGroupName);
        assertThat(groupsResponse.getGroupList()).containsExactly(parentGroup, childGroup);
        assertThat(membersRequest.hasInterval()).isTrue();
        assertThat(membersRequest.getInterval().getEndTime().getSeconds()).isEqualTo(1_700_002_300L);
        assertThat(membersResponse.getMembers(0).getLabelsOrThrow("zone")).isEqualTo("us-central1-a");
        assertThat(membersResponse.getTotalSize()).isEqualTo(1);
    }

    private static Duration duration(int seconds) {
        return Duration.newBuilder().setSeconds(seconds).build();
    }

    private static TimeInterval interval(long startEpochSeconds, long endEpochSeconds) {
        return TimeInterval.newBuilder()
                .setStartTime(Timestamp.newBuilder().setSeconds(startEpochSeconds))
                .setEndTime(Timestamp.newBuilder().setSeconds(endEpochSeconds))
                .build();
    }
}
