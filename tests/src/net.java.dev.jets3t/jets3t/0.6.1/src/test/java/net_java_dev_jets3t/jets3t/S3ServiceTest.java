/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jets3t.jets3t;

import org.jets3t.service.S3Service;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class S3ServiceTest {
    @Test
    public void validatesBucketNamesAndBuildsPublicS3Urls() {
        assertThat(S3Service.isBucketNameValidDNSName("coverage-bucket")).isTrue();
        assertThat(S3Service.isBucketNameValidDNSName("coverage.bucket")).isTrue();
        assertThat(S3Service.isBucketNameValidDNSName("ab")).isFalse();
        assertThat(S3Service.isBucketNameValidDNSName("CoverageBucket")).isFalse();
        assertThat(S3Service.isBucketNameValidDNSName("192.168.0.1")).isFalse();
        assertThat(S3Service.isBucketNameValidDNSName("coverage.-bucket")).isFalse();

        assertThat(S3Service.generateS3HostnameForBucket("coverage-bucket"))
            .isEqualTo("coverage-bucket.s3.amazonaws.com");
        assertThat(S3Service.generateS3HostnameForBucket("CoverageBucket"))
            .isEqualTo("s3.amazonaws.com");
        assertThat(S3Service.createTorrentUrl("coverage-bucket", "path/to/file.txt"))
            .isEqualTo("http://coverage-bucket.s3.amazonaws.com/path/to/file.txt?torrent");
        assertThat(S3Service.createTorrentUrl("CoverageBucket", "path/to/file.txt"))
            .isEqualTo("http://s3.amazonaws.com/CoverageBucket/path/to/file.txt?torrent");
    }

    @Test
    public void generatesPostPolicyConditionStrings() {
        assertThat(S3Service.generatePostPolicyCondition("starts-with", "key", "uploads/"))
            .isEqualTo("[\"starts-with\", \"$key\", \"uploads/\"]");
        assertThat(S3Service.generatePostPolicyCondition_AllowAnyValue("success_action_redirect"))
            .isEqualTo("[\"starts-with\", \"$success_action_redirect\", \"\"]");
        assertThat(S3Service.generatePostPolicyCondition_Equality("acl", "public-read"))
            .isEqualTo("{\"acl\": \"public-read\"}");
        assertThat(S3Service.generatePostPolicyCondition_Range(1, 1024))
            .isEqualTo("[\"content-length-range\", 1, 1024]");
    }
}
