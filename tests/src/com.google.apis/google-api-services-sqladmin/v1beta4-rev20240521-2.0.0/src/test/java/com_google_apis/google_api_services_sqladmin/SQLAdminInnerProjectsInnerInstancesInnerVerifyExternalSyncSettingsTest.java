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
import com.google.api.services.sqladmin.model.MySqlSyncConfig;
import com.google.api.services.sqladmin.model.SqlInstancesVerifyExternalSyncSettingsRequest;
import com.google.api.services.sqladmin.model.SqlInstancesVerifyExternalSyncSettingsResponse;
import com.google.api.services.sqladmin.model.SyncFlags;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SQLAdminInnerProjectsInnerInstancesInnerVerifyExternalSyncSettingsTest {
    @Test
    public void verifiesExternalSyncSettingsAndReturnsActionableFindings() throws IOException {
        MockLowLevelHttpResponse lowLevelResponse = new MockLowLevelHttpResponse()
                .setStatusCode(200)
                .setContentType("application/json; charset=UTF-8")
                .setContent("""
                        {
                          "kind": "sql#verifyExternalSyncSettings",
                          "errors": [
                            {
                              "kind": "sql#externalSyncSettingError",
                              "type": "UNSUPPORTED_DEFINER",
                              "detail": "A view uses a definer that is not available on the target."
                            }
                          ],
                          "warnings": [
                            {
                              "kind": "sql#externalSyncSettingError",
                              "type": "BINLOG_RETENTION_TOO_SHORT",
                              "detail": "Increase binary log retention before migration."
                            }
                          ]
                        }
                        """);
        MockHttpTransport transport = new MockHttpTransport.Builder()
                .setLowLevelHttpResponse(lowLevelResponse)
                .build();
        SQLAdmin client = new SQLAdmin.Builder(transport, GsonFactory.getDefaultInstance(), null)
                .setRootUrl("https://sqladmin.test/")
                .setApplicationName("reachability-metadata-test")
                .build();
        SqlInstancesVerifyExternalSyncSettingsRequest request =
                new SqlInstancesVerifyExternalSyncSettingsRequest()
                        .setMigrationType("ONLINE")
                        .setSyncMode("ONLINE")
                        .setSyncParallelLevel("MIN")
                        .setVerifyConnectionOnly(false)
                        .setVerifyReplicationOnly(true)
                        .setMysqlSyncConfig(new MySqlSyncConfig()
                                .setInitialSyncFlags(List.of(new SyncFlags()
                                        .setName("max_allowed_packet")
                                        .setValue("67108864"))));

        SqlInstancesVerifyExternalSyncSettingsResponse response = client.projects()
                .instances()
                .verifyExternalSyncSettings("sample-project", "external-primary", request)
                .execute();

        assertThat(response.getKind()).isEqualTo("sql#verifyExternalSyncSettings");
        assertThat(response.getErrors()).singleElement().satisfies(error -> {
            assertThat(error.getKind()).isEqualTo("sql#externalSyncSettingError");
            assertThat(error.getType()).isEqualTo("UNSUPPORTED_DEFINER");
            assertThat(error.getDetail()).contains("definer");
        });
        assertThat(response.getWarnings()).singleElement().satisfies(warning -> {
            assertThat(warning.getType()).isEqualTo("BINLOG_RETENTION_TOO_SHORT");
            assertThat(warning.getDetail()).contains("binary log retention");
        });

        MockLowLevelHttpRequest lowLevelRequest = transport.getLowLevelHttpRequest();
        GenericUrl requestUrl = new GenericUrl(lowLevelRequest.getUrl());
        assertThat(requestUrl.getRawPath())
                .isEqualTo("/sql/v1beta4/projects/sample-project/instances/"
                        + "external-primary/verifyExternalSyncSettings");
        assertThat(lowLevelRequest.getContentAsString())
                .contains("\"migrationType\":\"ONLINE\"")
                .contains("\"syncMode\":\"ONLINE\"")
                .contains("\"syncParallelLevel\":\"MIN\"")
                .contains("\"verifyConnectionOnly\":false")
                .contains("\"verifyReplicationOnly\":true")
                .contains("\"initialSyncFlags\":[{\"name\":\"max_allowed_packet\","
                        + "\"value\":\"67108864\"}]");
    }
}
