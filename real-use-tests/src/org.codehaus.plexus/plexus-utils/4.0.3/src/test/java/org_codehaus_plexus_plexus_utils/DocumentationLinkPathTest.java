/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus_plexus_utils;

import org.codehaus.plexus.util.PathTool;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Resolves documentation links with Plexus path utilities.
 *
 * A documentation publisher writes guides and API references into separate
 * output directories and generates links that work in both web pages and
 * downloadable files.
 */
class DocumentationLinkPathTest {

    @Test
    void buildsRelativeLinksForPublishedDocumentation() {
        String guideDirectory = "site/docs/guide";
        String referencePage = "site/docs/api/reference.html";
        String logoFile = "site/assets/logo.svg";

        String guidePage = guideDirectory + "/index.html";
        String guidePageDirectory = PathTool.getDirectoryComponent(guidePage);
        String referenceLink = PathTool.getRelativeWebPath(guidePageDirectory, referencePage);
        String logoLink = PathTool.getRelativeFilePath(guidePageDirectory, logoFile);

        assertThat(guidePageDirectory).isEqualTo("site/docs/guide");
        assertThat(referenceLink).isEqualTo("../api/reference.html");
        assertThat(logoLink).isEqualTo("../../assets/logo.svg");
    }
}
