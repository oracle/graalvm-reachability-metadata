/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_yaml_snakeyaml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.reader.StreamReader;
import org.yaml.snakeyaml.scanner.Scanner;
import org.yaml.snakeyaml.scanner.ScannerImpl;
import org.yaml.snakeyaml.tokens.AliasToken;
import org.yaml.snakeyaml.tokens.AnchorToken;
import org.yaml.snakeyaml.tokens.ScalarToken;
import org.yaml.snakeyaml.tokens.Token;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises SnakeYAML's lexical-scanning subsystem for indexing deployment manifests in an editor.
public class DeploymentManifestSyntaxIndexTest {
    @Test
    @Timeout(60)
    public void indexesManifestSyntaxWithoutConstructingApplicationObjects() {
        String manifestText = """
                service:
                  name: billing-api
                  regions: [us-east-1, eu-west-1]
                rollout: &defaults
                  maxUnavailable: 1
                canary:
                  <<: *defaults
                """;

        Scanner scanner = new ScannerImpl(new StreamReader(manifestText), new LoaderOptions());
        List<Token> tokens = new ArrayList<>();
        while (!scanner.checkToken(Token.ID.StreamEnd)) {
            tokens.add(scanner.getToken());
        }
        tokens.add(scanner.getToken());

        List<String> scalarValues = tokens.stream()
                .filter(ScalarToken.class::isInstance)
                .map(ScalarToken.class::cast)
                .map(ScalarToken::getValue)
                .toList();
        assertEquals(
                List.of("service", "name", "billing-api", "regions", "us-east-1", "eu-west-1",
                        "rollout", "maxUnavailable", "1", "canary", "<<"),
                scalarValues);

        AnchorToken anchor = tokens.stream()
                .filter(AnchorToken.class::isInstance)
                .map(AnchorToken.class::cast)
                .findFirst()
                .orElseThrow();
        AliasToken alias = tokens.stream()
                .filter(AliasToken.class::isInstance)
                .map(AliasToken.class::cast)
                .findFirst()
                .orElseThrow();

        assertEquals("defaults", anchor.getValue());
        assertEquals("defaults", alias.getValue());
        assertEquals(Token.ID.StreamStart, tokens.get(0).getTokenId());
        assertEquals(Token.ID.StreamEnd, tokens.get(tokens.size() - 1).getTokenId());

        Mark aliasStart = alias.getStartMark();
        assertEquals(6, aliasStart.getLine());
        assertTrue(aliasStart.getColumn() > 0);
    }
}
