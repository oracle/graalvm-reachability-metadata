/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_apis.google_api_services_sqladmin;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.sqladmin.SQLAdmin;
import com.google.api.services.sqladmin.SQLAdminRequestInitializer;
import com.google.api.services.sqladmin.model.Database;
import com.google.api.services.sqladmin.model.GenerateEphemeralCertRequest;
import com.google.api.services.sqladmin.model.GenerateEphemeralCertResponse;
import com.google.api.services.sqladmin.model.ImportContext;
import com.google.api.services.sqladmin.model.ImportContext.BakImportOptions;
import com.google.api.services.sqladmin.model.ImportContext.BakImportOptions.EncryptionOptions;
import com.google.api.services.sqladmin.model.InstancesListResponse;
import com.google.api.services.sqladmin.model.Operation;
import com.google.api.services.sqladmin.model.SqlServerDatabaseDetails;
import java.io.IOException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Google_api_services_sqladminTest {
    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    @Test
    public void listInstancesBuildsQueryAndParsesNestedConfiguration() throws IOException {
        MockHttpTransport transport = transportReturning("""
                {
                  "kind": "sql#instancesList",
                  "nextPageToken": "next-token",
                  "items": [
                    {
                      "name": "primary",
                      "project": "sample-project",
                      "databaseVersion": "POSTGRES_15",
                      "state": "RUNNABLE",
                      "settings": {
                        "tier": "db-custom-2-7680",
                        "storageAutoResize": true,
                        "userLabels": {"environment": "integration"},
                        "databaseFlags": [
                          {"name": "max_connections", "value": "200"}
                        ],
                        "ipConfiguration": {
                          "ipv4Enabled": false,
                          "authorizedNetworks": [
                            {"name": "office", "value": "192.0.2.0/24"}
                          ]
                        }
                      }
                    }
                  ],
                  "warnings": [
                    {
                      "code": "REGION_UNREACHABLE",
                      "message": "One region could not be queried",
                      "region": "us-central1"
                    }
                  ]
                }
                """);
        SQLAdmin client = newClient(transport, null);

        InstancesListResponse response = client.instances()
                .list("sample-project")
                .setFilter("state:RUNNABLE")
                .setMaxResults(25L)
                .setPageToken("page token")
                .setPrettyPrint(false)
                .execute();

        assertThat(response.getKind()).isEqualTo("sql#instancesList");
        assertThat(response.getNextPageToken()).isEqualTo("next-token");
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getWarnings()).hasSize(1);
        assertThat(response.getWarnings().get(0).getRegion()).isEqualTo("us-central1");

        assertThat(response.getItems().get(0).getName()).isEqualTo("primary");
        assertThat(response.getItems().get(0).getState()).isEqualTo("RUNNABLE");
        assertThat(response.getItems().get(0).getSettings().getTier())
                .isEqualTo("db-custom-2-7680");
        assertThat(response.getItems().get(0).getSettings().getStorageAutoResize()).isTrue();
        assertThat(response.getItems().get(0).getSettings().getUserLabels())
                .containsEntry("environment", "integration");
        assertThat(response.getItems().get(0).getSettings().getDatabaseFlags().get(0).getValue())
                .isEqualTo("200");
        assertThat(response.getItems().get(0).getSettings().getIpConfiguration().getIpv4Enabled())
                .isFalse();
        assertThat(response.getItems().get(0).getSettings()
                        .getIpConfiguration()
                        .getAuthorizedNetworks()
                        .get(0)
                        .getValue())
                .isEqualTo("192.0.2.0/24");

        GenericUrl requestUrl = new GenericUrl(transport.getLowLevelHttpRequest().getUrl());
        assertThat(requestUrl.getRawPath())
                .isEqualTo("/sql/v1beta4/projects/sample-project/instances");
        assertThat(requestUrl.getFirst("filter")).isEqualTo("state:RUNNABLE");
        assertThat(requestUrl.getFirst("maxResults")).isEqualTo("25");
        assertThat(requestUrl.getFirst("pageToken")).isEqualTo("page token");
        assertThat(requestUrl.getFirst("prettyPrint")).isEqualTo("false");
    }

    @Test
    public void insertDatabaseSerializesBodyAndParsesOperationErrors() throws IOException {
        MockHttpTransport transport = transportReturning("""
                {
                  "kind": "sql#operation",
                  "name": "operation-1",
                  "operationType": "CREATE_DATABASE",
                  "status": "DONE",
                  "targetProject": "sample-project",
                  "error": {
                    "kind": "sql#operationErrors",
                    "errors": [
                      {
                        "kind": "sql#operationError",
                        "code": "COMPLETED_WITH_WARNING",
                        "message": "Database was created with a warning"
                      }
                    ]
                  }
                }
                """);
        SQLAdminRequestInitializer initializer = new SQLAdminRequestInitializer("test-api-key");
        SQLAdmin client = newClient(transport, initializer);
        Database database = new Database()
                .setName("inventory")
                .setCharset("UTF8")
                .setCollation("en_US.UTF8")
                .setSqlserverDatabaseDetails(new SqlServerDatabaseDetails()
                        .setCompatibilityLevel(150)
                        .setRecoveryModel("FULL"));

        Operation operation = client.databases()
                .insert("sample-project", "primary", database)
                .setQuotaUser("integration-suite")
                .execute();

        assertThat(operation.getName()).isEqualTo("operation-1");
        assertThat(operation.getOperationType()).isEqualTo("CREATE_DATABASE");
        assertThat(operation.getStatus()).isEqualTo("DONE");
        assertThat(operation.getError().getKind()).isEqualTo("sql#operationErrors");
        assertThat(operation.getError().getErrors()).hasSize(1);
        assertThat(operation.getError().getErrors().get(0).getCode())
                .isEqualTo("COMPLETED_WITH_WARNING");
        assertThat(operation.getError().getErrors().get(0).getMessage())
                .isEqualTo("Database was created with a warning");

        MockLowLevelHttpRequest lowLevelRequest = transport.getLowLevelHttpRequest();
        GenericUrl requestUrl = new GenericUrl(lowLevelRequest.getUrl());
        assertThat(requestUrl.getRawPath())
                .isEqualTo("/sql/v1beta4/projects/sample-project/instances/primary/databases");
        assertThat(requestUrl.getFirst("key")).isEqualTo("test-api-key");
        assertThat(requestUrl.getFirst("quotaUser")).isEqualTo("integration-suite");
        assertThat(lowLevelRequest.getContentAsString())
                .contains("\"name\":\"inventory\"")
                .contains("\"charset\":\"UTF8\"")
                .contains("\"collation\":\"en_US.UTF8\"")
                .contains("\"compatibilityLevel\":150")
                .contains("\"recoveryModel\":\"FULL\"");
    }

    @Test
    public void generateEphemeralCertificateSerializesRequestAndParsesCertificate()
            throws IOException {
        MockHttpTransport transport = transportReturning("""
                {
                  "ephemeralCert": {
                    "cert": "-----BEGIN CERTIFICATE-----\\nclient-cert\\n-----END CERTIFICATE-----",
                    "certSerialNumber": "42",
                    "commonName": "client.example",
                    "createTime": "2024-05-21T10:15:30Z",
                    "expirationTime": "2024-05-21T11:15:30Z",
                    "instance": "primary",
                    "kind": "sql#sslCert",
                    "sha1Fingerprint": "AA:BB:CC"
                  }
                }
                """);
        SQLAdmin client = newClient(transport, null);
        String publicKey = """
                -----BEGIN PUBLIC KEY-----
                client-key
                -----END PUBLIC KEY-----
                """;
        GenerateEphemeralCertRequest request = new GenerateEphemeralCertRequest()
                .setPublicKey(publicKey)
                .setReadTime("2024-05-21T10:15:30Z")
                .setValidDuration("3600s");

        GenerateEphemeralCertResponse response = client.connect()
                .generateEphemeralCert("sample-project", "primary", request)
                .execute();

        assertThat(response.getEphemeralCert().getCert()).contains("client-cert");
        assertThat(response.getEphemeralCert().getCertSerialNumber()).isEqualTo("42");
        assertThat(response.getEphemeralCert().getCommonName()).isEqualTo("client.example");
        assertThat(response.getEphemeralCert().getCreateTime())
                .isEqualTo("2024-05-21T10:15:30Z");
        assertThat(response.getEphemeralCert().getExpirationTime())
                .isEqualTo("2024-05-21T11:15:30Z");
        assertThat(response.getEphemeralCert().getInstance()).isEqualTo("primary");
        assertThat(response.getEphemeralCert().getKind()).isEqualTo("sql#sslCert");
        assertThat(response.getEphemeralCert().getSha1Fingerprint()).isEqualTo("AA:BB:CC");

        MockLowLevelHttpRequest lowLevelRequest = transport.getLowLevelHttpRequest();
        GenericUrl requestUrl = new GenericUrl(lowLevelRequest.getUrl());
        assertThat(requestUrl.getRawPath())
                .isEqualTo("/sql/v1beta4/projects/sample-project/instances/"
                        + "primary:generateEphemeralCert");
        assertThat(lowLevelRequest.getContentAsString())
                .contains("\"public_key\":")
                .contains("client-key")
                .contains("\"readTime\":\"2024-05-21T10:15:30Z\"")
                .contains("\"validDuration\":\"3600s\"");
    }

    @Test
    public void importContextRoundTripsNestedOptionsAndUnknownFields() throws IOException {
        ImportContext context = JSON_FACTORY.fromString("""
                {
                  "database": "inventory",
                  "fileType": "BAK",
                  "importUser": "restore-user",
                  "uri": "gs://backups/inventory.bak",
                  "bakImportOptions": {
                    "bakType": "FULL",
                    "noRecovery": false,
                    "striped": true,
                    "encryptionOptions": {
                      "certPath": "gs://backups/certificate.cer",
                      "pvkPath": "gs://backups/private-key.pvk",
                      "pvkPassword": "secret"
                    }
                  },
                  "requestTag": "nightly-restore"
                }
                """, ImportContext.class);

        assertThat(context.getDatabase()).isEqualTo("inventory");
        assertThat(context.getFileType()).isEqualTo("BAK");
        assertThat(context.getBakImportOptions().getBakType()).isEqualTo("FULL");
        assertThat(context.getBakImportOptions().getNoRecovery()).isFalse();
        assertThat(context.getBakImportOptions().getStriped()).isTrue();
        assertThat(context.getBakImportOptions().getEncryptionOptions().getCertPath())
                .isEqualTo("gs://backups/certificate.cer");
        assertThat(context.get("requestTag")).isEqualTo("nightly-restore");

        ImportContext copy = context.clone()
                .setUri("gs://backups/inventory-copy.bak")
                .set("requestTag", "copy-restore");
        assertThat(context.getUri()).isEqualTo("gs://backups/inventory.bak");
        assertThat(context.get("requestTag")).isEqualTo("nightly-restore");

        String serialized = JSON_FACTORY.toString(copy);
        ImportContext roundTripped = JSON_FACTORY.fromString(serialized, ImportContext.class);
        BakImportOptions options = roundTripped.getBakImportOptions();
        EncryptionOptions encryption = options.getEncryptionOptions();
        assertThat(roundTripped.getUri()).isEqualTo("gs://backups/inventory-copy.bak");
        assertThat(roundTripped.get("requestTag")).isEqualTo("copy-restore");
        assertThat(options.getStriped()).isTrue();
        assertThat(encryption.getPvkPath()).isEqualTo("gs://backups/private-key.pvk");
        assertThat(encryption.getPvkPassword()).isEqualTo("secret");
    }

    private static SQLAdmin newClient(
            MockHttpTransport transport, SQLAdminRequestInitializer initializer) {
        return new SQLAdmin.Builder(transport, JSON_FACTORY, null)
                .setRootUrl("https://sqladmin.test/")
                .setApplicationName("reachability-metadata-test")
                .setSQLAdminRequestInitializer(initializer)
                .build();
    }

    private static MockHttpTransport transportReturning(String responseBody) {
        MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
                .setStatusCode(200)
                .setContentType("application/json; charset=UTF-8")
                .setContent(responseBody);
        return new MockHttpTransport.Builder().setLowLevelHttpResponse(response).build();
    }
}
