/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_data_appconfiguration;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.azure.core.util.Configuration;
import com.azure.data.appconfiguration.ConfigurationAsyncClient;
import com.azure.data.appconfiguration.ConfigurationClient;
import com.azure.data.appconfiguration.ConfigurationClientBuilder;
import com.azure.data.appconfiguration.models.ConfigurationSetting;
import com.azure.data.appconfiguration.models.ConfigurationSnapshot;
import com.azure.data.appconfiguration.models.ConfigurationSnapshotStatus;
import com.azure.data.appconfiguration.models.FeatureFlagConfigurationSetting;
import com.azure.data.appconfiguration.models.SecretReferenceConfigurationSetting;
import com.azure.data.appconfiguration.models.SettingFields;
import com.azure.data.appconfiguration.models.SettingLabel;
import com.azure.data.appconfiguration.models.SettingLabelSelector;
import com.azure.data.appconfiguration.models.SettingSelector;
import com.azure.data.appconfiguration.models.SnapshotComposition;
import com.azure.data.appconfiguration.models.SnapshotFields;
import com.azure.data.appconfiguration.models.SnapshotSelector;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Timeout(60)
public class AzureDataAppConfigurationTest {
    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(10);
    private static final String CONNECTION_STRING =
            "Endpoint=https://config.example.test;Id=test-id;Secret=c2VjcmV0";
    private static final String SETTING_RESPONSE = """
            {
              "key": "app:message",
              "label": "production",
              "content_type": "text/plain",
              "value": "hello from app configuration",
              "last_modified": "2024-05-01T12:30:00Z",
              "tags": {"owner": "platform", "tier": "api"},
              "locked": true,
              "etag": "etag-1"
            }
            """;
    private static final String FEATURE_FLAG_VALUE = """
            {
              "id": "BetaCheckout",
              "enabled": true,
              "description": "Gradual rollout",
              "display_name": "Beta checkout",
              "conditions": {
                "client_filters": [
                  {"name": "Microsoft.Percentage", "parameters": {"Value": 25}}
                ]
              }
            }
            """;
    private static final String FEATURE_FLAG_RESPONSE = """
            {
              "key": ".appconfig.featureflag/BetaCheckout",
              "label": "production",
              "content_type": "application/vnd.microsoft.appconfig.ff+json;charset=utf-8",
              "value": "%s",
              "last_modified": "2024-05-02T12:30:00Z",
              "tags": {"owner": "commerce"},
              "locked": false,
              "etag": "feature-etag"
            }
            """.formatted(asJsonString(FEATURE_FLAG_VALUE));
    private static final String SECRET_REFERENCE_RESPONSE = """
            {
              "key": "database:password",
              "label": "production",
              "content_type": "application/vnd.microsoft.appconfig.keyvaultref+json;charset=utf-8",
              "value": "%s",
              "last_modified": "2024-05-03T12:30:00Z",
              "tags": {"owner": "security"},
              "locked": false,
              "etag": "secret-etag"
            }
            """.formatted(asJsonString("{\"uri\":\"https://vault.example.test/secrets/database-password\"}"));
    private static final String SNAPSHOT_RESPONSE = """
            {
              "name": "production-release",
              "status": "ready",
              "filters": [
                {"key": "app:*", "label": "production", "tags": ["tier=api"]}
              ],
              "composition_type": "key_label",
              "created": "2024-05-01T10:00:00Z",
              "expires": "2024-06-01T10:00:00Z",
              "retention_period": 2592000,
              "size": 512,
              "items_count": 2,
              "tags": {"release": "may"},
              "etag": "snapshot-etag"
            }
            """;

    @Test
    void performsConfigurationSettingLifecycleThroughSignedRequests() {
        RecordingHttpClient httpClient = new RecordingHttpClient(
                SETTING_RESPONSE, SETTING_RESPONSE, SETTING_RESPONSE, SETTING_RESPONSE);
        ConfigurationClient client = newClient(httpClient);
        ConfigurationSetting input = new ConfigurationSetting()
                .setKey("app:message")
                .setLabel("production")
                .setValue("hello from app configuration")
                .setContentType("text/plain");

        ConfigurationSetting added = client.addConfigurationSetting(input);
        ConfigurationSetting fetched = client.getConfigurationSetting("app:message", "production");
        ConfigurationSetting locked = client.setReadOnly("app:message", "production", true);
        ConfigurationSetting deleted = client.deleteConfigurationSetting("app:message", "production");

        assertSetting(added);
        assertSetting(fetched);
        assertSetting(locked);
        assertSetting(deleted);
        assertThat(client.getEndpoint()).isEqualTo("https://config.example.test");
        assertThat(httpClient.requests()).hasSize(4);
        assertRequest(httpClient.requests().get(0), HttpMethod.PUT, "/kv/app:message");
        assertRequest(httpClient.requests().get(1), HttpMethod.GET, "/kv/app:message");
        assertRequest(httpClient.requests().get(2), HttpMethod.PUT, "/locks/app:message");
        assertRequest(httpClient.requests().get(3), HttpMethod.DELETE, "/kv/app:message");
    }

