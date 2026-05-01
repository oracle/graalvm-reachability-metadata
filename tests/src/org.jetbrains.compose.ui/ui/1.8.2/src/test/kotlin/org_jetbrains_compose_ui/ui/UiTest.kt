/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_compose_ui.ui

import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.coerceIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.substring
import androidx.compose.ui.text.withAnnotation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrain
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSatisfiedBy
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

public class UiTest {
    @Test
    fun alignmentResolvesDirectionalAndAbsolutePositions() {
        val childSize = IntSize(width = 20, height = 10)
        val containerSize = IntSize(width = 100, height = 50)

        assertThat(Alignment.TopStart.align(childSize, containerSize, LayoutDirection.Ltr))
            .isEqualTo(IntOffset(x = 0, y = 0))
        assertThat(Alignment.TopStart.align(childSize, containerSize, LayoutDirection.Rtl))
            .isEqualTo(IntOffset(x = 80, y = 0))
        assertThat(Alignment.BottomEnd.align(childSize, containerSize, LayoutDirection.Ltr))
            .isEqualTo(IntOffset(x = 80, y = 40))
        assertThat(Alignment.BottomEnd.align(childSize, containerSize, LayoutDirection.Rtl))
            .isEqualTo(IntOffset(x = 0, y = 40))

        val combinedAlignment = Alignment.End + Alignment.CenterVertically
        assertThat(combinedAlignment.align(childSize, containerSize, LayoutDirection.Ltr))
            .isEqualTo(IntOffset(x = 80, y = 20))
        assertThat(
            AbsoluteAlignment.Right.align(size = 20, space = 100, layoutDirection = LayoutDirection.Rtl)
        ).isEqualTo(80)
    }

    @Test
    fun customBiasAlignmentCanPlaceContentInsideAndOutsideBounds() {
        val childSize = IntSize(width = 40, height = 20)
        val containerSize = IntSize(width = 100, height = 60)

        val insideAlignment = BiasAlignment(horizontalBias = 0.5f, verticalBias = -0.5f)
        assertThat(insideAlignment.align(childSize, containerSize, LayoutDirection.Ltr))
            .isEqualTo(IntOffset(x = 45, y = 10))
        assertThat(insideAlignment.align(childSize, containerSize, LayoutDirection.Rtl))
            .isEqualTo(IntOffset(x = 15, y = 10))

        val overscanAlignment = BiasAlignment(horizontalBias = 1.5f, verticalBias = -1.5f)
        assertThat(overscanAlignment.align(childSize, containerSize, LayoutDirection.Ltr))
            .isEqualTo(IntOffset(x = 75, y = -10))
    }

    @Test
    fun modifierChainsPreserveOrderAndPredicateSemantics() {
        val outer = NamedModifierElement("outer")
        val middle = NamedModifierElement("middle")
        val inner = NamedModifierElement("inner")
        val chain = Modifier.then(outer).then(middle).then(Modifier).then(inner)

        val outsideInNames = chain.foldIn(emptyList<String>()) { names, element ->
            names + (element as NamedModifierElement).name
        }
        val insideOutNames = chain.foldOut(emptyList<String>()) { element, names ->
            names + (element as NamedModifierElement).name
        }

        assertThat(outsideInNames).containsExactly("outer", "middle", "inner")
        assertThat(insideOutNames).containsExactly("inner", "middle", "outer")
        assertThat(chain.any { it == middle }).isTrue()
        assertThat(chain.any { it == NamedModifierElement("missing") }).isFalse()
        assertThat(chain.all { it is NamedModifierElement }).isTrue()
    }

