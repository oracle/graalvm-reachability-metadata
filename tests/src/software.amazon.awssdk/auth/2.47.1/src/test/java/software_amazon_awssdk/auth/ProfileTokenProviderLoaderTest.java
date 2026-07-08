/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.token.credentials.SdkTokenProvider;
import software.amazon.awssdk.auth.token.internal.ProfileTokenProviderLoader;
import software.amazon.awssdk.profiles.ProfileFile;

public class ProfileTokenProviderLoaderTest {
    @Test
    void ssoSessionProfileLoadsSsoOidcTokenProviderFactory() {
        ProfileFile profileFile = configurationFile("""
                [profile bearer]
                sso_session = reachability
                region = us-east-1

                [sso-session reachability]
                sso_region = us-west-2
                sso_start_url = https://reachability-metadata-test.awsapps.com/start
                sso_account_id = 123456789012
                """);
        ProfileTokenProviderLoader loader = new ProfileTokenProviderLoader(() -> profileFile, "bearer");

        Optional<SdkTokenProvider> provider = loader.tokenProvider();

        assertThat(provider).isPresent();
        assertThatThrownBy(() -> provider.orElseThrow().resolveToken())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sso_account_id or sso_role_name properties must not be defined");
    }

    private static ProfileFile configurationFile(String content) {
        return ProfileFile.builder()
                          .content(content)
                          .type(ProfileFile.Type.CONFIGURATION)
                          .build();
    }
}
