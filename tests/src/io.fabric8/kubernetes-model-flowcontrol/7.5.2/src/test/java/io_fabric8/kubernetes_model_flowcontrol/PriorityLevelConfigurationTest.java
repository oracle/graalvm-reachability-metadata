/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_model_flowcontrol;

import io.fabric8.kubernetes.api.model.flowcontrol.v1.LimitedPriorityLevelConfiguration;
import io.fabric8.kubernetes.api.model.flowcontrol.v1.PriorityLevelConfiguration;
import io.fabric8.kubernetes.api.model.flowcontrol.v1.PriorityLevelConfigurationBuilder;
import io.fabric8.kubernetes.api.model.flowcontrol.v1.PriorityLevelConfigurationList;
import io.fabric8.kubernetes.api.model.flowcontrol.v1.PriorityLevelConfigurationListBuilder;
import io.fabric8.kubernetes.api.model.flowcontrol.v1.QueuingConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PriorityLevelConfigurationTest {
    @Test
    void buildsLimitedPriorityLevelAndEditsQueueCapacity() {
        PriorityLevelConfiguration level = new PriorityLevelConfigurationBuilder()
                .withNewMetadata().withName("workloads").endMetadata()
                .withNewSpec()
                    .withType("Limited")
                    .withNewLimited()
                        .withNominalConcurrencyShares(30)
                        .withLendablePercent(20)
                        .withBorrowingLimitPercent(50)
                        .withNewLimitResponse()
                            .withType("Queue")
                            .withNewQueuing()
                                .withQueues(64).withHandSize(8).withQueueLengthLimit(100)
                            .endQueuing()
                        .endLimitResponse()
                    .endLimited()
                .endSpec()
                .withNewStatus()
                    .addNewCondition()
                        .withType("ConcurrencyShared").withStatus("True")
                        .withReason("Configured").withMessage("Concurrency is available")
                    .endCondition()
                .endStatus()
                .build();
        PriorityLevelConfiguration edited = level.toBuilder()
                .editSpec()
                    .editLimited()
                        .editLimitResponse()
                            .editQueuing().withQueueLengthLimit(150).endQueuing()
                        .endLimitResponse()
                    .endLimited()
                .endSpec()
                .build();

        LimitedPriorityLevelConfiguration limited = edited.getSpec().getLimited();
        QueuingConfiguration queue = limited.getLimitResponse().getQueuing();
        assertThat(edited.getApiVersion()).isEqualTo("flowcontrol.apiserver.k8s.io/v1");
        assertThat(edited.getKind()).isEqualTo("PriorityLevelConfiguration");
        assertThat(edited.getFullResourceName())
                .isEqualTo("prioritylevelconfigurations.flowcontrol.apiserver.k8s.io");
        assertThat(edited.getMetadata().getName()).isEqualTo("workloads");
        assertThat(edited.getSpec().getType()).isEqualTo("Limited");
        assertThat(limited.getNominalConcurrencyShares()).isEqualTo(30);
        assertThat(limited.getLendablePercent()).isEqualTo(20);
        assertThat(limited.getBorrowingLimitPercent()).isEqualTo(50);
        assertThat(limited.getLimitResponse().getType()).isEqualTo("Queue");
        assertThat(queue.getQueues()).isEqualTo(64);
        assertThat(queue.getHandSize()).isEqualTo(8);
        assertThat(queue.getQueueLengthLimit()).isEqualTo(150);
        assertThat(level.getSpec().getLimited().getLimitResponse().getQueuing().getQueueLengthLimit())
                .isEqualTo(100);
        assertThat(edited.getStatus().getConditions().get(0).getReason()).isEqualTo("Configured");
    }

    @Test
    void buildsExemptAndRejectingLevelsAndEditsListItems() {
        PriorityLevelConfiguration exempt = new PriorityLevelConfigurationBuilder()
                .withNewMetadata().withName("system").endMetadata()
                .withNewSpec()
                    .withType("Exempt")
                    .withNewExempt().withNominalConcurrencyShares(10).withLendablePercent(0).endExempt()
                .endSpec()
                .addToAdditionalProperties("owner", "control-plane")
                .build();
        PriorityLevelConfiguration rejecting = new PriorityLevelConfigurationBuilder()
                .withNewMetadata().withName("batch").endMetadata()
                .withNewSpec()
                    .withType("Limited")
                    .withNewLimited()
                        .withNominalConcurrencyShares(5)
                        .withNewLimitResponse().withType("Reject").endLimitResponse()
                    .endLimited()
                .endSpec()
                .build();
        PriorityLevelConfigurationList list = new PriorityLevelConfigurationListBuilder()
                .withNewMetadata(null, null, "80", null)
                .addToItems(exempt, rejecting)
                .build();
        PriorityLevelConfigurationList edited = list.edit()
                .editMatchingItem(item -> "batch".equals(item.buildMetadata().getName()))
                    .editSpec().editLimited().withNominalConcurrencyShares(8).endLimited().endSpec()
                .endItem()
                .build();

        assertThat(exempt.getSpec().getExempt().getNominalConcurrencyShares()).isEqualTo(10);
        assertThat(exempt.getSpec().getExempt().getLendablePercent()).isZero();
        assertThat(exempt.getSpec().getLimited()).isNull();
        assertThat(exempt.getAdditionalProperties()).containsEntry("owner", "control-plane");
        assertThat(rejecting.getSpec().getLimited().getLimitResponse().getType()).isEqualTo("Reject");
        assertThat(rejecting.getSpec().getLimited().getLimitResponse().getQueuing()).isNull();
        assertThat(edited.getItems()).hasSize(2);
        assertThat(edited.getItems().get(0)).isEqualTo(exempt);
        assertThat(edited.getItems().get(1).getSpec().getLimited().getNominalConcurrencyShares()).isEqualTo(8);
        assertThat(rejecting.getSpec().getLimited().getNominalConcurrencyShares()).isEqualTo(5);
        assertThat(edited.getMetadata().getResourceVersion()).isEqualTo("80");
        assertThat(edited.getKind()).isEqualTo("PriorityLevelConfigurationList");
    }
}
