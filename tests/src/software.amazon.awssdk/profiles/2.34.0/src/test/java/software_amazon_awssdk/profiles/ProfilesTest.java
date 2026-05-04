/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.profiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.profiles.Profile;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.profiles.ProfileFileLocation;
import software.amazon.awssdk.profiles.ProfileFileSupplier;
import software.amazon.awssdk.profiles.ProfileFileSystemSetting;
import software.amazon.awssdk.profiles.ProfileProperty;

public class ProfilesTest {
    private static final String AWS_CONFIG_FILE_PROPERTY = "aws.configFile";
    private static final String AWS_SHARED_CREDENTIALS_FILE_PROPERTY = "aws.sharedCredentialsFile";

    @TempDir
    Path tempDir;

    @Test
    void profileBuilderCreatesImmutableProfilesAndSupportsBooleanProperties() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put(ProfileProperty.REGION, "eu-central-1");
        properties.put(ProfileProperty.USE_FIPS_ENDPOINT, "TrUe");
        properties.put(ProfileProperty.USE_DUALSTACK_ENDPOINT, "false");

        Profile profile = Profile.builder()
                                 .name("integration")
                                 .properties(properties)
                                 .build();
        properties.put(ProfileProperty.REGION, "us-east-1");

        assertThat(profile.name()).isEqualTo("integration");
        assertThat(profile.property(ProfileProperty.REGION)).contains("eu-central-1");
        assertThat(profile.property("missing")).isEmpty();
        assertThat(profile.booleanProperty(ProfileProperty.USE_FIPS_ENDPOINT)).contains(true);
        assertThat(profile.booleanProperty(ProfileProperty.USE_DUALSTACK_ENDPOINT)).contains(false);
        assertThat(profile.properties()).containsExactly(
                Map.entry(ProfileProperty.REGION, "eu-central-1"),
                Map.entry(ProfileProperty.USE_FIPS_ENDPOINT, "TrUe"),
                Map.entry(ProfileProperty.USE_DUALSTACK_ENDPOINT, "false"));
        assertThatThrownBy(() -> profile.properties().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);

        Profile copy = profile.toBuilder().build();
        Profile changed = profile.toBuilder()
                                 .properties(Map.of(ProfileProperty.REGION, "ap-south-1"))
                                 .build();

