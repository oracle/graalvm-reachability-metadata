/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_model_certificates;

import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.ListMeta;
import io.fabric8.kubernetes.api.model.ListMetaBuilder;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.certificates.v1.CertificateSigningRequest;
import io.fabric8.kubernetes.api.model.certificates.v1.CertificateSigningRequestBuilder;
import io.fabric8.kubernetes.api.model.certificates.v1.CertificateSigningRequestCondition;
import io.fabric8.kubernetes.api.model.certificates.v1.CertificateSigningRequestConditionBuilder;
import io.fabric8.kubernetes.api.model.certificates.v1.CertificateSigningRequestList;
import io.fabric8.kubernetes.api.model.certificates.v1.CertificateSigningRequestListBuilder;
import io.fabric8.kubernetes.api.model.certificates.v1.CertificateSigningRequestSpec;
import io.fabric8.kubernetes.api.model.certificates.v1.CertificateSigningRequestSpecBuilder;
import io.fabric8.kubernetes.api.model.certificates.v1.CertificateSigningRequestStatus;
import io.fabric8.kubernetes.api.model.certificates.v1.CertificateSigningRequestStatusBuilder;
import io.fabric8.kubernetes.api.model.certificates.v1alpha1.ClusterTrustBundle;
import io.fabric8.kubernetes.api.model.certificates.v1alpha1.ClusterTrustBundleBuilder;
import io.fabric8.kubernetes.api.model.certificates.v1alpha1.ClusterTrustBundleList;
import io.fabric8.kubernetes.api.model.certificates.v1alpha1.ClusterTrustBundleListBuilder;
import io.fabric8.kubernetes.api.model.certificates.v1alpha1.ClusterTrustBundleSpec;
import io.fabric8.kubernetes.api.model.certificates.v1alpha1.ClusterTrustBundleSpecBuilder;
import io.fabric8.kubernetes.api.model.certificates.v1alpha1.PodCertificateRequest;
import io.fabric8.kubernetes.api.model.certificates.v1alpha1.PodCertificateRequestBuilder;
import io.fabric8.kubernetes.api.model.certificates.v1alpha1.PodCertificateRequestList;
import io.fabric8.kubernetes.api.model.certificates.v1alpha1.PodCertificateRequestListBuilder;
import io.fabric8.kubernetes.api.model.certificates.v1alpha1.PodCertificateRequestSpec;
import io.fabric8.kubernetes.api.model.certificates.v1alpha1.PodCertificateRequestSpecBuilder;
import io.fabric8.kubernetes.api.model.certificates.v1alpha1.PodCertificateRequestStatus;
import io.fabric8.kubernetes.api.model.certificates.v1alpha1.PodCertificateRequestStatusBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Kubernetes_model_certificatesTest {
    private static final String CSR_REQUEST = "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURSBSRVFVRVNULS0tLS0=";
    private static final String CERTIFICATE_CHAIN = "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0t";
    private static final String TRUST_BUNDLE = """
            -----BEGIN CERTIFICATE-----
            MIIBexample
            -----END CERTIFICATE-----
            """;

    @Test
    void certificateSigningRequestBuilderCreatesEditableSpecStatusAndMetadata() {
        CertificateSigningRequestCondition approved = new CertificateSigningRequestConditionBuilder()
                .withType("Approved")
                .withStatus("True")
                .withReason("PolicyApproved")
                .withMessage("Request approved by policy")
                .withLastTransitionTime("2026-01-01T00:00:00Z")
                .withLastUpdateTime("2026-01-01T00:00:00Z")
                .addToAdditionalProperties("approver", "platform")
                .build();

        CertificateSigningRequest request = new CertificateSigningRequestBuilder()
                .withNewMetadata()
                    .withName("node-client")
                    .addToLabels("certificates.example.com/profile", "node-client")
                    .addToAnnotations("certificates.example.com/request-id", "csr-001")
                .endMetadata()
                .withNewSpec()
                    .withRequest(CSR_REQUEST)
                    .withSignerName("kubernetes.io/kube-apiserver-client-kubelet")
                    .withUsername("system:node:worker-1")
                    .withUid("node-uid")
                    .withGroups("system:nodes", "system:authenticated")
                    .withUsages("client auth", "digital signature")
                    .withExpirationSeconds(3600)
                    .addToExtra("authentication.kubernetes.io/pod-name", List.of("kubelet"))
                    .addToAdditionalProperties("requestedBy", "unit-test")
                .endSpec()
                .withNewStatus()
                    .withCertificate(CERTIFICATE_CHAIN)
                    .addToConditions(approved)
                    .addToAdditionalProperties("issuer", "cluster-ca")
                .endStatus()
                .addToAdditionalProperties("envelope", Map.of("trace", "csr"))
                .build();

        assertThat(request).isInstanceOf(HasMetadata.class);
        assertThat(request.getApiVersion()).isEqualTo("certificates.k8s.io/v1");
        assertThat(request.getKind()).isEqualTo("CertificateSigningRequest");
        assertThat(request.getMetadata().getName()).isEqualTo("node-client");
        assertThat(request.getMetadata().getLabels()).containsEntry("certificates.example.com/profile", "node-client");
        assertThat(request.getSpec().getSignerName()).isEqualTo("kubernetes.io/kube-apiserver-client-kubelet");
        assertThat(request.getSpec().getGroups()).containsExactly("system:nodes", "system:authenticated");
        assertThat(request.getSpec().getUsages()).containsExactly("client auth", "digital signature");
        assertThat(request.getSpec().getExpirationSeconds()).isEqualTo(3600);
        assertThat(request.getSpec().getExtra()).containsEntry(
                "authentication.kubernetes.io/pod-name", List.of("kubelet"));
        assertThat(request.getSpec().getAdditionalProperties()).containsEntry("requestedBy", "unit-test");
        assertThat(request.getStatus().getCertificate()).isEqualTo(CERTIFICATE_CHAIN);
        assertThat(request.getStatus().getConditions()).containsExactly(approved);
        assertThat(request.getStatus().getConditions().get(0).getAdditionalProperties())
                .containsEntry("approver", "platform");
        assertThat(request.getStatus().getAdditionalProperties()).containsEntry("issuer", "cluster-ca");
        assertThat(request.getAdditionalProperties()).containsEntry("envelope", Map.of("trace", "csr"));

        CertificateSigningRequest denied = request.toBuilder()
                .editMetadata()
                    .addToAnnotations("certificates.example.com/edited", "true")
                .endMetadata()
                .editSpec()
                    .setToUsages(0, "server auth")
                    .removeFromGroups("system:nodes")
                    .addToGroups("system:serviceaccounts")
                    .removeFromExtra("authentication.kubernetes.io/pod-name")
                .endSpec()
                .editStatus()
                    .editFirstCondition()
                        .withType("Denied")
                        .withStatus("False")
                        .withReason("PolicyDenied")
                    .endCondition()
                    .removeFromAdditionalProperties("issuer")
                .endStatus()
                .removeFromAdditionalProperties("envelope")
                .build();

        assertThat(denied.getMetadata().getAnnotations()).containsEntry("certificates.example.com/edited", "true");
        assertThat(denied.getSpec().getUsages()).containsExactly("server auth", "digital signature");
        assertThat(denied.getSpec().getGroups()).containsExactly("system:authenticated", "system:serviceaccounts");
        assertThat(denied.getSpec().getExtra()).doesNotContainKey("authentication.kubernetes.io/pod-name");
        assertThat(denied.getStatus().getConditions().get(0).getType()).isEqualTo("Denied");
        assertThat(denied.getStatus().getAdditionalProperties()).doesNotContainKey("issuer");
        assertThat(denied.getAdditionalProperties()).doesNotContainKey("envelope");
        assertThat(request.getSpec().getGroups()).containsExactly("system:nodes", "system:authenticated");
        assertThat(request.getStatus().getConditions().get(0).getType()).isEqualTo("Approved");
    }

    @Test
    void certificateSigningRequestConstructorsSettersEqualityAndListOperationsRemainConsistent() {
        ObjectMeta metadata = new ObjectMetaBuilder()
                .withName("constructed-csr")
                .withResourceVersion("11")
                .addToLabels("source", "constructor")
                .build();
        CertificateSigningRequestSpec spec = new CertificateSigningRequestSpec(
                7200,
                Map.of("scopes", List.of("read", "write")),
                List.of("system:authenticated"),
                CSR_REQUEST,
                "example.com/signer",
                "request-uid",
                List.of("client auth"),
                "developer");
        spec.setAdditionalProperty("specExtension", "present");
        CertificateSigningRequestStatus status = new CertificateSigningRequestStatus(
                CERTIFICATE_CHAIN,
                List.of(new CertificateSigningRequestCondition(
                        "2026-01-01T00:00:00Z",
                        "2026-01-01T00:00:00Z",
                        "Issued",
                        "IssuedByCA",
                        "True",
                        "Approved")));
        status.setAdditionalProperty("statusExtension", 1);
        CertificateSigningRequest constructed = new CertificateSigningRequest(
                "certificates.k8s.io/v1", "CertificateSigningRequest", metadata, spec, status);
        constructed.setAdditionalProperties(new HashMap<>(Map.of("cluster", "test")));
        constructed.setAdditionalProperty("validated", true);

        CertificateSigningRequest copied = new CertificateSigningRequestBuilder(constructed).build();
        CertificateSigningRequest edited = constructed.edit()
                .editSpec()
                    .withExpirationSeconds(1800)
                .endSpec()
                .editStatus()
                    .setNewConditionLike(0, constructed.getStatus().getConditions().get(0))
                        .withMessage("Renewed")
                    .endCondition()
                .endStatus()
                .build();

        assertThat(copied).isEqualTo(constructed);
        assertThat(copied.hashCode()).isEqualTo(constructed.hashCode());
        assertThat(copied.toString()).contains("constructed-csr", "CertificateSigningRequest");
        assertThat(copied.getSpec().getAdditionalProperties()).containsEntry("specExtension", "present");
        assertThat(copied.getStatus().getAdditionalProperties()).containsEntry("statusExtension", 1);
        assertThat(copied.getAdditionalProperties()).containsEntry("cluster", "test").containsEntry("validated", true);
        assertThat(edited.getSpec().getExpirationSeconds()).isEqualTo(1800);
        assertThat(edited.getStatus().getConditions().get(0).getMessage()).isEqualTo("Renewed");
        assertThat(constructed.getSpec().getExpirationSeconds()).isEqualTo(7200);

        CertificateSigningRequest replacement = edited.toBuilder()
                .editMetadata()
                    .withName("replacement-csr")
                .endMetadata()
                .build();
        ListMeta listMetadata = new ListMetaBuilder()
                .withContinue("next-page")
                .withRemainingItemCount(1L)
                .withResourceVersion("22")
                .build();
        CertificateSigningRequestList list = new CertificateSigningRequestListBuilder()
                .withApiVersion("certificates.k8s.io/v1")
                .withKind("CertificateSigningRequestList")
                .withMetadata(listMetadata)
                .addToItems(constructed)
                .addNewItemLike(replacement)
                    .editMetadata()
                        .addToAnnotations("audited", "true")
                    .endMetadata()
                .endItem()
                .addToAdditionalProperties("source", "unit-test")
                .build();

        assertThat(list.getApiVersion()).isEqualTo("certificates.k8s.io/v1");
        assertThat(list.getKind()).isEqualTo("CertificateSigningRequestList");
        assertThat(list.getMetadata().getContinue()).isEqualTo("next-page");
        assertThat(list.getItems()).extracting(item -> item.getMetadata().getName())
                .containsExactly("constructed-csr", "replacement-csr");
        assertThat(list.getItems().get(1).getMetadata().getAnnotations()).containsEntry("audited", "true");
        assertThat(list.getAdditionalProperties()).containsEntry("source", "unit-test");

        CertificateSigningRequestList updated = list.toBuilder()
                .editMatchingItem(item -> "replacement-csr".equals(item.buildMetadata().getName()))
                    .editSpec()
                        .withSignerName("example.com/replacement-signer")
                    .endSpec()
                .endItem()
                .removeMatchingFromItems(item -> "constructed-csr".equals(item.buildMetadata().getName()))
                .build();

        assertThat(updated.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getMetadata().getName()).isEqualTo("replacement-csr");
            assertThat(item.getSpec().getSignerName()).isEqualTo("example.com/replacement-signer");
        });
    }

    @Test
    void certificateSigningRequestFluentBuildersExposePredicateQueriesAndPositionalOperations() {
        CertificateSigningRequestCondition approved = new CertificateSigningRequestConditionBuilder()
                .withType("Approved")
                .withStatus("True")
                .withReason("InitialApproval")
                .build();
        CertificateSigningRequestCondition denied = new CertificateSigningRequestConditionBuilder()
                .withType("Denied")
                .withStatus("False")
                .withReason("PolicyCheck")
                .build();

        CertificateSigningRequestStatusBuilder statusBuilder = new CertificateSigningRequestStatusBuilder()
                .withCertificate(CERTIFICATE_CHAIN)
                .addToConditions(denied)
                .addToConditions(0, approved);

        assertThat(statusBuilder.hasConditions()).isTrue();
        assertThat(statusBuilder.hasMatchingCondition(condition -> "Approved".equals(condition.getType()))).isTrue();
        assertThat(statusBuilder.buildFirstCondition().getType()).isEqualTo("Approved");
        assertThat(statusBuilder.buildLastCondition().getType()).isEqualTo("Denied");
        assertThat(statusBuilder.buildMatchingCondition(condition -> "Denied".equals(condition.getType())).getReason())
                .isEqualTo("PolicyCheck");

        CertificateSigningRequestStatus editedStatus = statusBuilder
                .editLastCondition()
                    .withMessage("Denied condition remains false")
                .endCondition()
                .removeMatchingFromConditions(condition -> "Approved".equals(condition.getType()))
                .build();

        assertThat(editedStatus.getConditions()).singleElement().satisfies(condition -> {
            assertThat(condition.getType()).isEqualTo("Denied");
            assertThat(condition.getMessage()).isEqualTo("Denied condition remains false");
        });

        CertificateSigningRequestSpecBuilder specBuilder = new CertificateSigningRequestSpecBuilder()
                .withRequest(CSR_REQUEST)
                .withSignerName("example.com/predicate-signer")
                .withGroups("system:authenticated", "system:nodes")
                .withUsages("digital signature", "client auth");

        assertThat(specBuilder.getFirstGroup()).isEqualTo("system:authenticated");
        assertThat(specBuilder.getLastUsage()).isEqualTo("client auth");
        assertThat(specBuilder.hasMatchingGroup(group -> group.endsWith("nodes"))).isTrue();
        assertThat(specBuilder.getMatchingUsage(usage -> usage.contains("signature"))).isEqualTo("digital signature");

        CertificateSigningRequestSpec spec = specBuilder
                .setToGroups(1, "system:serviceaccounts")
                .addToUsages(1, "key encipherment")
                .removeFromUsages("client auth")
                .build();

        assertThat(spec.getGroups()).containsExactly("system:authenticated", "system:serviceaccounts");
        assertThat(spec.getUsages()).containsExactly("digital signature", "key encipherment");
    }

    @Test
    void clusterTrustBundleModelsSupportAlphaAndBetaApisListsAndNestedEdits() {
        ClusterTrustBundle alpha = new ClusterTrustBundleBuilder()
                .withNewMetadata()
                    .withName("alpha-root")
                    .addToLabels("bundle", "root")
                .endMetadata()
                .withNewSpec()
                    .withSignerName("example.com/alpha-signer")
                    .withTrustBundle(TRUST_BUNDLE)
                    .addToAdditionalProperties("pemCount", 1)
                .endSpec()
                .addToAdditionalProperties("phase", "alpha")
                .build();
        ClusterTrustBundle editedAlpha = alpha.toBuilder()
                .editMetadata()
                    .addToAnnotations("certificates.example.com/default", "true")
                .endMetadata()
                .editSpec()
                    .withSignerName("example.com/edited-alpha-signer")
                    .removeFromAdditionalProperties("pemCount")
                .endSpec()
                .removeFromAdditionalProperties("phase")
                .build();
        ClusterTrustBundleList alphaList = new ClusterTrustBundleListBuilder()
                .withNewMetadata("alpha-continue", 2L, "101", null)
                .withItems(alpha, editedAlpha)
                .editLastItem()
                    .editSpec()
                        .addToAdditionalProperties("selected", true)
                    .endSpec()
                .endItem()
                .build();

        assertThat(alpha.getApiVersion()).isEqualTo("certificates.k8s.io/v1alpha1");
        assertThat(alpha.getKind()).isEqualTo("ClusterTrustBundle");
        assertThat(alpha.getSpec().getSignerName()).isEqualTo("example.com/alpha-signer");
        assertThat(alpha.getSpec().getTrustBundle()).isEqualTo(TRUST_BUNDLE);
        assertThat(alpha.getSpec().getAdditionalProperties()).containsEntry("pemCount", 1);
        assertThat(editedAlpha.getMetadata().getAnnotations())
                .containsEntry("certificates.example.com/default", "true");
        assertThat(editedAlpha.getSpec().getAdditionalProperties()).doesNotContainKey("pemCount");
        assertThat(editedAlpha.getAdditionalProperties()).doesNotContainKey("phase");
        assertThat(alphaList.getApiVersion()).isEqualTo("certificates.k8s.io/v1alpha1");
        assertThat(alphaList.getKind()).isEqualTo("ClusterTrustBundleList");
        assertThat(alphaList.getMetadata().getContinue()).isEqualTo("alpha-continue");
        assertThat(alphaList.getItems()).extracting(item -> item.getMetadata().getName())
                .containsExactly("alpha-root", "alpha-root");
        assertThat(alphaList.getItems().get(1).getSpec().getAdditionalProperties()).containsEntry("selected", true);

        io.fabric8.kubernetes.api.model.certificates.v1beta1.ClusterTrustBundle beta =
                new io.fabric8.kubernetes.api.model.certificates.v1beta1.ClusterTrustBundleBuilder()
                .withNewMetadata()
                    .withName("beta-root")
                .endMetadata()
                .withNewSpec()
                    .withSignerName("example.com/beta-signer")
                    .withTrustBundle(TRUST_BUNDLE)
                .endSpec()
                .addToAdditionalProperties("phase", "beta")
                .build();
        io.fabric8.kubernetes.api.model.certificates.v1beta1.ClusterTrustBundle betaCopy = beta.edit()
                .editMetadata()
                    .withName("beta-root-copy")
                .endMetadata()
                .editSpec()
                    .addToAdditionalProperties("copied", true)
                .endSpec()
                .build();
        io.fabric8.kubernetes.api.model.certificates.v1beta1.ClusterTrustBundleList betaList =
                new io.fabric8.kubernetes.api.model.certificates.v1beta1.ClusterTrustBundleListBuilder()
                .withApiVersion("certificates.k8s.io/v1beta1")
                .withKind("ClusterTrustBundleList")
                .withItems(beta, betaCopy)
                .removeMatchingFromItems(item -> "beta-root".equals(item.buildMetadata().getName()))
                .build();

        assertThat(beta.getApiVersion()).isEqualTo("certificates.k8s.io/v1beta1");
        assertThat(beta.getKind()).isEqualTo("ClusterTrustBundle");
        assertThat(beta.getSpec().getSignerName()).isEqualTo("example.com/beta-signer");
        assertThat(beta.getAdditionalProperties()).containsEntry("phase", "beta");
        assertThat(betaList.getItems()).singleElement().satisfies(bundle -> {
            assertThat(bundle.getMetadata().getName()).isEqualTo("beta-root-copy");
            assertThat(bundle.getSpec().getAdditionalProperties()).containsEntry("copied", true);
        });
    }

    @Test
    void clusterTrustBundleConstructorsSettersAndBuilderCopiesPreserveExtensions() {
        ObjectMeta metadata = new ObjectMetaBuilder()
                .withName("constructed-bundle")
                .addToLabels("source", "constructor")
                .build();
        ClusterTrustBundleSpec spec = new ClusterTrustBundleSpec("example.com/constructed", TRUST_BUNDLE);
        spec.setAdditionalProperties(new HashMap<>(Map.of("anchors", 1)));
        spec.setAdditionalProperty("verified", true);
        ClusterTrustBundle bundle = new ClusterTrustBundle(
                "certificates.k8s.io/v1alpha1", "ClusterTrustBundle", metadata, spec);
        bundle.setAdditionalProperty("owner", "security");

        ClusterTrustBundle copied = new ClusterTrustBundleBuilder(bundle).build();
        ClusterTrustBundleSpec rebuiltSpec = new ClusterTrustBundleSpecBuilder(spec)
                .withSignerName("example.com/rebuilt")
                .build();
        ClusterTrustBundle rebuilt = copied.toBuilder()
                .withSpec(rebuiltSpec)
                .build();

        assertThat(copied).isEqualTo(bundle);
        assertThat(copied.hashCode()).isEqualTo(bundle.hashCode());
        assertThat(copied.toString()).contains("constructed-bundle", "ClusterTrustBundle");
        assertThat(copied.getSpec().getAdditionalProperties()).containsEntry("anchors", 1)
                .containsEntry("verified", true);
        assertThat(copied.getAdditionalProperties()).containsEntry("owner", "security");
        assertThat(rebuilt.getSpec().getSignerName()).isEqualTo("example.com/rebuilt");
        assertThat(bundle.getSpec().getSignerName()).isEqualTo("example.com/constructed");
    }

    @Test
    void certificateResourcesExposeNamespacedAndListContracts() {
        PodCertificateRequest podRequest = new PodCertificateRequestBuilder()
                .withNewMetadata()
                    .withName("scoped-pod-cert")
                    .withNamespace("tenant-a")
                .endMetadata()
                .withNewSpec()
                    .withSignerName("example.com/pod-signer")
                    .withPodName("frontend-0")
                    .withPodUID("pod-scope-uid")
                    .withPkixPublicKey("PUBLIC-KEY")
                    .withProofOfPossession("PROOF")
                .endSpec()
                .build();
        CertificateSigningRequest signingRequest = new CertificateSigningRequestBuilder()
                .withNewMetadata()
                    .withName("cluster-csr")
                .endMetadata()
                .withNewSpec()
                    .withRequest(CSR_REQUEST)
                    .withSignerName("example.com/cluster-signer")
                    .withUsername("cluster-user")
                .endSpec()
                .build();
        ClusterTrustBundle trustBundle = new ClusterTrustBundleBuilder()
                .withNewMetadata()
                    .withName("cluster-trust")
                .endMetadata()
                .withNewSpec()
                    .withTrustBundle(TRUST_BUNDLE)
                .endSpec()
                .build();

        assertThat(podRequest instanceof Namespaced).isTrue();
        assertThat(signingRequest instanceof Namespaced).isFalse();
        assertThat(trustBundle instanceof Namespaced).isFalse();

        KubernetesResourceList<PodCertificateRequest> podRequests = new PodCertificateRequestListBuilder()
                .withItems(podRequest)
                .build();
        KubernetesResourceList<CertificateSigningRequest> signingRequests = new CertificateSigningRequestListBuilder()
                .withItems(signingRequest)
                .build();
        KubernetesResourceList<ClusterTrustBundle> trustBundles = new ClusterTrustBundleListBuilder()
                .withItems(trustBundle)
                .build();

        assertThat(podRequests.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getMetadata().getNamespace()).isEqualTo("tenant-a");
            assertThat(item.getSpec().getSignerName()).isEqualTo("example.com/pod-signer");
        });
        assertThat(signingRequests.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getMetadata().getName()).isEqualTo("cluster-csr");
            assertThat(item.getSpec().getUsername()).isEqualTo("cluster-user");
        });
        assertThat(trustBundles.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getMetadata().getName()).isEqualTo("cluster-trust");
            assertThat(item.getSpec().getTrustBundle()).isEqualTo(TRUST_BUNDLE);
        });
    }

    @Test
    void podCertificateRequestModelsNamespacedWorkloadCertificatesAndStatusConditions() {
        Condition ready = new ConditionBuilder()
                .withType("Ready")
                .withStatus("True")
                .withReason("Issued")
                .withMessage("Pod certificate has been issued")
                .withObservedGeneration(3L)
                .build();
        PodCertificateRequest request = new PodCertificateRequestBuilder()
                .withNewMetadata()
                    .withName("web-pod-cert")
                    .withNamespace("production")
                    .addToLabels("app", "web")
                .endMetadata()
                .withNewSpec()
                    .withSignerName("example.com/pod-serving")
                    .withPodName("web-0")
                    .withPodUID("pod-uid")
                    .withNodeName("worker-1")
                    .withNodeUID("node-uid")
                    .withServiceAccountName("web")
                    .withServiceAccountUID("service-account-uid")
                    .withPkixPublicKey("PUBLIC-KEY")
                    .withProofOfPossession("PROOF")
                    .withMaxExpirationSeconds(86400)
                    .addToAdditionalProperties("requestedKeyAlgorithm", "EC")
                .endSpec()
                .withNewStatus()
                    .withCertificateChain(CERTIFICATE_CHAIN)
                    .withNotBefore("2026-01-01T00:00:00Z")
                    .withNotAfter("2026-01-02T00:00:00Z")
                    .withBeginRefreshAt("2026-01-01T12:00:00Z")
                    .withConditions(ready)
                    .addToAdditionalProperties("issuer", "pod-ca")
                .endStatus()
                .addToAdditionalProperties("namespaced", true)
                .build();

        assertThat(request.getApiVersion()).isEqualTo("certificates.k8s.io/v1alpha1");
        assertThat(request.getKind()).isEqualTo("PodCertificateRequest");
        assertThat(request.getMetadata().getNamespace()).isEqualTo("production");
        assertThat(request.getSpec().getSignerName()).isEqualTo("example.com/pod-serving");
        assertThat(request.getSpec().getPodName()).isEqualTo("web-0");
        assertThat(request.getSpec().getNodeName()).isEqualTo("worker-1");
        assertThat(request.getSpec().getServiceAccountName()).isEqualTo("web");
        assertThat(request.getSpec().getPkixPublicKey()).isEqualTo("PUBLIC-KEY");
        assertThat(request.getSpec().getProofOfPossession()).isEqualTo("PROOF");
        assertThat(request.getSpec().getMaxExpirationSeconds()).isEqualTo(86400);
        assertThat(request.getSpec().getAdditionalProperties()).containsEntry("requestedKeyAlgorithm", "EC");
        assertThat(request.getStatus().getCertificateChain()).isEqualTo(CERTIFICATE_CHAIN);
        assertThat(request.getStatus().getConditions()).containsExactly(ready);
        assertThat(request.getStatus().getAdditionalProperties()).containsEntry("issuer", "pod-ca");
        assertThat(request.getAdditionalProperties()).containsEntry("namespaced", true);

        PodCertificateRequest renewed = request.edit()
                .editSpec()
                    .withMaxExpirationSeconds(43200)
                    .withNodeName("worker-2")
                .endSpec()
                .editStatus()
                    .withBeginRefreshAt("2026-01-01T06:00:00Z")
                    .removeFromAdditionalProperties("issuer")
                .endStatus()
                .removeFromAdditionalProperties("namespaced")
                .build();

        assertThat(renewed.getSpec().getMaxExpirationSeconds()).isEqualTo(43200);
        assertThat(renewed.getSpec().getNodeName()).isEqualTo("worker-2");
        assertThat(renewed.getStatus().getBeginRefreshAt()).isEqualTo("2026-01-01T06:00:00Z");
        assertThat(renewed.getStatus().getAdditionalProperties()).doesNotContainKey("issuer");
        assertThat(renewed.getAdditionalProperties()).doesNotContainKey("namespaced");
        assertThat(request.getSpec().getNodeName()).isEqualTo("worker-1");
    }

    @Test
    void podCertificateRequestConstructorsListsAndFluentStatusOperationsRemainConsistent() {
        PodCertificateRequestSpec spec = new PodCertificateRequestSpec(
                3600,
                "worker-a",
                "worker-a-uid",
                "PKIX",
                "api-0",
                "api-0-uid",
                "POP",
                "api",
                "api-sa-uid",
                "example.com/pod-client");
        spec.setAdditionalProperty("profile", "client");
        PodCertificateRequestStatus status = new PodCertificateRequestStatus(
                "2026-01-01T00:30:00Z",
                CERTIFICATE_CHAIN,
                List.of(new ConditionBuilder().withType("Pending").withStatus("False").build()),
                "2026-01-01T01:00:00Z",
                "2026-01-01T00:00:00Z");
        status.setAdditionalProperties(new HashMap<>(Map.of("state", "created")));
        ObjectMeta metadata = new ObjectMetaBuilder()
                .withName("api-pod-cert")
                .withNamespace("default")
                .build();
        PodCertificateRequest constructed = new PodCertificateRequest(
                "certificates.k8s.io/v1alpha1", "PodCertificateRequest", metadata, spec, status);
        constructed.setAdditionalProperty("owner", "api-team");

        PodCertificateRequest copied = new PodCertificateRequestBuilder(constructed).build();
        PodCertificateRequest edited = copied.toBuilder()
                .editMetadata()
                    .withNamespace("platform")
                .endMetadata()
                .withStatus(copied.getStatus().toBuilder()
                        .setToConditions(0, new ConditionBuilder()
                                .withType("Ready")
                                .withStatus("True")
                                .withReason("Issued")
                                .build())
                        .addToAdditionalProperties("state", "issued")
                        .build())
                .build();

        assertThat(copied).isEqualTo(constructed);
        assertThat(copied.hashCode()).isEqualTo(constructed.hashCode());
        assertThat(copied.toString()).contains("api-pod-cert", "PodCertificateRequest");
        assertThat(copied.getSpec().getAdditionalProperties()).containsEntry("profile", "client");
        assertThat(copied.getStatus().getAdditionalProperties()).containsEntry("state", "created");
        assertThat(copied.getAdditionalProperties()).containsEntry("owner", "api-team");
        assertThat(edited.getMetadata().getNamespace()).isEqualTo("platform");
        assertThat(edited.getStatus().getConditions().get(0).getType()).isEqualTo("Ready");
        assertThat(constructed.getMetadata().getNamespace()).isEqualTo("default");

        PodCertificateRequestList list = new PodCertificateRequestListBuilder()
                .withNewMetadata("pod-continue", 2L, "55", null)
                .addToItems(constructed)
                .addNewItemLike(edited)
                    .editMetadata()
                        .withName("api-pod-cert-renewal")
                    .endMetadata()
                .endItem()
                .addToAdditionalProperties("source", "constructor-test")
                .build();

        assertThat(list.getApiVersion()).isEqualTo("certificates.k8s.io/v1alpha1");
        assertThat(list.getKind()).isEqualTo("PodCertificateRequestList");
        assertThat(list.getMetadata().getContinue()).isEqualTo("pod-continue");
        assertThat(list.getItems()).extracting(item -> item.getMetadata().getName())
                .containsExactly("api-pod-cert", "api-pod-cert-renewal");
        assertThat(list.getAdditionalProperties()).containsEntry("source", "constructor-test");

        PodCertificateRequestList filtered = list.edit()
                .editMatchingItem(item -> "api-pod-cert-renewal".equals(item.buildMetadata().getName()))
                    .editSpec()
                        .withPodName("api-1")
                    .endSpec()
                .endItem()
                .removeMatchingFromItems(item -> "api-pod-cert".equals(item.buildMetadata().getName()))
                .build();

        assertThat(filtered.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getMetadata().getName()).isEqualTo("api-pod-cert-renewal");
            assertThat(item.getSpec().getPodName()).isEqualTo("api-1");
        });
    }

    @Test
    void v1beta1CertificateSigningRequestModelsSupportLegacyApiBuildersAndLists() {
        io.fabric8.kubernetes.api.model.certificates.v1beta1.CertificateSigningRequestCondition approved =
                new io.fabric8.kubernetes.api.model.certificates.v1beta1.CertificateSigningRequestConditionBuilder()
                .withType("Approved")
                .withStatus("True")
                .withReason("LegacyApproved")
                .withMessage("Legacy CSR approved")
                .build();
        io.fabric8.kubernetes.api.model.certificates.v1beta1.CertificateSigningRequest request =
                new io.fabric8.kubernetes.api.model.certificates.v1beta1.CertificateSigningRequestBuilder()
                .withNewMetadata()
                    .withName("legacy-csr")
                .endMetadata()
                .withNewSpec()
                    .withRequest(CSR_REQUEST)
                    .withSignerName("kubernetes.io/legacy-unknown")
                    .withUsername("legacy-user")
                    .withUid("legacy-uid")
                    .withGroups("system:authenticated")
                    .withUsages("client auth")
                    .addToExtra("legacy", List.of("true"))
                    .addToAdditionalProperties("legacySpec", true)
                .endSpec()
                .withNewStatus()
                    .withCertificate(CERTIFICATE_CHAIN)
                    .addToConditions(approved)
                    .addToAdditionalProperties("legacyStatus", true)
                .endStatus()
                .addToAdditionalProperties("legacy", true)
                .build();

        assertThat(request.getApiVersion()).isEqualTo("certificates.k8s.io/v1beta1");
        assertThat(request.getKind()).isEqualTo("CertificateSigningRequest");
        assertThat(request.getSpec().getSignerName()).isEqualTo("kubernetes.io/legacy-unknown");
        assertThat(request.getSpec().getExtra()).containsEntry("legacy", List.of("true"));
        assertThat(request.getSpec().getAdditionalProperties()).containsEntry("legacySpec", true);
        assertThat(request.getStatus().getConditions()).containsExactly(approved);
        assertThat(request.getStatus().getAdditionalProperties()).containsEntry("legacyStatus", true);
        assertThat(request.getAdditionalProperties()).containsEntry("legacy", true);

        io.fabric8.kubernetes.api.model.certificates.v1beta1.CertificateSigningRequest edited = request.edit()
                .editSpec()
                    .setToUsages(0, "server auth")
                    .removeFromExtra("legacy")
                .endSpec()
                .editStatus()
                    .editFirstCondition()
                        .withReason("LegacyRechecked")
                    .endCondition()
                .endStatus()
                .build();
        io.fabric8.kubernetes.api.model.certificates.v1beta1.CertificateSigningRequestList list =
                new io.fabric8.kubernetes.api.model.certificates.v1beta1.CertificateSigningRequestListBuilder()
                .withNewMetadata(null, 2L, "legacy-rv", null)
                .withItems(request, edited)
                .editLastItem()
                    .editMetadata()
                        .withName("legacy-csr-edited")
                    .endMetadata()
                .endItem()
                .build();

        assertThat(edited.getSpec().getUsages()).containsExactly("server auth");
        assertThat(edited.getSpec().getExtra()).doesNotContainKey("legacy");
        assertThat(edited.getStatus().getConditions().get(0).getReason()).isEqualTo("LegacyRechecked");
        assertThat(list.getApiVersion()).isEqualTo("certificates.k8s.io/v1beta1");
        assertThat(list.getKind()).isEqualTo("CertificateSigningRequestList");
        assertThat(list.getItems()).extracting(item -> item.getMetadata().getName())
                .containsExactly("legacy-csr", "legacy-csr-edited");
    }

    @Test
    void certificateResourcesSupportResourceNamesFinalizersAndOwnerReferences() {
        CertificateSigningRequest issuer = new CertificateSigningRequestBuilder()
                .withNewMetadata()
                    .withName("cluster-issuer-csr")
                    .withUid("csr-issuer-uid")
                .endMetadata()
                .withNewSpec()
                    .withRequest(CSR_REQUEST)
                    .withSignerName("example.com/cluster-issuer")
                    .withUsername("system:serviceaccount:certificates:issuer")
                .endSpec()
                .build();
        ClusterTrustBundle bundle = new ClusterTrustBundleBuilder()
                .withNewMetadata()
                    .withName("owned-trust-bundle")
                .endMetadata()
                .withNewSpec()
                    .withSignerName("example.com/cluster-issuer")
                    .withTrustBundle(TRUST_BUNDLE)
                .endSpec()
                .build();

        assertThat(issuer.getPlural()).isEqualTo("certificatesigningrequests");
        assertThat(issuer.getSingular()).isEqualTo("certificatesigningrequest");
        assertThat(issuer.getFullResourceName()).isEqualTo("certificatesigningrequests.certificates.k8s.io");
        assertThat(bundle.getPlural()).isEqualTo("clustertrustbundles");
        assertThat(bundle.getFullResourceName()).isEqualTo("clustertrustbundles.certificates.k8s.io");
        assertThat(issuer.isFinalizerValid("certificates.example.com/cleanup")).isTrue();

        assertThat(issuer.addFinalizer("certificates.example.com/cleanup")).isTrue();
        assertThat(issuer.addFinalizer("certificates.example.com/cleanup")).isFalse();
        assertThat(issuer.hasFinalizer("certificates.example.com/cleanup")).isTrue();
        assertThat(issuer.getFinalizers()).containsExactly("certificates.example.com/cleanup");
        assertThat(issuer.removeFinalizer("certificates.example.com/cleanup")).isTrue();
        assertThat(issuer.hasFinalizer("certificates.example.com/cleanup")).isFalse();

        OwnerReference ownerReference = bundle.addOwnerReference(issuer);

        assertThat(ownerReference.getApiVersion()).isEqualTo("certificates.k8s.io/v1");
        assertThat(ownerReference.getKind()).isEqualTo("CertificateSigningRequest");
        assertThat(ownerReference.getName()).isEqualTo("cluster-issuer-csr");
        assertThat(ownerReference.getUid()).isEqualTo("csr-issuer-uid");
        assertThat(bundle.hasOwnerReferenceFor(issuer)).isTrue();
        assertThat(bundle.getOwnerReferenceFor("csr-issuer-uid")).hasValue(ownerReference);

        bundle.removeOwnerReference(issuer);

        assertThat(bundle.hasOwnerReferenceFor(issuer)).isFalse();
    }

    @Test
    void serviceLoaderDiscoversCertificateResources() {
        List<String> discoveredApiKinds = new ArrayList<>();
        for (KubernetesResource resource : ServiceLoader.load(KubernetesResource.class)) {
            if (resource instanceof HasMetadata metadata) {
                String apiVersion = metadata.getApiVersion();
                if (apiVersion != null && apiVersion.startsWith("certificates.k8s.io/")) {
                    discoveredApiKinds.add(apiVersion + "/" + metadata.getKind());
                }
            }
        }

        assertThat(discoveredApiKinds)
                .contains("certificates.k8s.io/v1/CertificateSigningRequest")
                .contains("certificates.k8s.io/v1alpha1/ClusterTrustBundle")
                .contains("certificates.k8s.io/v1alpha1/PodCertificateRequest")
                .contains("certificates.k8s.io/v1beta1/CertificateSigningRequest")
                .contains("certificates.k8s.io/v1beta1/ClusterTrustBundle");
    }
}
