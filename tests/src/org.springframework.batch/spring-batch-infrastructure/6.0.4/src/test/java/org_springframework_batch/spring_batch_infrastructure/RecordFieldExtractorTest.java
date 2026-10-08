/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_batch.spring_batch_infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.file.transform.RecordFieldExtractor;

public class RecordFieldExtractorTest {
    @Test
    void extractsSelectedRecordComponentsInConfiguredOrder() {
        RecordFieldExtractor<Customer> extractor = new RecordFieldExtractor<>(Customer.class);
        extractor.setNames("active", "name");

        assertThat(extractor.extract(new Customer("Ada", 37, true))).containsExactly(true, "Ada");
    }

    public record Customer(String name, int age, boolean active) {}
}
