/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus_plexus_utils;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.codehaus.plexus.util.InterpolationFilterReader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Expands deployment templates with Plexus interpolation readers.
 *
 * A release tool renders a service manifest from a checked-in template and the
 * values selected for a deployment, while preserving placeholders it cannot
 * resolve for a later configuration stage.
 */
class TemplateRenderingTest {

    @Test
    void rendersDeploymentManifestFromSelectedValues() throws IOException {
        String template = "service: ${service.name}\n"
                + "image: registry.example.test/${image.name}:${image.tag}\n"
                + "replicas: ${replicas}\n"
                + "later.stage: ${secret.reference}\n";
        Map<String, Object> deploymentValues = new LinkedHashMap<>();
        deploymentValues.put("service.name", "catalog");
        deploymentValues.put("image.name", "catalog-service");
        deploymentValues.put("image.tag", "2025.03");
        deploymentValues.put("replicas", 3);

        StringWriter renderedManifest = new StringWriter();
        try (InterpolationFilterReader reader = new InterpolationFilterReader(
                new StringReader(template), deploymentValues)) {
            reader.transferTo(renderedManifest);
        }

        assertThat(renderedManifest.toString()).isEqualTo(
                "service: catalog\n"
                        + "image: registry.example.test/catalog-service:2025.03\n"
                        + "replicas: 3\n"
                        + "later.stage: ${secret.reference}\n");
    }
}
