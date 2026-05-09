/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jets3t.jets3t;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

import org.jets3t.service.acl.AccessControlList;
import org.jets3t.service.model.S3Bucket;
import org.jets3t.service.model.S3Object;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class S3ObjectTest {
    @Test
    public void managesObjectIdentityMetadataAndModifiableHeaders() {
        S3Bucket bucket = new S3Bucket("coverage-bucket");
        S3Object object = new S3Object(bucket, "documents/report.txt");

        object.setETag("\"abcdef123456\"");
        object.setContentLength(42L);
        object.setContentType("text/plain");
        object.setContentLanguage("en-US");
        object.setContentEncoding("gzip");
        object.setContentDisposition("attachment; filename=report.txt");
        object.setStorageClass("STANDARD");
        object.setMetadataComplete(true);
        object.addMetadata("request-id", "request-1");
        object.addMetadata("custom-header", "custom-value");

        assertThat(object.getBucketName()).isEqualTo("coverage-bucket");
        assertThat(object.getKey()).isEqualTo("documents/report.txt");
        assertThat(object.getETag()).isEqualTo("abcdef123456");
        assertThat(object.getContentLength()).isEqualTo(42L);
        assertThat(object.getContentType()).isEqualTo("text/plain");
        assertThat(object.getContentLanguage()).isEqualTo("en-US");
        assertThat(object.getContentEncoding()).isEqualTo("gzip");
        assertThat(object.getContentDisposition()).isEqualTo("attachment; filename=report.txt");
        assertThat(object.getStorageClass()).isEqualTo("STANDARD");
        assertThat(object.isMetadataComplete()).isTrue();

        Map<?, ?> modifiableMetadata = object.getModifiableMetadata();
        assertThat(modifiableMetadata.get("custom-header")).isEqualTo("custom-value");
        assertThat(modifiableMetadata.get(S3Object.METADATA_HEADER_CONTENT_TYPE)).isEqualTo("text/plain");
        assertThat(modifiableMetadata.containsKey(S3Object.METADATA_HEADER_CONTENT_LENGTH)).isFalse();
        assertThat(modifiableMetadata.containsKey(S3Object.METADATA_HEADER_ETAG)).isFalse();
        assertThat(modifiableMetadata.containsKey("request-id")).isFalse();
    }

    @Test
    public void configuresAclAndDataStreamWithoutNetworkAccess() throws Exception {
        S3Object object = new S3Object("payload.txt");
        InputStream stream = new ByteArrayInputStream("payload".getBytes("UTF-8"));

        object.setDataInputStream(stream);
        object.setAcl(AccessControlList.REST_CANNED_PUBLIC_READ);

        assertThat(object.getDataInputStream()).isSameAs(stream);
        assertThat(object.getAcl()).isSameAs(AccessControlList.REST_CANNED_PUBLIC_READ);
        assertThat(object.getMetadata("x-amz-acl")).isEqualTo("public-read");

        object.closeDataInputStream();

        assertThat(object.getDataInputStream()).isNull();
    }
}
