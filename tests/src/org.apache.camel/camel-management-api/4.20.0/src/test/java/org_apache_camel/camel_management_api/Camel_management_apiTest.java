/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_management_api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

import org.apache.camel.Processor;
import org.apache.camel.api.management.JmxNotificationBroadcasterAware;
import org.apache.camel.api.management.JmxSystemPropertyKeys;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.ManagedInstance;
import org.apache.camel.api.management.NotificationSender;
import org.apache.camel.api.management.NotificationSenderAware;
import org.apache.camel.api.management.mbean.CamelOpenMBeanTypes;
import org.apache.camel.api.management.mbean.ComponentVerifierExtension.Result;
import org.apache.camel.api.management.mbean.ComponentVerifierExtension.Scope;
import org.apache.camel.api.management.mbean.ComponentVerifierExtension.VerificationError;
import org.apache.camel.api.management.mbean.ManagedCamelContextMBean;
import org.apache.camel.api.management.mbean.ManagedConsumerMBean;
import org.apache.camel.api.management.mbean.ManagedProcessorAware;
import org.apache.camel.api.management.mbean.ManagedProcessorMBean;
import org.apache.camel.api.management.mbean.ManagedRouteGroupMBean;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.api.management.mbean.ManagedStepMBean;
import org.apache.camel.api.management.mbean.RouteError;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Camel_management_apiTest {
    @Test
    void componentVerifierScopesAndResultStatusesExposeStablePublicNames() {
        assertThat(Scope.fromString("parameters")).isSameAs(Scope.PARAMETERS);
        assertThat(Scope.fromString("ConNecTiViTy")).isSameAs(Scope.CONNECTIVITY);
        assertThat(Scope.values()).containsExactly(Scope.PARAMETERS, Scope.CONNECTIVITY);

        assertThatThrownBy(() -> Scope.fromString("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Scope.fromString(null))
                .isInstanceOf(NullPointerException.class);

        assertThat(Result.Status.values()).containsExactly(
                Result.Status.OK,
                Result.Status.ERROR,
                Result.Status.UNSUPPORTED);
        assertThat(RouteError.Phase.values()).containsExactly(
                RouteError.Phase.START,
                RouteError.Phase.STOP,
                RouteError.Phase.SUSPEND,
                RouteError.Phase.RESUME,
                RouteError.Phase.SHUTDOWN,
                RouteError.Phase.REMOVE);
    }

    @Test
    void verificationErrorCodesAndAttributesCanUseStandardAndCustomNames() {
        VerificationError.Code customCode = VerificationError.asCode("backend_rejected_request");
        VerificationError.Code sameCustomCode = VerificationError.asCode("backend_rejected_request");
        VerificationError.Attribute customAttribute = VerificationError.asAttribute("retry_after");
        VerificationError.Attribute sameCustomAttribute = VerificationError.asAttribute("retry_after");

        assertThat(customCode.name()).isEqualTo("backend_rejected_request");
        assertThat(customCode.getName()).isEqualTo("backend_rejected_request");
        assertThat(customCode).isEqualTo(sameCustomCode).hasSameHashCodeAs(sameCustomCode);
        assertThat(customCode).hasToString("backend_rejected_request");
        assertThat(customCode).isNotEqualTo(VerificationError.StandardCode.AUTHENTICATION);

        assertThat(customAttribute.name()).isEqualTo("retry_after");
        assertThat(customAttribute.getName()).isEqualTo("retry_after");
        assertThat(customAttribute).isEqualTo(sameCustomAttribute).hasSameHashCodeAs(sameCustomAttribute);
        assertThat(customAttribute).hasToString("retry_after");
        assertThat(customAttribute).isNotEqualTo(VerificationError.HttpAttribute.HTTP_CODE);

        assertThat(VerificationError.StandardCode.AUTHENTICATION.name()).isEqualTo("AUTHENTICATION");
        assertThat(VerificationError.StandardCode.ILLEGAL_PARAMETER_GROUP_COMBINATION.getName())
                .isEqualTo("ILLEGAL_PARAMETER_GROUP_COMBINATION");
        assertThat(VerificationError.HttpAttribute.HTTP_REDIRECT.name()).isEqualTo("HTTP_REDIRECT");
        assertThat(VerificationError.ExceptionAttribute.EXCEPTION_CLASS.name()).isEqualTo("EXCEPTION_CLASS");
        assertThat(VerificationError.GroupAttribute.GROUP_OPTIONS.name()).isEqualTo("GROUP_OPTIONS");

        assertThatThrownBy(() -> VerificationError.asCode(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VerificationError.asAttribute(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verificationErrorDefaultDetailAccessorsLookupByAttributeName() {
        Map<VerificationError.Attribute, Object> details = new LinkedHashMap<>();
        details.put(VerificationError.HttpAttribute.HTTP_CODE, 503);
        details.put(VerificationError.asAttribute("retry_after"), "PT5S");
        details.put(VerificationError.ExceptionAttribute.EXCEPTION_CLASS, IllegalStateException.class.getName());

        VerificationError error = new TestVerificationError(
                VerificationError.StandardCode.EXCEPTION,
                "backend refused connection",
                Set.of("host", "port"),
                details);

        assertThat(error.getCode()).isSameAs(VerificationError.StandardCode.EXCEPTION);
        assertThat(error.getDescription()).isEqualTo("backend refused connection");
        assertThat(error.getParameterKeys()).containsExactlyInAnyOrder("host", "port");
        assertThat(error.getDetail(VerificationError.HttpAttribute.HTTP_CODE)).isEqualTo(503);
        assertThat(error.getDetail("retry_after")).isEqualTo("PT5S");
        assertThat(error.getDetail("EXCEPTION_CLASS")).isEqualTo(IllegalStateException.class.getName());
        assertThat(error.getDetail("missing_detail")).isNull();

        VerificationError errorWithoutDetails = new TestVerificationError(
                VerificationError.StandardCode.GENERIC,
                "no details",
                Collections.emptySet(),
                null);
        assertThat(errorWithoutDetails.getDetail("anything")).isNull();
    }

    @Test
    void managedCamelContextConvenienceMethodsDelegateToTypedLookups() {
        RecordingManagedCamelContext context = new RecordingManagedCamelContext();

        assertThat(context.getManagedProcessor("processor-1")).isNull();
        assertThat(context.lastProcessorId).isEqualTo("processor-1");
        assertThat(context.lastProcessorType).isEqualTo(ManagedProcessorMBean.class);

        assertThat(context.getManagedRoute("route-1")).isNull();
        assertThat(context.lastRouteId).isEqualTo("route-1");
        assertThat(context.lastRouteType).isEqualTo(ManagedRouteMBean.class);

        assertThat(context.getManagedConsumer("consumer-1")).isNull();
        assertThat(context.lastConsumerId).isEqualTo("consumer-1");
        assertThat(context.lastConsumerType).isEqualTo(ManagedConsumerMBean.class);
    }

    @Test
    void managedProcessorAwareObjectsExposeAndReplaceUnderlyingProcessorInstances() throws Exception {
        List<String> processedBy = new ArrayList<>();
        Processor firstProcessor = exchange -> processedBy.add("first");
        Processor replacementProcessor = exchange -> processedBy.add("replacement");
        ManagedProcessorHolder managedProcessor = new ManagedProcessorHolder();

        managedProcessor.setProcessor(firstProcessor);
        assertThat(managedProcessor.getProcessor()).isSameAs(firstProcessor);
        assertThat(managedProcessor.getInstance()).isSameAs(firstProcessor);
        managedProcessor.getProcessor().process(null);

        managedProcessor.setProcessor(replacementProcessor);
        assertThat(managedProcessor.getProcessor()).isSameAs(replacementProcessor);
        assertThat(managedProcessor.getInstance()).isSameAs(replacementProcessor);
        managedProcessor.getProcessor().process(null);

        assertThat(processedBy).containsExactly("first", "replacement");
    }

    @Test
    void notificationAwareManagementObjectsCanPublishJmxNotifications() {
        NotificationBroadcasterSupport broadcaster = new NotificationBroadcasterSupport();
        List<Notification> receivedNotifications = new ArrayList<>();
        broadcaster.addNotificationListener(
                (notification, handback) -> {
                    assertThat(handback).isEqualTo("camel-handback");
                    receivedNotifications.add(notification);
                },
                notification -> notification.getType().startsWith("org.apache.camel."),
                "camel-handback");

        RecordingNotificationManagedObject managedObject = new RecordingNotificationManagedObject("camel-context");
        managedObject.setNotificationBroadcaster(broadcaster);
        managedObject.setNotificationSender(broadcaster::sendNotification);
        managedObject.emitRouteStarted("route-1");

        assertThat(receivedNotifications).hasSize(1);
        Notification notification = receivedNotifications.get(0);
        assertThat(notification.getType()).isEqualTo("org.apache.camel.management.route.started");
        assertThat(notification.getSource()).isEqualTo("camel-context");
        assertThat(notification.getSequenceNumber()).isEqualTo(1L);
        assertThat(notification.getMessage()).isEqualTo("route started");
        assertThat(notification.getUserData()).isEqualTo(Map.of("routeId", "route-1"));
    }

    @Test
    void jmxSystemPropertyKeysUseCamelManagementNamespace() {
        assertThat(List.of(
                JmxSystemPropertyKeys.DISABLED,
                JmxSystemPropertyKeys.DOMAIN,
                JmxSystemPropertyKeys.MBEAN_DOMAIN,
                JmxSystemPropertyKeys.USE_PLATFORM_MBS,
                JmxSystemPropertyKeys.ONLY_REGISTER_PROCESSOR_WITH_CUSTOM_ID,
                JmxSystemPropertyKeys.LOAD_STATISTICS_ENABLED,
                JmxSystemPropertyKeys.ENDPOINT_RUNTIME_STATISTICS_ENABLED,
                JmxSystemPropertyKeys.STATISTICS_LEVEL,
                JmxSystemPropertyKeys.REGISTER_ALWAYS,
                JmxSystemPropertyKeys.REGISTER_NEW_ROUTES,
                JmxSystemPropertyKeys.REGISTER_ROUTES_CREATED_BY_TEMPLATE,
                JmxSystemPropertyKeys.REGISTER_ROUTES_CREATED_BY_KAMELET,
                JmxSystemPropertyKeys.MASK,
                JmxSystemPropertyKeys.INCLUDE_HOST_NAME,
                JmxSystemPropertyKeys.MANAGEMENT_NAME_PATTERN,
                JmxSystemPropertyKeys.USE_HOST_IP_ADDRESS,
                JmxSystemPropertyKeys.UPDATE_ROUTE_ENABLED))
                .allSatisfy(key -> assertThat(key).startsWith("org.apache.camel.jmx."))
                .doesNotHaveDuplicates();
        assertThat(JmxSystemPropertyKeys.REGISTER_ROUTES_CREATED_BY_TEMPLATE)
                .isEqualTo("org.apache.camel.jmx.registerRoutesCreateByTemplate");
        assertThat(JmxSystemPropertyKeys.REGISTER_ROUTES_CREATED_BY_KAMELET)
                .isEqualTo("org.apache.camel.jmx.registerRoutesCreateByKamelet");
    }

    @Test
    void camelOpenMBeanFactoriesCreateConsistentCompositeAndTabularTypes() throws Exception {
        for (OpenMBeanDescriptor descriptor : openMBeanDescriptors()) {
            CompositeType compositeType = descriptor.compositeFactory.create();
            TabularType tabularType = descriptor.tabularFactory.create();

            assertThat(compositeType.getTypeName()).isEqualTo(descriptor.compositeName);
            assertThat(compositeType.keySet()).containsAll(Arrays.asList(descriptor.itemNames));
            assertThat(tabularType.getTypeName()).isEqualTo(descriptor.tabularName);
            assertThat(tabularType.getRowType()).isEqualTo(compositeType);
            assertThat(tabularType.getIndexNames()).containsExactly(descriptor.indexNames);
        }
    }

    @Test
    void camelOpenMBeanCompositeTypesExposeExpectedItemOpenTypes() throws Exception {
        assertThat(CamelOpenMBeanTypes.listEndpointServicesCompositeType().getType("hits"))
                .isEqualTo(SimpleType.LONG);
        assertThat(CamelOpenMBeanTypes.listEndpointServicesCompositeType().getType("endpointUri"))
                .isEqualTo(SimpleType.STRING);
        assertThat(CamelOpenMBeanTypes.listComponentsCompositeType().getType("deprecated"))
                .isEqualTo(SimpleType.STRING);
        assertThat(CamelOpenMBeanTypes.listRuntimeEndpointsCompositeType().getType("dynamic"))
                .isEqualTo(SimpleType.BOOLEAN);
        assertThat(CamelOpenMBeanTypes.camelHealthDetailsCompositeType().getType("failureCount"))
                .isEqualTo(SimpleType.INTEGER);
        assertThat(CamelOpenMBeanTypes.camelHealthDetailsCompositeType().getType("enabled"))
                .isEqualTo(SimpleType.BOOLEAN);
        assertThat(CamelOpenMBeanTypes.supervisingRouteControllerRouteStatusCompositeType().getType("attempts"))
                .isEqualTo(SimpleType.LONG);
        assertThat(CamelOpenMBeanTypes.camelVariablesCompositeType().getType("key"))
                .isEqualTo(SimpleType.STRING);
        assertThat(CamelOpenMBeanTypes.listErrorRegistryCompositeType().getType("handled"))
                .isEqualTo(SimpleType.BOOLEAN);
    }

    @Test
    void camelOpenMBeanTabularTypesAcceptRowsUsingDeclaredIndexes() throws Exception {
        CompositeType utilizationCompositeType = CamelOpenMBeanTypes.endpointsUtilizationCompositeType();
        TabularDataSupport utilizationTable = new TabularDataSupport(
                CamelOpenMBeanTypes.endpointsUtilizationTabularType());
        utilizationTable.put(new CompositeDataSupport(
                utilizationCompositeType,
                Map.of("url", "direct:start", "hits", 42L)));

        assertThat(utilizationTable.containsKey(new Object[] {"direct:start" })).isTrue();
        assertThat(utilizationTable.get(new Object[] {"direct:start" }).get("hits")).isEqualTo(42L);

        CompositeType variablesCompositeType = CamelOpenMBeanTypes.camelVariablesCompositeType();
        TabularDataSupport variablesTable = new TabularDataSupport(CamelOpenMBeanTypes.camelVariablesTabularType());
        variablesTable.put(new CompositeDataSupport(
                variablesCompositeType,
                Map.of("id", "global", "key", "threshold", "className", "java.lang.Integer", "value", "10")));

        assertThat(variablesTable.containsKey(new Object[] {"global", "threshold" })).isTrue();
        assertThat(variablesTable.get(new Object[] {"global", "threshold" }).get("value")).isEqualTo("10");
    }

    private static List<OpenMBeanDescriptor> openMBeanDescriptors() {
        List<OpenMBeanDescriptor> descriptors = new ArrayList<>();
        descriptors.add(new OpenMBeanDescriptor(
                "endpoints", "listEndpointServices", CamelOpenMBeanTypes::listEndpointServicesCompositeType,
                CamelOpenMBeanTypes::listEndpointServicesTabularType,
                new String[] {
                        "component", "dir", "protocol", "serviceUrl", "metadata", "endpointUri", "routeId", "hits" },
                "component", "dir", "serviceUrl", "endpointUri"));
        descriptors.add(new OpenMBeanDescriptor(
                "rests", "listRestServices", CamelOpenMBeanTypes::listRestServicesCompositeType,
                CamelOpenMBeanTypes::listRestServicesTabularType,
                new String[] {"url", "baseUrl", "basePath", "uriTemplate", "method", "consumes", "produces", "inType",
                        "outType", "kind", "state", "description" },
                "url", "method"));
        descriptors.add(new OpenMBeanDescriptor(
                "endpoints", "listEndpoints", CamelOpenMBeanTypes::listEndpointsCompositeType,
                CamelOpenMBeanTypes::listEndpointsTabularType,
                new String[] {"url", "static", "dynamic" },
                "url"));
        descriptors.add(new OpenMBeanDescriptor(
                "factories", "listExchangeFactory", CamelOpenMBeanTypes::listExchangeFactoryCompositeType,
                CamelOpenMBeanTypes::listExchangeFactoryTabularType,
                new String[] {"url", "routeId", "capacity", "pooled", "created", "acquired", "released", "discarded" },
                "url"));
        descriptors.add(new OpenMBeanDescriptor(
                "endpoints", "listRuntimeEndpoints", CamelOpenMBeanTypes::listRuntimeEndpointsCompositeType,
                CamelOpenMBeanTypes::listRuntimeEndpointsTabularType,
                new String[] {"index", "url", "routeId", "direction", "static", "dynamic", "hits" },
                "index"));
        descriptors.add(new OpenMBeanDescriptor(
                "components", "listComponents", CamelOpenMBeanTypes::listComponentsCompositeType,
                CamelOpenMBeanTypes::listComponentsTabularType,
                new String[] {
                        "name", "title", "syntax", "description", "label", "deprecated", "secret", "status", "type",
                        "groupId", "artifactId", "version" },
                "name"));
        descriptors.add(new OpenMBeanDescriptor(
                "threads", "listAwaitThreads", CamelOpenMBeanTypes::listAwaitThreadsCompositeType,
                CamelOpenMBeanTypes::listAwaitThreadsTabularType,
                new String[] {"id", "name", "exchangeId", "routeId", "nodeId", "duration" },
                "id"));
        descriptors.add(new OpenMBeanDescriptor(
                "eips", "listEips", CamelOpenMBeanTypes::listEipsCompositeType,
                CamelOpenMBeanTypes::listEipsTabularType,
                new String[] {"name", "title", "description", "label", "status", "type" },
                "name"));
        descriptors.add(new OpenMBeanDescriptor(
                "exchanges", "listInflightExchanges", CamelOpenMBeanTypes::listInflightExchangesCompositeType,
                CamelOpenMBeanTypes::listInflightExchangesTabularType,
                new String[] {"exchangeId", "fromRouteId", "routeId", "nodeId", "elapsed", "duration" },
                "exchangeId"));
        descriptors.add(new OpenMBeanDescriptor(
                "predicates", "choice", CamelOpenMBeanTypes::choiceCompositeType,
                CamelOpenMBeanTypes::choiceTabularType,
                new String[] {"predicate", "language", "matches" },
                "predicate"));
        descriptors.add(new OpenMBeanDescriptor(
                "exceptions", "doTry", CamelOpenMBeanTypes::doTryCompositeType,
                CamelOpenMBeanTypes::doTryTabularType,
                new String[] {"exception", "predicate", "language", "matches" },
                "exception"));
        descriptors.add(new OpenMBeanDescriptor(
                "exceptions", "exception", CamelOpenMBeanTypes::loadbalancerExceptionsCompositeType,
                CamelOpenMBeanTypes::loadbalancerExceptionsTabularType,
                new String[] {"exception", "failures" },
                "exception"));
        descriptors.add(new OpenMBeanDescriptor(
                "endpoints", "endpointsUtilization", CamelOpenMBeanTypes::endpointsUtilizationCompositeType,
                CamelOpenMBeanTypes::endpointsUtilizationTabularType,
                new String[] {"url", "hits" },
                "url"));
        descriptors.add(new OpenMBeanDescriptor(
                "transformers", "listTransformers", CamelOpenMBeanTypes::listTransformersCompositeType,
                CamelOpenMBeanTypes::listTransformersTabularType,
                new String[] {"name", "from", "to", "static", "dynamic", "description" },
                "name", "from", "to"));
        descriptors.add(new OpenMBeanDescriptor(
                "validators", "listValidators", CamelOpenMBeanTypes::listValidatorsCompositeType,
                CamelOpenMBeanTypes::listValidatorsTabularType,
                new String[] {"type", "static", "dynamic", "description" },
                "type"));
        descriptors.add(new OpenMBeanDescriptor(
                "healthDetails", "healthDetails", CamelOpenMBeanTypes::camelHealthDetailsCompositeType,
                CamelOpenMBeanTypes::camelHealthDetailsTabularType,
                new String[] {"id", "group", "state", "enabled", "message", "failureUri", "failureCount",
                        "failureStackTrace", "readiness", "liveness" },
                "id"));
        descriptors.add(new OpenMBeanDescriptor(
                "routeProperties", "routeProperties", CamelOpenMBeanTypes::camelRoutePropertiesCompositeType,
                CamelOpenMBeanTypes::camelRoutePropertiesTabularType,
                new String[] {"key", "value" },
                "key"));
        descriptors.add(new OpenMBeanDescriptor(
                "routes", "routeStatus", CamelOpenMBeanTypes::supervisingRouteControllerRouteStatusCompositeType,
                CamelOpenMBeanTypes::supervisingRouteControllerRouteStatusTabularType,
                new String[] {"index", "routeId", "status", "supervising", "attempts", "elapsed", "last", "error",
                        "stacktrace" },
                "index"));
        descriptors.add(new OpenMBeanDescriptor(
                "variables", "variables", CamelOpenMBeanTypes::camelVariablesCompositeType,
                CamelOpenMBeanTypes::camelVariablesTabularType,
                new String[] {"id", "key", "className", "value" },
                "id", "key"));
        descriptors.add(new OpenMBeanDescriptor(
                "tasks", "listBackoff", CamelOpenMBeanTypes::listBackoffTaskCompositeType,
                CamelOpenMBeanTypes::listBackoffTaskTabularType,
                new String[] {
                        "name", "kind", "status", "attempts", "delay", "elapsed", "firstTime", "lastTime", "nextTime",
                        "failure" },
                "name"));
        descriptors.add(new OpenMBeanDescriptor(
                "tasks", "listTask", CamelOpenMBeanTypes::listInternalTaskCompositeType,
                CamelOpenMBeanTypes::listInternalTaskTabularType,
                new String[] {
                        "name", "kind", "status", "attempts", "delay", "elapsed", "firstTime", "lastTime", "nextTime",
                        "failure" },
                "name"));
        descriptors.add(new OpenMBeanDescriptor(
                "errors", "listErrors", CamelOpenMBeanTypes::listErrorRegistryCompositeType,
                CamelOpenMBeanTypes::listErrorRegistryTabularType,
                new String[] {"exchangeId", "routeId", "endpointUri", "timestamp", "handled", "exceptionType",
                        "exceptionMessage" },
                "exchangeId"));
        return descriptors;
    }

    private interface OpenDataFactory<T> {
        T create() throws OpenDataException;
    }

    private record OpenMBeanDescriptor(
            String compositeName,
            String tabularName,
            OpenDataFactory<CompositeType> compositeFactory,
            OpenDataFactory<TabularType> tabularFactory,
            String[] itemNames,
            String... indexNames) {
    }

    private static final class TestVerificationError implements VerificationError {
        private final VerificationError.Code code;
        private final String description;
        private final Set<String> parameterKeys;
        private final Map<VerificationError.Attribute, Object> details;

        private TestVerificationError(
                VerificationError.Code code,
                String description,
                Set<String> parameterKeys,
                Map<VerificationError.Attribute, Object> details) {
            this.code = code;
            this.description = description;
            this.parameterKeys = parameterKeys;
            this.details = details;
        }

        @Override
        public VerificationError.Code getCode() {
            return code;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public Set<String> getParameterKeys() {
            return parameterKeys;
        }

        @Override
        public Map<VerificationError.Attribute, Object> getDetails() {
            return details;
        }
    }

    private static final class ManagedProcessorHolder implements ManagedProcessorAware, ManagedInstance {
        private Processor processor;

        @Override
        public Processor getProcessor() {
            return processor;
        }

        @Override
        public void setProcessor(Processor processor) {
            this.processor = processor;
        }

        @Override
        public Object getInstance() {
            return processor;
        }
    }

    private static final class RecordingNotificationManagedObject
            implements NotificationSenderAware, JmxNotificationBroadcasterAware {
        private final String source;
        private NotificationBroadcasterSupport notificationBroadcaster;
        private NotificationSender notificationSender;
        private long sequenceNumber;

        private RecordingNotificationManagedObject(String source) {
            this.source = source;
        }

        @Override
        public void setNotificationBroadcaster(NotificationBroadcasterSupport notificationBroadcaster) {
            this.notificationBroadcaster = notificationBroadcaster;
        }

        @Override
        public void setNotificationSender(NotificationSender notificationSender) {
            this.notificationSender = notificationSender;
        }

        private void emitRouteStarted(String routeId) {
            assertThat(notificationBroadcaster).isNotNull();
            assertThat(notificationSender).isNotNull();
            Notification notification = new Notification(
                    "org.apache.camel.management.route.started",
                    source,
                    ++sequenceNumber,
                    "route started");
            notification.setUserData(Map.of("routeId", routeId));
            notificationSender.sendNotification(notification);
        }
    }

    private static final class RecordingManagedCamelContext implements ManagedCamelContext {
        private String lastProcessorId;
        private Class<?> lastProcessorType;
        private String lastRouteId;
        private Class<?> lastRouteType;
        private String lastConsumerId;
        private Class<?> lastConsumerType;

        @Override
        public ManagedCamelContextMBean getManagedCamelContext() {
            return null;
        }

        @Override
        public <T extends ManagedProcessorMBean> T getManagedProcessor(String id, Class<T> type) {
            lastProcessorId = id;
            lastProcessorType = type;
            return null;
        }

        @Override
        public ManagedStepMBean getManagedStep(String id) {
            return null;
        }

        @Override
        public <T extends ManagedRouteMBean> T getManagedRoute(String routeId, Class<T> type) {
            lastRouteId = routeId;
            lastRouteType = type;
            return null;
        }

        @Override
        public List<ManagedRouteMBean> getManagedRoutes() {
            return Collections.emptyList();
        }

        @Override
        public ManagedRouteGroupMBean getManagedRouteGroup(String group) {
            return null;
        }

        @Override
        public List<ManagedRouteMBean> getManagedRoutesByGroup(String groupId) {
            return Collections.emptyList();
        }

        @Override
        public <T extends ManagedConsumerMBean> T getManagedConsumer(String id, Class<T> type) {
            lastConsumerId = id;
            lastConsumerType = type;
            return null;
        }
    }
}