        assertThat(copy).isEqualTo(profile);
        assertThat(copy.hashCode()).isEqualTo(profile.hashCode());
        assertThat(changed).isNotEqualTo(profile);
        assertThat(profile.toString()).contains("integration", ProfileProperty.REGION);
    }

    @Test
    void profileBooleanPropertyRejectsNonBooleanValues() {
        Profile profile = Profile.builder()
                                 .name("strict")
                                 .properties(Map.of(ProfileProperty.ENDPOINT_DISCOVERY_ENABLED, "yes"))
                                 .build();

        assertThatThrownBy(() -> profile.booleanProperty(ProfileProperty.ENDPOINT_DISCOVERY_ENABLED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ProfileProperty.ENDPOINT_DISCOVERY_ENABLED)
                .hasMessageContaining("yes");
    }

    @Test
    void profileBuilderRequiresNameAndProperties() {
        assertThatThrownBy(() -> Profile.builder()
                                        .properties(Map.of(ProfileProperty.REGION, "us-west-2"))
                                        .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("name");
        assertThatThrownBy(() -> Profile.builder()
                                        .name("missing-properties")
                                        .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("properties");
        assertThatThrownBy(() -> Profile.builder()
                                        .name("null-properties")
                                        .properties(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void configurationProfileFileParsesProfilesSectionsCommentsAndContinuations() {
        ProfileFile profileFile = configurationFile("""
                # leading comments and blank lines are ignored

                [default]
                region = us-west-2
                retry_mode = standard ; trailing comment after whitespace is ignored

                [profile default]
                region = eu-west-1

                [profile dev]# profile comments need no separating whitespace
                region = us-east-1
                endpoint_url = https://example.test/path#fragment
                request_checksum_calculation = when_supported # trimmed comment
                s3 =
                  endpoint_url = http://localhost:4566
                  use_arn_region = true

                [ignored-without-profile-prefix]
                region = should-not-appear

                [profile bad name]
                region = invalid-name-is-ignored

                [sso-session admin]
                sso_region = us-west-2
                sso_start_url = https://example.awsapps.com/start

                [services local]
                dynamodb =
                  endpoint_url = http://localhost:8000
                """);

        assertThat(profileFile.profiles().keySet()).containsExactly("default", "dev");

        Profile defaultProfile = profileFile.profile("default").orElseThrow();
        assertThat(defaultProfile.property(ProfileProperty.REGION)).contains("eu-west-1");
        assertThat(defaultProfile.property(ProfileProperty.RETRY_MODE)).isEmpty();

        Profile dev = profileFile.profile("dev").orElseThrow();
        assertThat(dev.properties()).containsEntry(ProfileProperty.REGION, "us-east-1")
                                    .containsEntry(ProfileProperty.ENDPOINT_URL, "https://example.test/path#fragment")
                                    .containsEntry(ProfileProperty.REQUEST_CHECKSUM_CALCULATION, "when_supported")
                                    .containsEntry("s3.endpoint_url", "http://localhost:4566")
                                    .containsEntry("s3.use_arn_region", "true");
        assertThat(dev.property("s3")).contains("\nendpoint_url = http://localhost:4566\nuse_arn_region = true");
        assertThat(profileFile.profile("ignored-without-profile-prefix")).isEmpty();
        assertThat(profileFile.profile("bad name")).isEmpty();

        Profile ssoSession = profileFile.getSection("sso-session", "admin").orElseThrow();
        assertThat(ssoSession.properties()).containsEntry(ProfileProperty.SSO_REGION, "us-west-2")
                                           .containsEntry(ProfileProperty.SSO_START_URL,
                                                          "https://example.awsapps.com/start");

        Profile localServices = profileFile.getSection("services", "local").orElseThrow();
        assertThat(localServices.property("dynamodb.endpoint_url")).contains("http://localhost:8000");
        assertThat(profileFile.getSection("sso-session", "missing")).isEmpty();
    }

    @Test
    void credentialsProfileFileParsesUnprefixedProfilesAndDuplicateProperties() {
        ProfileFile profileFile = credentialsFile("""
                [default]
                aws_access_key_id = first
                aws_secret_access_key = secret
                aws_access_key_id = second

                [profile dev]
                aws_access_key_id = ignored-because-spaces-are-not-valid-in-credentials-profile-names

                [tools]
                aws_session_token = token-value
                metadata_service_timeout = 3
                """);

        assertThat(profileFile.profile("default").orElseThrow().properties())
                .containsEntry(ProfileProperty.AWS_ACCESS_KEY_ID, "second")
                .containsEntry(ProfileProperty.AWS_SECRET_ACCESS_KEY, "secret");
        assertThat(profileFile.profile("tools").orElseThrow().properties())
                .containsEntry(ProfileProperty.AWS_SESSION_TOKEN, "token-value")
                .containsEntry(ProfileProperty.METADATA_SERVICE_TIMEOUT, "3");
        assertThat(profileFile.profile("profile dev")).isEmpty();
    }

    @Test
    void parserHandlesTabsSemicolonCommentsEqualsInValuesAndInvalidProperties() {
        ProfileFile profileFile = credentialsFile("""
                ; leading semicolon comment
                [default] ; section comment
                aws_access_key_id\t=\tkey=value ; stripped comment
                !invalid = ignored
                  ignored-continuation
                multiline = first line
                  second line # continuation comments are values
                subproperties =
                  endpoint_url = http://localhost:9000/path?a=b # kept
                  use_arn_region = true
                """);

        Profile profile = profileFile.profile("default").orElseThrow();
        assertThat(profile.property(ProfileProperty.AWS_ACCESS_KEY_ID)).contains("key=value");
        assertThat(profile.property("!invalid")).isEmpty();
        assertThat(profile.property("multiline"))
                .contains("first line\nsecond line # continuation comments are values");
        assertThat(profile.property("subproperties"))
                .contains("\nendpoint_url = http://localhost:9000/path?a=b # kept\nuse_arn_region = true");
        assertThat(profile.properties())
                .containsEntry("subproperties.endpoint_url", "http://localhost:9000/path?a=b # kept")
                .containsEntry("subproperties.use_arn_region", "true");
    }

    @Test
    void configurationSectionsSupportTabsAndCredentialsFilesIgnoreSections() {
        ProfileFile configuration = configurationFile("""
                [sso-session\tadmin]
                sso_region = us-east-2

                [services\tlocal-services]
                sqs =
                  endpoint_url = http://localhost:9324

                [sso-session bad name]
                sso_region = ignored
                """);
        ProfileFile credentials = credentialsFile("""
                [sso-session admin]
                sso_region = ignored

                [valid]
                aws_access_key_id = still-read
                """);

        assertThat(configuration.getSection("sso-session", "admin").orElseThrow().property(ProfileProperty.SSO_REGION))
                .contains("us-east-2");
        assertThat(configuration.getSection("services", "local-services").orElseThrow()
                                .property("sqs.endpoint_url"))
                .contains("http://localhost:9324");
        assertThat(configuration.getSection("sso-session", "bad name")).isEmpty();
        assertThat(credentials.getSection("sso-session", "admin")).isEmpty();
        assertThat(credentials.profile("valid").orElseThrow().property(ProfileProperty.AWS_ACCESS_KEY_ID))
                .contains("still-read");
    }

    @Test
    void profileFileBuilderReadsAndClosesInputStreamContent() {
        CloseRecordingInputStream content = new CloseRecordingInputStream("""
                [stream]
                aws_account_id = 123456789012
                """.getBytes(StandardCharsets.UTF_8));

        ProfileFile profileFile = ProfileFile.builder()
                                              .content(content)
                                              .type(ProfileFile.Type.CREDENTIALS)
                                              .build();

        assertThat(profileFile.profile("stream").orElseThrow().property(ProfileProperty.AWS_ACCOUNT_ID))
                .contains("123456789012");
        assertThat(content.isClosed()).isTrue();
    }

    @Test
    void profileFileBuilderReadsPathContentAndReportsInvalidInput() throws IOException {
        Path credentials = tempDir.resolve("credentials");
        Files.writeString(credentials, """
                [disk]
                aws_access_key_id = disk-key
                """);

        ProfileFile fromPath = ProfileFile.builder()
                                          .content(credentials)
                                          .type(ProfileFile.Type.CREDENTIALS)
                                          .build();

        assertThat(fromPath.profile("disk").orElseThrow().property(ProfileProperty.AWS_ACCESS_KEY_ID))
                .contains("disk-key");
        assertThatThrownBy(() -> ProfileFile.builder().type(ProfileFile.Type.CREDENTIALS).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content");
        assertThatThrownBy(() -> ProfileFile.builder().content(credentials).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("type");
        assertThatThrownBy(() -> ProfileFile.builder()
                                             .content(tempDir.resolve("missing"))
                                             .type(ProfileFile.Type.CREDENTIALS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not exist");
        assertThatThrownBy(() -> ProfileFile.builder()
                                             .content((Path) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("profileLocation");
    }

    @Test
    void profileFilesExposeImmutableViewsEqualityHashCodeAndToString() {
        ProfileFile profileFile = credentialsFile("""
                [one]
                aws_access_key_id = first-key
                """);
        ProfileFile same = credentialsFile("""
                [one]
                aws_access_key_id = first-key
                """);
        ProfileFile different = credentialsFile("""
                [one]
                aws_access_key_id = different-key
                """);
        ProfileFile empty = ProfileFile.aggregator().build();

        assertThat(empty.profiles()).isEmpty();
        assertThat(empty.profile("one")).isEmpty();
        assertThat(profileFile.profiles()).containsOnlyKeys("one");
        assertThatThrownBy(() -> profileFile.profiles().put("two", profileFile.profile("one").orElseThrow()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(profileFile).isEqualTo(same);
        assertThat(profileFile.hashCode()).isEqualTo(same.hashCode());
        assertThat(profileFile).isNotEqualTo(different);
        assertThat(profileFile).isNotEqualTo("not-a-profile-file");
        assertThat(profileFile.toString()).contains("ProfileFile", "profiles", "one");
    }

    @Test
    void aggregatorMergesProfilesAndKeepsEarlierFilesHigherPrecedence() {
        ProfileFile credentials = credentialsFile("""
                [default]
                aws_access_key_id = credential-key
                region = credentials-region

                [shared]
                aws_secret_access_key = shared-secret
                """);
        ProfileFile configuration = configurationFile("""
                [default]
                region = config-region
                retry_mode = adaptive

                [profile shared]
                region = shared-region
                """);

        ProfileFile aggregate = ProfileFile.aggregator()
                                           .addFile(credentials)
                                           .addFile(configuration)
                                           .build();

        assertThat(aggregate.profile("default").orElseThrow().properties())
                .containsEntry(ProfileProperty.AWS_ACCESS_KEY_ID, "credential-key")
                .containsEntry(ProfileProperty.REGION, "credentials-region")
                .containsEntry(ProfileProperty.RETRY_MODE, "adaptive");
        assertThat(aggregate.profile("shared").orElseThrow().properties())
                .containsEntry(ProfileProperty.AWS_SECRET_ACCESS_KEY, "shared-secret")
                .containsEntry(ProfileProperty.REGION, "shared-region");
    }

    @Test
    void aggregatorMergesConfigurationSectionsAndKeepsEarlierFilesHigherPrecedence() {
        ProfileFile first = configurationFile("""
                [sso-session admin]
                sso_region = us-east-1

                [services local]
                s3 =
                  endpoint_url = http://localhost:4566
                """);
        ProfileFile second = configurationFile("""
                [sso-session admin]
                sso_region = eu-west-1
                sso_registration_scopes = sso:account:access

                [services local]
                s3 =
                  endpoint_url = http://secondary.example.test
                  use_dualstack_endpoint = true
                dynamodb =
                  endpoint_url = http://localhost:8000
                """);

        ProfileFile aggregate = ProfileFile.aggregator()
                                           .addFile(first)
                                           .addFile(second)
                                           .build();

        Profile ssoSession = aggregate.getSection("sso-session", "admin").orElseThrow();
        assertThat(ssoSession.properties())
                .containsEntry(ProfileProperty.SSO_REGION, "us-east-1")
                .containsEntry("sso_registration_scopes", "sso:account:access");

        Profile services = aggregate.getSection("services", "local").orElseThrow();
        assertThat(services.properties())
                .containsEntry("s3.endpoint_url", "http://localhost:4566")
                .containsEntry("s3.use_dualstack_endpoint", "true")
                .containsEntry("dynamodb.endpoint_url", "http://localhost:8000");
    }

    @Test
    void profileFileSuppliersCanReturnFixedAndAggregatedViews() {
        ProfileFile first = credentialsFile("""
                [default]
                aws_access_key_id = first-key
                """);
        ProfileFile second = configurationFile("""
                [default]
                region = eu-north-1
                """);
        AtomicReference<ProfileFile> mutableConfiguration = new AtomicReference<>(second);

        ProfileFileSupplier fixed = ProfileFileSupplier.fixedProfileFile(first);
        ProfileFileSupplier aggregate = ProfileFileSupplier.aggregate(fixed, mutableConfiguration::get);

        ProfileFile initial = aggregate.get();
        assertThat(fixed.get()).isSameAs(first);
        assertThat(initial.profile("default").orElseThrow().properties())
                .containsEntry(ProfileProperty.AWS_ACCESS_KEY_ID, "first-key")
                .containsEntry(ProfileProperty.REGION, "eu-north-1");
        assertThat(aggregate.get()).isSameAs(initial);

        mutableConfiguration.set(configurationFile("""
                [default]
                region = ap-southeast-2
                defaults_mode = mobile
                """));

        ProfileFile updated = aggregate.get();
        assertThat(updated).isNotSameAs(initial);
        assertThat(updated.profile("default").orElseThrow().properties())
                .containsEntry(ProfileProperty.AWS_ACCESS_KEY_ID, "first-key")
                .containsEntry(ProfileProperty.REGION, "ap-southeast-2")
                .containsEntry(ProfileProperty.DEFAULTS_MODE, "mobile");
    }

    @Test
    void reloadWhenModifiedSupplierReloadsChangedDiskFile() throws IOException, InterruptedException {
        Path credentials = tempDir.resolve("reloadable-credentials");
        Files.writeString(credentials, """
                [reloadable]
                aws_access_key_id = initial-key
                """);
        ProfileFileSupplier supplier = ProfileFileSupplier.reloadWhenModified(credentials, ProfileFile.Type.CREDENTIALS);

        ProfileFile initial = supplier.get();
        assertThat(initial.profile("reloadable").orElseThrow().property(ProfileProperty.AWS_ACCESS_KEY_ID))
                .contains("initial-key");
        assertThat(supplier.get()).isSameAs(initial);

        Files.writeString(credentials, """
                [reloadable]
                aws_access_key_id = refreshed-key
                aws_secret_access_key = refreshed-secret
                """);
        Files.setLastModifiedTime(credentials, FileTime.from(Instant.now().plusSeconds(2)));
        Thread.sleep(1_200L);

        ProfileFile refreshed = supplier.get();
        assertThat(refreshed).isNotSameAs(initial);
        assertThat(refreshed.profile("reloadable").orElseThrow().properties())
                .containsEntry(ProfileProperty.AWS_ACCESS_KEY_ID, "refreshed-key")
                .containsEntry(ProfileProperty.AWS_SECRET_ACCESS_KEY, "refreshed-secret");
        assertThat(supplier.get()).isSameAs(refreshed);
    }

    @Test
    void profileFileSystemSettingExposesAwsProfileSelection() {
        assertThat(ProfileFileSystemSetting.AWS_PROFILE.property()).isEqualTo("aws.profile");
        assertThat(ProfileFileSystemSetting.AWS_PROFILE.environmentVariable()).isEqualTo("AWS_PROFILE");
        assertThat(ProfileFileSystemSetting.AWS_PROFILE.defaultValue()).isEqualTo("default");

        withSystemProperty(ProfileFileSystemSetting.AWS_PROFILE.property(), "analytics", () -> {
            assertThat(ProfileFileSystemSetting.AWS_PROFILE.getStringValue()).contains("analytics");
            assertThat(ProfileFileSystemSetting.AWS_PROFILE.getNonDefaultStringValue()).contains("analytics");
            assertThat(ProfileFileSystemSetting.AWS_PROFILE.getStringValueOrThrow()).isEqualTo("analytics");
        });
    }

    @Test
    void defaultProfileFileUsesConfiguredLocationsWhenTheyExist() throws IOException {
        Path credentials = tempDir.resolve("credentials");
        Path config = tempDir.resolve("config");
        Files.writeString(credentials, """
                [default]
                aws_access_key_id = configured-key
                region = credentials-region
                """);
        Files.writeString(config, """
                [default]
                region = config-region
                use_dualstack_endpoint = true
                """);

        withSystemProperty(AWS_SHARED_CREDENTIALS_FILE_PROPERTY, credentials.toString(), () ->
                withSystemProperty(AWS_CONFIG_FILE_PROPERTY, config.toString(), () -> {
                    assertThat(ProfileFileLocation.credentialsFilePath()).isEqualTo(credentials);
                    assertThat(ProfileFileLocation.configurationFilePath()).isEqualTo(config);
                    assertThat(ProfileFileLocation.credentialsFileLocation()).contains(credentials);
                    assertThat(ProfileFileLocation.configurationFileLocation()).contains(config);

                    Profile profile = ProfileFile.defaultProfileFile().profile("default").orElseThrow();
                    assertThat(profile.properties())
                            .containsEntry(ProfileProperty.AWS_ACCESS_KEY_ID, "configured-key")
                            .containsEntry(ProfileProperty.REGION, "credentials-region")
                            .containsEntry(ProfileProperty.USE_DUALSTACK_ENDPOINT, "true");
                    assertThat(profile.booleanProperty(ProfileProperty.USE_DUALSTACK_ENDPOINT)).contains(true);
                }));
    }

    @Test
    void defaultSupplierReturnsEmptyProfileFileWhenConfiguredLocationsDoNotExist() {
        Path missingCredentials = tempDir.resolve("missing-credentials");
        Path missingConfig = tempDir.resolve("missing-config");

        withSystemProperty(AWS_SHARED_CREDENTIALS_FILE_PROPERTY, missingCredentials.toString(), () ->
                withSystemProperty(AWS_CONFIG_FILE_PROPERTY, missingConfig.toString(), () -> {
                    ProfileFile supplied = ProfileFileSupplier.defaultSupplier().get();

                    assertThat(ProfileFileLocation.credentialsFileLocation()).isEmpty();
                    assertThat(ProfileFileLocation.configurationFileLocation()).isEmpty();
                    assertThat(supplied.profiles()).isEmpty();
                    assertThat(supplied.profile("default")).isEmpty();
                }));
    }

    @Test
    void defaultSupplierReadsOnlyExistingConfiguredLocation() throws IOException {
        Path credentials = tempDir.resolve("credentials-only");
        Path config = tempDir.resolve("config-only");
        Path missingCredentials = tempDir.resolve("missing-credentials");
        Path missingConfig = tempDir.resolve("missing-config");
        Files.writeString(credentials, """
                [default]
                aws_access_key_id = credentials-only-key
                """);
        Files.writeString(config, """
                [default]
                region = ca-central-1
                """);

        withSystemProperty(AWS_SHARED_CREDENTIALS_FILE_PROPERTY, credentials.toString(), () ->
                withSystemProperty(AWS_CONFIG_FILE_PROPERTY, missingConfig.toString(), () -> {
                    Profile profile = ProfileFileSupplier.defaultSupplier().get().profile("default").orElseThrow();

                    assertThat(profile.property(ProfileProperty.AWS_ACCESS_KEY_ID)).contains("credentials-only-key");
                    assertThat(profile.property(ProfileProperty.REGION)).isEmpty();
                }));
        withSystemProperty(AWS_SHARED_CREDENTIALS_FILE_PROPERTY, missingCredentials.toString(), () ->
                withSystemProperty(AWS_CONFIG_FILE_PROPERTY, config.toString(), () -> {
                    Profile profile = ProfileFileSupplier.defaultSupplier().get().profile("default").orElseThrow();

                    assertThat(profile.property(ProfileProperty.REGION)).contains("ca-central-1");
                    assertThat(profile.property(ProfileProperty.AWS_ACCESS_KEY_ID)).isEmpty();
                }));
    }

    @Test
    void profileFileLocationsExpandHomeDirectoryMarkerInConfiguredLocations() {
        withSystemProperty(AWS_SHARED_CREDENTIALS_FILE_PROPERTY, "~/custom-credentials", () ->
                assertThat(ProfileFileLocation.credentialsFilePath())
                        .isEqualTo(Path.of(System.getProperty("user.home"), "custom-credentials")));
        withSystemProperty(AWS_CONFIG_FILE_PROPERTY, "~/custom-config", () ->
                assertThat(ProfileFileLocation.configurationFilePath())
                        .isEqualTo(Path.of(System.getProperty("user.home"), "custom-config")));
    }

    @Test
    void malformedProfileFilesFailFastWithLineSpecificMessages() {
        assertThatThrownBy(() -> credentialsFile("aws_access_key_id = no-section"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected a profile definition on line 1");

        assertThatThrownBy(() -> credentialsFile("""
                [default]
                aws_access_key_id no-equals
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected an '=' sign defining a property on line 2");

        assertThatThrownBy(() -> credentialsFile("""
                [default
                aws_access_key_id = key
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Profile definition must end with ']'");

        assertThatThrownBy(() -> credentialsFile("  orphan-continuation"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected a profile or property definition on line 1");
        assertThatThrownBy(() -> credentialsFile("""
                [default]
                  orphan-continuation
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected a profile or property definition on line 2");
        assertThatThrownBy(() -> credentialsFile("""
                [default]
                = unnamed
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Property did not have a name on line 2");
        assertThatThrownBy(() -> configurationFile("""
                [sso-session admin
                sso_region = us-west-2
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Section definition must end with ']'");
    }

    private static final class CloseRecordingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private CloseRecordingInputStream(byte[] data) {
            super(data);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        private boolean isClosed() {
            return closed;
        }
    }

    private static ProfileFile configurationFile(String content) {
        return ProfileFile.builder()
                          .content(content)
                          .type(ProfileFile.Type.CONFIGURATION)
                          .build();
    }

    private static ProfileFile credentialsFile(String content) {
        return ProfileFile.builder()
                          .content(content)
                          .type(ProfileFile.Type.CREDENTIALS)
                          .build();
    }

    private static void withSystemProperty(String name, String value, Runnable action) {
        String previous = System.getProperty(name);
        try {
            System.setProperty(name, value);
            action.run();
        } finally {
            if (previous == null) {
                System.clearProperty(name);
            } else {
                System.setProperty(name, previous);
            }
        }
    }
}
