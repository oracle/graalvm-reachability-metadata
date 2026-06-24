/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_oci_sdk.oci_java_sdk_certificates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.bmc.certificates.CertificatesClient;
import com.oracle.bmc.certificates.model.CaBundle;
import com.oracle.bmc.certificates.model.CertificateAuthorityBundle;
import com.oracle.bmc.certificates.model.CertificateAuthorityBundleVersionCollection;
import com.oracle.bmc.certificates.model.CertificateAuthorityBundleVersionSummary;
import com.oracle.bmc.certificates.model.CertificateBundle;
import com.oracle.bmc.certificates.model.CertificateBundlePublicOnly;
import com.oracle.bmc.certificates.model.CertificateBundleVersionCollection;
import com.oracle.bmc.certificates.model.CertificateBundleVersionSummary;
import com.oracle.bmc.certificates.model.CertificateBundleWithPrivateKey;
import com.oracle.bmc.certificates.model.RevocationReason;
import com.oracle.bmc.certificates.model.RevocationStatus;
import com.oracle.bmc.certificates.model.Validity;
import com.oracle.bmc.certificates.model.VersionStage;
import com.oracle.bmc.certificates.requests.GetCaBundleRequest;
import com.oracle.bmc.certificates.requests.GetCertificateAuthorityBundleRequest;
import com.oracle.bmc.certificates.requests.GetCertificateBundleRequest;
import com.oracle.bmc.certificates.requests.ListCertificateAuthorityBundleVersionsRequest;
import com.oracle.bmc.certificates.requests.ListCertificateBundleVersionsRequest;
import com.oracle.bmc.certificates.responses.GetCaBundleResponse;
import com.oracle.bmc.certificates.responses.GetCertificateAuthorityBundleResponse;
import com.oracle.bmc.certificates.responses.GetCertificateBundleResponse;
import com.oracle.bmc.certificates.responses.ListCertificateAuthorityBundleVersionsResponse;
import com.oracle.bmc.certificates.responses.ListCertificateBundleVersionsResponse;
import com.oracle.bmc.http.client.RequestInterceptor;
import com.oracle.bmc.retrier.RetryConfiguration;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

public class Oci_java_sdk_certificatesTest {
    private static final Date CREATED = new Date(1_710_000_000_000L);
    private static final Date NOT_BEFORE = new Date(1_709_000_000_000L);
    private static final Date NOT_AFTER = new Date(1_809_000_000_000L);
    private static final Date REVOKED = new Date(1_711_000_000_000L);
    private static final Date DELETION = new Date(1_812_000_000_000L);

    @Test
    void certificateBundleBuildersCopyAndExposeAllPublicFields() {
        Validity validity = validity();
        RevocationStatus revocationStatus = revocationStatus();
        CertificateBundlePublicOnly publicBundle =
                CertificateBundlePublicOnly.builder()
                        .certificateId("certificate-1")
                        .certificateName("leaf-certificate")
                        .versionNumber(7L)
                        .serialNumber("00:11:22")
                        .certificatePem(
                                "-----BEGIN CERTIFICATE-----public-----END CERTIFICATE-----")
                        .certChainPem("-----BEGIN CERTIFICATE-----chain-----END CERTIFICATE-----")
                        .timeCreated(CREATED)
                        .validity(validity)
                        .versionName("version-a")
                        .stages(List.of(VersionStage.Current, VersionStage.Latest))
                        .revocationStatus(revocationStatus)
                        .build();

        assertThat(publicBundle.getCertificateId()).isEqualTo("certificate-1");
        assertThat(publicBundle.getCertificateName()).isEqualTo("leaf-certificate");
        assertThat(publicBundle.getVersionNumber()).isEqualTo(7L);
        assertThat(publicBundle.getSerialNumber()).isEqualTo("00:11:22");
        assertThat(publicBundle.getCertificatePem()).contains("public");
        assertThat(publicBundle.getCertChainPem()).contains("chain");
        assertThat(publicBundle.getTimeCreated()).isEqualTo(CREATED);
        assertThat(publicBundle.getValidity()).isEqualTo(validity);
        assertThat(publicBundle.getVersionName()).isEqualTo("version-a");
        assertThat(publicBundle.getStages())
                .containsExactly(VersionStage.Current, VersionStage.Latest);
        assertThat(publicBundle.getRevocationStatus()).isEqualTo(revocationStatus);
        assertThat(publicBundle.toString()).contains("leaf-certificate", "version-a");

        CertificateBundlePublicOnly renamedBundle =
                publicBundle.toBuilder().versionName("version-b").build();
        assertThat(renamedBundle)
                .isNotEqualTo(publicBundle)
                .extracting(CertificateBundlePublicOnly::getVersionName)
                .isEqualTo("version-b");
        assertThat(renamedBundle.toBuilder().build()).isEqualTo(renamedBundle);

        CertificateBundleWithPrivateKey privateBundle =
                CertificateBundleWithPrivateKey.builder()
                        .certificateId("certificate-1")
                        .certificateName("leaf-certificate")
                        .versionNumber(8L)
                        .serialNumber("00:11:23")
                        .certificatePem("certificate-pem")
                        .certChainPem("chain-pem")
                        .timeCreated(CREATED)
                        .validity(validity)
                        .versionName("version-private")
                        .stages(List.of(VersionStage.Pending))
                        .revocationStatus(revocationStatus)
                        .privateKeyPem("private-key-pem")
                        .privateKeyPemPassphrase("passphrase")
                        .build();

        assertThat(privateBundle.getPrivateKeyPem()).isEqualTo("private-key-pem");
        assertThat(privateBundle.getPrivateKeyPemPassphrase()).isEqualTo("passphrase");
        assertThat(privateBundle.toBuilder().privateKeyPem("new-key").build().getPrivateKeyPem())
                .isEqualTo("new-key");
    }

