/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.models.AzureCloud;
import com.azure.core.util.serializer.JacksonAdapter;
import com.azure.core.util.serializer.SerializerEncoding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class JacksonAdapterTest {
    @Test
    void deserializesExpandableStringEnumFromText() throws Exception {
        AzureCloud cloud = new JacksonAdapter()
                .deserialize("CONTOSO_PRIVATE_CLOUD", AzureCloud.class, SerializerEncoding.TEXT);

        assertThat(cloud.getValue()).isEqualTo("CONTOSO_PRIVATE_CLOUD");
        assertThat(AzureCloud.fromString("CONTOSO_PRIVATE_CLOUD")).isSameAs(cloud);
    }
}
