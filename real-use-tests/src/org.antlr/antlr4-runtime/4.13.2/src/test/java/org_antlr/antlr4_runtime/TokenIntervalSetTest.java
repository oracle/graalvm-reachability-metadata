/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_antlr.antlr4_runtime;

import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.VocabularyImpl;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises ANTLR's token interval-set subsystem to classify commands in an API gateway.
public class TokenIntervalSetTest {

    private static final int DEPLOY = 1;
    private static final int SCALE = 2;
    private static final int ROLLBACK = 3;
    private static final int SERVICE_NAME = 4;
    private static final int REPLICA_COUNT = 5;
    private static final int COMMENT = 6;

    private static final Vocabulary VOCABULARY = new VocabularyImpl(
            new String[]{null, "'deploy'", "'scale'", "'rollback'", null, null, null},
            new String[]{null, "DEPLOY", "SCALE", "ROLLBACK", "SERVICE_NAME", "REPLICA_COUNT", "COMMENT"});

    @Test
    @Timeout(60)
    public void classifiesGatewayTokensWithRangesAndSetOperations() {
        IntervalSet commandKeywords = new IntervalSet(DEPLOY, SCALE);
        commandKeywords.add(ROLLBACK);
        IntervalSet commandArguments = new IntervalSet(SERVICE_NAME, REPLICA_COUNT);
        IntervalSet ignoredTokens = IntervalSet.of(COMMENT);

        IntervalSet accepted = commandKeywords.or(commandArguments);
        IntervalSet requiredArguments = accepted.subtract(commandKeywords);
        IntervalSet unexpected = accepted.complement(DEPLOY, COMMENT);

        assertEquals(List.of(DEPLOY, SCALE, ROLLBACK, SERVICE_NAME, REPLICA_COUNT), accepted.toList());
        assertEquals(List.of(SERVICE_NAME, REPLICA_COUNT), requiredArguments.toList());
        assertEquals(List.of(COMMENT), unexpected.toList());
        assertEquals(List.of("'deploy'", "'scale'", "'rollback'"), tokenNames(commandKeywords));
        assertEquals("{'deploy', 'scale', 'rollback'}", commandKeywords.toString(VOCABULARY));
        assertEquals(5, accepted.size());
        assertEquals(REPLICA_COUNT, accepted.getMaxElement());
        assertTrue(accepted.contains(SERVICE_NAME));
        assertFalse(accepted.contains(COMMENT));
        assertTrue(ignoredTokens.contains(COMMENT));
    }

    private static List<String> tokenNames(IntervalSet tokenTypes) {
        return tokenTypes.toList().stream()
                .map(VOCABULARY::getDisplayName)
                .toList();
    }
}
