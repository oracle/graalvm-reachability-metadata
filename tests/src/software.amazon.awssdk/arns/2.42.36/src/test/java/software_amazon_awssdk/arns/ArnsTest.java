/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.arns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.arns.ArnResource;

public class ArnsTest {
    @Test
    void parsesCompleteArnAndStructuredResource() {
        Arn arn = Arn.fromString("arn:aws:lambda:us-east-1:123456789012:function:process-events:live");

        assertThat(arn.partition()).isEqualTo("aws");
        assertThat(arn.service()).isEqualTo("lambda");
        assertThat(arn.region()).contains("us-east-1");
        assertThat(arn.accountId()).contains("123456789012");
        assertThat(arn.resourceAsString()).isEqualTo("function:process-events:live");
        assertThat(arn.toString()).isEqualTo("arn:aws:lambda:us-east-1:123456789012:function:process-events:live");

        ArnResource resource = arn.resource();
        assertThat(resource.resourceType()).contains("function");
        assertThat(resource.resource()).isEqualTo("process-events");
        assertThat(resource.qualifier()).contains("live");
    }

    @Test
    void parsesArnWithEmptyRegionAndAccountSections() {
        Arn arn = Arn.fromString("arn:aws:s3:::customer-bucket");

        assertThat(arn.partition()).isEqualTo("aws");
        assertThat(arn.service()).isEqualTo("s3");
        assertThat(arn.region()).isEmpty();
        assertThat(arn.accountId()).isEmpty();
        assertThat(arn.resourceAsString()).isEqualTo("customer-bucket");
        assertThat(arn.resource().resourceType()).isEmpty();
        assertThat(arn.resource().resource()).isEqualTo("customer-bucket");
        assertThat(arn.resource().qualifier()).isEmpty();
        assertThat(arn.toString()).isEqualTo("arn:aws:s3:::customer-bucket");
    }

    @Test
    void builderCreatesArnWhenOptionalRegionAndAccountAreOmitted() {
        Arn arn = Arn.builder()
                     .partition("aws")
                     .service("s3")
                     .resource("customer-bucket")
                     .build();

        assertThat(arn.partition()).isEqualTo("aws");
        assertThat(arn.service()).isEqualTo("s3");
        assertThat(arn.region()).isEmpty();
        assertThat(arn.accountId()).isEmpty();
        assertThat(arn.resourceAsString()).isEqualTo("customer-bucket");
        assertThat(arn.toString()).isEqualTo("arn:aws:s3:::customer-bucket");
    }

    @Test
    void tryFromStringReturnsOptionalInsteadOfThrowingForSupportedInputs() {
        Optional<Arn> parsedArn = Arn.tryFromString("arn:aws-us-gov:iam::123456789012:role/Admin");

        assertThat(parsedArn).isPresent();
        assertThat(parsedArn.orElseThrow().partition()).isEqualTo("aws-us-gov");
        assertThat(parsedArn.orElseThrow().service()).isEqualTo("iam");
        assertThat(parsedArn.orElseThrow().region()).isEmpty();
        assertThat(parsedArn.orElseThrow().accountId()).contains("123456789012");
        assertThat(parsedArn.orElseThrow().resource().resourceType()).contains("role");
        assertThat(parsedArn.orElseThrow().resource().resource()).isEqualTo("Admin");

        assertThat(Arn.tryFromString(null)).isEmpty();
        assertThat(Arn.tryFromString("not-an-arn")).isEmpty();
        assertThat(Arn.tryFromString("arn:aws:s3:::")).isEmpty();
    }

