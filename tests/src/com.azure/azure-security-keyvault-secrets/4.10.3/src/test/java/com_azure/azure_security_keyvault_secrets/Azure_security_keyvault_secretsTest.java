/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_security_keyvault_secrets;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpHeaderName;
import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.azure.core.util.BinaryData;
import com.azure.core.util.polling.LongRunningOperationStatus;
import com.azure.core.util.polling.PollResponse;
import com.azure.security.keyvault.secrets.SecretAsyncClient;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.DeletedSecret;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.azure.security.keyvault.secrets.models.KeyVaultSecretIdentifier;
import com.azure.security.keyvault.secrets.models.SecretProperties;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Timeout(60)
public class Azure_security_keyvault_secretsTest {
    private static final Duration IO_TIMEOUT = Duration.ofSeconds(10);
    private static final String VAULT_URL = "https://vault.example";
    private static final String SECRET_NAME = "integration-secret";
    private static final String SECRET_VERSION = "version-1";
    private static final String SECRET_VALUE = "correct horse battery staple";

    @Test
    void secretIdentifiersExposeVaultNameAndOptionalVersion() {
        String versionedId = VAULT_URL + "/secrets/" + SECRET_NAME + "/" + SECRET_VERSION;
        KeyVaultSecretIdentifier versionedIdentifier = new KeyVaultSecretIdentifier(versionedId);

        assertThat(versionedIdentifier.getSourceId()).isEqualTo(versionedId);
        assertThat(versionedIdentifier.getVaultUrl()).isEqualTo(VAULT_URL);
        assertThat(versionedIdentifier.getName()).isEqualTo(SECRET_NAME);
        assertThat(versionedIdentifier.getVersion()).isEqualTo(SECRET_VERSION);

        String versionlessId = VAULT_URL + "/secrets/" + SECRET_NAME;
        KeyVaultSecretIdentifier versionlessIdentifier = new KeyVaultSecretIdentifier(versionlessId);

        assertThat(versionlessIdentifier.getSourceId()).isEqualTo(versionlessId);
        assertThat(versionlessIdentifier.getVaultUrl()).isEqualTo(VAULT_URL);
        assertThat(versionlessIdentifier.getName()).isEqualTo(SECRET_NAME);
        assertThat(versionlessIdentifier.getVersion()).isNull();
    }

