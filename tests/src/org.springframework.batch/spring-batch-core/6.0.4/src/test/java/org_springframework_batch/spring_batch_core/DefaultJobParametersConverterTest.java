/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_batch.spring_batch_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.LocalDate;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.converter.DefaultJobParametersConverter;
import org.springframework.batch.core.job.parameters.JobParameter;
import org.springframework.batch.core.job.parameters.JobParameters;

public class DefaultJobParametersConverterTest {
    @Test
    void convertsTypedCommandLineParameter() {
        Properties properties = new Properties();
        properties.setProperty("businessDate", "2026-03-01,java.time.LocalDate,false");
        DefaultJobParametersConverter converter = new DefaultJobParametersConverter();

        JobParameters parameters = converter.getJobParameters(properties);
        JobParameter<?> parameter = parameters.getParameter("businessDate");

        assertEquals(LocalDate.of(2026, 3, 1), parameter.value());
        assertEquals(LocalDate.class, parameter.type());
        assertFalse(parameter.identifying());
        assertEquals(properties, converter.getProperties(parameters));
    }
}
