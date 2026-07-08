/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.internal.ProfileCredentialsUtils;
import software.amazon.awssdk.profiles.Profile;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.utils.SdkAutoCloseable;

public class ProfileCredentialsUtilsTest {
    @Test
    void stsProfileWithSourceProfileCreatesAssumeRoleProvider() {
        ProfileFile profileFile = credentialsFile("""
                [assumed]
                role_arn = arn:aws:iam::123456789012:role/IntegrationTestRole
                role_session_name = reachability-metadata-test
                source_profile = source
                region = us-east-1

                [source]
                aws_access_key_id = AKIAIOSFODNN7EXAMPLE
                aws_secret_access_key = wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
                """);

        Optional<AwsCredentialsProvider> provider = credentialsProvider(profileFile, "assumed");

        assertThat(provider).isPresent();
        assertThat(provider.orElseThrow().getClass().getName()).contains("StsProfileCredentialsProvider");
        close(provider.orElseThrow());
    }

    @Test
    void ssoProfileLoadsFactoryBeforeReadingLocalTokenCache() {
        ProfileFile profileFile = configurationFile("""
                [profile sso]
                sso_account_id = 123456789012
                sso_role_name = ReadOnly
                sso_region = us-west-2
                sso_start_url = https://reachability-metadata-test.awsapps.com/start
                region = us-east-1
                """);

        assertThatThrownBy(() -> credentialsProvider(profileFile, "sso"))
                .isInstanceOf(UncheckedIOException.class);
    }

    private static Optional<AwsCredentialsProvider> credentialsProvider(ProfileFile profileFile, String profileName) {
        Profile profile = profileFile.profile(profileName).orElseThrow();
        ProfileCredentialsUtils credentialsUtils = new ProfileCredentialsUtils(profileFile, profile, profileFile::profile);
        return credentialsUtils.credentialsProvider();
    }

    private static ProfileFile credentialsFile(String content) {
        return ProfileFile.builder()
                          .content(content)
                          .type(ProfileFile.Type.CREDENTIALS)
                          .build();
    }

    private static ProfileFile configurationFile(String content) {
        return ProfileFile.builder()
                          .content(content)
                          .type(ProfileFile.Type.CONFIGURATION)
                          .build();
    }

    private static void close(AwsCredentialsProvider provider) {
        if (provider instanceof SdkAutoCloseable closeable) {
            closeable.close();
        }
    }
}
