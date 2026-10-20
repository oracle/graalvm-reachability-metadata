/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_paho.org_eclipse_paho_client_mqttv3;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Exercises subscription grant handling under broker topic authorization: the SUBACK
/// granted-QoS codes the client reports per filter, the `0x80` failure code a broker ACL
/// refuses a filter with, `MqttException.REASON_CODE_SUBSCRIBE_FAILED` from a refused
/// single-filter `MqttClient.subscribe`, QoS downgrades by the broker's `max_qos`, and
/// `unsubscribe` ending a granted subscription.
///
/// The scenario is a multi-tenant smart building whose accounts and roles are provisioned
/// through Mosquitto's dynamic-security control topic: each tenant's gateway logs in with
/// its own account and may only read and write its tenant's topic tree, read building
/// announcements, and write its own telemetry. The facilities service reads every tenant's
/// tree and telemetry and writes announcements. Gateways learn from the grant codes which
/// filters the broker accepted, and publishes into another tenant's tree are dropped.
public class TopicAuthorizationSubscriptionGrantTest {

    private static final String IMAGE = "eclipse-mosquitto:2.0.22";

    private static final String BROKER_CONFIG = """
            listener 1883
            allow_anonymous false
            max_qos 1
            plugin /usr/lib/mosquitto_dynamic_security.so
            plugin_opt_config_file /tmp/auth/dynamic-security.json
            """;

    private static final String BROKER_SCRIPT = """
            mkdir -p /tmp/auth \
            && printf '%s' "$1" > /tmp/auth/mosquitto.conf \
            && mosquitto_ctrl dynsec init /tmp/auth/dynamic-security.json admin "$2" \
            && chmod 0700 /tmp/auth && chmod 0600 /tmp/auth/* && chown -R mosquitto /tmp/auth \
            && exec mosquitto -c /tmp/auth/mosquitto.conf""";

    private static final String CONTROL_TOPIC = "$CONTROL/dynamic-security/v1";

    private static final String ADMIN_PASSWORD = "admin-secret";

    private static final String PASSWORD = "building-secret";

