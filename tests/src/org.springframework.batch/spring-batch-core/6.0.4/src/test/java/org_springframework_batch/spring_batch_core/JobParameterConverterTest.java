/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_batch.spring_batch_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.repository.persistence.JobParameter;
import org.springframework.batch.core.repository.persistence.converter.JobParameterConverter;

public class JobParameterConverterTest {
    @Test
    void convertsPersistenceParameterToDomainParameter() {
        JobParameter<Long> stored = new JobParameter<>("sequence", 42L, Long.class.getName(), false);
        JobParameterConverter converter = new JobParameterConverter();

        org.springframework.batch.core.job.parameters.JobParameter<Long> domain = converter.toJobParameter(stored);

        assertEquals("sequence", domain.name());
        assertEquals(42L, domain.value());
        assertEquals(Long.class, domain.type());
        assertFalse(domain.identifying());
        assertEquals(stored, converter.fromJobParameter(domain));
    }
}
