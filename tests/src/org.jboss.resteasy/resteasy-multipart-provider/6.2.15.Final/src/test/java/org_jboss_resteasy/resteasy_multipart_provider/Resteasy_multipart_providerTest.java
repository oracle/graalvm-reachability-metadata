/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_multipart_provider;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataOutput;
import org.jboss.resteasy.plugins.providers.multipart.OutputPart;
import org.junit.jupiter.api.Test;

public class Resteasy_multipart_providerTest {
    @Test
    void retainsRepeatedFormFieldsAndAttachmentDetails() {
        MultipartFormDataOutput output = new MultipartFormDataOutput();
        OutputPart first = output.addFormData("tag", "research", MediaType.TEXT_PLAIN_TYPE);
        OutputPart second = output.addFormData("tag", "history", MediaType.TEXT_PLAIN_TYPE);
        byte[] content = {1, 2, 3};
        OutputPart attachment = output.addFormData("attachment", content,
                MediaType.APPLICATION_OCTET_STREAM_TYPE, "report.bin");

        assertThat(output.getFormDataMap()).containsOnlyKeys("tag", "attachment");
        assertThat(output.getFormDataMap().get("tag")).containsExactly(first, second);
        assertThat(output.getFormDataMap().get("attachment")).containsExactly(attachment);
        assertThat(output.getParts()).containsExactly(first, second, attachment);
        assertThat(first.getEntity()).isEqualTo("research");
        assertThat(second.getEntity()).isEqualTo("history");
        assertThat(attachment.getEntity()).isSameAs(content);
        assertThat(attachment.getMediaType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM_TYPE);
        assertThat(attachment.getFilename()).isEqualTo("report.bin");
    }
}
