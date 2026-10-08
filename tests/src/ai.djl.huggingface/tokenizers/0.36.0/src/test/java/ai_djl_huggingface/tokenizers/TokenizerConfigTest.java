/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ai_djl_huggingface.tokenizers;

import static org.assertj.core.api.Assertions.assertThat;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public class TokenizerConfigTest {

    private static final String TOKENIZER_CONFIG_JSON = """
            {
              "tokenizer_class": "BertTokenizer",
              "model_max_length": 4,
              "strip_accents": true,
              "clean_up_tokenization_spaces": true,
              "add_prefix_space": false,
              "bos_token": "[CLS]",
              "eos_token": "[SEP]",
              "unk_token": "[UNK]",
              "sep_token": "[SEP]",
              "pad_token": "[PAD]",
              "cls_token": "[CLS]"
            }
            """;

    @Test
    void loadsTokenizerConfigurationThroughTheBuilder() throws Exception {
        Path tokenizerDirectory = Files.createTempDirectory("djl-tokenizer-config");
        Path tokenizerPath = tokenizerDirectory.resolve("tokenizer.json");
        Path configPath = tokenizerDirectory.resolve("tokenizer_config.json");
        Files.writeString(tokenizerPath, TokenizersTest.TOKENIZER_JSON);
        Files.writeString(configPath, TOKENIZER_CONFIG_JSON);

        HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.builder()
                .optTokenizerPath(tokenizerPath)
                .optTokenizerConfigPath(configPath.toString())
                .optTruncation(true)
                .optPadToMaxLength()
                .build();
        try {
            Encoding encoding = tokenizer.encode("hello");

            assertThat(tokenizer.getMaxLength()).isEqualTo(4);
            assertThat(tokenizer.getTruncation()).isEqualTo("LONGEST_FIRST");
            assertThat(tokenizer.getPadding()).isEqualTo("MAX_LENGTH");
            assertThat(encoding.getTokens()).containsExactly("[CLS]", "hello", "[SEP]", "[PAD]");
            assertThat(encoding.getIds()).containsExactly(2L, 4L, 3L, 0L);
            assertThat(encoding.getAttentionMask()).containsExactly(1L, 1L, 1L, 0L);
        } finally {
            tokenizer.close();
            Files.deleteIfExists(configPath);
            Files.deleteIfExists(tokenizerPath);
            Files.deleteIfExists(tokenizerDirectory);
        }
    }
}