    @Test
    void synchronousClientPerformsSecretLifecycle() {
        KeyVaultHttpClient httpClient = new KeyVaultHttpClient();
        AtomicInteger tokenRequests = new AtomicInteger();
        SecretClient client = newBuilder(httpClient, tokenRequests).buildClient();

        KeyVaultSecret created = client.setSecret(new KeyVaultSecret(SECRET_NAME, SECRET_VALUE)
                .setProperties(new SecretProperties().setContentType("text/plain")));
        assertSecret(created);

        KeyVaultSecret fetched = client.getSecret(SECRET_NAME, SECRET_VERSION);
        assertSecret(fetched);
        assertThat(fetched.getProperties().getTags()).containsEntry("environment", "integration");
        assertThat(fetched.getProperties().getCreatedOn()).isEqualTo(OffsetDateTime.parse("2023-11-14T22:13:20Z"));

        SecretProperties updated = client.updateSecretProperties(
                fetched.getProperties().setEnabled(false).setContentType("application/secret"));
        assertThat(updated.getName()).isEqualTo(SECRET_NAME);
        assertThat(updated.getVersion()).isEqualTo(SECRET_VERSION);
        assertThat(updated.getContentType()).isEqualTo("text/plain");
        assertThat(updated.getRecoveryLevel()).isEqualTo("Recoverable+Purgeable");

        List<SecretProperties> listed = client.listPropertiesOfSecrets().stream().toList();
        assertThat(listed).singleElement().satisfies(properties -> {
            assertThat(properties.getName()).isEqualTo(SECRET_NAME);
            assertThat(properties.getRecoverableDays()).isEqualTo(90);
            assertThat(properties.isManaged()).isFalse();
        });

        PollResponse<DeletedSecret> deletion = client.beginDeleteSecret(SECRET_NAME)
                .setPollInterval(Duration.ofMillis(1))
                .waitForCompletion(IO_TIMEOUT);
        assertThat(deletion.getStatus()).isEqualTo(LongRunningOperationStatus.SUCCESSFULLY_COMPLETED);
        assertThat(deletion.getValue().getName()).isEqualTo(SECRET_NAME);
        assertThat(deletion.getValue().getRecoveryId()).isEqualTo(VAULT_URL + "/deletedsecrets/" + SECRET_NAME);
        assertThat(deletion.getValue().getDeletedOn()).isEqualTo(OffsetDateTime.parse("2023-11-14T22:15:00Z"));

        DeletedSecret deleted = client.getDeletedSecret(SECRET_NAME);
        assertThat(deleted.getScheduledPurgeDate()).isEqualTo(OffsetDateTime.parse("2023-11-15T22:15:00Z"));
        client.purgeDeletedSecret(SECRET_NAME);

        assertThat(client.getVaultUrl()).isEqualTo(VAULT_URL);
        assertThat(tokenRequests).hasValueGreaterThan(0);
        assertThat(httpClient.authorizedRequests()).anySatisfy(request -> {
            assertThat(request.getHttpMethod()).isEqualTo(HttpMethod.PUT);
            assertThat(request.getUrl().getPath()).isEqualTo("/secrets/" + SECRET_NAME);
            assertThat(request.getBodyAsBinaryData().toString()).contains(SECRET_VALUE, "text/plain");
        });
        assertThat(httpClient.authorizedRequests()).anySatisfy(request -> {
            assertThat(request.getHttpMethod()).isEqualTo(HttpMethod.PATCH);
            assertThat(request.getBodyAsBinaryData().toString()).contains("application/secret", "false");
        });
        assertThat(httpClient.authorizedRequests()).anySatisfy(request -> {
            assertThat(request.getHttpMethod()).isEqualTo(HttpMethod.DELETE);
            assertThat(request.getUrl().getPath()).isEqualTo("/deletedsecrets/" + SECRET_NAME);
        });
    }

    @Test
    void synchronousClientRecoversDeletedSecret() {
        KeyVaultHttpClient httpClient = new KeyVaultHttpClient();
        AtomicInteger tokenRequests = new AtomicInteger();
        SecretClient client = newBuilder(httpClient, tokenRequests).buildClient();

        PollResponse<KeyVaultSecret> recovery = client.beginRecoverDeletedSecret(SECRET_NAME)
                .setPollInterval(Duration.ofMillis(1))
                .waitForCompletion(IO_TIMEOUT);

        assertThat(recovery.getStatus()).isEqualTo(LongRunningOperationStatus.SUCCESSFULLY_COMPLETED);
        assertSecret(recovery.getValue());
        assertThat(tokenRequests).hasValueGreaterThan(0);
        assertThat(httpClient.authorizedRequests()).anySatisfy(request -> {
            assertThat(request.getHttpMethod()).isEqualTo(HttpMethod.POST);
            assertThat(request.getUrl().getPath()).isEqualTo("/deletedsecrets/" + SECRET_NAME + "/recover");
        });
        assertThat(httpClient.authorizedRequests()).anySatisfy(request -> {
            assertThat(request.getHttpMethod()).isEqualTo(HttpMethod.GET);
            assertThat(request.getUrl().getPath()).startsWith("/secrets/" + SECRET_NAME);
        });
    }

