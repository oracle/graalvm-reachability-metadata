/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud.google_cloud_storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.NoCredentials;
import com.google.cloud.storage.GrpcStorageOptions;
import com.google.cloud.storage.Storage;
import java.time.Duration;
import org.junit.jupiter.api.Test;

public class GrpcStorageOptionsInnerZeroCopyReadinessCheckerTest {
    @Test
    void creatingGrpcStorageServiceChecksZeroCopyReadiness() throws Exception {
        GrpcStorageOptions options = GrpcStorageOptions.newBuilder()
                .setProjectId("test-project")
                .setCredentials(NoCredentials.getInstance())
                .setAttemptDirectPath(false)
                .setEnableGrpcClientMetrics(false)
                .setTerminationAwaitJavaTimeDuration(Duration.ofSeconds(1))
                .build();

        try (Storage storage = GrpcStorageOptions.defaults().getDefaultServiceFactory().create(options)) {
            assertThat(storage).isNotNull();
        }
    }
}
