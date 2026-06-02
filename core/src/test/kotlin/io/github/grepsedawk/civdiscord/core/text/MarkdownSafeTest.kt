package io.github.grepsedawk.civdiscord.core.text

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MarkdownSafeTest {

    @Test
    fun `code strips backticks`() {
        MarkdownSafe.code("weird`name") shouldBe "weirdname"
    }

    @Test
    fun `code strips multiple backticks`() {
        MarkdownSafe.code("a`b`c```d") shouldBe "abcd"
    }

    @Test
    fun `code leaves backtick-free input unchanged`() {
        MarkdownSafe.code("alice") shouldBe "alice"
    }

    @Test
    fun `text escapes backslash`() {
        MarkdownSafe.text("a\\b") shouldBe "a\\\\b"
    }

    @Test
    fun `text escapes asterisk`() {
        MarkdownSafe.text("a*b") shouldBe "a\\*b"
    }

    @Test
    fun `text escapes underscore`() {
        MarkdownSafe.text("a_b") shouldBe "a\\_b"
    }

    @Test
    fun `text escapes tilde`() {
        MarkdownSafe.text("a~b") shouldBe "a\\~b"
    }

    @Test
    fun `text escapes backtick`() {
        MarkdownSafe.text("a`b") shouldBe "a\\`b"
    }

    @Test
    fun `text escapes pipe`() {
        MarkdownSafe.text("a|b") shouldBe "a\\|b"
    }

    @Test
    fun `text escapes greater-than`() {
        MarkdownSafe.text("a>b") shouldBe "a\\>b"
    }

    @Test
    fun `text escapes backslash before other chars so escape sequences survive`() {
        MarkdownSafe.text("\\*") shouldBe "\\\\\\*"
    }

    @Test
    fun `text leaves plain input unchanged`() {
        MarkdownSafe.text("hello world") shouldBe "hello world"
    }
}
