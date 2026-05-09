/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_doxia.doxia_module_fml;

import org.apache.maven.doxia.module.fml.model.Faqs;
import org.apache.maven.doxia.module.fml.model.Part;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FaqsTest {

    @Test
    void rejectsNullPartWithPartTypeMessage() {
        Faqs faqs = new Faqs();

        assertThatThrownBy(() -> faqs.addPart(null))
                .isInstanceOf(ClassCastException.class)
                .hasMessageContaining(Part.class.getName());
    }

    @Test
    void storesFaqMetadataAndParts() {
        Faqs faqs = new Faqs();
        Part part = new Part();

        faqs.setTitle("Build questions");
        faqs.setToplink(false);
        faqs.addPart(part);
        faqs.setModelEncoding("ISO-8859-1");

        assertThat(faqs.getTitle()).isEqualTo("Build questions");
        assertThat(faqs.isToplink()).isFalse();
        assertThat(faqs.getParts()).containsExactly(part);
        assertThat(faqs.getModelEncoding()).isEqualTo("ISO-8859-1");
        assertThat(faqs.toString()).contains("Build questions", "false");
    }
}