    @Test
    void fromStringReportsMalformedArns() {
        assertThatThrownBy(() -> Arn.fromString("aws:s3:::bucket"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("arn");
        assertThatThrownBy(() -> Arn.fromString("arn:aws"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("partition");
        assertThatThrownBy(() -> Arn.fromString("arn:aws:"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("service");
        assertThatThrownBy(() -> Arn.fromString("arn:aws:s3:"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("region");
        assertThatThrownBy(() -> Arn.fromString("arn:aws:s3:us-east-1:"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("account");
        assertThatThrownBy(() -> Arn.fromString("arn:aws:s3:us-east-1:123456789012:"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resource");
    }

    @Test
    void builderCreatesArnsAndToBuilderCopiesValues() {
        Arn queueArn = Arn.builder()
                           .partition("aws-cn")
                           .service("sqs")
                           .region("cn-north-1")
                           .accountId("123456789012")
                           .resource("queue/orders")
                           .build();

        Arn copiedArn = queueArn.toBuilder()
                                .region("cn-northwest-1")
                                .resource("queue/invoices")
                                .build();

        assertThat(queueArn.partition()).isEqualTo("aws-cn");
        assertThat(queueArn.service()).isEqualTo("sqs");
        assertThat(queueArn.region()).contains("cn-north-1");
        assertThat(queueArn.accountId()).contains("123456789012");
        assertThat(queueArn.resource().resourceType()).contains("queue");
        assertThat(queueArn.resource().resource()).isEqualTo("orders");
        assertThat(queueArn.toString()).isEqualTo("arn:aws-cn:sqs:cn-north-1:123456789012:queue/orders");

        assertThat(copiedArn).isNotEqualTo(queueArn);
        assertThat(copiedArn.region()).contains("cn-northwest-1");
        assertThat(copiedArn.resource().resource()).isEqualTo("invoices");
        assertThat(copiedArn.toString()).isEqualTo("arn:aws-cn:sqs:cn-northwest-1:123456789012:queue/invoices");
    }

    @Test
    void equivalentArnsCompareByValue() {
        Arn parsedArn = Arn.fromString("arn:aws:dynamodb:us-west-2:123456789012:table/Orders/index/ByCustomer");
        Arn builtArn = Arn.builder()
                          .partition("aws")
                          .service("dynamodb")
                          .region("us-west-2")
                          .accountId("123456789012")
                          .resource("table/Orders/index/ByCustomer")
                          .build();
        Arn differentArn = builtArn.toBuilder().resource("table/Orders").build();

        assertThat(parsedArn).isEqualTo(builtArn);
        assertThat(parsedArn).hasSameHashCodeAs(builtArn);
        assertThat(parsedArn).isNotEqualTo(differentArn);
        assertThat(parsedArn).isNotEqualTo("arn:aws:dynamodb:us-west-2:123456789012:table/Orders/index/ByCustomer");
    }

    @Test
    void builderRejectsBlankRequiredArnFields() {
        assertThatThrownBy(() -> Arn.builder()
                                     .partition(" ")
                                     .service("s3")
                                     .resource("bucket")
                                     .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("partition");
        assertThatThrownBy(() -> Arn.builder()
                                     .partition("aws")
                                     .service(" ")
                                     .resource("bucket")
                                     .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("service");
        assertThatThrownBy(() -> Arn.builder()
                                     .partition("aws")
                                     .service("s3")
                                     .resource(" ")
                                     .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resource");
    }

    @Test
    void parsesResourceStringsUsingColonAndSlashSeparators() {
        ArnResource untypedResource = ArnResource.fromString("bucket-name");
        ArnResource slashSeparatedResource = ArnResource.fromString("accesspoint/my-access-point");
        ArnResource colonSeparatedResource = ArnResource.fromString("function:process-events:live");
        ArnResource resourceWithEmbeddedSeparators = ArnResource.fromString("table/Orders/index/ByCustomer");

        assertThat(untypedResource.resourceType()).isEmpty();
        assertThat(untypedResource.resource()).isEqualTo("bucket-name");
        assertThat(untypedResource.qualifier()).isEmpty();

        assertThat(slashSeparatedResource.resourceType()).contains("accesspoint");
        assertThat(slashSeparatedResource.resource()).isEqualTo("my-access-point");
        assertThat(slashSeparatedResource.qualifier()).isEmpty();

        assertThat(colonSeparatedResource.resourceType()).contains("function");
        assertThat(colonSeparatedResource.resource()).isEqualTo("process-events");
        assertThat(colonSeparatedResource.qualifier()).contains("live");

        assertThat(resourceWithEmbeddedSeparators.resourceType()).contains("table");
        assertThat(resourceWithEmbeddedSeparators.resource()).isEqualTo("Orders");
        assertThat(resourceWithEmbeddedSeparators.qualifier()).contains("index/ByCustomer");
    }

    @Test
    void resourceBuilderCreatesCopyAndComparesByValue() {
        ArnResource resource = ArnResource.builder()
                                          .resourceType("function")
                                          .resource("process-events")
                                          .qualifier("live")
                                          .build();
        ArnResource equivalentResource = ArnResource.fromString("function:process-events:live");
        ArnResource copiedResource = resource.toBuilder()
                                             .qualifier("candidate")
                                             .build();

        assertThat(resource.resourceType()).contains("function");
        assertThat(resource.resource()).isEqualTo("process-events");
        assertThat(resource.qualifier()).contains("live");
        assertThat(resource.toString()).isEqualTo("function:process-events:live");
        assertThat(resource).isEqualTo(equivalentResource);
        assertThat(resource).hasSameHashCodeAs(equivalentResource);
        assertThat(resource).isNotEqualTo(copiedResource);
        assertThat(resource).isNotEqualTo("function:process-events:live");
        assertThat(copiedResource.qualifier()).contains("candidate");
    }

    @Test
    void resourceBuilderRejectsBlankResource() {
        assertThatThrownBy(() -> ArnResource.builder()
                                            .resourceType("function")
                                            .resource(" ")
                                            .qualifier("live")
                                            .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resource");
    }
}
