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
import com.github.ajalt.mordant.rendering.Theme
import com.github.ajalt.mordant.rendering.plus
import com.github.ajalt.mordant.rendering.VerticalAlign
import com.github.ajalt.mordant.rendering.Whitespace
import com.github.ajalt.mordant.table.Borders
import com.github.ajalt.mordant.table.ColumnWidth
import com.github.ajalt.mordant.table.CsvQuoting
import com.github.ajalt.mordant.table.contentToCsv
import com.github.ajalt.mordant.table.grid
import com.github.ajalt.mordant.table.horizontalLayout
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.table.verticalLayout
import com.github.ajalt.mordant.terminal.StringPrompt
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import com.github.ajalt.mordant.terminal.YesNoPrompt
import com.github.ajalt.mordant.terminal.outputAsHtml
import com.github.ajalt.mordant.widgets.Caption
import com.github.ajalt.mordant.widgets.HorizontalRule
import com.github.ajalt.mordant.widgets.OrderedList
import com.github.ajalt.mordant.widgets.Padding
import com.github.ajalt.mordant.widgets.Panel
import com.github.ajalt.mordant.widgets.ProgressBar
import com.github.ajalt.mordant.widgets.SelectList
import com.github.ajalt.mordant.widgets.Text
import com.github.ajalt.mordant.widgets.UnorderedList
import com.github.ajalt.mordant.widgets.Viewport
import com.github.ajalt.mordant.widgets.definitionList
import com.github.ajalt.mordant.widgets.withPadding
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class Mordant_core_jvmTest {
    @Test
    fun terminalRecorderCapturesPlainAndStyledOutputStreams() {
        val (terminal, recorder) = terminal(AnsiLevel.TRUECOLOR, width = 48)
        val style = TextColors.rgb("#336699") + TextStyles.bold + TextStyles.underline

        terminal.print("plain ")
        terminal.println(style("styled"))
        terminal.rawPrint("warning", true)

        assertThat(recorder.stdout()).contains("plain", "styled")
        assertThat(recorder.stdout()).contains("\u001B[")
        assertThat(recorder.stderr()).contains("warning")
        assertThat(recorder.output()).contains("plain", "warning")
        assertThat(style.bold).isTrue()
        assertThat(style.underline).isTrue()
        assertThat(style.color).isNotNull()
    }

    @Test
    fun terminalRecorderExportsStyledOutputAsEscapedHtml() {
        val (terminal, recorder) = terminal(AnsiLevel.TRUECOLOR, width = 60)
        val style = TextColors.rgb("#123456") + TextStyles.bold + TextStyles.italic

        terminal.println(style("build <ok> & ready"))

        val html = recorder.outputAsHtml(false, true, null)

        assertThat(html).startsWith("<pre")
        assertThat(html).contains("<code>", "</code>")
        assertThat(html).contains("build &lt;ok&gt; &amp; ready")
        assertThat(html).contains("color: #123456", "font-weight: bold", "font-style: italic")
        assertThat(html).doesNotContain("\u001B[")
    }

    @Test
    fun textRenderingHandlesWhitespaceAlignmentWrappingAndViewportCropping() {
        val (terminal, _) = terminal(width = 12)
        val wrappedText = Text(
            "alpha   beta gamma",
            Whitespace.NORMAL,
            TextAlign.CENTER,
            OverflowWrap.BREAK_WORD,
            8,
            null,
        )
        val paddedViewport = Viewport(wrappedText.withPadding(Padding(1, 2, 1, 2)), 10, 3, 0, 0)

        val rendered = terminal.render(paddedViewport)

        assertThat(rendered.lines()).hasSize(3)
        assertThat(rendered).contains("alpha")
        assertThat(rendered).contains("beta")
        assertThat(rendered.lines()).allMatch { line -> line.length <= 10 }
    }

    @Test
    fun panelsCaptionsAndRulesRenderAsciiBordersAndTitles() {
        val (terminal, _) = terminal(width = 40)
        val panel = Panel(
            "build passed",
            "Status",
            "native-ready",
            false,
            Padding(0, 1, 0, 1),
            BorderType.ASCII,
            TextAlign.LEFT,
            TextAlign.CENTER,
            TextColors.green,
            28,
        )
        val captioned = Caption(
            panel,
            "top caption",
            "bottom caption",
            TextAlign.CENTER,
            TextAlign.RIGHT,
        )
        val rule = HorizontalRule("section", "-", TextColors.blue, TextAlign.CENTER, 28, false)

        val rendered = terminal.render(verticalLayout {
            cell(captioned)
            cell(rule)
        })

        assertThat(rendered).contains("build passed", "top caption", "bottom caption", "section")
        assertThat(rendered).contains("+")
        assertThat(rendered).contains("-")
    }

    @Test
    fun tableDslRendersSectionsSpansStylesAndExportsCsv() {
        val (terminal, _) = terminal(width = 80)
        val report = table {
            borderType = BorderType.ASCII
            tableBorders = Borders.ALL
            captionTop("Team report", TextAlign.CENTER)
            captionBottom("2 engineers", TextAlign.RIGHT)
            header {
                style = TextStyles.bold.style
                row("Name", "Role", "Score")
            }
            body {
                row("Ada, Lovelace", "Compiler", 99) {
                    cellBorders = Borders.BOTTOM
                }
                row {
                    cell("Grace")
                    cell("Runtime")
                    cell("98") {
                        align = TextAlign.RIGHT
                    }
                }
            }
            footer {
                row {
                    cell("Average") {
                        columnSpan = 2
                    }
                    cell("98.5")
                }
            }
        }

        val rendered = terminal.render(report)
        val csv = report.contentToCsv()
        val quotedCsv = report.contentToCsv(quoting = CsvQuoting.ALL)

        assertThat(rendered).contains("Team report", "Ada, Lovelace", "Grace", "Average", "2 engineers")
        assertThat(rendered).contains("+")
        assertThat(csv).contains("Name,Role,Score")
        assertThat(csv).contains("\"Ada, Lovelace\",Compiler,99")
        assertThat(quotedCsv).contains("\"Name\",\"Role\",\"Score\"")
    }

    @Test
    fun gridAndLinearLayoutsComposeWidgetsWithColumnWidths() {
        val (terminal, _) = terminal(width = 48)
        val grid = grid {
            padding(0)
            column(0) {
                width = ColumnWidth.Fixed(8)
            }
            column(1) {
                width = ColumnWidth.Expand(1)
            }
            row("left", Text("right column", width = 12, overflowWrap = OverflowWrap.BREAK_WORD))
            row("bottom", "cell")
        }
        val horizontal = horizontalLayout {
            spacing = 2
            verticalAlign = VerticalAlign.MIDDLE
            cell("A")
            cell("B")
            cell("C")
        }
        val vertical = verticalLayout {
            spacing = 1
            width = ColumnWidth.Fixed(20)
            cell(grid)
            cell(horizontal)
        }

        val rendered = terminal.render(vertical)

        assertThat(rendered).contains("left", "right", "bottom", "A", "B", "C")
        assertThat(ColumnWidth.Fixed(8).width).isEqualTo(8)
        assertThat(ColumnWidth.Expand(1).expandWeight).isEqualTo(1.0f)
        assertThat(ColumnWidth.Auto.width).isNull()
    }

    @Test
    fun selectListWidgetRendersCursorSelectionMarkersDescriptionsAndCaption() {
        val (terminal, _) = terminal(width = 64)
        val choices = SelectList(
            listOf(
                SelectList.Entry("Generate metadata", "Creates configuration", false),
                SelectList.Entry("Run native image", "Verifies executable", true),
                SelectList.Entry("Publish report", "Shares results", false),
            ),
            Text("Pick task"),
            1,
            false,
            ">",
            "[x]",
            "[ ]",
            Text("Use arrows to move"),
            TextColors.green,
            TextStyles.bold.style,
            TextColors.gray,
            TextColors.gray,
        )

        val rendered = terminal.render(choices)

        assertThat(rendered).contains("Pick task", "Use arrows to move")
        assertThat(rendered).contains("Generate metadata", "Creates configuration")
        assertThat(rendered).contains("Run native image", "Verifies executable")
        assertThat(rendered).contains("Publish report", "Shares results")
        assertThat(rendered).contains(">", "[x]", "[ ]")
    }

    @Test
    fun listDefinitionAndProgressWidgetsRenderReadableSummaries() {
        val (terminal, _) = terminal(width = 50)
        val definitions = definitionList {
            inline = false
            descriptionSpacing = 2
            entry("ansi", "colored terminal output")
            entry {
                term("native image")
                description("closed-world executable")
            }
        }
        val unordered = UnorderedList(listOf(Text("red"), Text("green"), Text("blue")), "-", TextColors.gray)
        val ordered = OrderedList(listOf(Text("compile"), Text("link"), Text("run")), TextColors.gray, ".")
        val progress = ProgressBar(
            4L,
            10L,
            true,
            12,
            1.0f,
            false,
            "[",
            "]",
            "=",
            TextColors.green,
            TextColors.gray,
            TextColors.gray,
            TextColors.green,
            TextColors.yellow,
        )

        val rendered = terminal.render(verticalLayout {
            cell(definitions)
            cell(unordered)
            cell(ordered)
            cell(progress)
        })

        assertThat(rendered).contains("ansi", "colored terminal output", "native image", "closed-world executable")
        assertThat(rendered).contains("red", "green", "blue", "compile", "link", "run")
        assertThat(rendered).contains("============")
    }

    @Test
    fun promptsConsumeRecorderInputAndRenderQuestionsDefaultsAndChoices() {
        val (terminal, recorder) = terminal(width = 60)
        recorder.inputLines = mutableListOf("Kotlin", "yes")

        val language = StringPrompt(
            "Language",
            terminal,
            "Java",
            true,
            false,
            true,
            listOf("Java", "Kotlin"),
            ": ",
            "Choose a listed language",
            false,
        ).ask()
        val confirmed = YesNoPrompt(
            "Continue",
            terminal,
            false,
            true,
            true,
            listOf("yes", "no"),
            ": ",
            "Answer yes or no",
        ).ask()

        assertThat(language).isEqualTo("Kotlin")
        assertThat(confirmed).isTrue()
        assertThat(recorder.stdout()).contains("Language", "Java", "Continue")
    }

    @Test
    fun themesResolveCustomStylesStringsFlagsAndDimensions() {
        val baseTheme = Theme(Theme.PlainAscii) {
            styles["status.ok"] = TextColors.green + TextStyles.bold
            strings["status.symbol"] = "OK"
            flags["status.enabled"] = true
            dimensions["status.padding"] = 3
        }
        val overrideTheme = Theme {
            strings["status.symbol"] = "READY"
            dimensions["status.padding"] = 5
        }
        val mergedTheme = baseTheme + overrideTheme
        val (terminal, _) = terminal(AnsiLevel.NONE, width = 30, theme = mergedTheme)

        val rendered = terminal.render(mergedTheme.style("status.ok")("${mergedTheme.string("status.symbol")} service"))

        assertThat(mergedTheme.style("status.ok").bold).isTrue()
        assertThat(mergedTheme.string("status.symbol")).isEqualTo("READY")
        assertThat(mergedTheme.flag("status.enabled")).isTrue()
        assertThat(mergedTheme.dimension("status.padding")).isEqualTo(5)
        assertThat(mergedTheme.stringOrNull("missing")).isNull()
        assertThat(rendered).contains("READY service")
    }

    private fun terminal(
        ansiLevel: AnsiLevel = AnsiLevel.NONE,
        width: Int = 40,
        height: Int = 20,
        theme: Theme = Theme.PlainAscii,
    ): Pair<Terminal, TerminalRecorder> {
        val recorder = TerminalRecorder(ansiLevel, width, height, true, true, true, true)
        val terminal = Terminal(ansiLevel, theme, width, height, null, null, true, 8, true, recorder)
        return terminal to recorder
    }
}
