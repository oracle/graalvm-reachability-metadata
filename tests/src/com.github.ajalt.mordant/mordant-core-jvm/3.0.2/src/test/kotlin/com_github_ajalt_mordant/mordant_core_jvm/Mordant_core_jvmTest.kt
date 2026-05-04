/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ajalt_mordant.mordant_core_jvm

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.BorderType
import com.github.ajalt.mordant.rendering.OverflowWrap
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.rendering.Whitespace
import com.github.ajalt.mordant.table.Borders
import com.github.ajalt.mordant.table.ColumnWidth
import com.github.ajalt.mordant.table.contentToCsv
import com.github.ajalt.mordant.table.horizontalLayout
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.table.verticalLayout
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import com.github.ajalt.mordant.terminal.YesNoPrompt
import com.github.ajalt.mordant.terminal.outputAsHtml
import com.github.ajalt.mordant.terminal.prompt
import com.github.ajalt.mordant.widgets.HorizontalRule
import com.github.ajalt.mordant.widgets.OrderedList
import com.github.ajalt.mordant.widgets.Padding
import com.github.ajalt.mordant.widgets.Panel
import com.github.ajalt.mordant.widgets.ProgressBar
import com.github.ajalt.mordant.widgets.Spinner
import com.github.ajalt.mordant.widgets.Text
import com.github.ajalt.mordant.widgets.UnorderedList
import com.github.ajalt.mordant.widgets.Viewport
import com.github.ajalt.mordant.widgets.definitionList
import com.github.ajalt.mordant.widgets.withPadding
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

public class Mordant_core_jvmTest {
    @Test
    fun terminalRecorderCapturesStyledStdoutStderrAndHtml() {
        val recorder: TerminalRecorder = TerminalRecorder(
            ansiLevel = AnsiLevel.ANSI16,
            width = 50,
            height = 12,
            hyperlinks = true,
            outputInteractive = true,
            inputInteractive = true,
        )
        val terminal: Terminal = Terminal(
            ansiLevel = AnsiLevel.ANSI16,
            width = 50,
            height = 12,
            hyperlinks = true,
            interactive = true,
            terminalInterface = recorder,
        )
        val styledStatus: String = (TextColors.red + TextStyles.bold)("failed")

        terminal.println("Status: $styledStatus")
        terminal.print("stderr line", stderr = true)

        assertThat(recorder.stdout()).contains("Status:", "failed", "\u001B[")
        assertThat(recorder.stderr()).isEqualTo("stderr line")
        assertThat(recorder.output()).contains("Status:", "stderr line")

        val html: String = recorder.outputAsHtml(
            includeBodyTag = false,
            includeCodeTag = false,
            backgroundColor = null,
        )
        assertThat(html).contains("<pre", "Status:", "failed")
        assertThat(html).contains("color:").contains("font-weight: bold")
    }

    @Test
    fun ansiNoneRenderingStripsStyleControlSequencesButKeepsText() {
        val terminal: Terminal = Terminal(
            ansiLevel = AnsiLevel.NONE,
            width = 40,
            height = 8,
            terminalInterface = TerminalRecorder(ansiLevel = AnsiLevel.NONE, width = 40, height = 8),
        )
        val style = (TextColors.green on TextColors.blue) + TextStyles.underline + TextStyles.italic

        val rendered: String = terminal.render("plain ${style("message")}")

        assertThat(rendered).isEqualTo("plain message")
        assertThat(rendered).doesNotContain("\u001B[")
        assertThat(style.color).isNotNull()
        assertThat(style.bgColor).isNotNull()
        assertThat(style.underline).isTrue()
        assertThat(style.italic).isTrue()
    }

    @Test
    fun textRenderingSupportsWhitespaceAlignmentOverflowTabsAndUnicode() {
        val terminal: Terminal = Terminal(
            ansiLevel = AnsiLevel.NONE,
            width = 12,
            height = 8,
            tabWidth = 4,
            terminalInterface = TerminalRecorder(ansiLevel = AnsiLevel.NONE, width = 12, height = 8),
        )

        val centered: String = terminal.render(
            "kotlin",
            whitespace = Whitespace.PRE,
            align = TextAlign.CENTER,
            overflowWrap = OverflowWrap.NORMAL,
            width = 10,
        )
        val wrapped: String = terminal.render(
            Text(
                "alpha   beta\n👩‍💻\ttabular",
                whitespace = Whitespace.PRE_WRAP,
                align = TextAlign.LEFT,
                overflowWrap = OverflowWrap.BREAK_WORD,
                width = 8,
                tabWidth = 2,
            ),
        )

        assertThat(centered).isEqualTo("  kotlin  ")
        assertThat(wrapped).contains("alpha", "beta", "👩‍💻", "tabular")
        assertThat(wrapped.lines().size).isGreaterThan(1)
    }

