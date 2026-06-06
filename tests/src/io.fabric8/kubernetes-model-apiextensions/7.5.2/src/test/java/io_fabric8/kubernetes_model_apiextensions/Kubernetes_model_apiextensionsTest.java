/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_model_apiextensions;

import java.util.LinkedHashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.ConversionReview;
import io.fabric8.kubernetes.api.model.apiextensions.v1.ConversionReviewBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceColumnDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceColumnDefinitionBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceConversion;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceConversionBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionNamesBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionSpec;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionVersion;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionVersionBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceSubresourcesBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceValidationBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.JSONSchemaProps;
import io.fabric8.kubernetes.api.model.apiextensions.v1.JSONSchemaPropsBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.JSONSchemaPropsOrArrayBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.SelectableField;
import io.fabric8.kubernetes.api.model.apiextensions.v1.SelectableFieldBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.ValidationRule;
import io.fabric8.kubernetes.api.model.apiextensions.v1.ValidationRuleBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Kubernetes_model_apiextensionsTest {
    @Test
    void buildsCustomResourceDefinitionWithOpenApiSchemaAndVersionOptions() {
        JSONSchemaProps stringSchema = new JSONSchemaPropsBuilder()
                .withType("string")
                .withMinLength(1L)
                .build();
        JSONSchemaProps replicasSchema = new JSONSchemaPropsBuilder()
                .withType("integer")
                .withMinimum(1.0)
                .withMaximum(10.0)
                .build();
        JSONSchemaProps tagsSchema = new JSONSchemaPropsBuilder()
                .withType("array")
                .withXKubernetesListType("set")
                .withItems(new JSONSchemaPropsOrArrayBuilder()
                        .withSchema(stringSchema)
                        .build())
                .build();
        ValidationRule validationRule = new ValidationRuleBuilder()
                .withRule("self.replicas >= 1")
                .withMessage("replicas must be positive")
                .withFieldPath(".spec.replicas")
                .build();
        JSONSchemaProps specSchema = new JSONSchemaPropsBuilder()
                .withType("object")
                .withRequired("replicas")
                .addToProperties("replicas", replicasSchema)
                .addToProperties("tags", tagsSchema)
                .withXKubernetesValidations(validationRule)
                .build();
        JSONSchemaProps rootSchema = new JSONSchemaPropsBuilder()
                .withType("object")
                .withRequired("spec")
                .addToProperties("spec", specSchema)
                .build();
        SelectableField replicasSelectableField = new SelectableFieldBuilder()
                .withJsonPath(".spec.replicas")
                .build();
        CustomResourceColumnDefinition replicasColumn = new CustomResourceColumnDefinitionBuilder()
                .withName("Replicas")
                .withType("integer")
                .withJsonPath(".spec.replicas")
                .build();
        CustomResourceDefinitionVersion version = new CustomResourceDefinitionVersionBuilder()
                .withName("v1")
                .withServed(true)
                .withStorage(true)
                .withSchema(new CustomResourceValidationBuilder()
                        .withOpenAPIV3Schema(rootSchema)
                        .build())
                .withSelectableFields(replicasSelectableField)
                .withAdditionalPrinterColumns(replicasColumn)
                .withSubresources(new CustomResourceSubresourcesBuilder()
                        .withNewScale(".status.selector", ".spec.replicas", ".status.replicas")
                        .withNewStatus()
                        .endStatus()
                        .build())
                .build();

        CustomResourceDefinition customResourceDefinition = new CustomResourceDefinitionBuilder()
                .withApiVersion("apiextensions.k8s.io/v1")
                .withKind("CustomResourceDefinition")
                .withMetadata(new ObjectMetaBuilder()
                        .withName("widgets.example.com")
                        .build())
                .withNewSpec()
                .withGroup("example.com")
                .withScope("Namespaced")
                .withNames(new CustomResourceDefinitionNamesBuilder()
                        .withPlural("widgets")
                        .withSingular("widget")
                        .withKind("Widget")
                        .withShortNames("wdg")
                        .build())
                .withVersions(version)
                .endSpec()
                .build();

        assertThat(customResourceDefinition.getMetadata().getName()).isEqualTo("widgets.example.com");
        assertThat(customResourceDefinition.getSpec().getNames().getShortNames()).containsExactly("wdg");
        assertThat(customResourceDefinition.getSpec().getVersions()).hasSize(1);

        CustomResourceDefinitionVersion builtVersion = customResourceDefinition.getSpec().getVersions().get(0);
        assertThat(builtVersion.getName()).isEqualTo("v1");
        assertThat(builtVersion.getServed()).isTrue();
        assertThat(builtVersion.getStorage()).isTrue();
        assertThat(builtVersion.getSelectableFields())
                .extracting(SelectableField::getJsonPath)
                .containsExactly(".spec.replicas");
        assertThat(builtVersion.getAdditionalPrinterColumns())
                .extracting(CustomResourceColumnDefinition::getJsonPath)
                .containsExactly(".spec.replicas");
        assertThat(builtVersion.getSubresources().getScale().getLabelSelectorPath())
                .isEqualTo(".status.selector");
        assertThat(builtVersion.getSubresources().getScale().getSpecReplicasPath())
                .isEqualTo(".spec.replicas");
        assertThat(builtVersion.getSubresources().getScale().getStatusReplicasPath())
                .isEqualTo(".status.replicas");
        assertThat(builtVersion.getSubresources().getStatus()).isNotNull();

        JSONSchemaProps builtSpecSchema = builtVersion.getSchema()
                .getOpenAPIV3Schema()
                .getProperties()
                .get("spec");
        assertThat(builtSpecSchema.getRequired()).containsExactly("replicas");
        assertThat(builtSpecSchema.getProperties().get("replicas").getMinimum()).isEqualTo(1.0);
        assertThat(builtSpecSchema.getProperties().get("replicas").getMaximum()).isEqualTo(10.0);
        assertThat(builtSpecSchema.getProperties().get("tags").getXKubernetesListType()).isEqualTo("set");
        assertThat(builtSpecSchema.getProperties().get("tags").getItems().getSchema().getType())
                .isEqualTo("string");
        assertThat(builtSpecSchema.getXKubernetesValidations())
                .extracting(ValidationRule::getMessage)
                .containsExactly("replicas must be positive");
    }

    @Test
    void editsCustomResourceDefinitionVersionWithoutMutatingOriginal() {
        CustomResourceDefinition customResourceDefinition = new CustomResourceDefinitionBuilder()
                .withNewMetadata()
                .withName("widgets.example.com")
                .endMetadata()
                .withNewSpec()
                .withGroup("example.com")
                .withScope("Namespaced")
                .withNames(new CustomResourceDefinitionNamesBuilder()
                        .withPlural("widgets")
                        .withKind("Widget")
                        .build())
                .addNewVersion()
                .withName("v1")
                .withServed(true)
                .withStorage(true)
                .withSchema(new CustomResourceValidationBuilder()
                        .withOpenAPIV3Schema(new JSONSchemaPropsBuilder().withType("object").build())
                        .build())
                .endVersion()
                .endSpec()
                .build();

        CustomResourceDefinition editedCustomResourceDefinition = customResourceDefinition.toBuilder()
                .editSpec()
                .editFirstVersion()
                .withStorage(false)
                .withDeprecated(true)
                .withDeprecationWarning("use the next served version")
                .endVersion()
                .addNewVersion()
                .withName("v2")
                .withServed(true)
                .withStorage(true)
                .withSchema(new CustomResourceValidationBuilder()
                        .withOpenAPIV3Schema(new JSONSchemaPropsBuilder()
                                .withType("object")
                                .withXKubernetesPreserveUnknownFields(true)
                                .build())
                        .build())
                .endVersion()
                .endSpec()
                .build();

        CustomResourceDefinitionSpec originalSpec = customResourceDefinition.getSpec();
        assertThat(originalSpec.getVersions()).hasSize(1);
        assertThat(originalSpec.getVersions().get(0).getStorage()).isTrue();
        assertThat(originalSpec.getVersions().get(0).getDeprecated()).isNull();

        assertThat(editedCustomResourceDefinition.getSpec().getVersions()).hasSize(2);
        assertThat(editedCustomResourceDefinition.getSpec().getVersions().get(0).getStorage()).isFalse();
        assertThat(editedCustomResourceDefinition.getSpec().getVersions().get(0).getDeprecated()).isTrue();
        assertThat(editedCustomResourceDefinition.getSpec().getVersions().get(0).getDeprecationWarning())
                .isEqualTo("use the next served version");
        assertThat(editedCustomResourceDefinition.getSpec().getVersions().get(1).getName()).isEqualTo("v2");
        assertThat(editedCustomResourceDefinition.getSpec().getVersions().get(1)
                .getSchema()
                .getOpenAPIV3Schema()
                .getXKubernetesPreserveUnknownFields()).isTrue();
    }

    @Test
    void buildsWebhookConversionConfiguration() {
        CustomResourceConversion conversion = new CustomResourceConversionBuilder()
                .withStrategy("Webhook")
                .withNewWebhook()
                .withConversionReviewVersions("v1")
                .withNewClientConfig()
                .withCaBundle("certificate-authority-data")
                .withNewService("conversion-webhook", "webhooks", "/convert", 443)
                .endClientConfig()
                .endWebhook()
                .build();

        assertThat(conversion.getStrategy()).isEqualTo("Webhook");
        assertThat(conversion.getWebhook().getConversionReviewVersions()).containsExactly("v1");
        assertThat(conversion.getWebhook().getClientConfig().getCaBundle())
                .isEqualTo("certificate-authority-data");
        assertThat(conversion.getWebhook().getClientConfig().getService().getName())
                .isEqualTo("conversion-webhook");
        assertThat(conversion.getWebhook().getClientConfig().getService().getNamespace())
                .isEqualTo("webhooks");
        assertThat(conversion.getWebhook().getClientConfig().getService().getPath())
                .isEqualTo("/convert");
        assertThat(conversion.getWebhook().getClientConfig().getService().getPort())
                .isEqualTo(443);
    }

    @Test
    void buildsConversionReviewRequestAndResponse() {
        Map<String, Object> originalResource = resource("example.com/v1alpha1", "Widget");
        Map<String, Object> convertedResource = resource("example.com/v1", "Widget");

        ConversionReview conversionReview = new ConversionReviewBuilder()
                .withApiVersion("apiextensions.k8s.io/v1")
                .withKind("ConversionReview")
                .withNewRequest()
                .withUid("request-uid")
                .withDesiredAPIVersion("example.com/v1")
                .withObjects(originalResource)
                .endRequest()
                .withNewResponse()
                .withUid("request-uid")
                .withConvertedObjects(convertedResource)
                .withResult(new StatusBuilder()
                        .withStatus("Success")
                        .withCode(200)
                        .build())
                .endResponse()
                .build();

        assertThat(conversionReview.getRequest().getUid()).isEqualTo("request-uid");
        assertThat(conversionReview.getRequest().getDesiredAPIVersion()).isEqualTo("example.com/v1");
        assertThat(conversionReview.getRequest().getObjects()).containsExactly(originalResource);
        assertThat(conversionReview.getResponse().getUid()).isEqualTo("request-uid");
        assertThat(conversionReview.getResponse().getConvertedObjects()).containsExactly(convertedResource);
        assertThat(conversionReview.getResponse().getResult().getStatus()).isEqualTo("Success");
        assertThat(conversionReview.getResponse().getResult().getCode()).isEqualTo(200);
    }

    private static Map<String, Object> resource(String apiVersion, String kind) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("apiVersion", apiVersion);
        resource.put("kind", kind);
        resource.put("metadata", Map.of("name", "sample-widget"));
        return resource;
    }
}
