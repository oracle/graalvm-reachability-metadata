/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package postgresql;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.postgresql.jdbc.EscapedFunctions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises dynamic access paths owned by {@code org.postgresql.jdbc.EscapedFunctions}.
 */
@SuppressWarnings("deprecation")
public class EscapedFunctionsTest {

    @Test
    void getFunctionDiscoversLegacyTranslatorByName() {
        Method function = EscapedFunctions.getFunction("LcAsE");

        assertThat(function).isNotNull();
        assertThat(function.getName()).isEqualTo("sqllcase");
    }

    @Test
    void legacyEscapeFunctionTranslatesSingleArgumentCall() throws Exception {
        String translatedSql = EscapedFunctions.sqllcase(List.of("'PGJDBC'"));

        assertThat(translatedSql).isEqualTo("lower('PGJDBC')");
    }
}
