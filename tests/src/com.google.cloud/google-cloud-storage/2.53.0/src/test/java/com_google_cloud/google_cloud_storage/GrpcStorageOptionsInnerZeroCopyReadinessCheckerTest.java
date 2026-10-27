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
import com.google.cloud.storage.StorageFactory;
import org.junit.jupiter.api.Test;

public class GrpcStorageOptionsInnerZeroCopyReadinessCheckerTest {
    @Test
    public void createGrpcServiceInitializesZeroCopyReadinessChecker() throws Exception {
        GrpcStorageOptions options = GrpcStorageOptions.newBuilder()
                .setProjectId("test-project")
                .setCredentials(NoCredentials.getInstance())
                .setHost("http://localhost:1")
                .setEnableGrpcClientMetrics(false)
                .build();
        StorageFactory factory = GrpcStorageOptions.defaults().getDefaultServiceFactory();

        Storage storage = factory.create(options);
        try {
            assertThat(storage).isNotNull();
        } finally {
            storage.close();
        }
    }
}