    @Test
    void materializesFeatureFlagsAndSecretReferences() {
        RecordingHttpClient httpClient =
                new RecordingHttpClient(FEATURE_FLAG_RESPONSE, SECRET_REFERENCE_RESPONSE);
        ConfigurationClient client = newClient(httpClient);

        ConfigurationSetting featureSetting =
                client.getConfigurationSetting(".appconfig.featureflag/BetaCheckout", "production");
        ConfigurationSetting secretSetting =
                client.getConfigurationSetting("database:password", "production");

        assertThat(featureSetting).isInstanceOf(FeatureFlagConfigurationSetting.class);
        FeatureFlagConfigurationSetting feature = (FeatureFlagConfigurationSetting) featureSetting;
        assertThat(feature.getFeatureId()).isEqualTo("BetaCheckout");
        assertThat(feature.isEnabled()).isTrue();
        assertThat(feature.getDescription()).isEqualTo("Gradual rollout");
        assertThat(feature.getDisplayName()).isEqualTo("Beta checkout");
        assertThat(feature.getClientFilters()).hasSize(1);
        assertThat(feature.getClientFilters().get(0).getName()).isEqualTo("Microsoft.Percentage");
        assertThat(feature.getClientFilters().get(0).getParameters().get("Value")).hasToString("25");

        assertThat(secretSetting).isInstanceOf(SecretReferenceConfigurationSetting.class);
        SecretReferenceConfigurationSetting secret = (SecretReferenceConfigurationSetting) secretSetting;
        assertThat(secret.getKey()).isEqualTo("database:password");
        assertThat(secret.getSecretId())
                .isEqualTo("https://vault.example.test/secrets/database-password");
    }

    @Test
    void listsSettingsLabelsAndSnapshotsWithSelectors() {
        RecordingHttpClient httpClient = new RecordingHttpClient(
                "{\"items\":[" + SETTING_RESPONSE + "]}",
                "{\"items\":[{\"name\":\"production\"},{\"name\":\"staging\"}]}",
                "{\"items\":[" + SNAPSHOT_RESPONSE + "]}");
        ConfigurationClient client = newClient(httpClient);
        SettingSelector settingSelector = new SettingSelector()
                .setKeyFilter("app:*")
                .setLabelFilter("production")
                .setFields(SettingFields.KEY, SettingFields.VALUE, SettingFields.TAGS);
        SettingLabelSelector labelSelector = new SettingLabelSelector().setNameFilter("prod*");
        SnapshotSelector snapshotSelector = new SnapshotSelector()
                .setNameFilter("production*")
                .setStatus(ConfigurationSnapshotStatus.READY)
                .setFields(SnapshotFields.NAME, SnapshotFields.STATUS, SnapshotFields.ITEM_COUNT);

        List<ConfigurationSetting> settings = new ArrayList<>();
        client.listConfigurationSettings(settingSelector).forEach(settings::add);
        List<SettingLabel> labels = new ArrayList<>();
        client.listLabels(labelSelector).forEach(labels::add);
        List<ConfigurationSnapshot> snapshots = new ArrayList<>();
        client.listSnapshots(snapshotSelector).forEach(snapshots::add);

        assertThat(settings).hasSize(1);
        assertSetting(settings.get(0));
        assertThat(labels).extracting(SettingLabel::getName).containsExactly("production", "staging");
        assertThat(snapshots).hasSize(1);
        assertSnapshot(snapshots.get(0));
        assertThat(httpClient.requests()).hasSize(3);
        assertThat(httpClient.requests().get(0).getUrl().getPath()).isEqualTo("/kv");
        assertThat(URLDecoder.decode(httpClient.requests().get(0).getUrl().getQuery(), UTF_8))
                .contains("key=app:*")
                .contains("label=production")
                .contains("$Select=key,value,tags");
        assertThat(httpClient.requests().get(1).getUrl().getPath()).isEqualTo("/labels");
        assertThat(httpClient.requests().get(2).getUrl().getPath()).isEqualTo("/snapshots");
    }

