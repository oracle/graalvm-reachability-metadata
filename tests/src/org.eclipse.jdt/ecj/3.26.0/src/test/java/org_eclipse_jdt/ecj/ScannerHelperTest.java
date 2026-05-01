/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jdt.ecj;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.parser.ScannerHelper;
import org.junit.jupiter.api.Test;

public class ScannerHelperTest {
    private static final int LATIN_CAPITAL_A_WITH_GRAVE = '\u00C0';

    @Test
    void loadsUnicodeIdentifierTablesForSupportedComplianceLevels() {
        assertIdentifierCharacterRecognizedForComplianceLevel(ClassFileConstants.JDK1_6);
        assertIdentifierCharacterRecognizedForComplianceLevel(ClassFileConstants.JDK1_7);
        assertIdentifierCharacterRecognizedForComplianceLevel(ClassFileConstants.JDK1_8);
        assertIdentifierCharacterRecognizedForComplianceLevel(ClassFileConstants.JDK9);
        assertIdentifierCharacterRecognizedForComplianceLevel(ClassFileConstants.JDK11);
        assertIdentifierCharacterRecognizedForComplianceLevel(ClassFileConstants.JDK12);
        assertIdentifierCharacterRecognizedForComplianceLevel(ClassFileConstants.JDK13);
        assertIdentifierCharacterRecognizedForComplianceLevel(ClassFileConstants.JDK15);
    }

    private static void assertIdentifierCharacterRecognizedForComplianceLevel(long complianceLevel) {
        assertThat(ScannerHelper.isJavaIdentifierStart(complianceLevel, LATIN_CAPITAL_A_WITH_GRAVE)).isTrue();
        assertThat(ScannerHelper.isJavaIdentifierPart(complianceLevel, LATIN_CAPITAL_A_WITH_GRAVE)).isTrue();
    }
}
