/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_thisptr.jackson_jq;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.thisptr.jackson.jq.BuiltinFunctionLoader;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Versions;
import org.junit.jupiter.api.Test;

public class BuiltinFunctionLoaderTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void loadsBuiltInJqFunctionsFromBundledConfigurationResource() throws Exception {
        Scope scope = Scope.newEmptyScope();
        BuiltinFunctionLoader.getInstance().loadFunctions(Versions.JQ_1_6, scope);

        assertThat(scope.getFunction("map", 1)).isNotNull();
        assertThat(scope.getFunction("paths", 0)).isNotNull();

        JsonQuery query = JsonQuery.compile("map(. + 1)", Versions.JQ_1_6);
        List<JsonNode> outputs = new ArrayList<>();
        query.apply(scope, mapper.readTree("[1, 2, 3]"), outputs::add);

        assertThat(outputs).containsExactly(mapper.readTree("[2, 3, 4]"));
    }
}
