/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_puppycrawl_tools.checkstyle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import com.puppycrawl.tools.checkstyle.utils.TokenUtil;
import org.junit.jupiter.api.Test;

public class TokenUtilTest {
    @Test
    void resolvesTokenIdsFromPublicTokenTypeFields() {
        int classDefinitionId = TokenUtil.getTokenId("CLASS_DEF");

        assertThat(classDefinitionId).isEqualTo(TokenTypes.CLASS_DEF);
        assertThat(TokenUtil.getTokenName(classDefinitionId)).isEqualTo("CLASS_DEF");
        assertThat(TokenUtil.getAllTokenIds())
            .contains(TokenTypes.CLASS_DEF, TokenTypes.METHOD_DEF);
    }

    @Test
    void resolvesShortDescriptionFromTokenTypesResourceBundle() {
        String description = TokenUtil.getShortDescription("CLASS_DEF");

        assertThat(description).isNotBlank();
    }

    @Test
    void rejectsUnknownTokenNamesBeforeLoadingShortDescription() {
        assertThatThrownBy(() -> TokenUtil.getShortDescription("NOT_A_CHECKSTYLE_TOKEN"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("given name NOT_A_CHECKSTYLE_TOKEN");
    }
}
