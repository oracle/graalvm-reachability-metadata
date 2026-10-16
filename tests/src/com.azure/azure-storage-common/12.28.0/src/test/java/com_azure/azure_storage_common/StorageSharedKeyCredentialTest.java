/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_storage_common;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.storage.common.StorageSharedKeyCredential;
import java.net.URL;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class StorageSharedKeyCredentialTest {
    private static final String ACCOUNT_NAME = "sampleaccount";
    private static final String ACCOUNT_KEY = "c2FtcGxlLXNlY3JldC1rZXk=";

    @Test
    void createsCredentialFromConnectionStringAndSignsContent() {
        StorageSharedKeyCredential credential = StorageSharedKeyCredential.fromConnectionString(
                "DefaultEndpointsProtocol=https;AccountName=" + ACCOUNT_NAME + ";AccountKey=" + ACCOUNT_KEY
                        + ";EndpointSuffix=core.windows.net");

        assertThat(credential.getAccountName()).isEqualTo(ACCOUNT_NAME);
        assertThat(credential.computeHmac256("storage request payload"))
                .isEqualTo("EvvYlsmrR/RGPv1wIHl7ZjwmT2k6ipv+Zp0Sb1hp5wg=");
    }

    @Test
    void generatesAuthorizationHeaderFromCanonicalRequest() throws Exception {
        StorageSharedKeyCredential credential = new StorageSharedKeyCredential(ACCOUNT_NAME, ACCOUNT_KEY);
        URL requestUrl = new URL(
                "https://sampleaccount.blob.core.windows.net/container/report.txt?comp=metadata");
        Map<String, String> headers = Map.of(
                "Content-Length", "0",
                "x-ms-date", "Wed, 01 May 2024 12:30:00 GMT",
                "x-ms-version", "2023-11-03");

        String authorization = credential.generateAuthorizationHeader(requestUrl, "GET", headers);

        assertThat(authorization)
                .isEqualTo("SharedKey sampleaccount:Q0WDnFALy1twl3SW76ew0vO2D9UPLQrmUBIOQOAI0eE=");
    }
}