    @Test
    void modelsTrackExplicitlySetNullPropertiesSeparatelyFromUnsetProperties() {
        CaBundle unsetNameBundle =
                CaBundle.builder()
                        .id("ca-bundle-1")
                        .caBundlePem("ca-bundle-pem")
                        .build();
        CaBundle explicitNullNameBundle =
                CaBundle.builder()
                        .id("ca-bundle-1")
                        .name(null)
                        .caBundlePem("ca-bundle-pem")
                        .build();

        assertThat(unsetNameBundle.getName()).isNull();
        assertThat(explicitNullNameBundle.getName()).isNull();
        assertThat(unsetNameBundle.wasPropertyExplicitlySet("name")).isFalse();
        assertThat(explicitNullNameBundle.wasPropertyExplicitlySet("name")).isTrue();
        assertThat(explicitNullNameBundle.wasPropertyExplicitlySet("id")).isTrue();
        assertThat(explicitNullNameBundle).isNotEqualTo(unsetNameBundle);

        CaBundle copiedExplicitNullNameBundle = explicitNullNameBundle.toBuilder().build();
        CaBundle copiedUnsetNameBundle = unsetNameBundle.toBuilder().build();

        assertThat(copiedExplicitNullNameBundle).isEqualTo(explicitNullNameBundle);
        assertThat(copiedExplicitNullNameBundle.wasPropertyExplicitlySet("name")).isTrue();
        assertThat(copiedUnsetNameBundle).isEqualTo(unsetNameBundle);
        assertThat(copiedUnsetNameBundle.wasPropertyExplicitlySet("name")).isFalse();
    }

