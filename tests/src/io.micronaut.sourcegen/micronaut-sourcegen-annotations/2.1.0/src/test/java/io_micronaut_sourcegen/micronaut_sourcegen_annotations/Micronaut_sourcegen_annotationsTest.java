/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_sourcegen.micronaut_sourcegen_annotations;

import io.micronaut.sourcegen.annotations.Builder;
import io.micronaut.sourcegen.annotations.Delegate;
import io.micronaut.sourcegen.annotations.EqualsAndHashCode;
import io.micronaut.sourcegen.annotations.Singular;
import io.micronaut.sourcegen.annotations.SuperBuilder;
import io.micronaut.sourcegen.annotations.ToString;
import io.micronaut.sourcegen.annotations.Wither;
import io.micronaut.sourcegen.info.MicronautSourcegenAnnotationsModuleInfo;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Micronaut_sourcegen_annotationsTest {
    @Test
    void exposesModuleInformation() {
        MicronautSourcegenAnnotationsModuleInfo moduleInfo = new MicronautSourcegenAnnotationsModuleInfo();

        assertThat(moduleInfo.getId()).isEqualTo("io.micronaut.sourcegen:micronaut-sourcegen-annotations");
        assertThat(moduleInfo.getName()).isEqualTo("sourcegen-annotations");
        assertThat(moduleInfo.getDescription())
                .hasValue("Micronaut SourceGen exposes a language-neutral API for source code generation.");
        assertThat(moduleInfo.getMavenCoordinates()).hasValueSatisfying(coordinates -> {
            assertThat(coordinates.groupId()).isEqualTo("io.micronaut.sourcegen");
            assertThat(coordinates.artifactId()).isEqualTo("micronaut-sourcegen-annotations");
            assertThat(coordinates.version()).isEqualTo(moduleInfo.getVersion()).isNotBlank();
        });
        assertThat(moduleInfo.getParentModuleId()).isEmpty();
        assertThat(moduleInfo.getTags()).isEmpty();
    }

    @Test
    void builderCreatesRecordWithSingularCollections() {
        Purchase purchase = PurchaseBuilder.builder()
                .customer("Ada")
                .item("book")
                .items(List.of("pen", "paper"))
                .attribute("priority", "high")
                .attributes(Map.of("channel", "web"))
                .build();

        assertThat(purchase.customer()).isEqualTo("Ada");
        assertThat(purchase.items()).containsExactly("book", "pen", "paper");
        assertThat(purchase.attributes())
                .containsEntry("priority", "high")
                .containsEntry("channel", "web");

        Purchase emptyPurchase = new PurchaseBuilder().customer("Grace").build();
        assertThat(emptyPurchase.items()).isEmpty();
        assertThat(emptyPurchase.attributes()).isEmpty();
    }

    @Test
    void superBuilderPopulatesInheritedAndDeclaredProperties() {
        ComputeNode node = new ComputeNodeSuperBuilder()
                .identifier("node-7")
                .region("west")
                .cores(16)
                .active(true)
                .build();

        assertThat(node.getIdentifier()).isEqualTo("node-7");
        assertThat(node.getRegion()).isEqualTo("west");
        assertThat(node.getCores()).isEqualTo(16);
        assertThat(node.isActive()).isTrue();
    }

    @Test
    void witherCopiesRecordsAndIntegratesWithGeneratedBuilder() {
        UserProfile original = UserProfileBuilder.builder()
                .displayName("Lin")
                .score(10)
                .build();

        UserProfile renamed = original.withDisplayName("Lin Q");
        UserProfile rescored = renamed.with().score(42).build();

        assertThat(original).isEqualTo(new UserProfile("Lin", 10));
        assertThat(renamed).isEqualTo(new UserProfile("Lin Q", 10));
        assertThat(rescored).isEqualTo(new UserProfile("Lin Q", 42));
    }

    @Test
    void generatedObjectMethodsHonorBothExcludeAnnotations() {
        ReleaseNote first = new ReleaseNote("audit-a", "Stable", "token");
        ReleaseNote sameContent = new ReleaseNote("audit-b", "Stable", "token");
        ReleaseNote differentSecret = new ReleaseNote("audit-c", "Stable", "other-token");

        assertThat(first).isEqualTo(sameContent).hasSameHashCodeAs(sameContent);
        assertThat(first).isNotEqualTo(differentSecret).isNotEqualTo(new Object());
        assertThat(first.toString())
                .contains("ReleaseNote[", "auditId=audit-a", "title=Stable")
                .doesNotContain("token", "secret");
    }

    @Test
    void delegateForwardsInterfaceMethodsAndAllowsSelectiveOverrides() {
        PriceBook base = new FixedPriceBook(Map.of("apple", "2", "pear", "3"));
        CountingPriceBook decorated = new CountingPriceBook(base);

        assertThat(decorated.size()).isEqualTo(2);
        assertThat(decorated.priceFor("apple")).isEqualTo("USD 2");
        assertThat(decorated.priceFor("pear")).isEqualTo("USD 3");
        assertThat(decorated.lookupCount()).isEqualTo(2);
    }

    @Test
    void singularBuilderClearsAccumulatedElements() {
        TagCollection tags = TagCollectionBuilder.builder()
                .tag("draft")
                .tags(Set.of("internal", "reviewed"))
                .clearTags()
                .tag("published")
                .build();

        assertThat(tags.tags()).containsExactly("published");
    }
}

@Builder
record Purchase(
        String customer,
        @Singular List<String> items,
        @Singular("attribute") Map<String, String> attributes) {
}

@Builder
record TagCollection(@Singular Set<String> tags) {
}

@SuperBuilder
abstract class InfrastructureAsset {
    private String identifier;
    private String region;

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }
}

@SuperBuilder
class ComputeNode extends InfrastructureAsset {
    private int cores;
    private boolean active;

    public int getCores() {
        return cores;
    }

    public void setCores(int cores) {
        this.cores = cores;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}

@Builder
@Wither
record UserProfile(String displayName, int score) implements UserProfileWither {
}

@ToString
@EqualsAndHashCode
class ReleaseNote {
    @EqualsAndHashCode.Exclude
    private final String auditId;
    private final String title;
    @ToString.Exclude
    private final String secret;

    ReleaseNote(String auditId, String title, String secret) {
        this.auditId = auditId;
        this.title = title;
        this.secret = secret;
    }

    public String getAuditId() {
        return auditId;
    }

    public String getTitle() {
        return title;
    }

    public String getSecret() {
        return secret;
    }

    @Override
    public String toString() {
        return ReleaseNoteObject.toString(this);
    }

    @Override
    public boolean equals(Object other) {
        return ReleaseNoteObject.equals(this, other);
    }

    @Override
    public int hashCode() {
        return ReleaseNoteObject.hashCode(this);
    }
}

@Delegate
interface PriceBook {
    String priceFor(String product);

    int size();
}

final class FixedPriceBook implements PriceBook {
    private final Map<String, String> prices;

    FixedPriceBook(Map<String, String> prices) {
        this.prices = prices;
    }

    @Override
    public String priceFor(String product) {
        return prices.get(product);
    }

    @Override
    public int size() {
        return prices.size();
    }
}

final class CountingPriceBook extends PriceBookDelegate {
    private int lookupCount;

    CountingPriceBook(PriceBook delegatee) {
        super(delegatee);
    }

    @Override
    public String priceFor(String product) {
        lookupCount++;
        return "USD " + super.priceFor(product);
    }

    int lookupCount() {
        return lookupCount;
    }
}
