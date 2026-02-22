package com.aireview.plugin.annotator

import com.aireview.plugin.model.ReviewFinding
import com.aireview.plugin.model.Severity
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D

/**
 * Renders AI review findings as inline text after the end of a line (Error Lens style).
 * Uses [JBColor] for proper light/dark theme support.
 */
class InlineFindingRenderer(val finding: ReviewFinding) : EditorCustomElementRenderer {

    private val prefix = when (finding.severity) {
        Severity.ERROR -> "\u2716 "   // ✖
        Severity.WARNING -> "\u26A0 " // ⚠
        Severity.INFO -> "\u2139 "    // ℹ
    }

    private val displayText = "$prefix${finding.message}"

    private val bgColor = when (finding.severity) {
        Severity.ERROR -> JBColor(Color(255, 200, 200, 40), Color(120, 40, 40, 50))
        Severity.WARNING -> JBColor(Color(255, 240, 200, 40), Color(120, 100, 30, 50))
        Severity.INFO -> JBColor(Color(200, 220, 255, 40), Color(40, 60, 120, 50))
    }

    private val fgColor = when (finding.severity) {
        Severity.ERROR -> JBColor(Color(220, 50, 50), Color(255, 100, 100))
        Severity.WARNING -> JBColor(Color(180, 130, 0), Color(230, 180, 60))
        Severity.INFO -> JBColor(Color(80, 120, 180), Color(120, 160, 220))
    }

    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        val editor = inlay.editor
        val metrics = editor.contentComponent.getFontMetrics(
            editor.colorsScheme.getFont(com.intellij.openapi.editor.colors.EditorFontType.PLAIN).deriveFont(Font.ITALIC)
        )
        return metrics.stringWidth(displayText) + 24 // padding
    }

    override fun paint(
        inlay: Inlay<*>,
        g: Graphics2D,
        targetRegion: Rectangle2D,
        textAttributes: TextAttributes
    ) {
        val editor = inlay.editor
        val baseFont = editor.colorsScheme.getFont(com.intellij.openapi.editor.colors.EditorFontType.PLAIN)
        val font = baseFont.deriveFont(Font.ITALIC, baseFont.size2D * 0.9f)
        val metrics = g.getFontMetrics(font)

        val x = targetRegion.x.toInt() + 12
        val y = targetRegion.y.toInt() + metrics.ascent

        // Draw background
        g.color = bgColor
        g.fillRoundRect(
            x - 4,
            targetRegion.y.toInt() + 1,
            metrics.stringWidth(displayText) + 8,
            targetRegion.height.toInt() - 2,
            6, 6
        )

        // Draw text
        g.font = font
        g.color = fgColor
        g.drawString(displayText, x, y)
    }
}