    @Test
    void caBundleAndVersionCollectionsPreserveNestedModels() {
        Validity validity = validity();
        RevocationStatus revocationStatus = revocationStatus();
        CertificateAuthorityBundle authorityBundle =
                CertificateAuthorityBundle.builder()
                        .certificateAuthorityId("authority-1")
                        .certificateAuthorityName("root-ca")
                        .serialNumber("ca-serial")
                        .certificatePem("ca-certificate")
                        .certChainPem("ca-chain")
                        .versionName("ca-version")
                        .timeCreated(CREATED)
                        .versionNumber(11L)
                        .validity(validity)
                        .stages(List.of(VersionStage.Current))
                        .revocationStatus(revocationStatus)
                        .build();
        CertificateBundleVersionSummary certificateSummary =
                CertificateBundleVersionSummary.builder()
                        .certificateId("certificate-1")
                        .serialNumber("leaf-serial")
                        .versionName("leaf-version")
                        .certificateName("leaf")
                        .versionNumber(12L)
                        .timeCreated(CREATED)
                        .validity(validity)
                        .timeOfDeletion(DELETION)
                        .stages(List.of(VersionStage.Previous))
                        .revocationStatus(revocationStatus)
                        .build();
        CertificateAuthorityBundleVersionSummary authoritySummary =
                CertificateAuthorityBundleVersionSummary.builder()
                        .certificateAuthorityId("authority-1")
                        .serialNumber("authority-serial")
                        .timeCreated(CREATED)
                        .versionNumber(13L)
                        .versionName("authority-version")
                        .certificateAuthorityName("root-ca")
                        .timeOfDeletion(DELETION)
                        .validity(validity)
                        .stages(List.of(VersionStage.Deprecated))
                        .revocationStatus(revocationStatus)
                        .build();

        assertThat(authorityBundle.getCertificateAuthorityId()).isEqualTo("authority-1");
        assertThat(authorityBundle.getCertificateAuthorityName()).isEqualTo("root-ca");
        assertThat(authorityBundle.getSerialNumber()).isEqualTo("ca-serial");
        assertThat(authorityBundle.getCertificatePem()).isEqualTo("ca-certificate");
        assertThat(authorityBundle.getCertChainPem()).isEqualTo("ca-chain");
        assertThat(authorityBundle.getVersionName()).isEqualTo("ca-version");
        assertThat(authorityBundle.getVersionNumber()).isEqualTo(11L);
        assertThat(
                        authorityBundle
                                .toBuilder()
                                .versionName("ca-version-copy")
                                .build()
                                .getVersionName())
                .isEqualTo("ca-version-copy");

        CertificateBundleVersionCollection certificateCollection =
                CertificateBundleVersionCollection.builder()
                        .items(List.of(certificateSummary))
                        .build();
        CertificateAuthorityBundleVersionCollection authorityCollection =
                CertificateAuthorityBundleVersionCollection.builder()
                        .items(List.of(authoritySummary))
                        .build();

        assertThat(certificateCollection.getItems()).containsExactly(certificateSummary);
        assertThat(certificateCollection.toBuilder().build()).isEqualTo(certificateCollection);
        assertThat(authorityCollection.getItems()).containsExactly(authoritySummary);
        assertThat(authorityCollection.toBuilder().build()).isEqualTo(authorityCollection);
        assertThat(certificateSummary.getTimeOfDeletion()).isEqualTo(DELETION);
        assertThat(authoritySummary.getTimeOfDeletion()).isEqualTo(DELETION);
    }

