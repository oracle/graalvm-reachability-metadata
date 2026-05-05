/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pdfbox.pdfbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.StandardStructureTypes;
import org.junit.jupiter.api.Test;

public class StandardStructureTypesTest {
    @Test
    void typesContainsCommonStandardTaggedPdfStructureTypes() {
        List<String> types = StandardStructureTypes.types;

        assertThat(types)
                .contains(
                        StandardStructureTypes.DOCUMENT,
                        StandardStructureTypes.PART,
                        StandardStructureTypes.SECT,
                        StandardStructureTypes.P,
                        StandardStructureTypes.H1,
                        StandardStructureTypes.TABLE,
                        StandardStructureTypes.TR,
                        StandardStructureTypes.TH,
                        StandardStructureTypes.TD,
                        StandardStructureTypes.SPAN,
                        StandardStructureTypes.LINK,
                        StandardStructureTypes.Figure,
                        StandardStructureTypes.FORMULA,
                        StandardStructureTypes.FORM)
                .doesNotContainNull()
                .isSorted();
    }
}
