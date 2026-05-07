/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.regions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.regions.EndpointTag;
import software.amazon.awssdk.regions.GeneratedRegionMetadataProvider;
import software.amazon.awssdk.regions.GeneratedServiceMetadataProvider;
import software.amazon.awssdk.regions.PartitionEndpointKey;
import software.amazon.awssdk.regions.PartitionMetadata;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.RegionMetadata;
import software.amazon.awssdk.regions.RegionScope;
import software.amazon.awssdk.regions.ServiceEndpointKey;
import software.amazon.awssdk.regions.ServiceMetadata;
import software.amazon.awssdk.regions.ServiceMetadataAdvancedOption;
import software.amazon.awssdk.regions.ServiceMetadataConfiguration;
import software.amazon.awssdk.regions.ServicePartitionMetadata;
import software.amazon.awssdk.regions.providers.AwsProfileRegionProvider;
import software.amazon.awssdk.regions.providers.AwsRegionProviderChain;
import software.amazon.awssdk.regions.providers.SystemSettingsRegionProvider;

public class RegionsTest {
    @Test
    void regionFactoryReturnsCachedKnownAndCustomRegions() {
        Region knownRegion = Region.of("us-east-1");
        Region customRegion = Region.of("local-test-region-1");

        assertThat(knownRegion).isSameAs(Region.US_EAST_1);
        assertThat(knownRegion.id()).isEqualTo("us-east-1");
        assertThat(knownRegion.toString()).isEqualTo("us-east-1");
        assertThat(knownRegion.isGlobalRegion()).isFalse();
        assertThat(Region.of("local-test-region-1")).isSameAs(customRegion);
        assertThat(customRegion.id()).isEqualTo("local-test-region-1");
        assertThat(customRegion.isGlobalRegion()).isFalse();
    }