    @Test
    fun densityAndConstraintsConvertAndClampLayoutUnits() {
        val density = Density(density = 2f, fontScale = 1.5f)

        with(density) {
            assertThat(12.dp.toPx()).isEqualTo(24f)
            assertThat(2.5.dp.roundToPx()).isEqualTo(5)
            assertThat(18.sp.toPx()).isEqualTo(54f)
            assertThat(20.toDp()).isEqualTo(10.dp)
            assertThat(6.dp.toSp()).isEqualTo(4.sp)
        }

        val constraints = Constraints(minWidth = 10, maxWidth = 40, minHeight = 5, maxHeight = 25)
        assertThat(constraints.constrain(IntSize(width = 100, height = 0)))
            .isEqualTo(IntSize(width = 40, height = 5))
        assertThat(constraints.constrainWidth(7)).isEqualTo(10)
        assertThat(constraints.constrainHeight(30)).isEqualTo(25)
        assertThat(constraints.isSatisfiedBy(IntSize(width = 20, height = 20))).isTrue()
        assertThat(constraints.isSatisfiedBy(IntSize(width = 41, height = 20))).isFalse()

        val shifted = constraints.offset(horizontal = -15, vertical = 10)
        assertThat(shifted.minWidth).isEqualTo(0)
        assertThat(shifted.maxWidth).isEqualTo(25)
        assertThat(shifted.minHeight).isEqualTo(15)
        assertThat(shifted.maxHeight).isEqualTo(35)
    }

    @Test
    fun geometryPrimitivesSupportVectorMathAndRectangleOperations() {
        val vector = Offset(x = 3f, y = 4f)
        assertThat(vector.getDistance()).isEqualTo(5f)
        assertThat(vector + Offset(x = -1f, y = 2f)).isEqualTo(Offset(x = 2f, y = 6f))
        assertThat(-vector).isEqualTo(Offset(x = -3f, y = -4f))

        val moved = Rect(left = 0f, top = 0f, right = 10f, bottom = 8f).translate(vector)
        assertThat(moved).isEqualTo(Rect(left = 3f, top = 4f, right = 13f, bottom = 12f))
        assertThat(moved.width).isEqualTo(10f)
        assertThat(moved.height).isEqualTo(8f)
        assertThat(moved.contains(Offset(x = 12.9f, y = 11.9f))).isTrue()
        assertThat(moved.contains(Offset(x = 13f, y = 12f))).isFalse()

        val inflated = moved.inflate(delta = 2f)
        val intersection = inflated.intersect(Rect(left = 10f, top = 0f, right = 20f, bottom = 10f))
        assertThat(intersection).isEqualTo(Rect(left = 10f, top = 2f, right = 15f, bottom = 10f))
        assertThat(inflated.overlaps(Rect(left = 14.5f, top = 13.5f, right = 20f, bottom = 20f)))
            .isTrue()
        assertThat(Size(width = 12f, height = 8f).center).isEqualTo(Offset(x = 6f, y = 4f))
    }

    @Test
    fun colorsExposeArgbComponentsCompositionAndFallbacks() {
        val color = Color(red = 0x33, green = 0x66, blue = 0x99, alpha = 0xCC)
        assertThat(color.toArgb()).isEqualTo(0xCC336699.toInt())
        assertThat(color.red).isCloseTo(0x33 / 255f, within(0.001f))
        assertThat(color.green).isCloseTo(0x66 / 255f, within(0.001f))
        assertThat(color.blue).isCloseTo(0x99 / 255f, within(0.001f))
        assertThat(color.alpha).isCloseTo(0xCC / 255f, within(0.001f))

        val translucentRed = Color.Red.copy(alpha = 0.25f)
        val composited = translucentRed.compositeOver(Color.Blue)
        assertThat(translucentRed.alpha).isCloseTo(0.25f, within(0.01f))
        assertThat(composited.alpha).isCloseTo(1f, within(0.01f))
        assertThat(Color.Unspecified.isSpecified).isFalse()
        assertThat(Color.Unspecified.takeOrElse { Color.Green }).isEqualTo(Color.Green)
    }

