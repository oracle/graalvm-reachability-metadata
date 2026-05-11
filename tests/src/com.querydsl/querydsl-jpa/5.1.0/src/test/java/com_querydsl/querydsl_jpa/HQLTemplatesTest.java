/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_jpa;

import java.math.BigDecimal;
import java.util.List;

import com.querydsl.core.types.Ops;
import com.querydsl.jpa.HQLTemplates;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HQLTemplatesTest {
    @Test
    void defaultTemplatesUseHibernateQueryHandlerWhenHibernateIsAvailable() {
        HQLTemplates templates = HQLTemplates.DEFAULT;

        assertThat(templates.getQueryHandler().getClass().getName())
                .isEqualTo("com.querydsl.jpa.HibernateHandler");
        assertThat(templates.getExistsProjection()).isEqualTo("1");
        assertThat(templates.getTypeForCast(BigDecimal.class)).isEqualTo("big_decimal");
        assertThat(templates.isWithForOn()).isTrue();
        assertThat(templates.isCaseWithLiterals()).isTrue();
        assertThat(templates.wrapElements(Ops.QuantOps.ALL)).isTrue();
        assertThat(templates.wrapConstant(List.of("value"))).isTrue();
    }
}