    @Test
    void requestBuildersCopyQueryHeaderAndSortingOptions() {
        GetCertificateBundleRequest certificateRequest =
                GetCertificateBundleRequest.builder()
                        .certificateId("certificate-1")
                        .opcRequestId("request-1")
                        .versionNumber(3L)
                        .certificateVersionName("leaf-version")
                        .stage(GetCertificateBundleRequest.Stage.Current)
                        .certificateBundleType(
                                GetCertificateBundleRequest.CertificateBundleType
                                        .CertificateContentWithPrivateKey)
                        .build();
        GetCertificateAuthorityBundleRequest authorityRequest =
                GetCertificateAuthorityBundleRequest.builder()
                        .certificateAuthorityId("authority-1")
                        .opcRequestId("request-2")
                        .versionNumber(4L)
                        .certificateAuthorityVersionName("authority-version")
                        .stage(GetCertificateAuthorityBundleRequest.Stage.Pending)
                        .build();
        GetCaBundleRequest caBundleRequest =
                GetCaBundleRequest.builder()
                        .caBundleId("ca-bundle-1")
                        .opcRequestId("request-3")
                        .build();
        ListCertificateBundleVersionsRequest listCertificateRequest =
                ListCertificateBundleVersionsRequest.builder()
                        .certificateId("certificate-1")
                        .opcRequestId("request-4")
                        .sortBy(ListCertificateBundleVersionsRequest.SortBy.VersionNumber)
                        .sortOrder(ListCertificateBundleVersionsRequest.SortOrder.Desc)
                        .build();
        ListCertificateAuthorityBundleVersionsRequest listAuthorityRequest =
                ListCertificateAuthorityBundleVersionsRequest.builder()
                        .certificateAuthorityId("authority-1")
                        .opcRequestId("request-5")
                        .sortBy(ListCertificateAuthorityBundleVersionsRequest.SortBy.VersionNumber)
                        .sortOrder(ListCertificateAuthorityBundleVersionsRequest.SortOrder.Asc)
                        .build();

        assertThat(certificateRequest.getCertificateId()).isEqualTo("certificate-1");
        assertThat(certificateRequest.getOpcRequestId()).isEqualTo("request-1");
        assertThat(certificateRequest.getVersionNumber()).isEqualTo(3L);
        assertThat(certificateRequest.getCertificateVersionName()).isEqualTo("leaf-version");
        assertThat(certificateRequest.getStage())
                .isEqualTo(GetCertificateBundleRequest.Stage.Current);
        assertThat(certificateRequest.getCertificateBundleType().getValue())
                .isEqualTo("CERTIFICATE_CONTENT_WITH_PRIVATE_KEY");
        assertThat(certificateRequest.toBuilder().versionNumber(5L).build().getVersionNumber())
                .isEqualTo(5L);

        assertThat(authorityRequest.getCertificateAuthorityId()).isEqualTo("authority-1");
        assertThat(authorityRequest.getCertificateAuthorityVersionName())
                .isEqualTo("authority-version");
        assertThat(authorityRequest.getStage())
                .isEqualTo(GetCertificateAuthorityBundleRequest.Stage.Pending);
        assertThat(caBundleRequest.getCaBundleId()).isEqualTo("ca-bundle-1");
        assertThat(listCertificateRequest.getSortBy())
                .isEqualTo(ListCertificateBundleVersionsRequest.SortBy.VersionNumber);
        assertThat(listCertificateRequest.getSortOrder())
                .isEqualTo(ListCertificateBundleVersionsRequest.SortOrder.Desc);
        assertThat(listAuthorityRequest.getSortBy())
                .isEqualTo(ListCertificateAuthorityBundleVersionsRequest.SortBy.VersionNumber);
        assertThat(listAuthorityRequest.getSortOrder())
                .isEqualTo(ListCertificateAuthorityBundleVersionsRequest.SortOrder.Asc);
    }

    @Test
    void requestBuildersSupportInvocationCallbacksAndRetryConfiguration() {
        RequestInterceptor interceptor = httpRequest -> assertThat(httpRequest).isNotNull();
        RetryConfiguration retryConfiguration = RetryConfiguration.NO_RETRY_CONFIGURATION;

        GetCaBundleRequest request =
                GetCaBundleRequest.builder()
                        .caBundleId("ca-bundle-1")
                        .invocationCallback(interceptor)
                        .retryConfiguration(retryConfiguration)
                        .build();

        assertThat(request.getInvocationCallback()).isSameAs(interceptor);
        assertThat(request.getRetryConfiguration()).isSameAs(retryConfiguration);

        GetCaBundleRequest requestWithoutCallback =
                GetCaBundleRequest.builder()
                        .caBundleId("ca-bundle-1")
                        .invocationCallback(interceptor)
                        .retryConfiguration(retryConfiguration)
                        .buildWithoutInvocationCallback();
        assertThat(requestWithoutCallback.getCaBundleId()).isEqualTo("ca-bundle-1");
        assertThat(requestWithoutCallback.getInvocationCallback()).isNull();
    }

