/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DetailPanelTest {

    @Test
    void initializingDetailPanelLoadsItsOwnClassForLoggerInitialization() throws Exception {
        Class<?> detailPanelClass = Class.forName(
                "org.apache.log4j.chainsaw.DetailPanel",
                true,
                DetailPanelTest.class.getClassLoader()
        );

        assertThat(detailPanelClass.getName()).isEqualTo("org.apache.log4j.chainsaw.DetailPanel");
    }
}
