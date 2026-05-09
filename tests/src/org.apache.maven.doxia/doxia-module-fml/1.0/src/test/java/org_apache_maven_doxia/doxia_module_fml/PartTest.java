/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_doxia.doxia_module_fml;

import org.apache.maven.doxia.module.fml.model.Faq;
import org.apache.maven.doxia.module.fml.model.Part;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PartTest {

    @Test
    void rejectsNullFaqWithFaqTypeMessage() {
        Part part = new Part();

        assertThatThrownBy(() -> part.addFaq(null))
                .isInstanceOf(ClassCastException.class)
                .hasMessageContaining(Faq.class.getName());
    }

    @Test
    void storesMetadataAndFaqs() {
        Part part = new Part();
        Faq faq = new Faq();
        faq.setId("build");
        faq.setQuestion("How do I build the site?");
        faq.setAnswer("Run the Maven site lifecycle.");

        part.setId("usage");
        part.setTitle("Usage questions");
        part.addFaq(faq);
        part.setModelEncoding("ISO-8859-1");

        assertThat(part.getId()).isEqualTo("usage");
        assertThat(part.getTitle()).isEqualTo("Usage questions");
        assertThat(part.getFaqs()).containsExactly(faq);
        assertThat(part.getModelEncoding()).isEqualTo("ISO-8859-1");
        assertThat(part.toString()).contains("usage", "Usage questions", "How do I build the site?");

        part.removeFaq(faq);

        assertThat(part.getFaqs()).isEmpty();
    }
}