    @Test
    void enumeratesStaticRegionsAndGlobalRegions() {
        List<Region> regions = Region.regions();

        assertThat(regions)
                .contains(Region.US_EAST_1, Region.US_WEST_2, Region.EU_WEST_1, Region.CN_NORTH_1,
                        Region.US_GOV_WEST_1, Region.AWS_GLOBAL);
        assertThat(Region.AWS_GLOBAL.id()).isEqualTo("aws-global");
        assertThat(Region.AWS_GLOBAL.isGlobalRegion()).isTrue();
        assertThat(Region.AWS_CN_GLOBAL.isGlobalRegion()).isTrue();
        assertThat(Region.AWS_US_GOV_GLOBAL.isGlobalRegion()).isTrue();
        assertThatThrownBy(() -> regions.add(Region.of("should-not-be-added-1")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void loadsRegionMetadataFromConvenienceAndGeneratedProviders() {
        RegionMetadata usEast = RegionMetadata.of(Region.US_EAST_1);
        GeneratedRegionMetadataProvider provider = new GeneratedRegionMetadataProvider();
        RegionMetadata china = provider.regionMetadata(Region.CN_NORTH_1);

        assertThat(Region.US_EAST_1.metadata().id()).isEqualTo(usEast.id());
        assertThat(usEast.id()).isEqualTo("us-east-1");
        assertThat(usEast.description()).isEqualTo("US East (N. Virginia)");
        assertThat(usEast.partition().id()).isEqualTo("aws");
        assertThat(usEast.partition().name()).isEqualTo("AWS Standard");
        assertThat(china.id()).isEqualTo("cn-north-1");
        assertThat(china.partition().id()).isEqualTo("aws-cn");
    }

    @Test
    void resolvesPartitionMetadataAndEndpointTagSpecificRules() {
        PartitionMetadata aws = PartitionMetadata.of("aws");
        PartitionMetadata china = PartitionMetadata.of(Region.CN_NORTHWEST_1);
        PartitionEndpointKey standard = PartitionEndpointKey.builder().build();
        PartitionEndpointKey fips = PartitionEndpointKey.builder().tags(EndpointTag.FIPS).build();
        PartitionEndpointKey dualstack = PartitionEndpointKey.builder().tags(EndpointTag.DUALSTACK).build();
        PartitionEndpointKey fipsDualstack = PartitionEndpointKey.builder()
                .tags(EndpointTag.DUALSTACK, EndpointTag.FIPS)
                .build();

        assertThat(aws.id()).isEqualTo("aws");
        assertThat(aws.name()).isEqualTo("AWS Standard");
        assertThat(aws.regionRegex())
                .startsWith("^(")
                .contains("us", "eu", "ap", "mx")
                .endsWith(")\\-\\w+\\-\\d+$");
        assertThat(aws.dnsSuffix()).isEqualTo("amazonaws.com");
        assertThat(aws.hostname()).isEqualTo("{service}.{region}.{dnsSuffix}");
        assertThat(aws.hostname(fips)).isEqualTo("{service}-fips.{region}.{dnsSuffix}");
        assertThat(aws.dnsSuffix(dualstack)).isEqualTo("api.aws");
        assertThat(aws.hostname(fipsDualstack)).isEqualTo("{service}-fips.{region}.{dnsSuffix}");
        assertThat(china.id()).isEqualTo("aws-cn");
        assertThat(china.name()).isEqualTo("AWS China");
        assertThat(china.dnsSuffix(standard)).isEqualTo("amazonaws.com.cn");
    }

    @Test
    void endpointTagsAreCachedAndCanBeUsedInEndpointKeys() {
        EndpointTag customTag = EndpointTag.of("private-link");
        ServiceEndpointKey serviceKey = ServiceEndpointKey.builder()
                .region(Region.US_WEST_2)
                .tags(EndpointTag.FIPS, customTag)
                .build();
        ServiceEndpointKey equalServiceKey = ServiceEndpointKey.builder()
                .region(Region.US_WEST_2)
                .tags(customTag, EndpointTag.FIPS)
                .build();
        PartitionEndpointKey partitionKey = PartitionEndpointKey.builder()
                .tags(List.of(EndpointTag.DUALSTACK, EndpointTag.FIPS))
                .build();
        PartitionEndpointKey equalPartitionKey = PartitionEndpointKey.builder()
                .tags(EndpointTag.FIPS, EndpointTag.DUALSTACK)
                .build();

        assertThat(EndpointTag.of("dualstack")).isSameAs(EndpointTag.DUALSTACK);
        assertThat(EndpointTag.of("fips")).isSameAs(EndpointTag.FIPS);
        assertThat(EndpointTag.of("private-link")).isSameAs(customTag);
        assertThat(customTag.id()).isEqualTo("private-link");
        assertThat(EndpointTag.endpointTags()).containsExactly(EndpointTag.DUALSTACK, EndpointTag.FIPS);
        assertThat(serviceKey.region()).isSameAs(Region.US_WEST_2);
        assertThat(serviceKey.tags()).containsExactlyInAnyOrder(EndpointTag.FIPS, customTag);
        assertThat(serviceKey).isEqualTo(equalServiceKey).hasSameHashCodeAs(equalServiceKey);
        assertThat(partitionKey).isEqualTo(equalPartitionKey).hasSameHashCodeAs(equalPartitionKey);
    }

    @Test
    void serviceMetadataResolvesGeneratedAndFallbackServiceEndpoints() {
        ServiceMetadata dynamoDb = ServiceMetadata.of("dynamodb");
        ServiceMetadata fallback = ServiceMetadata.of("example-service");
        GeneratedServiceMetadataProvider provider = new GeneratedServiceMetadataProvider();
        ServiceEndpointKey usWestKey = ServiceEndpointKey.builder().region(Region.US_WEST_2).build();

        assertThat(provider.serviceMetadata("dynamodb")).isNotNull();
        assertThat(dynamoDb.regions()).contains(Region.US_EAST_1, Region.US_WEST_2, Region.EU_WEST_1);
        assertThat(dynamoDb.endpointFor(Region.US_WEST_2)).hasToString("dynamodb.us-west-2.amazonaws.com");
        assertThat(dynamoDb.endpointFor(usWestKey)).hasToString("dynamodb.us-west-2.amazonaws.com");
        assertThat(dynamoDb.signingRegion(usWestKey)).isSameAs(Region.US_WEST_2);
        assertThat(fallback.regions()).isEmpty();
        assertThat(fallback.servicePartitions()).isEmpty();
        assertThat(fallback.endpointFor(Region.US_WEST_2))
                .hasToString("example-service.us-west-2.amazonaws.com");
        assertThat(fallback.signingRegion(Region.US_WEST_2)).isSameAs(Region.US_WEST_2);
    }

    @Test
    void s3ServiceMetadataSupportsUsEastOneRegionalReconfiguration() {
        ServiceMetadata s3 = ServiceMetadata.of("s3");
        ServiceMetadata legacyS3 = s3.reconfigure(builder -> builder.putAdvancedOption(
                ServiceMetadataAdvancedOption.DEFAULT_S3_US_EAST_1_REGIONAL_ENDPOINT, "legacy"));
        ServiceMetadata regionalS3 = s3.reconfigure(builder -> builder.putAdvancedOption(
                ServiceMetadataAdvancedOption.DEFAULT_S3_US_EAST_1_REGIONAL_ENDPOINT, "regional"));
        ServiceMetadataConfiguration configuration = ServiceMetadataConfiguration.builder()
                .putAdvancedOption(ServiceMetadataAdvancedOption.DEFAULT_S3_US_EAST_1_REGIONAL_ENDPOINT, "regional")
                .build();
        ServiceMetadata explicitlyRegionalS3 = s3.reconfigure(configuration);

        assertThat(legacyS3.endpointFor(Region.US_EAST_1)).hasToString("s3.amazonaws.com");
        assertThat(regionalS3.endpointFor(Region.US_EAST_1)).hasToString("s3.us-east-1.amazonaws.com");
        assertThat(explicitlyRegionalS3.endpointFor(Region.US_EAST_1))
                .hasToString("s3.us-east-1.amazonaws.com");
        assertThat(s3.endpointFor(Region.US_WEST_2)).hasToString("s3.us-west-2.amazonaws.com");
        assertThat(s3.endpointFor(ServiceEndpointKey.builder()
                .region(Region.US_WEST_2)
                .tags(EndpointTag.DUALSTACK)
                .build())).hasToString("s3.dualstack.us-west-2.amazonaws.com");
    }

    @Test
    void servicePartitionMetadataExposesPartitionsAndGlobalSigningRegions() {
        ServiceMetadata iam = ServiceMetadata.of("iam");

        assertThat(iam.regions()).contains(Region.AWS_GLOBAL, Region.AWS_CN_GLOBAL, Region.AWS_US_GOV_GLOBAL);
        assertThat(iam.endpointFor(Region.AWS_GLOBAL)).hasToString("iam.amazonaws.com");
        assertThat(iam.signingRegion(Region.AWS_GLOBAL)).isSameAs(Region.US_EAST_1);
        assertThat(iam.servicePartitions())
                .extracting(ServicePartitionMetadata::partition)
                .extracting(PartitionMetadata::id)
                .contains("aws", "aws-cn", "aws-us-gov");
        assertThat(iam.servicePartitions())
                .filteredOn(partition -> "aws".equals(partition.partition().id()))
                .singleElement()
                .satisfies(partition -> assertThat(partition.globalRegion()).contains(Region.AWS_GLOBAL));
    }

    @Test
    void profileRegionProviderReadsSelectedProfileFromInMemoryConfiguration() {
        ProfileFile profileFile = ProfileFile.builder()
                .type(ProfileFile.Type.CONFIGURATION)
                .content(new ByteArrayInputStream("""
                        [default]
                        region = us-east-2
                        [profile integration]
                        region = eu-west-1
                        """.getBytes(StandardCharsets.UTF_8)))
                .build();
        ServiceMetadataConfiguration configuration = ServiceMetadataConfiguration.builder()
                .profileFile(() -> profileFile)
                .profileName("integration")
                .putAdvancedOption(ServiceMetadataAdvancedOption.DEFAULT_S3_US_EAST_1_REGIONAL_ENDPOINT, "regional")
                .build();

        AwsProfileRegionProvider provider = new AwsProfileRegionProvider(() -> profileFile, "integration");

        assertThat(provider.getRegion()).isSameAs(Region.EU_WEST_1);
        assertThat(configuration.profileFile().get()).isSameAs(profileFile);
        assertThat(configuration.profileName()).isEqualTo("integration");
        assertThat(configuration.advancedOption(ServiceMetadataAdvancedOption.DEFAULT_S3_US_EAST_1_REGIONAL_ENDPOINT))
                .contains("regional");
    }

    @Test
    void regionProviderChainFallsThroughFailuresAndSystemSettingsReadSystemProperty() {
        AwsRegionProviderChain chain = new AwsRegionProviderChain(
                () -> {
                    throw SdkClientException.create("not configured here");
                },
                () -> null,
                () -> Region.AP_SOUTH_1);
        String previousRegion = System.getProperty("aws.region");

        try {
            System.setProperty("aws.region", "ca-central-1");

            assertThat(chain.getRegion()).isSameAs(Region.AP_SOUTH_1);
            assertThat(new SystemSettingsRegionProvider().getRegion()).isSameAs(Region.CA_CENTRAL_1);
        } finally {
            if (previousRegion == null) {
                System.clearProperty("aws.region");
            } else {
                System.setProperty("aws.region", previousRegion);
            }
        }
    }

    @Test
    void regionScopeUsesValueSemanticsForGlobalAndCustomScopes() {
        RegionScope customScope = RegionScope.create("aws-partition");
        RegionScope sameCustomScope = RegionScope.create("aws-partition");

        assertThat(RegionScope.GLOBAL.id()).isEqualTo("*");
        assertThat(customScope.id()).isEqualTo("aws-partition");
        assertThat(customScope).isEqualTo(sameCustomScope).hasSameHashCodeAs(sameCustomScope);
        assertThat(customScope).isNotEqualTo(RegionScope.GLOBAL);
    }
}