    private static final int REFUSED = 0x80;

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);

    private static final long TIMEOUT_MS = 10_000;

    private static String containerName;

    private static String serverUri;

    @BeforeAll
    static void startBroker() throws Exception {
        containerName = "paho-acl-" + System.nanoTime();
        docker("run", "-d", "--name", containerName, "-p", "127.0.0.1::1883", IMAGE,
                "sh", "-c", BROKER_SCRIPT, "sh", BROKER_CONFIG, ADMIN_PASSWORD);
        String mapping = docker("port", containerName, "1883/tcp").lines().findFirst().orElseThrow();
        serverUri = "tcp://127.0.0.1:" + mapping.substring(mapping.lastIndexOf(':') + 1);
        awaitBroker();
        provisionAccounts();
    }

    @AfterAll
    static void stopBroker() throws Exception {
        if (containerName != null) {
            docker("rm", "-f", "-v", containerName);
        }
    }

    @Test
    void gatewayLearnsWhichFiltersTheBrokerGranted() throws Exception {
        BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
        MqttClient gateway = connected("tenant-a");
        MqttClient facilities = connected("facilities");
        try {
            String[] filters = {"tenants/tenant-a/#", "tenants/tenant-b/#", "building/announcements"};
            int[] qos = {2, 1, 0};
            IMqttMessageListener record = (topic, message) -> inbox.add(topic + " " + text(message));
            gateway.subscribe(filters, qos, new IMqttMessageListener[] {record, record, record});
            assertThat(qos).as("granted QoS written back per filter").containsExactly(1, REFUSED, 0);

            facilities.publish("tenants/tenant-b/notice", bytes("lift B out of service"), 1, false);
            facilities.publish("building/announcements", bytes("fire drill at noon"), 1, false);
            facilities.publish("tenants/tenant-a/notice", bytes("parcel at reception"), 1, false);
            assertThat(inbox.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    .isEqualTo("building/announcements fire drill at noon");
            assertThat(inbox.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    .isEqualTo("tenants/tenant-a/notice parcel at reception");

            assertThatThrownBy(() -> gateway.subscribe("telemetry/#", 1))
                    .isInstanceOfSatisfying(MqttException.class, e -> assertThat(e.getReasonCode())
                            .isEqualTo(MqttException.REASON_CODE_SUBSCRIBE_FAILED));
            assertThat(gateway.isConnected()).isTrue();
            assertThat(inbox).isEmpty();
        } finally {
            closeQuietly(gateway);
            closeQuietly(facilities);
        }
    }

    @Test
    void asyncGatewayReadsGrantCodesFromTheSubscribeToken() throws Exception {
        BlockingQueue<MqttMessage> inbox = new LinkedBlockingQueue<>();
        MqttAsyncClient gateway = new MqttAsyncClient(serverUri, "tenant-b-" + System.nanoTime(),
                new MemoryPersistence());
        MqttClient facilities = connected("facilities");
        try {
            gateway.connect(credentials("tenant-b", PASSWORD)).waitForCompletion(TIMEOUT_MS);
            String[] filters = {"telemetry/tenant-b/+", "tenants/tenant-b/#", "tenants/+/notice"};
            IMqttMessageListener record = (topic, message) -> inbox.add(message);
            IMqttToken subscription = gateway.subscribe(filters, new int[] {0, 2, 1}, null, null,
                    new IMqttMessageListener[] {record, record, record});
            subscription.waitForCompletion(TIMEOUT_MS);
            assertThat(subscription.isComplete()).isTrue();
            assertThat(subscription.getException()).isNull();
            assertThat(subscription.getTopics()).containsExactly(filters);
            assertThat(subscription.getGrantedQos()).containsExactly(REFUSED, 1, REFUSED);

            facilities.publish("tenants/tenant-b/door", bytes("open"), 1, false);
            MqttMessage door = inbox.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertThat(door).isNotNull();
            assertThat(text(door)).isEqualTo("open");
            assertThat(door.getQos()).isEqualTo(1);

            gateway.unsubscribe("tenants/tenant-b/#").waitForCompletion(TIMEOUT_MS);
            facilities.publish("tenants/tenant-b/door", bytes("closed"), 1, false);
            IMqttToken marker = gateway.subscribe("building/announcements", 1, null, null, record);
            marker.waitForCompletion(TIMEOUT_MS);
            assertThat(marker.getGrantedQos()).containsExactly(1);
            facilities.publish("building/announcements", bytes("lobby closed"), 1, false);
            MqttMessage announcement = inbox.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertThat(announcement).isNotNull();
            assertThat(text(announcement)).as("door event after unsubscribe was not delivered")
                    .isEqualTo("lobby closed");
            gateway.disconnect().waitForCompletion(TIMEOUT_MS);
        } finally {
            if (gateway.isConnected()) {
                gateway.disconnectForcibly(1_000, 1_000);
            }
            gateway.close();
            closeQuietly(facilities);
        }
    }

    @Test
    void publishesOutsideTheTenantTreeAreDropped() throws Exception {
        BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
        MqttClient gateway = connected("tenant-a");
        MqttClient facilities = connected("facilities");
        try {
            int[] qos = {1, 1};
            facilities.subscribe(new String[] {"tenants/#", "telemetry/#"}, qos,
                    new IMqttMessageListener[] {
                        (topic, message) -> inbox.add(topic + " " + text(message)),
                        (topic, message) -> inbox.add(topic + " " + text(message))
                    });
            assertThat(qos).containsExactly(1, 1);

            gateway.publish("tenants/tenant-b/door", bytes("forced"), 1, false);
            gateway.publish("building/announcements", bytes("free coffee"), 1, false);
            gateway.publish("telemetry/tenant-b/temperature", bytes("99.9"), 1, false);
            gateway.publish("telemetry/tenant-a/temperature", bytes("21.5"), 1, false);
            gateway.publish("tenants/tenant-a/door", bytes("locked"), 1, false);

            assertThat(inbox.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    .isEqualTo("telemetry/tenant-a/temperature 21.5");
            assertThat(inbox.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    .isEqualTo("tenants/tenant-a/door locked");
            assertThat(gateway.isConnected()).isTrue();
            assertThat(gateway.getPendingDeliveryTokens()).isEmpty();
            assertThat(inbox).isEmpty();
        } finally {
            closeQuietly(gateway);
            closeQuietly(facilities);
        }
    }

    /// Creates one role and one account per tenant and for the facilities service through the
    /// broker's dynamic-security control topic, then checks that every command succeeded.
    private static void provisionAccounts() throws Exception {
        ObjectMapper json = new ObjectMapper();
        ArrayNode commands = json.createArrayNode();
        for (String tenant : List.of("tenant-a", "tenant-b")) {
            role(commands, tenant);
            acl(commands, tenant, "subscribePattern", "tenants/" + tenant + "/#");
            acl(commands, tenant, "publishClientSend", "tenants/" + tenant + "/#");
            acl(commands, tenant, "publishClientSend", "telemetry/" + tenant + "/+");
            acl(commands, tenant, "subscribeLiteral", "building/announcements");
            account(commands, tenant);
        }
        role(commands, "facilities");
        acl(commands, "facilities", "subscribePattern", "tenants/#");
        acl(commands, "facilities", "subscribePattern", "telemetry/#");
        acl(commands, "facilities", "publishClientSend", "tenants/#");
        acl(commands, "facilities", "publishClientSend", "building/announcements");
        account(commands, "facilities");
        ObjectNode request = json.createObjectNode();
        request.set("commands", commands);

        BlockingQueue<MqttMessage> responses = new LinkedBlockingQueue<>();
        MqttClient admin = new MqttClient(serverUri, "admin-" + System.nanoTime(), new MemoryPersistence());
        try {
            admin.connect(credentials("admin", ADMIN_PASSWORD));
            admin.subscribe(CONTROL_TOPIC + "/response", 1, (topic, message) -> responses.add(message));
            admin.publish(CONTROL_TOPIC, json.writeValueAsBytes(request), 1, false);
            MqttMessage reply = responses.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertThat(reply).as("dynamic-security response").isNotNull();
            JsonNode results = json.readTree(reply.getPayload()).get("responses");
            assertThat(results).hasSize(commands.size());
            for (JsonNode result : results) {
                assertThat(result.has("error")).as(result.toString()).isFalse();
            }
        } finally {
            closeQuietly(admin);
        }
    }

    private static void role(ArrayNode commands, String role) {
        commands.addObject().put("command", "createRole").put("rolename", role);
    }

    private static void acl(ArrayNode commands, String role, String type, String topic) {
        commands.addObject().put("command", "addRoleACL").put("rolename", role)
                .put("acltype", type).put("topic", topic).put("allow", true);
    }

    private static void account(ArrayNode commands, String user) {
        ObjectNode command = commands.addObject().put("command", "createClient")
                .put("username", user).put("password", PASSWORD);
        command.putArray("roles").addObject().put("rolename", user);
    }

    private static MqttClient connected(String user) throws MqttException {
        MqttClient client = new MqttClient(serverUri, user + "-" + System.nanoTime(), new MemoryPersistence());
        client.setTimeToWait(TIMEOUT_MS);
        client.connect(credentials(user, PASSWORD));
        return client;
    }

    private static MqttConnectOptions credentials(String user, String password) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
        options.setCleanSession(true);
        options.setAutomaticReconnect(false);
        options.setConnectionTimeout(5);
        options.setKeepAliveInterval(30);
        options.setUserName(user);
        options.setPassword(password.toCharArray());
        return options;
    }

    private static void closeQuietly(MqttClient client) throws MqttException {
        if (client.isConnected()) {
            client.disconnectForcibly(1_000, 1_000);
        }
        client.close();
    }

    private static void awaitBroker() throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        MqttException last = null;
        while (System.nanoTime() < deadline) {
            MqttClient probe = new MqttClient(serverUri, "startup-probe-" + System.nanoTime(),
                    new MemoryPersistence());
            try {
                probe.connect(credentials("admin", ADMIN_PASSWORD));
                probe.disconnect();
                return;
            } catch (MqttException e) {
                last = e;
            } finally {
                closeQuietly(probe);
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("Mosquitto did not accept connections in time", last);
    }

    private static String docker(String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (InputStream stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        return output;
    }

    private static String text(MqttMessage message) {
        return new String(message.getPayload(), StandardCharsets.UTF_8);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