    @Test
    void asynchronousClientHandlesBackupRestoreAndPagedResults() {
        KeyVaultHttpClient httpClient = new KeyVaultHttpClient();
        AtomicInteger tokenRequests = new AtomicInteger();
        SecretAsyncClient client = newBuilder(httpClient, tokenRequests).buildAsyncClient();

        KeyVaultSecret created = client.setSecret(SECRET_NAME, SECRET_VALUE).block(IO_TIMEOUT);
        assertSecret(created);
        KeyVaultSecret fetched = client.getSecret(SECRET_NAME, SECRET_VERSION).block(IO_TIMEOUT);
        assertSecret(fetched);

        byte[] backup = client.backupSecret(SECRET_NAME).block(IO_TIMEOUT);
        assertThat(backup).containsExactly(1, 2, 3, 4);
        KeyVaultSecret restored = client.restoreSecretBackup(backup).block(IO_TIMEOUT);
        assertSecret(restored);

        List<SecretProperties> versions = client.listPropertiesOfSecretVersions(SECRET_NAME)
                .collectList()
                .block(IO_TIMEOUT);
        assertThat(versions).isNotNull().singleElement().satisfies(properties -> {
            assertThat(properties.getName()).isEqualTo(SECRET_NAME);
            assertThat(properties.getVersion()).isEqualTo(SECRET_VERSION);
        });

        List<DeletedSecret> deletedSecrets = client.listDeletedSecrets().collectList().block(IO_TIMEOUT);
        assertThat(deletedSecrets).isNotNull().singleElement().satisfies(secret -> {
            assertThat(secret.getName()).isEqualTo(SECRET_NAME);
            assertThat(secret.getRecoveryId()).endsWith("/deletedsecrets/" + SECRET_NAME);
        });

        assertThat(client.getVaultUrl()).isEqualTo(VAULT_URL);
        assertThat(tokenRequests).hasValueGreaterThan(0);
        assertThat(httpClient.authorizedRequests()).anySatisfy(request -> {
            assertThat(request.getHttpMethod()).isEqualTo(HttpMethod.POST);
            assertThat(request.getUrl().getPath()).isEqualTo("/secrets/" + SECRET_NAME + "/backup");
        });
        assertThat(httpClient.authorizedRequests()).anySatisfy(request -> {
            assertThat(request.getHttpMethod()).isEqualTo(HttpMethod.POST);
            assertThat(request.getUrl().getPath()).isEqualTo("/secrets/restore");
            assertThat(request.getBodyAsBinaryData().toString()).contains("AQIDBA");
        });
    }

    private static SecretClientBuilder newBuilder(HttpClient httpClient, AtomicInteger tokenRequests) {
        TokenCredential credential = request -> {
            tokenRequests.incrementAndGet();
            assertThat(request.getScopes()).containsExactly("https://vault.azure.net/.default");
            assertThat(request.getTenantId()).isEqualTo("integration-tenant");
            return Mono.just(new AccessToken("integration-token", OffsetDateTime.now().plusHours(1)));
        };
        return new SecretClientBuilder()
                .vaultUrl(VAULT_URL)
                .credential(credential)
                .httpClient(httpClient)
                .disableChallengeResourceVerification();
    }

    private static void assertSecret(KeyVaultSecret secret) {
        assertThat(secret).isNotNull();
        assertThat(secret.getName()).isEqualTo(SECRET_NAME);
        assertThat(secret.getValue()).isEqualTo(SECRET_VALUE);
        assertThat(secret.getId()).isEqualTo(VAULT_URL + "/secrets/" + SECRET_NAME + "/" + SECRET_VERSION);
        assertThat(secret.getProperties().isEnabled()).isTrue();
        assertThat(secret.getProperties().getKeyId()).isEqualTo(VAULT_URL + "/keys/wrapping-key/key-version");
    }

    private static final class KeyVaultHttpClient implements HttpClient {
        private final List<HttpRequest> authorizedRequests = new ArrayList<>();

        @Override
        public Mono<HttpResponse> send(HttpRequest request) {
            if (request.getHeaders().getValue(HttpHeaderName.AUTHORIZATION) == null) {
                HttpHeaders headers = new HttpHeaders().set(
                        HttpHeaderName.WWW_AUTHENTICATE,
                        "Bearer authorization=\"https://login.microsoftonline.com/integration-tenant\", "
                                + "resource=\"https://vault.azure.net\"");
                return Mono.just(new KeyVaultHttpResponse(request, 401, headers, ""));
            }

            authorizedRequests.add(request.copy());
            return Mono.just(route(request));
        }

