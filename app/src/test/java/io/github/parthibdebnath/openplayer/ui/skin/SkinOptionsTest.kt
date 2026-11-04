package io.github.parthibdebnath.openplayer.ui.skin

import io.github.parthibdebnath.openplayer.data.settings.UiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinOptionsTest {
    @Test
    fun aRemovedThemeReadsBackAsOpenplayersOwnLook() {
        // Cards was in test builds; a phone that had it picked shouldn't be left without a look.
        assertEquals(UiTheme.DEFAULT, UiTheme.fromName("CARDS"))
        assertEquals(UiTheme.DEFAULT, UiTheme.fromName(null))
        assertEquals(UiTheme.METRO, UiTheme.fromName("METRO"))
    }

    @Test
    fun choicesAreReadPerTheme() {
        val stored = mapOf("METRO.accent" to "lime", "METRO.background" to "light", "XMB.colour" to "3")
        assertEquals(mapOf("accent" to "lime", "background" to "light"), SkinChoices.forTheme(UiTheme.METRO, stored).values)
        assertEquals(mapOf("colour" to "3"), SkinChoices.forTheme(UiTheme.XMB, stored).values)
        assertTrue(SkinChoices.forTheme(UiTheme.AQUA, stored).values.isEmpty())
    }

    @Test
    fun anUnknownChoiceReadsBackAsTheDefault() {
        val option = SkinOption("x", "X", listOf(SkinChoice("a", "A"), SkinChoice("b", "B")), SkinOption.Style.List)
        assertEquals("a", SkinChoices(mapOf("x" to "nonsense")).of(option).id)
        assertEquals("a", SkinChoices().of(option).id)
        assertEquals("b", SkinChoices(mapOf("x" to "b")).of(option).id)
    }

    @Test
    fun everyThemesOptionsAreUsable() {
        UiTheme.entries.forEach { theme ->
            val options = skinOptions(theme)
            assertEquals("option keys of $theme", options.map { it.key }.distinct(), options.map { it.key })
            options.forEach { option ->
                if (option.style == SkinOption.Style.Swatches) {
                    assertTrue("every swatch of ${option.key} has a colour", option.choices.all { it.swatch != null })
                }
                if (option.style == SkinOption.Style.Segmented) {
                    assertTrue("${option.key} fits side by side", option.choices.size <= 3)
                }
            }
        }
    }

    @Test
    fun aSkinIsBuiltOncePerLook() {
        assertNull(skinFor(UiTheme.DEFAULT))
        val lime = skinFor(UiTheme.METRO, SkinChoices(mapOf("accent" to "lime")))
        // Stored keys a theme doesn't use, and choices spelled out as their defaults, are the same look.
        assertSame(lime, skinFor(UiTheme.METRO, SkinChoices(mapOf("accent" to "lime", "background" to "dark", "stale" to "x"))))
        assertNotSame(lime, skinFor(UiTheme.METRO, SkinChoices(mapOf("accent" to "lime", "background" to "light"))))
        assertSame(skinFor(UiTheme.METRO), skinFor(UiTheme.METRO, SkinChoices(mapOf("accent" to "unknown"))))
        UiTheme.entries.filter { it != UiTheme.DEFAULT }.forEach { theme ->
            assertEquals(theme, skinFor(theme)!!.theme)
        }
    }
}