    @Test
    void responseBuildersCopyStatusHeadersAndBodies() {
        CaBundle caBundle =
                CaBundle.builder()
                        .id("ca-bundle-1")
                        .name("downloadable-ca-bundle")
                        .caBundlePem("ca-bundle-pem")
                        .build();
        CertificateBundlePublicOnly certificateBundle =
                CertificateBundlePublicOnly.builder()
                        .certificateId("certificate-1")
                        .certificateName("leaf")
                        .versionNumber(1L)
                        .build();
        CertificateAuthorityBundle authorityBundle =
                CertificateAuthorityBundle.builder()
                        .certificateAuthorityId("authority-1")
                        .certificateAuthorityName("root-ca")
                        .versionNumber(2L)
                        .build();
        CertificateBundleVersionCollection certificateCollection =
                CertificateBundleVersionCollection.builder()
                        .items(
                                List.of(
                                        CertificateBundleVersionSummary.builder()
                                                .certificateId("certificate-1")
                                                .build()))
                        .build();
        CertificateAuthorityBundleVersionCollection authorityCollection =
                CertificateAuthorityBundleVersionCollection.builder()
                        .items(
                                List.of(
                                        CertificateAuthorityBundleVersionSummary.builder()
                                                .certificateAuthorityId("authority-1")
                                                .build()))
                        .build();

        GetCaBundleResponse caResponse =
                GetCaBundleResponse.builder()
                        .__httpStatusCode__(200)
                        .etag("etag-ca")
                        .opcRequestId("opc-ca")
                        .caBundle(caBundle)
                        .build();
        GetCertificateBundleResponse certificateResponse =
                GetCertificateBundleResponse.builder()
                        .__httpStatusCode__(200)
                        .etag("etag-certificate")
                        .opcRequestId("opc-certificate")
                        .certificateBundle(certificateBundle)
                        .build();
        GetCertificateAuthorityBundleResponse authorityResponse =
                GetCertificateAuthorityBundleResponse.builder()
                        .__httpStatusCode__(200)
                        .etag("etag-authority")
                        .opcRequestId("opc-authority")
                        .certificateAuthorityBundle(authorityBundle)
                        .build();
        ListCertificateBundleVersionsResponse listCertificateResponse =
                ListCertificateBundleVersionsResponse.builder()
                        .__httpStatusCode__(200)
                        .opcRequestId("opc-list-certificate")
                        .certificateBundleVersionCollection(certificateCollection)
                        .build();
        ListCertificateAuthorityBundleVersionsResponse listAuthorityResponse =
                ListCertificateAuthorityBundleVersionsResponse.builder()
                        .__httpStatusCode__(200)
                        .opcRequestId("opc-list-authority")
                        .certificateAuthorityBundleVersionCollection(authorityCollection)
                        .build();

        assertThat(caResponse.getEtag()).isEqualTo("etag-ca");
        assertThat(caResponse.getOpcRequestId()).isEqualTo("opc-ca");
        assertThat(caResponse.getCaBundle()).isEqualTo(caBundle);
        assertThat(caResponse.toString()).contains("etag-ca", "opc-ca");
        assertThat(caResponse.toString()).contains("ca-bundle-1");

        assertThat(certificateResponse.getEtag()).isEqualTo("etag-certificate");
        assertThat(certificateResponse.getOpcRequestId()).isEqualTo("opc-certificate");
        assertThat(certificateResponse.getCertificateBundle()).isEqualTo(certificateBundle);
        GetCertificateBundleResponse copiedCertificateResponse =
                GetCertificateBundleResponse.builder().copy(certificateResponse).build();
        assertThat(certificateResponse).isEqualTo(copiedCertificateResponse);

        assertThat(authorityResponse.getCertificateAuthorityBundle()).isEqualTo(authorityBundle);
        assertThat(authorityResponse.getEtag()).isEqualTo("etag-authority");
        assertThat(listCertificateResponse.getCertificateBundleVersionCollection())
                .isEqualTo(certificateCollection);
        assertThat(listAuthorityResponse.getCertificateAuthorityBundleVersionCollection())
                .isEqualTo(authorityCollection);
    }