    @Test
    void listsConfigurationSettingRevisionsAtPointInTime() {
        String revisionResponse = """
                {
                  "items": [{
                    "key": "app:message",
                    "label": "production",
                    "content_type": "text/plain",
                    "value": "previous message",
                    "last_modified": "2024-04-30T09:15:00Z",
                    "tags": {"owner": "platform"},
                    "locked": false,
                    "etag": "etag-previous"
                  }]
                }
                """;
        RecordingHttpClient httpClient = new RecordingHttpClient(revisionResponse);
        ConfigurationClient client = newClient(httpClient);
        SettingSelector selector = new SettingSelector()
                .setKeyFilter("app:message")
                .setLabelFilter("production")
                .setAcceptDatetime(OffsetDateTime.parse("2024-05-01T12:00:00Z"));

        List<ConfigurationSetting> revisions = new ArrayList<>();
        client.listRevisions(selector).forEach(revisions::add);

        assertThat(revisions).hasSize(1);
        ConfigurationSetting revision = revisions.get(0);
        assertThat(revision.getKey()).isEqualTo("app:message");
        assertThat(revision.getLabel()).isEqualTo("production");
        assertThat(revision.getValue()).isEqualTo("previous message");
        assertThat(revision.getLastModified()).hasToString("2024-04-30T09:15Z");
        assertThat(revision.getETag()).isEqualTo("etag-previous");
        assertThat(revision.isReadOnly()).isFalse();
        assertThat(revision.getTags()).containsEntry("owner", "platform");
        assertThat(httpClient.requests()).hasSize(1);
        HttpRequest request = httpClient.requests().get(0);
        assertRequest(request, HttpMethod.GET, "/revisions");
        assertThat(URLDecoder.decode(request.getUrl().getQuery(), UTF_8))
                .contains("key=app:message")
                .contains("label=production");
        assertThat(request.getHeaders().getValue("Accept-Datetime"))
                .isEqualTo("Wed, 1 May 2024 12:00:00 GMT");
    }

    @Test
    void archivesAndRecoversSnapshot() {
        String archivedSnapshotResponse = SNAPSHOT_RESPONSE.replace("\"ready\"", "\"archived\"");
        RecordingHttpClient httpClient =
                new RecordingHttpClient(archivedSnapshotResponse, SNAPSHOT_RESPONSE);
        ConfigurationClient client = newClient(httpClient);

        ConfigurationSnapshot archived = client.archiveSnapshot("production-release");
        ConfigurationSnapshot recovered = client.recoverSnapshot("production-release");

        assertThat(archived.getName()).isEqualTo("production-release");
        assertThat(archived.getStatus()).isEqualTo(ConfigurationSnapshotStatus.ARCHIVED);
        assertThat(recovered.getName()).isEqualTo("production-release");
        assertThat(recovered.getStatus()).isEqualTo(ConfigurationSnapshotStatus.READY);
        assertThat(httpClient.requests()).hasSize(2);
        assertRequest(httpClient.requests().get(0), HttpMethod.PATCH, "/snapshots/production-release");
        assertRequest(httpClient.requests().get(1), HttpMethod.PATCH, "/snapshots/production-release");
    }

    @Test
    void getsSnapshotAndUpdatesSettingAsynchronously() {
        RecordingHttpClient snapshotHttpClient = new RecordingHttpClient(SNAPSHOT_RESPONSE);
        ConfigurationClient client = newClient(snapshotHttpClient);

        ConfigurationSnapshot snapshot = client.getSnapshot("production-release");

        assertSnapshot(snapshot);
        assertRequest(snapshotHttpClient.requests().get(0), HttpMethod.GET, "/snapshots/production-release");

        RecordingHttpClient asyncHttpClient = new RecordingHttpClient(SETTING_RESPONSE);
        ConfigurationAsyncClient asyncClient = newAsyncClient(asyncHttpClient);
        ConfigurationSetting updated = asyncClient
                .setConfigurationSetting(new ConfigurationSetting()
                        .setKey("app:message")
                        .setLabel("production")
                        .setValue("hello from app configuration"))
                .block(ASYNC_TIMEOUT);

        assertSetting(updated);
        assertThat(asyncHttpClient.requests()).hasSize(1);
        assertRequest(asyncHttpClient.requests().get(0), HttpMethod.PUT, "/kv/app:message");
    }

