package com.aireview.plugin.annotator

import com.aireview.plugin.model.ReviewFinding
import com.aireview.plugin.model.Severity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InlineFindingRendererTest {

    @Test
    fun `ERROR finding has cross prefix`() {
        val renderer = createRenderer(Severity.ERROR)
        assertTrue(renderer.finding.severity == Severity.ERROR)
        // Verify displayText field via reflection
        val displayText = getDisplayText(renderer)
        assertTrue(displayText.startsWith("\u2716")) // ✖
    }

    @Test
    fun `WARNING finding has warning prefix`() {
        val renderer = createRenderer(Severity.WARNING)
        val displayText = getDisplayText(renderer)
        assertTrue(displayText.startsWith("\u26A0")) // ⚠
    }

    @Test
    fun `INFO finding has info prefix`() {
        val renderer = createRenderer(Severity.INFO)
        val displayText = getDisplayText(renderer)
        assertTrue(displayText.startsWith("\u2139")) // ℹ
    }

    @Test
    fun `displayText combines prefix and message`() {
        val renderer = createRenderer(Severity.ERROR, "Null pointer exception")
        val displayText = getDisplayText(renderer)
        assertTrue(displayText.contains("Null pointer exception"))
    }

    @Test
    fun `each severity has distinct background color`() {
        val errorBg = getBgColor(createRenderer(Severity.ERROR))
        val warningBg = getBgColor(createRenderer(Severity.WARNING))
        val infoBg = getBgColor(createRenderer(Severity.INFO))

        assertNotEquals(errorBg, warningBg)
        assertNotEquals(warningBg, infoBg)
        assertNotEquals(errorBg, infoBg)
    }

    @Test
    fun `each severity has distinct foreground color`() {
        val errorFg = getFgColor(createRenderer(Severity.ERROR))
        val warningFg = getFgColor(createRenderer(Severity.WARNING))
        val infoFg = getFgColor(createRenderer(Severity.INFO))

        assertNotEquals(errorFg, warningFg)
        assertNotEquals(warningFg, infoFg)
        assertNotEquals(errorFg, infoFg)
    }

    // --- Helpers ---

    private fun createRenderer(severity: Severity, message: String = "Test message"): InlineFindingRenderer {
        return InlineFindingRenderer(ReviewFinding(line = 1, severity = severity, message = message))
    }

    private fun getDisplayText(renderer: InlineFindingRenderer): String {
        val field = InlineFindingRenderer::class.java.getDeclaredField("displayText")
        field.isAccessible = true
        return field.get(renderer) as String
    }

    private fun getBgColor(renderer: InlineFindingRenderer): Any {
        val field = InlineFindingRenderer::class.java.getDeclaredField("bgColor")
        field.isAccessible = true
        return field.get(renderer)
    }

    private fun getFgColor(renderer: InlineFindingRenderer): Any {
        val field = InlineFindingRenderer::class.java.getDeclaredField("fgColor")
        field.isAccessible = true
        return field.get(renderer)
    }
}
