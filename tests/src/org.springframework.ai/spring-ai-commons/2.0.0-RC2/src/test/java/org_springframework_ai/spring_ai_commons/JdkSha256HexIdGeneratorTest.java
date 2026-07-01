/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ai.spring_ai_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.ai.document.id.JdkSha256HexIdGenerator;

public class JdkSha256HexIdGeneratorTest {
    @Test
    void generateIdSerializesContentsAndReturnsStableUuid() {
        JdkSha256HexIdGenerator idGenerator = new JdkSha256HexIdGenerator();

        String firstId = idGenerator.generateId("spring", "ai");
        String repeatedId = idGenerator.generateId("spring", "ai");
        String differentId = idGenerator.generateId("spring", "ai", "commons");

        assertThat(UUID.fromString(firstId).toString()).isEqualTo(firstId);
        assertThat(repeatedId).isEqualTo(firstId);
        assertThat(differentId).isNotEqualTo(firstId);
    }
}