    private static ConfigurationClient newClient(HttpClient httpClient) {
        return new ConfigurationClientBuilder()
                .connectionString(CONNECTION_STRING)
                .configuration(Configuration.NONE)
                .httpClient(httpClient)
                .buildClient();
    }

    private static ConfigurationAsyncClient newAsyncClient(HttpClient httpClient) {
        return new ConfigurationClientBuilder()
                .connectionString(CONNECTION_STRING)
                .configuration(Configuration.NONE)
                .httpClient(httpClient)
                .buildAsyncClient();
    }

    private static void assertSetting(ConfigurationSetting setting) {
        assertThat(setting).isNotNull();
        assertThat(setting.getKey()).isEqualTo("app:message");
        assertThat(setting.getLabel()).isEqualTo("production");
        assertThat(setting.getValue()).isEqualTo("hello from app configuration");
        assertThat(setting.getContentType()).isEqualTo("text/plain");
        assertThat(setting.getETag()).isEqualTo("etag-1");
        assertThat(setting.getLastModified()).hasToString("2024-05-01T12:30Z");
        assertThat(setting.isReadOnly()).isTrue();
        assertThat(setting.getTags()).containsEntry("owner", "platform").containsEntry("tier", "api");
    }

    private static void assertSnapshot(ConfigurationSnapshot snapshot) {
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getName()).isEqualTo("production-release");
        assertThat(snapshot.getStatus()).isEqualTo(ConfigurationSnapshotStatus.READY);
        assertThat(snapshot.getSnapshotComposition()).isEqualTo(SnapshotComposition.KEY_LABEL);
        assertThat(snapshot.getRetentionPeriod()).isEqualTo(Duration.ofDays(30));
        assertThat(snapshot.getSizeInBytes()).isEqualTo(512L);
        assertThat(snapshot.getItemCount()).isEqualTo(2L);
        assertThat(snapshot.getFilters()).hasSize(1);
        assertThat(snapshot.getFilters().get(0).getKey()).isEqualTo("app:*");
        assertThat(snapshot.getTags()).containsEntry("release", "may");
    }

    private static void assertRequest(HttpRequest request, HttpMethod method, String path) {
        assertThat(request.getHttpMethod()).isEqualTo(method);
        assertThat(URLDecoder.decode(request.getUrl().getPath(), UTF_8)).isEqualTo(path);
        assertThat(request.getHeaders().getValue("Authorization"))
                .startsWith("HMAC-SHA256 Credential=test-id");
    }

    private static String asJsonString(String value) {
        return value.strip().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static final class RecordingHttpClient implements HttpClient {
        private final Queue<String> responses;
        private final List<HttpRequest> requests = new ArrayList<>();

        private RecordingHttpClient(String... responses) {
            this.responses = new ArrayDeque<>(Arrays.asList(responses));
        }

        @Override
        public Mono<HttpResponse> send(HttpRequest request) {
            requests.add(request);
            String response = responses.poll();
            if (response == null) {
                return Mono.error(new IllegalStateException("No response configured for " + request.getUrl()));
            }
            return Mono.just(new JsonHttpResponse(request, response));
        }

        private List<HttpRequest> requests() {
            return requests;
        }
    }

    private static final class JsonHttpResponse extends HttpResponse {
        private final byte[] body;
        private final HttpHeaders headers;

        private JsonHttpResponse(HttpRequest request, String body) {
            super(request);
            this.body = body.getBytes(UTF_8);
            this.headers = new HttpHeaders()
                    .set("Content-Type", "application/json")
                    .set("Content-Length", Integer.toString(this.body.length));
        }

        @Override
        public int getStatusCode() {
            return 200;
        }

        @Override
        public String getHeaderValue(String name) {
            return headers.getValue(name);
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public Flux<ByteBuffer> getBody() {
            return Flux.defer(() -> Flux.just(ByteBuffer.wrap(body)));
        }

        @Override
        public Mono<byte[]> getBodyAsByteArray() {
            return Mono.just(body.clone());
        }

        @Override
        public Mono<String> getBodyAsString() {
            return Mono.just(new String(body, UTF_8));
        }

        @Override
        public Mono<String> getBodyAsString(Charset charset) {
            return Mono.just(new String(body, charset));
        }
    }
}
