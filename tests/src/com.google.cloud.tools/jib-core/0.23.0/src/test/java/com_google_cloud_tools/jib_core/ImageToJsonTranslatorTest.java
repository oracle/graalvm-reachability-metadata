/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud_tools.jib_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.ImageToJsonTranslator;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import org.junit.jupiter.api.Test;

public class ImageToJsonTranslatorTest {

    @Test
    void getManifestTemplateInstantiatesManifestTemplateClass() throws Exception {
        DescriptorDigest configurationDigest = DescriptorDigest.fromHash(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        BlobDescriptor configurationDescriptor = new BlobDescriptor(123L, configurationDigest);
        Image image = Image.builder(V22ManifestTemplate.class).build();

        V22ManifestTemplate manifestTemplate = new ImageToJsonTranslator(image)
                .getManifestTemplate(V22ManifestTemplate.class, configurationDescriptor);

        BuildableManifestTemplate.ContentDescriptorTemplate configuration =
                manifestTemplate.getContainerConfiguration();
        assertThat(configuration).isNotNull();
        assertThat(configuration.getDigest()).isEqualTo(configurationDigest);
        assertThat(configuration.getSize()).isEqualTo(123L);
        assertThat(manifestTemplate.getLayers()).isEmpty();
    }
}
