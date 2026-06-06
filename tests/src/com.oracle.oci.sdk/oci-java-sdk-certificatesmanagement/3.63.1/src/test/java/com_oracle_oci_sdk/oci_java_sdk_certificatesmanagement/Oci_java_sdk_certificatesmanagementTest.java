/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_oci_sdk.oci_java_sdk_certificatesmanagement;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracle.bmc.Region;
import com.oracle.bmc.certificatesmanagement.CertificatesManagement;
import com.oracle.bmc.certificatesmanagement.CertificatesManagementPaginators;
import com.oracle.bmc.certificatesmanagement.CertificatesManagementWaiters;
import com.oracle.bmc.certificatesmanagement.model.Certificate;
import com.oracle.bmc.certificatesmanagement.model.CertificateCollection;
import com.oracle.bmc.certificatesmanagement.model.CertificateConfigType;
import com.oracle.bmc.certificatesmanagement.model.CertificateLifecycleState;
import com.oracle.bmc.certificatesmanagement.model.CertificateProfileType;
import com.oracle.bmc.certificatesmanagement.model.CertificateRenewalRule;
import com.oracle.bmc.certificatesmanagement.model.CertificateRevocationListDetails;
import com.oracle.bmc.certificatesmanagement.model.CertificateSubject;
import com.oracle.bmc.certificatesmanagement.model.CertificateSubjectAlternativeName;
import com.oracle.bmc.certificatesmanagement.model.CertificateSummary;
import com.oracle.bmc.certificatesmanagement.model.CertificateVersionSummary;
import com.oracle.bmc.certificatesmanagement.model.CreateCertificateByImportingConfigDetails;
import com.oracle.bmc.certificatesmanagement.model.CreateCertificateDetails;
import com.oracle.bmc.certificatesmanagement.model.CreateCertificateIssuedByInternalCaConfigDetails;
import com.oracle.bmc.certificatesmanagement.model.CreateCertificateManagedExternallyIssuedByInternalCaConfigDetails;
import com.oracle.bmc.certificatesmanagement.model.CreateRootCaByGeneratingInternallyConfigDetails;
import com.oracle.bmc.certificatesmanagement.model.KeyAlgorithm;
import com.oracle.bmc.certificatesmanagement.model.ObjectStorageBucketConfigDetails;
import com.oracle.bmc.certificatesmanagement.model.RevocationReason;
import com.oracle.bmc.certificatesmanagement.model.RevocationStatus;
import com.oracle.bmc.certificatesmanagement.model.SignatureAlgorithm;
import com.oracle.bmc.certificatesmanagement.model.Validity;
import com.oracle.bmc.certificatesmanagement.model.VersionStage;
import com.oracle.bmc.certificatesmanagement.requests.CancelCertificateAuthorityDeletionRequest;
import com.oracle.bmc.certificatesmanagement.requests.CancelCertificateAuthorityVersionDeletionRequest;
import com.oracle.bmc.certificatesmanagement.requests.CancelCertificateDeletionRequest;
import com.oracle.bmc.certificatesmanagement.requests.CancelCertificateVersionDeletionRequest;
import com.oracle.bmc.certificatesmanagement.requests.ChangeCaBundleCompartmentRequest;
import com.oracle.bmc.certificatesmanagement.requests.ChangeCertificateAuthorityCompartmentRequest;
import com.oracle.bmc.certificatesmanagement.requests.ChangeCertificateCompartmentRequest;
import com.oracle.bmc.certificatesmanagement.requests.CreateCaBundleRequest;
import com.oracle.bmc.certificatesmanagement.requests.CreateCertificateAuthorityRequest;
import com.oracle.bmc.certificatesmanagement.requests.CreateCertificateRequest;
import com.oracle.bmc.certificatesmanagement.requests.DeleteCaBundleRequest;
import com.oracle.bmc.certificatesmanagement.requests.GetAssociationRequest;
import com.oracle.bmc.certificatesmanagement.requests.GetCaBundleRequest;
import com.oracle.bmc.certificatesmanagement.requests.GetCertificateAuthorityRequest;
import com.oracle.bmc.certificatesmanagement.requests.GetCertificateAuthorityVersionRequest;
import com.oracle.bmc.certificatesmanagement.requests.GetCertificateRequest;
import com.oracle.bmc.certificatesmanagement.requests.GetCertificateVersionRequest;
import com.oracle.bmc.certificatesmanagement.requests.ListAssociationsRequest;
import com.oracle.bmc.certificatesmanagement.requests.ListCaBundlesRequest;
import com.oracle.bmc.certificatesmanagement.requests.ListCertificateAuthoritiesRequest;
import com.oracle.bmc.certificatesmanagement.requests.ListCertificateAuthorityVersionsRequest;
import com.oracle.bmc.certificatesmanagement.requests.ListCertificateVersionsRequest;
import com.oracle.bmc.certificatesmanagement.requests.ListCertificatesRequest;
import com.oracle.bmc.certificatesmanagement.requests.RevokeCertificateAuthorityVersionRequest;
import com.oracle.bmc.certificatesmanagement.requests.RevokeCertificateVersionRequest;
import com.oracle.bmc.certificatesmanagement.requests.ScheduleCertificateAuthorityDeletionRequest;
import com.oracle.bmc.certificatesmanagement.requests.ScheduleCertificateAuthorityVersionDeletionRequest;
import com.oracle.bmc.certificatesmanagement.requests.ScheduleCertificateDeletionRequest;
import com.oracle.bmc.certificatesmanagement.requests.ScheduleCertificateVersionDeletionRequest;
import com.oracle.bmc.certificatesmanagement.requests.UpdateCaBundleRequest;
import com.oracle.bmc.certificatesmanagement.requests.UpdateCertificateAuthorityRequest;
import com.oracle.bmc.certificatesmanagement.requests.UpdateCertificateRequest;
import com.oracle.bmc.certificatesmanagement.responses.CancelCertificateAuthorityDeletionResponse;
import com.oracle.bmc.certificatesmanagement.responses.CancelCertificateAuthorityVersionDeletionResponse;
import com.oracle.bmc.certificatesmanagement.responses.CancelCertificateDeletionResponse;
import com.oracle.bmc.certificatesmanagement.responses.CancelCertificateVersionDeletionResponse;
import com.oracle.bmc.certificatesmanagement.responses.ChangeCaBundleCompartmentResponse;
import com.oracle.bmc.certificatesmanagement.responses.ChangeCertificateAuthorityCompartmentResponse;
import com.oracle.bmc.certificatesmanagement.responses.ChangeCertificateCompartmentResponse;
import com.oracle.bmc.certificatesmanagement.responses.CreateCaBundleResponse;
import com.oracle.bmc.certificatesmanagement.responses.CreateCertificateAuthorityResponse;
import com.oracle.bmc.certificatesmanagement.responses.CreateCertificateResponse;
import com.oracle.bmc.certificatesmanagement.responses.DeleteCaBundleResponse;
import com.oracle.bmc.certificatesmanagement.responses.GetAssociationResponse;
import com.oracle.bmc.certificatesmanagement.responses.GetCaBundleResponse;
import com.oracle.bmc.certificatesmanagement.responses.GetCertificateAuthorityResponse;
import com.oracle.bmc.certificatesmanagement.responses.GetCertificateAuthorityVersionResponse;
import com.oracle.bmc.certificatesmanagement.responses.GetCertificateResponse;
import com.oracle.bmc.certificatesmanagement.responses.GetCertificateVersionResponse;
import com.oracle.bmc.certificatesmanagement.responses.ListAssociationsResponse;
import com.oracle.bmc.certificatesmanagement.responses.ListCaBundlesResponse;
import com.oracle.bmc.certificatesmanagement.responses.ListCertificateAuthoritiesResponse;
import com.oracle.bmc.certificatesmanagement.responses.ListCertificateAuthorityVersionsResponse;
import com.oracle.bmc.certificatesmanagement.responses.ListCertificateVersionsResponse;
import com.oracle.bmc.certificatesmanagement.responses.ListCertificatesResponse;
import com.oracle.bmc.certificatesmanagement.responses.RevokeCertificateAuthorityVersionResponse;
import com.oracle.bmc.certificatesmanagement.responses.RevokeCertificateVersionResponse;
import com.oracle.bmc.certificatesmanagement.responses.ScheduleCertificateAuthorityDeletionResponse;
import com.oracle.bmc.certificatesmanagement.responses.ScheduleCertificateAuthorityVersionDeletionResponse;
import com.oracle.bmc.certificatesmanagement.responses.ScheduleCertificateDeletionResponse;
import com.oracle.bmc.certificatesmanagement.responses.ScheduleCertificateVersionDeletionResponse;
import com.oracle.bmc.certificatesmanagement.responses.UpdateCaBundleResponse;
import com.oracle.bmc.certificatesmanagement.responses.UpdateCertificateAuthorityResponse;
import com.oracle.bmc.certificatesmanagement.responses.UpdateCertificateResponse;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class Oci_java_sdk_certificatesmanagementTest {
    private static final Date NOT_BEFORE = new Date(1_700_000_000_000L);
    private static final Date NOT_AFTER = new Date(1_731_536_000_000L);

    @Test
    void certificateCreationModelsPreserveNestedConfigurationAndImportedPemFields() {
        Validity validity =
                Validity.builder()
                        .timeOfValidityNotBefore(NOT_BEFORE)
                        .timeOfValidityNotAfter(NOT_AFTER)
                        .build();
        CertificateSubject subject =
                CertificateSubject.builder()
                        .commonName("service.example.com")
                        .country("US")
                        .localityName("Austin")
                        .organization("Example")
                        .organizationalUnit("Platform")
                        .stateOrProvinceName("TX")
                        .build();
        CertificateSubjectAlternativeName dnsName =
                CertificateSubjectAlternativeName.builder()
                        .type(CertificateSubjectAlternativeName.Type.Dns)
                        .value("api.example.com")
                        .build();
        CreateCertificateIssuedByInternalCaConfigDetails issuedByInternalCa =
                CreateCertificateIssuedByInternalCaConfigDetails.builder()
                        .versionName("issued-v1")
                        .certificateProfileType(CertificateProfileType.TlsServer)
                        .issuerCertificateAuthorityId("ocid1.certificateauthority.oc1..issuer")
                        .validity(validity)
                        .subject(subject)
                        .subjectAlternativeNames(List.of(dnsName))
                        .keyAlgorithm(KeyAlgorithm.Rsa2048)
                        .signatureAlgorithm(SignatureAlgorithm.Sha256WithRsa)
                        .build();
        CertificateRenewalRule renewalRule =
                CertificateRenewalRule.builder()
                        .renewalInterval("P180D")
                        .advanceRenewalPeriod("P30D")
                        .build();

        CreateCertificateDetails details =
                CreateCertificateDetails.builder()
                        .name("service-cert")
                        .description("TLS certificate")
                        .compartmentId("ocid1.compartment.oc1..example")
                        .certificateConfig(issuedByInternalCa)
                        .certificateRules(List.of(renewalRule))
                        .freeformTags(Map.of("env", "test"))
                        .definedTags(Map.of("Operations", Map.of("costCenter", "42")))
                        .build();

        assertThat(details.getCertificateConfig())
                .isInstanceOf(CreateCertificateIssuedByInternalCaConfigDetails.class);
        CreateCertificateIssuedByInternalCaConfigDetails config =
                (CreateCertificateIssuedByInternalCaConfigDetails) details.getCertificateConfig();
        assertThat(config.getCertificateProfileType()).isEqualTo(CertificateProfileType.TlsServer);
        assertThat(config.getSubjectAlternativeNames()).containsExactly(dnsName);
        assertThat(details.getCertificateRules()).containsExactly(renewalRule);
        assertThat(details.toBuilder().build()).isEqualTo(details).hasSameHashCodeAs(details);

        CreateCertificateByImportingConfigDetails imported =
                CreateCertificateByImportingConfigDetails.builder()
                        .versionName("imported-v1")
                        .certificatePem(
                                """
                                -----BEGIN CERTIFICATE-----
                                certificate
                                -----END CERTIFICATE-----
                                """)
                        .certChainPem(
                                """
                                -----BEGIN CERTIFICATE-----
                                chain
                                -----END CERTIFICATE-----
                                """)
                        .privateKeyPem(
                                """
                                -----BEGIN PRIVATE KEY-----
                                secret
                                -----END PRIVATE KEY-----
                                """)
                        .privateKeyPemPassphrase("secret-passphrase")
                        .build();

        assertThat(imported.getVersionName()).isEqualTo("imported-v1");
        assertThat(imported.toString())
                .contains("certificatePem")
                .contains("privateKeyPemPassphrase");
        assertThat(imported.toBuilder().build()).isEqualTo(imported);
    }

    @Test
    void certificateAuthorityAndRevocationModelsExposeConfiguredValues() {
        Validity validity =
                Validity.builder()
                        .timeOfValidityNotBefore(NOT_BEFORE)
                        .timeOfValidityNotAfter(NOT_AFTER)
                        .build();
        CertificateSubject subject =
                CertificateSubject.builder()
                        .commonName("Example Root CA")
                        .organization("Example")
                        .country("US")
                        .build();
        CreateRootCaByGeneratingInternallyConfigDetails rootConfig =
                CreateRootCaByGeneratingInternallyConfigDetails.builder()
                        .versionName("root-v1")
                        .validity(validity)
                        .signingAlgorithm(SignatureAlgorithm.Sha384WithRsa)
                        .subject(subject)
                        .build();
        CertificateRevocationListDetails crlDetails =
                CertificateRevocationListDetails.builder()
                        .objectStorageConfig(
                                ObjectStorageBucketConfigDetails.builder()
                                        .objectStorageNamespace("namespace")
                                        .objectStorageBucketName("crl-bucket")
                                        .objectStorageObjectNameFormat("crls/{issuer}.crl")
                                        .build())
                        .customFormattedUrls(List.of("https://example.com/crls/root.crl"))
                        .build();
        RevocationStatus revocationStatus =
                RevocationStatus.builder()
                        .timeOfRevocation(NOT_AFTER)
                        .revocationReason(RevocationReason.KeyCompromise)
                        .build();
        CertificateVersionSummary versionSummary =
                CertificateVersionSummary.builder()
                        .certificateId("ocid1.certificate.oc1..cert")
                        .versionName("v1")
                        .versionNumber(1L)
                        .issuerCaVersionNumber(7L)
                        .validity(validity)
                        .stages(List.of(VersionStage.Current, VersionStage.Latest))
                        .revocationStatus(revocationStatus)
                        .build();
        Certificate certificate =
                Certificate.builder()
                        .id("ocid1.certificate.oc1..cert")
                        .issuerCertificateAuthorityId("ocid1.certificateauthority.oc1..issuer")
                        .name("service-cert")
                        .description("certificate resource")
                        .lifecycleState(CertificateLifecycleState.Active)
                        .lifecycleDetails("ready")
                        .compartmentId("ocid1.compartment.oc1..example")
                        .currentVersion(versionSummary)
                        .subject(subject)
                        .certificateRevocationListDetails(crlDetails)
                        .configType(CertificateConfigType.IssuedByInternalCa)
                        .keyAlgorithm(KeyAlgorithm.Rsa4096)
                        .signatureAlgorithm(SignatureAlgorithm.Sha384WithRsa)
                        .certificateProfileType(CertificateProfileType.TlsServerOrClient)
                        .freeformTags(Map.of("managed-by", "test"))
                        .build();

        assertThat(rootConfig.getVersionName()).isEqualTo("root-v1");
        assertThat(rootConfig.getSubject()).isEqualTo(subject);
        assertThat(certificate.getCertificateRevocationListDetails().getObjectStorageConfig())
                .extracting(ObjectStorageBucketConfigDetails::getObjectStorageBucketName)
                .isEqualTo("crl-bucket");
        assertThat(certificate.getCurrentVersion().getStages())
                .containsExactly(VersionStage.Current, VersionStage.Latest);
        assertThat(certificate.getCurrentVersion().getRevocationStatus().getRevocationReason())
                .isEqualTo(RevocationReason.KeyCompromise);
        assertThat(certificate.toBuilder().build()).isEqualTo(certificate);
        assertThat(certificate.toString())
                .contains("service-cert")
                .contains("certificateRevocationListDetails");
    }

    @Test
    void requestAndResponseBuildersCopyHeadersBodiesAndResourcePayloads() {
        CreateCertificateDetails details =
                CreateCertificateDetails.builder()
                        .name("imported-cert")
                        .compartmentId("ocid1.compartment.oc1..example")
                        .certificateConfig(
                                CreateCertificateManagedExternallyIssuedByInternalCaConfigDetails
                                        .builder()
                                        .versionName("external-v1")
                                        .issuerCertificateAuthorityId(
                                                "ocid1.certificateauthority.oc1..issuer")
                                        .csrPem(
                                                """
                                                -----BEGIN CERTIFICATE REQUEST-----
                                                csr
                                                -----END CERTIFICATE REQUEST-----
                                                """)
                                        .validity(
                                                Validity.builder()
                                                        .timeOfValidityNotBefore(NOT_BEFORE)
                                                        .timeOfValidityNotAfter(NOT_AFTER)
                                                        .build())
                                        .build())
                        .build();
        CreateCertificateRequest request =
                CreateCertificateRequest.builder()
                        .createCertificateDetails(details)
                        .opcRequestId("request-1")
                        .opcRetryToken("retry-token")
                        .build();
        Certificate certificate =
                Certificate.builder()
                        .id("ocid1.certificate.oc1..created")
                        .name("imported-cert")
                        .lifecycleState(CertificateLifecycleState.Creating)
                        .build();
        CreateCertificateResponse response =
                CreateCertificateResponse.builder()
                        .__httpStatusCode__(200)
                        .etag("etag-1")
                        .opcRequestId("request-1")
                        .headers(Map.of("etag", List.of("etag-1")))
                        .certificate(certificate)
                        .build();

        assertThat(request.getBody$()).isSameAs(details);
        assertThat(request.toBuilder().build()).isEqualTo(request);
        assertThat(response.getEtag()).isEqualTo("etag-1");
        assertThat(response.getCertificate()).isEqualTo(certificate);
        assertThat(CreateCertificateResponse.builder().copy(response).build()).isEqualTo(response);
    }

    @Test
    void paginatorsRequestFollowingPagesAndFlattenCertificateRecords() {
        RecordingCertificatesManagement service = new RecordingCertificatesManagement();
        CertificatesManagementPaginators paginators = new CertificatesManagementPaginators(service);
        ListCertificatesRequest request =
                ListCertificatesRequest.builder()
                        .compartmentId("ocid1.compartment.oc1..example")
                        .limit(1)
                        .sortBy(ListCertificatesRequest.SortBy.Name)
                        .sortOrder(ListCertificatesRequest.SortOrder.Asc)
                        .build();

        List<String> names = new ArrayList<>();
        for (CertificateSummary summary : paginators.listCertificatesRecordIterator(request)) {
            names.add(summary.getName());
        }

        assertThat(names).containsExactly("first", "second");
        assertThat(service.requestedPages).containsExactly(null, "next-page");
        assertThat(service.requestedCompartments)
                .containsExactly(
                        "ocid1.compartment.oc1..example", "ocid1.compartment.oc1..example");
    }

    @Test
    void enumsMapServiceValuesAndUnknownValuesSafely() {
        assertThat(KeyAlgorithm.create(KeyAlgorithm.Rsa4096.getValue()))
                .isEqualTo(KeyAlgorithm.Rsa4096);
        assertThat(SignatureAlgorithm.create(SignatureAlgorithm.Sha512WithEcdsa.getValue()))
                .isEqualTo(SignatureAlgorithm.Sha512WithEcdsa);
        assertThat(VersionStage.create(VersionStage.Previous.getValue()))
                .isEqualTo(VersionStage.Previous);
        assertThat(CertificateSubjectAlternativeName.Type.create(
                        CertificateSubjectAlternativeName.Type.Ip.getValue()))
                .isEqualTo(CertificateSubjectAlternativeName.Type.Ip);
        assertThat(KeyAlgorithm.create("FUTURE_ALGORITHM"))
                .isEqualTo(KeyAlgorithm.UnknownEnumValue);
        assertThat(CertificateLifecycleState.create("ARCHIVED"))
                .isEqualTo(CertificateLifecycleState.UnknownEnumValue);
    }

    private static CertificateSummary certificateSummary(String name) {
        return CertificateSummary.builder()
                .id("ocid1.certificate.oc1.." + name)
                .name(name)
                .lifecycleState(CertificateLifecycleState.Active)
                .configType(CertificateConfigType.IssuedByInternalCa)
                .certificateProfileType(CertificateProfileType.TlsServer)
                .keyAlgorithm(KeyAlgorithm.Rsa2048)
                .signatureAlgorithm(SignatureAlgorithm.Sha256WithRsa)
                .build();
    }

    private static final class RecordingCertificatesManagement implements CertificatesManagement {
        private final List<String> requestedPages = new ArrayList<>();
        private final List<String> requestedCompartments = new ArrayList<>();

        @Override
        public void refreshClient() {}

        @Override
        public void setEndpoint(String endpoint) {}

        @Override
        public String getEndpoint() {
            return "https://certificatesmanagement.example.com";
        }

        @Override
        public void setRegion(Region region) {}

        @Override
        public void setRegion(String regionId) {}

        @Override
        public void useRealmSpecificEndpointTemplate(boolean useOfRealmSpecificEndpointTemplate) {}

        @Override
        public ListCertificatesResponse listCertificates(ListCertificatesRequest request) {
            requestedPages.add(request.getPage());
            requestedCompartments.add(request.getCompartmentId());
            if (request.getPage() == null) {
                return ListCertificatesResponse.builder()
                        .opcRequestId("page-1")
                        .opcNextPage("next-page")
                        .certificateCollection(
                                CertificateCollection.builder()
                                        .items(List.of(certificateSummary("first")))
                                        .build())
                        .build();
            }
            return ListCertificatesResponse.builder()
                    .opcRequestId("page-2")
                    .certificateCollection(
                            CertificateCollection.builder()
                                    .items(List.of(certificateSummary("second")))
                                    .build())
                    .build();
        }

        @Override
        public void close() {}

        @Override
        public CancelCertificateAuthorityDeletionResponse cancelCertificateAuthorityDeletion(
                CancelCertificateAuthorityDeletionRequest request) {
            throw unsupported();
        }

        @Override
        public CancelCertificateAuthorityVersionDeletionResponse
                cancelCertificateAuthorityVersionDeletion(
                        CancelCertificateAuthorityVersionDeletionRequest request) {
            throw unsupported();
        }

        @Override
        public CancelCertificateDeletionResponse cancelCertificateDeletion(
                CancelCertificateDeletionRequest request) {
            throw unsupported();
        }

        @Override
        public CancelCertificateVersionDeletionResponse cancelCertificateVersionDeletion(
                CancelCertificateVersionDeletionRequest request) {
            throw unsupported();
        }

        @Override
        public ChangeCaBundleCompartmentResponse changeCaBundleCompartment(
                ChangeCaBundleCompartmentRequest request) {
            throw unsupported();
        }

        @Override
        public ChangeCertificateAuthorityCompartmentResponse changeCertificateAuthorityCompartment(
                ChangeCertificateAuthorityCompartmentRequest request) {
            throw unsupported();
        }

        @Override
        public ChangeCertificateCompartmentResponse changeCertificateCompartment(
                ChangeCertificateCompartmentRequest request) {
            throw unsupported();
        }

        @Override
        public CreateCaBundleResponse createCaBundle(CreateCaBundleRequest request) {
            throw unsupported();
        }

        @Override
        public CreateCertificateResponse createCertificate(CreateCertificateRequest request) {
            throw unsupported();
        }

        @Override
        public CreateCertificateAuthorityResponse createCertificateAuthority(
                CreateCertificateAuthorityRequest request) {
            throw unsupported();
        }

        @Override
        public DeleteCaBundleResponse deleteCaBundle(DeleteCaBundleRequest request) {
            throw unsupported();
        }

        @Override
        public GetAssociationResponse getAssociation(GetAssociationRequest request) {
            throw unsupported();
        }

        @Override
        public GetCaBundleResponse getCaBundle(GetCaBundleRequest request) {
            throw unsupported();
        }

        @Override
        public GetCertificateResponse getCertificate(GetCertificateRequest request) {
            throw unsupported();
        }

        @Override
        public GetCertificateAuthorityResponse getCertificateAuthority(
                GetCertificateAuthorityRequest request) {
            throw unsupported();
        }

        @Override
        public GetCertificateAuthorityVersionResponse getCertificateAuthorityVersion(
                GetCertificateAuthorityVersionRequest request) {
            throw unsupported();
        }

        @Override
        public GetCertificateVersionResponse getCertificateVersion(
                GetCertificateVersionRequest request) {
            throw unsupported();
        }

        @Override
        public ListAssociationsResponse listAssociations(ListAssociationsRequest request) {
            throw unsupported();
        }

        @Override
        public ListCaBundlesResponse listCaBundles(ListCaBundlesRequest request) {
            throw unsupported();
        }

        @Override
        public ListCertificateAuthoritiesResponse listCertificateAuthorities(
                ListCertificateAuthoritiesRequest request) {
            throw unsupported();
        }

        @Override
        public ListCertificateAuthorityVersionsResponse listCertificateAuthorityVersions(
                ListCertificateAuthorityVersionsRequest request) {
            throw unsupported();
        }

        @Override
        public ListCertificateVersionsResponse listCertificateVersions(
                ListCertificateVersionsRequest request) {
            throw unsupported();
        }

        @Override
        public RevokeCertificateAuthorityVersionResponse revokeCertificateAuthorityVersion(
                RevokeCertificateAuthorityVersionRequest request) {
            throw unsupported();
        }

        @Override
        public RevokeCertificateVersionResponse revokeCertificateVersion(
                RevokeCertificateVersionRequest request) {
            throw unsupported();
        }

        @Override
        public ScheduleCertificateAuthorityDeletionResponse scheduleCertificateAuthorityDeletion(
                ScheduleCertificateAuthorityDeletionRequest request) {
            throw unsupported();
        }

        @Override
        public ScheduleCertificateAuthorityVersionDeletionResponse
                scheduleCertificateAuthorityVersionDeletion(
                        ScheduleCertificateAuthorityVersionDeletionRequest request) {
            throw unsupported();
        }

        @Override
        public ScheduleCertificateDeletionResponse scheduleCertificateDeletion(
                ScheduleCertificateDeletionRequest request) {
            throw unsupported();
        }

        @Override
        public ScheduleCertificateVersionDeletionResponse scheduleCertificateVersionDeletion(
                ScheduleCertificateVersionDeletionRequest request) {
            throw unsupported();
        }

        @Override
        public UpdateCaBundleResponse updateCaBundle(UpdateCaBundleRequest request) {
            throw unsupported();
        }

        @Override
        public UpdateCertificateResponse updateCertificate(UpdateCertificateRequest request) {
            throw unsupported();
        }

        @Override
        public UpdateCertificateAuthorityResponse updateCertificateAuthority(
                UpdateCertificateAuthorityRequest request) {
            throw unsupported();
        }

        @Override
        public CertificatesManagementWaiters getWaiters() {
            throw unsupported();
        }

        @Override
        public CertificatesManagementPaginators getPaginators() {
            return new CertificatesManagementPaginators(this);
        }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("not used by this test");
        }
    }
}
