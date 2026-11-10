/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java_api;

import com.google.gson.JsonParser;

import static org.assertj.core.api.Assertions.assertThat;

final class ModelJson {
    static final String ENDPOINT_PORT = """
            {"appProtocol":"http","name":"web","port":8080,"protocol":"TCP"}
            """;
    static final String CLIENT_IP = """
            {"timeoutSeconds":600}
            """;
    static final String CONDITION = """
            {"lastTransitionTime":"2024-03-04T05:06:07.000000Z","message":"Address allocated",
             "observedGeneration":3,"reason":"Allocated","status":"True","type":"Ready"}
            """;
    static final String OBJECT_REFERENCE = """
            {"apiVersion":"v1","fieldPath":"spec.containers{web}","kind":"Pod",
             "name":"web-0","namespace":"apps","resourceVersion":"42","uid":"pod-uid"}
            """;
    static final String ENDPOINT_ADDRESS = """
            {"hostname":"web-0","ip":"10.0.0.8","nodeName":"worker-a","targetRef":%s}
            """.formatted(OBJECT_REFERENCE);
    static final String ENDPOINT_SUBSET = """
            {"addresses":[%s],"notReadyAddresses":[{"ip":"10.0.0.9","nodeName":"worker-b"}],
             "ports":[%s]}
            """.formatted(ENDPOINT_ADDRESS, ENDPOINT_PORT);
    static final String LIST_META = """
            {"continue":"next-page","remainingItemCount":2,"resourceVersion":"42",
             "selfLink":"/api/v1/namespaces/apps/services"}
            """;
    static final String PORT_STATUS = """
            {"error":"PortAllocationPending","port":8080,"protocol":"TCP"}
            """;
    static final String INGRESS = """
            {"hostname":"web.example.test","ip":"192.0.2.10","ipMode":"VIP","ports":[%s]}
            """.formatted(PORT_STATUS);
    static final String LOAD_BALANCER = """
            {"ingress":[%s]}
            """.formatted(INGRESS);
    static final String MANAGED_FIELDS = """
            {"apiVersion":"v1","fieldsType":"FieldsV1","fieldsV1":{"f:spec":{"f:ports":{}}},
             "manager":"service-controller","operation":"Update","subresource":"status",
             "time":"2024-03-04T05:06:07.000000Z"}
            """;
    static final String OWNER_REFERENCE = """
            {"apiVersion":"apps/v1","blockOwnerDeletion":true,"controller":true,
             "kind":"Deployment","name":"web","uid":"deployment-uid"}
            """;
    static final String OBJECT_META = """
            {"annotations":{"sync.example.test/enabled":"true"},
             "creationTimestamp":"2024-03-04T05:06:07.000000Z","deletionGracePeriodSeconds":30,
             "deletionTimestamp":"2024-03-05T05:06:07.000000Z","finalizers":["sync.example.test/cleanup"],
             "generateName":"web-","generation":3,"labels":{"app":"web"},
             "name":"web","namespace":"apps","ownerReferences":[%s],"resourceVersion":"42",
             "selfLink":"/api/v1/namespaces/apps/services/web","uid":"service-uid"}
            """.formatted(OWNER_REFERENCE);
    static final String ENDPOINTS = """
            {"apiVersion":"v1","kind":"Endpoints","metadata":%s,"subsets":[%s]}
            """.formatted(OBJECT_META, ENDPOINT_SUBSET);
    static final String ENDPOINTS_LIST = """
            {"apiVersion":"v1","items":[%s],"kind":"EndpointsList","metadata":%s}
            """.formatted(ENDPOINTS, LIST_META);
    static final String SERVICE_PORT = """
            {"appProtocol":"http","name":"web","nodePort":30080,"port":80,
             "protocol":"TCP","targetPort":"http"}
            """;
    static final String AFFINITY = """
            {"clientIP":%s}
            """.formatted(CLIENT_IP);
    static final String SERVICE_SPEC = """
            {"allocateLoadBalancerNodePorts":true,"clusterIP":"10.96.0.10",
             "clusterIPs":["10.96.0.10","fd00::10"],"externalIPs":["192.0.2.20"],
             "externalName":"web.example.test","externalTrafficPolicy":"Local",
             "healthCheckNodePort":30100,"internalTrafficPolicy":"Cluster",
             "ipFamilies":["IPv4","IPv6"],"ipFamilyPolicy":"PreferDualStack",
             "loadBalancerClass":"example.test/balancer","loadBalancerIP":"192.0.2.10",
             "loadBalancerSourceRanges":["192.0.2.0/24"],"ports":[%s],
             "publishNotReadyAddresses":true,"selector":{"app":"web"},
             "sessionAffinity":"ClientIP","sessionAffinityConfig":%s,
             "trafficDistribution":"PreferClose","type":"LoadBalancer"}
            """.formatted(SERVICE_PORT, AFFINITY);
    static final String SERVICE_STATUS = """
            {"conditions":[%s],"loadBalancer":%s}
            """.formatted(CONDITION, LOAD_BALANCER);
    static final String SERVICE = """
            {"apiVersion":"v1","kind":"Service","metadata":%s,"spec":%s,"status":%s}
            """.formatted(OBJECT_META, SERVICE_SPEC, SERVICE_STATUS);
    static final String SERVICE_LIST = """
            {"apiVersion":"v1","items":[%s],"kind":"ServiceList","metadata":%s}
            """.formatted(SERVICE, LIST_META);
    static final String STATUS_CAUSE = """
            {"field":"spec.ports[0].port","message":"Must be a valid port","reason":"FieldValueInvalid"}
            """;
    static final String STATUS_DETAILS = """
            {"causes":[%s],"group":"","kind":"services","name":"web",
             "retryAfterSeconds":10,"uid":"service-uid"}
            """.formatted(STATUS_CAUSE);
    static final String STATUS = """
            {"apiVersion":"v1","code":422,"details":%s,"kind":"Status",
             "message":"Service is invalid","metadata":%s,"reason":"Invalid","status":"Failure"}
            """.formatted(STATUS_DETAILS, LIST_META);

    private ModelJson() {
    }

    static void assertJsonEquals(String expected, String actual) {
        assertThat(JsonParser.parseString(actual)).isEqualTo(JsonParser.parseString(expected));
    }
}