    @Test
    void enumsExposeWireValuesAndDifferentiateRequestFromResponseUnknownValues() {
        assertThat(VersionStage.Current.getValue()).isEqualTo("CURRENT");
        assertThat(VersionStage.create("FUTURE_STAGE")).isEqualTo(VersionStage.UnknownEnumValue);
        assertThat(RevocationReason.KeyCompromise.getValue()).isEqualTo("KEY_COMPROMISE");
        assertThat(RevocationReason.create("NEW_REASON"))
                .isEqualTo(RevocationReason.UnknownEnumValue);
        assertThat(CertificateBundle.CertificateBundleType.CertificateContentPublicOnly.getValue())
                .isEqualTo("CERTIFICATE_CONTENT_PUBLIC_ONLY");
        assertThat(CertificateBundle.CertificateBundleType.create("NEW_BUNDLE_TYPE"))
                .isEqualTo(CertificateBundle.CertificateBundleType.UnknownEnumValue);
        assertThat(GetCertificateBundleRequest.Stage.create("LATEST"))
                .isEqualTo(GetCertificateBundleRequest.Stage.Latest);
        assertThat(GetCertificateBundleRequest.CertificateBundleType.create(
                        "CERTIFICATE_CONTENT_PUBLIC_ONLY"))
                .isEqualTo(
                        GetCertificateBundleRequest.CertificateBundleType
                                .CertificateContentPublicOnly);
        assertThat(CertificatesClient.SERVICE.getServiceName()).isEqualTo("CERTIFICATES");
        assertThat(CertificatesClient.SERVICE.getServiceEndpointTemplate())
                .isEqualTo("https://certificates.{region}.oci.{secondLevelDomain}");

        assertThatThrownBy(() -> GetCertificateBundleRequest.Stage.create("FUTURE_STAGE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Stage: FUTURE_STAGE");
        assertThatThrownBy(
                        () ->
                                GetCertificateBundleRequest.CertificateBundleType.create(
                                        "NEW_BUNDLE_TYPE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid CertificateBundleType: NEW_BUNDLE_TYPE");
    }

    @Test
    void jacksonUsesCertificateBundleDiscriminatorForPolymorphicModels() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String privateKeyBundleJson =
                """
                {
                  "certificateBundleType": "CERTIFICATE_CONTENT_WITH_PRIVATE_KEY",
                  "certificateId": "certificate-1",
                  "certificateName": "leaf-certificate",
                  "versionNumber": 9,
                  "serialNumber": "serial-1",
                  "certificatePem": "certificate-pem",
                  "certChainPem": "chain-pem",
                  "versionName": "version-private",
                  "stages": ["CURRENT", "LATEST"],
                  "revocationStatus": {
                    "timeRevoked": 1711000000000,
                    "revocationReason": "KEY_COMPROMISE"
                  },
                  "privateKeyPem": "private-key-pem",
                  "privateKeyPemPassphrase": "passphrase"
                }
                """;
        String publicBundleJson =
                """
                {
                  "certificateBundleType": "CERTIFICATE_CONTENT_PUBLIC_ONLY",
                  "certificateId": "certificate-2",
                  "certificateName": "public-certificate",
                  "versionNumber": 10,
                  "validity": {
                    "timeOfValidityNotBefore": 1709000000000,
                    "timeOfValidityNotAfter": 1809000000000
                  }
                }
                """;

        CertificateBundle privateBundle =
                objectMapper.readValue(privateKeyBundleJson, CertificateBundle.class);
        CertificateBundle publicBundle =
                objectMapper.readValue(publicBundleJson, CertificateBundle.class);

        assertThat(privateBundle).isInstanceOf(CertificateBundleWithPrivateKey.class);
        CertificateBundleWithPrivateKey withPrivateKey =
                (CertificateBundleWithPrivateKey) privateBundle;
        assertThat(withPrivateKey.getCertificateId()).isEqualTo("certificate-1");
        assertThat(withPrivateKey.getVersionNumber()).isEqualTo(9L);
        assertThat(withPrivateKey.getStages())
                .containsExactly(VersionStage.Current, VersionStage.Latest);
        assertThat(withPrivateKey.getRevocationStatus().getRevocationReason())
                .isEqualTo(RevocationReason.KeyCompromise);
        assertThat(withPrivateKey.getPrivateKeyPem()).isEqualTo("private-key-pem");
        assertThat(withPrivateKey.getPrivateKeyPemPassphrase()).isEqualTo("passphrase");

        assertThat(publicBundle).isInstanceOf(CertificateBundlePublicOnly.class);
        assertThat(publicBundle.getCertificateId()).isEqualTo("certificate-2");
        assertThat(publicBundle.getValidity().getTimeOfValidityNotBefore()).isEqualTo(NOT_BEFORE);
        assertThat(publicBundle.getValidity().getTimeOfValidityNotAfter()).isEqualTo(NOT_AFTER);
    }

    private static Validity validity() {
        return Validity.builder()
                .timeOfValidityNotBefore(NOT_BEFORE)
                .timeOfValidityNotAfter(NOT_AFTER)
                .build();
    }

    private static RevocationStatus revocationStatus() {
        return RevocationStatus.builder()
                .timeRevoked(REVOKED)
                .revocationReason(RevocationReason.KeyCompromise)
                .build();
    }
}