    @Test
    fun annotatedStringsRetainStylesAnnotationsAndSubranges() {
        val annotated = buildAnnotatedString {
            append("Hello ")
            withAnnotation(tag = "kind", annotation = "framework") {
                withStyle(SpanStyle(color = Color.Blue, fontSize = 20.sp)) {
                    append("Compose")
                }
            }
            withStyle(ParagraphStyle(textAlign = TextAlign.Center, lineHeight = 24.sp)) {
                append(" UI")
            }
            addStringAnnotation(tag = "source", annotation = "manual", start = 0, end = length)
        }

        assertThat(annotated.text).isEqualTo("Hello Compose UI")
        assertThat(annotated.subSequence(TextRange(start = 6, end = 13)).text).isEqualTo("Compose")
        assertThat(annotated[6]).isEqualTo('C')
        assertThat(annotated.getStringAnnotations(tag = "kind", start = 6, end = 7).single().item)
            .isEqualTo("framework")
        assertThat(annotated.hasStringAnnotations(tag = "kind", start = 0, end = 5)).isFalse()
        assertThat(annotated.getStringAnnotations(tag = "source", start = 0, end = annotated.length))
            .hasSize(1)

        val span = annotated.spanStyles.single()
        assertThat(span.start).isEqualTo(6)
        assertThat(span.end).isEqualTo(13)
        assertThat(span.item.color).isEqualTo(Color.Blue)
        assertThat(span.item.fontSize).isEqualTo(20.sp)

        val paragraph = annotated.paragraphStyles.single()
        assertThat(paragraph.start).isEqualTo(13)
        assertThat(paragraph.end).isEqualTo(16)
        assertThat(paragraph.item.textAlign).isEqualTo(TextAlign.Center)
    }

    @Test
    fun semanticsConfigurationStoresAccessibilityPropertiesAndActions() {
        val semantics = SemanticsConfiguration()
        var clickCount = 0

        semantics.contentDescription = "Submit order"
        semantics.stateDescription = "Ready"
        semantics.role = Role.Button
        semantics.progressBarRangeInfo = ProgressBarRangeInfo(current = 0.4f, range = 0f..1f, steps = 4)
        semantics.horizontalScrollAxisRange = ScrollAxisRange(
            value = { 12f },
            maxValue = { 48f },
            reverseScrolling = true
        )
        semantics.onClick(label = "Submit") {
            clickCount += 1
            true
        }

        assertThat(semantics[SemanticsProperties.ContentDescription]).containsExactly("Submit order")
        assertThat(semantics[SemanticsProperties.StateDescription]).isEqualTo("Ready")
        assertThat(semantics[SemanticsProperties.Role]).isEqualTo(Role.Button)

        val progress = semantics[SemanticsProperties.ProgressBarRangeInfo]
        assertThat(progress.current).isCloseTo(0.4f, within(0.001f))
        assertThat(progress.range.start).isEqualTo(0f)
        assertThat(progress.range.endInclusive).isEqualTo(1f)
        assertThat(progress.steps).isEqualTo(4)

        val scrollRange = semantics[SemanticsProperties.HorizontalScrollAxisRange]
        assertThat(scrollRange.value()).isEqualTo(12f)
        assertThat(scrollRange.maxValue()).isEqualTo(48f)
        assertThat(scrollRange.reverseScrolling).isTrue()

        val onClick = semantics[SemanticsActions.OnClick]
        assertThat(onClick.label).isEqualTo("Submit")
        assertThat(onClick.action?.invoke()).isTrue()
        assertThat(clickCount).isEqualTo(1)
    }

    @Test
    fun textRangesSupportReversedBoundsContainmentAndClamping() {
        val reversed = TextRange(start = 8, end = 3)

        assertThat(reversed.reversed).isTrue()
        assertThat(reversed.collapsed).isFalse()
        assertThat(reversed.min).isEqualTo(3)
        assertThat(reversed.max).isEqualTo(8)
        assertThat(reversed.length).isEqualTo(5)
        assertThat(5 in reversed).isTrue()
        assertThat(8 in reversed).isFalse()
        assertThat(reversed.contains(TextRange(start = 4, end = 7))).isTrue()
        assertThat(reversed.intersects(TextRange(start = 7, end = 10))).isTrue()
        assertThat("0123456789".substring(reversed)).isEqualTo("34567")
        assertThat(reversed.coerceIn(minimumValue = 4, maximumValue = 6))
            .isEqualTo(TextRange(start = 6, end = 4))

        val concatenated = AnnotatedString("Compose") + AnnotatedString(" UI")
        assertThat(concatenated.text).isEqualTo("Compose UI")
    }

    private data class NamedModifierElement(val name: String) : Modifier.Element
}
