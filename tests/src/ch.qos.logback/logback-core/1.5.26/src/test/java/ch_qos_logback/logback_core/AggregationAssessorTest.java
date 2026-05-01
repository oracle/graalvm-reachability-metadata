/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.joran.spi.DefaultNestedComponentRegistry;
import ch.qos.logback.core.joran.util.AggregationAssessor;
import ch.qos.logback.core.joran.util.beans.BeanDescriptionCache;
import ch.qos.logback.core.model.Model;
import ch.qos.logback.core.model.processor.ModelInterpretationContext;
import ch.qos.logback.core.util.AggregationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AggregationAssessorTest {
    private AggregationAssessor assessor;

    @BeforeEach
    void setUp() {
        BeanDescriptionCache beanDescriptionCache = new BeanDescriptionCache(new ContextBase());
        assessor = new AggregationAssessor(beanDescriptionCache, ModelInterpretationContext.class);
    }

    @Test
    void implicitRulesReturnConcreteSetterTypeThatCanBeInstantiated() {
        AggregationType aggregationType = assessor.computeAggregationType("topModel");

        Class<?> modelType = assessor.getClassNameViaImplicitRules(
                "topModel", aggregationType, new DefaultNestedComponentRegistry());

        assertThat(aggregationType).isEqualTo(AggregationType.AS_COMPLEX_PROPERTY);
        assertThat(modelType).isEqualTo(Model.class);
    }
}