    @Test
    fun tableDslRendersStyledSectionsSpansCaptionsAndExportsCsv() {
        val terminal: Terminal = Terminal(
            ansiLevel = AnsiLevel.NONE,
            width = 60,
            height = 20,
            terminalInterface = TerminalRecorder(ansiLevel = AnsiLevel.NONE, width = 60, height = 20),
        )
        val report = table {
            borderType = BorderType.ASCII
            borderStyle = TextColors.gray
            cellBorders = Borders.ALL
            tableBorders = Borders.ALL
            captionTop("Metrics", align = TextAlign.LEFT)
            column(0) { width = ColumnWidth.Fixed(14) }
            header {
                style = TextStyles.bold.style
                row("Name", "Value")
            }
            body {
                rowStyles(TextColors.white, TextColors.brightCyan)
                row {
                    cell("plain")
                    cell(TextColors.green("42")) { align = TextAlign.RIGHT }
                }
                rowFrom(listOf("needs,quote", "line break"))
            }
            footer {
                row {
                    cell("Total") {
                        columnSpan = 2
                        align = TextAlign.CENTER
                    }
                }
            }
        }

        val rendered: String = terminal.render(report)

        assertThat(rendered).contains("Metrics", "Name", "Value", "plain", "42", "needs,quote", "Total")
        assertThat(rendered).contains("+").contains("|")

        val csvTable = table {
            header { row("Name", "Value") }
            body {
                row("Ada", "Hello, \"world\"")
                row("Bob", "line\nbreak")
            }
        }
        val csv: String = csvTable.contentToCsv(doubleQuote = true)

        assertThat(csv).startsWith("Name,Value\n")
        assertThat(csv).contains("Ada,\"Hello, \"\"world\"\"\"\n")
        assertThat(csv).contains("Bob,\"line\nbreak\"\n")
    }

    @Test
    fun layoutsPanelsRulesListsDefinitionListsPaddingAndViewportsCompose() {
        val terminal: Terminal = Terminal(
            ansiLevel = AnsiLevel.NONE,
            width = 48,
            height = 20,
            terminalInterface = TerminalRecorder(ansiLevel = AnsiLevel.NONE, width = 48, height = 20),
        )
        val glossary = definitionList {
            inline = true
            entry("CPU", "central processor")
            entry {
                term("RAM")
                description("working memory")
            }
        }
        val layout = verticalLayout {
            spacing = 1
            cell(
                Panel(
                    content = horizontalLayout {
                        spacing = 2
                        cell(Text("left"))
                        cell(Text("right"))
                    },
                    title = Text("Summary"),
                    bottomTitle = Text("done"),
                    borderType = BorderType.ROUNDED,
                    titleAlign = TextAlign.LEFT,
                    bottomTitleAlign = TextAlign.RIGHT,
                    padding = Padding(top = 0, right = 1, bottom = 0, left = 1),
                ),
            )
            cell(HorizontalRule("Details", ruleCharacter = "-", titleAlign = TextAlign.CENTER))
            cell(UnorderedList("red", "blue", bulletText = "-", bulletStyle = TextColors.cyan))
            cell(OrderedList("first", "second", numberSeparator = ")"))
            cell(Viewport(glossary.withPadding { left = 2 }, height = 2, width = 36))
        }

        val rendered: String = terminal.render(layout)

        assertThat(rendered).contains("Summary", "left", "right", "done")
        assertThat(rendered).contains("Details")
        assertThat(rendered).contains("- red", "- blue")
        assertThat(rendered).contains("1) first", "2) second")
        assertThat(rendered).contains("CPU", "central processor")
    }

    @Test
    fun promptsReuseTerminalInputValidateChoicesAndApplyDefaults() {
        val recorder: TerminalRecorder = TerminalRecorder(
            ansiLevel = AnsiLevel.NONE,
            width = 60,
            height = 10,
            inputInteractive = true,
        )
        recorder.inputLines = mutableListOf("orange", "green", "")
        val terminal: Terminal = Terminal(
            ansiLevel = AnsiLevel.NONE,
            width = 60,
            height = 10,
            interactive = true,
            terminalInterface = recorder,
        )

        val color: String? = terminal.prompt(
            "Color",
            choices = listOf("red", "green", "blue"),
        )
        val confirmation: Boolean? = YesNoPrompt(
            prompt = "Continue",
            terminal = terminal,
            default = true,
        ).ask()

        assertThat(color).isEqualTo("green")
        assertThat(confirmation).isTrue()
        assertThat(recorder.output()).contains("Color", "Invalid value", "red, green, blue", "Continue", "Y/n")
    }

    @Test
    fun progressBarsAndSpinnersRenderDeterministicFrames() {
        val terminal: Terminal = Terminal(
            ansiLevel = AnsiLevel.NONE,
            width = 20,
            height = 8,
            terminalInterface = TerminalRecorder(ansiLevel = AnsiLevel.NONE, width = 20, height = 8),
        )
        val progress: ProgressBar = ProgressBar(
            total = 5,
            completed = 3,
            width = 10,
            pendingChar = "-",
            separatorChar = ">",
            completeChar = "#",
        )
        val indeterminate: ProgressBar = ProgressBar(
            fractionComplete = 0.25f,
            indeterminate = true,
            width = 8,
            pulsePosition = 0.5f,
            showPulse = true,
            pendingChar = ".",
            separatorChar = "*",
            completeChar = "=",
        )
        val spinner: Spinner = Spinner("abc", duration = 1)

        assertThat(terminal.render(progress)).contains("#", ">", "-").hasSize(10)
        assertThat(terminal.render(indeterminate)).hasSize(8)
        assertThat(terminal.render(spinner)).isEqualTo("a")
        spinner.advanceTick()
        assertThat(terminal.render(spinner)).isEqualTo("b")
        spinner.advanceTick()
        assertThat(terminal.render(spinner)).isEqualTo("c")
        spinner.advanceTick()
        assertThat(terminal.render(spinner)).isEqualTo("a")
    }

    @Test
    fun widgetsRejectInvalidDimensions() {
        assertThatThrownBy { Text("x", width = -1) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("width")
        assertThatThrownBy { Text("x", tabWidth = -1) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("tab")
        assertThatThrownBy { Padding(top = 0, right = -1, bottom = 0, left = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("right")
    }
}
