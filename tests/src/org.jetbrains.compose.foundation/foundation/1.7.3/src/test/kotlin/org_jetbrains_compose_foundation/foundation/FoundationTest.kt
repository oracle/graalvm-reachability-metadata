/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_compose_foundation.foundation

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.geometry.Offset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class FoundationTest {
    @Test
    fun test() {
        println("This is just a placeholder, implement your test")
    }

    @Test
    fun interactionPairsRetainTheirOriginatingInteractionInstances() {
        val press = PressInteraction.Press(Offset(10f, 20f))
        val release = PressInteraction.Release(press)
        val pressCancel = PressInteraction.Cancel(press)
        val dragStart = DragInteraction.Start()
        val dragStop = DragInteraction.Stop(dragStart)
        val dragCancel = DragInteraction.Cancel(dragStart)
        val focus = FocusInteraction.Focus()
        val unfocus = FocusInteraction.Unfocus(focus)
        val hoverEnter = HoverInteraction.Enter()
        val hoverExit = HoverInteraction.Exit(hoverEnter)

        assertThat(press.pressPosition).isEqualTo(Offset(10f, 20f))
        assertThat(release.press).isSameAs(press)
        assertThat(pressCancel.press).isSameAs(press)
        assertThat(dragStop.start).isSameAs(dragStart)
        assertThat(dragCancel.start).isSameAs(dragStart)
        assertThat(unfocus.focus).isSameAs(focus)
        assertThat(hoverExit.enter).isSameAs(hoverEnter)
    }

    @Test
    fun mutableInteractionSourceAcceptsSequentialInteractionEvents() {
        val source = MutableInteractionSource()
        val press = PressInteraction.Press(Offset.Zero)
        val release = PressInteraction.Release(press)
        val dragStart = DragInteraction.Start()
        val dragStop = DragInteraction.Stop(dragStart)
        val focus = FocusInteraction.Focus()
        val unfocus = FocusInteraction.Unfocus(focus)

        assertThat(source.tryEmit(press)).isTrue()
        assertThat(source.tryEmit(release)).isTrue()
        assertThat(source.tryEmit(dragStart)).isTrue()
        assertThat(source.tryEmit(dragStop)).isTrue()
        assertThat(source.tryEmit(focus)).isTrue()
        assertThat(source.tryEmit(unfocus)).isTrue()
    }
}