        List<HttpRequest> authorizedRequests() {
            return authorizedRequests;
        }

        private static HttpResponse route(HttpRequest request) {
            String path = request.getUrl().getPath();
            HttpMethod method = request.getHttpMethod();
            if (method == HttpMethod.DELETE && path.equals("/deletedsecrets/" + SECRET_NAME)) {
                return jsonResponse(request, 204, "");
            }
            if (method == HttpMethod.DELETE && path.equals("/secrets/" + SECRET_NAME)) {
                return jsonResponse(request, 200, deletedSecretJson());
            }
            if (method == HttpMethod.POST && path.equals("/secrets/" + SECRET_NAME + "/backup")) {
                return jsonResponse(request, 200, "{\"value\":\"AQIDBA==\"}");
            }
            if (method == HttpMethod.POST && path.equals("/deletedsecrets/" + SECRET_NAME + "/recover")) {
                return jsonResponse(request, 200, secretJson());
            }
            if (method == HttpMethod.POST && path.equals("/secrets/restore")) {
                return jsonResponse(request, 200, secretJson());
            }
            if (method == HttpMethod.GET && path.equals("/deletedsecrets")) {
                return jsonResponse(request, 200, "{\"value\":[" + deletedSecretJson() + "]}");
            }
            if (method == HttpMethod.GET && path.equals("/deletedsecrets/" + SECRET_NAME)) {
                return jsonResponse(request, 200, deletedSecretJson());
            }
            if (method == HttpMethod.GET && (path.equals("/secrets") || path.endsWith("/versions"))) {
                return jsonResponse(request, 200, "{\"value\":[" + secretPropertiesJson() + "]}");
            }
            if ((method == HttpMethod.PUT || method == HttpMethod.PATCH || method == HttpMethod.GET)
                    && path.startsWith("/secrets/" + SECRET_NAME)) {
                return jsonResponse(request, 200, secretJson());
            }
            return jsonResponse(request, 404, "{\"error\":{\"code\":\"NotFound\",\"message\":\"unknown route\"}}");
        }

        private static HttpResponse jsonResponse(HttpRequest request, int status, String body) {
            HttpHeaders headers = new HttpHeaders().set(HttpHeaderName.CONTENT_TYPE, "application/json");
            return new KeyVaultHttpResponse(request, status, headers, body);
        }
    }

    private static final class KeyVaultHttpResponse extends HttpResponse {
        private final int statusCode;
        private final HttpHeaders headers;
        private final byte[] body;

        private KeyVaultHttpResponse(HttpRequest request, int statusCode, HttpHeaders headers, String body) {
            super(request);
            this.statusCode = statusCode;
            this.headers = headers;
            this.body = body.getBytes(UTF_8);
        }

        @Override
        public int getStatusCode() {
            return statusCode;
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
        public BinaryData getBodyAsBinaryData() {
            return BinaryData.fromBytes(body);
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

    private static String secretJson() {
        return "{\"value\":\"" + SECRET_VALUE + "\"," + secretPropertiesJson().substring(1);
    }

    private static String secretPropertiesJson() {
        return "{\"id\":\"" + VAULT_URL + "/secrets/" + SECRET_NAME + "/" + SECRET_VERSION + "\","
                + "\"attributes\":{\"enabled\":true,\"created\":1700000000,\"updated\":1700000100,"
                + "\"recoveryLevel\":\"Recoverable+Purgeable\",\"recoverableDays\":90},"
                + "\"contentType\":\"text/plain\",\"tags\":{\"environment\":\"integration\"},"
                + "\"kid\":\"" + VAULT_URL + "/keys/wrapping-key/key-version\",\"managed\":false}";
    }

    private static String deletedSecretJson() {
        return "{\"recoveryId\":\"" + VAULT_URL + "/deletedsecrets/" + SECRET_NAME + "\","
                + "\"deletedDate\":1700000100,\"scheduledPurgeDate\":1700086500,"
                + "\"value\":\"" + SECRET_VALUE + "\"," + secretPropertiesJson().substring(1);
    }
}
