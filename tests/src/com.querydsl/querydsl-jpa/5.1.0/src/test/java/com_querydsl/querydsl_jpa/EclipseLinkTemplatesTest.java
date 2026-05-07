/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_jpa;

import com.querydsl.jpa.EclipseLinkTemplates;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EclipseLinkTemplatesTest {
    @Test
    void defaultTemplatesUseEclipseLinkQueryHandlerWhenEclipseLinkIsAvailable() {
        EclipseLinkTemplates templates = EclipseLinkTemplates.DEFAULT;

        assertThat(templates.getQueryHandler().getClass().getName())
                .isEqualTo("com.querydsl.jpa.EclipseLinkHandler");
        assertThat(templates.isPathInEntitiesSupported()).isFalse();
        assertThat(templates.getTypeForCast(Integer.class)).isEqualTo("integer");
    }
}
