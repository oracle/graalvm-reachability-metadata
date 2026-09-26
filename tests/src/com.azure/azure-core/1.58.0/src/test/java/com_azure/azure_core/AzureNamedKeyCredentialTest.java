/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.credential.AzureNamedKey;
import com.azure.core.credential.AzureNamedKeyCredential;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class AzureNamedKeyCredentialTest {
    @Test
    void updatesTheNameAndKeyAsOneCredentialValue() {
        AzureNamedKeyCredential credential = new AzureNamedKeyCredential("primary", "key-one");

        AzureNamedKey initial = credential.getAzureNamedKey();
        credential.update("secondary", "key-two");
        AzureNamedKey updated = credential.getAzureNamedKey();

        assertThat(initial.getName()).isEqualTo("primary");
        assertThat(initial.getKey()).isEqualTo("key-one");
        assertThat(updated.getName()).isEqualTo("secondary");
        assertThat(updated.getKey()).isEqualTo("key-two");
    }
}
