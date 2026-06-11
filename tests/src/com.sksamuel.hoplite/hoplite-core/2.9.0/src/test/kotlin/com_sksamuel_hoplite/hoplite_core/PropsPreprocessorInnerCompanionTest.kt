/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sksamuel_hoplite.hoplite_core

import com.sksamuel.hoplite.DecoderContext
import com.sksamuel.hoplite.Pos
import com.sksamuel.hoplite.StringNode
import com.sksamuel.hoplite.decoder.DotPath
import com.sksamuel.hoplite.preprocessor.PropsPreprocessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@Suppress("DEPRECATION")
public class PropsPreprocessorInnerCompanionTest {
    @Test
    fun loadsPropertiesFromClasspathResourcePath(): Unit {
        val preprocessor: PropsPreprocessor = PropsPreprocessor("/META-INF/MANIFEST.MF")
        val unresolvedValue: String = "value=\${definitely.not.a.manifest.property}"
        val node: StringNode = StringNode(unresolvedValue, Pos.NoPos, DotPath.root)

        val processed: StringNode = preprocessor.process(node, DecoderContext.zero)
            .getUnsafe() as StringNode

        assertThat(processed.value).isEqualTo(unresolvedValue)
    }
}
