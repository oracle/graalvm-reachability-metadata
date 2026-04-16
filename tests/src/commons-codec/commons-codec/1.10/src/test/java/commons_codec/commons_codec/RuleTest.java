/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_codec.commons_codec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.apache.commons.codec.language.bm.NameType;
import org.apache.commons.codec.language.bm.Rule;
import org.apache.commons.codec.language.bm.RuleType;
import org.junit.jupiter.api.Test;

class RuleTest {
    @Test
    void loadsTopLevelAndIncludedRuleResources() {
        Map<String, List<Rule>> germanExactRules = Rule.getInstanceMap(NameType.GENERIC, RuleType.EXACT, "german");

        assertThat(germanExactRules).isNotEmpty().containsKey("E");

        Rule includedRule = germanExactRules.get("E").stream()
                .filter(rule -> rule.getPattern().equals("EE"))
                .findFirst()
                .orElseThrow();

        assertThat(includedRule.patternAndContextMatches("EE", 0)).isTrue();
        assertThat(includedRule.patternAndContextMatches("E", 0)).isFalse();
    }
}
