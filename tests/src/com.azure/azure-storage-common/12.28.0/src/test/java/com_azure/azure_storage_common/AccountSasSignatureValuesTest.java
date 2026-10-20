/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_storage_common;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.common.sas.AccountSasPermission;
import com.azure.storage.common.sas.AccountSasQueryParameters;
import com.azure.storage.common.sas.AccountSasResourceType;
import com.azure.storage.common.sas.AccountSasService;
import com.azure.storage.common.sas.AccountSasSignatureValues;
import com.azure.storage.common.sas.SasProtocol;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
@SuppressWarnings("deprecation")
public class AccountSasSignatureValuesTest {
    private static final String ACCOUNT_NAME = "sampleaccount";
    private static final String ACCOUNT_KEY = "c2FtcGxlLXNlY3JldC1rZXk=";

    @Test
    void generatesEncodedAccountSasForSelectedServicesAndResources() {
        AccountSasPermission permissions = new AccountSasPermission()
                .setListPermission(true)
                .setWritePermission(true)
                .setReadPermission(true);
        AccountSasService services = new AccountSasService().setQueueAccess(true).setBlobAccess(true);
        AccountSasResourceType resourceTypes = new AccountSasResourceType()
                .setObject(true)
                .setService(true)
                .setContainer(true);
        AccountSasSignatureValues signatureValues = new AccountSasSignatureValues(
                        OffsetDateTime.parse("2024-05-02T12:30:00Z"), permissions, services, resourceTypes)
                .setStartTime(OffsetDateTime.parse("2024-05-01T12:30:00Z"))
                .setProtocol(SasProtocol.HTTPS_ONLY);

        AccountSasQueryParameters queryParameters = signatureValues.generateSasQueryParameters(
                new StorageSharedKeyCredential(ACCOUNT_NAME, ACCOUNT_KEY));

        assertThat(queryParameters.getPermissions()).isEqualTo("rwl");
        assertThat(queryParameters.getServices()).isEqualTo("bq");
        assertThat(queryParameters.getResourceTypes()).isEqualTo("sco");
        assertThat(queryParameters.getSignature()).isEqualTo("cOsVNK4ijKHrjaaisCrk7lJtjHq2hBX2EI4C1KtGhhs=");
        assertThat(queryParameters.encode())
                .isEqualTo("sv=2020-10-02&ss=bq&srt=sco&spr=https&st=2024-05-01T12%3A30%3A00Z"
                        + "&se=2024-05-02T12%3A30%3A00Z&sp=rwl"
                        + "&sig=cOsVNK4ijKHrjaaisCrk7lJtjHq2hBX2EI4C1KtGhhs%3D");
    }
}
