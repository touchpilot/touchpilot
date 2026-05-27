package dev.touchpilot.app.security

/**
 * A redaction rule that knows how to match a sensitive substring AND how to
 * replace only the secret portion of its match, leaving the surrounding
 * structural characters (`}`, `)`, `]`, `>`, quote characters, header
 * keywords, etc.) intact.
 *
 * The previous implementation in [SensitiveTextRedactor] used a single
 * `[^\s,;]+` token class together with a shared replacement lambda that
 * truncated the match at its first `:` or `=`. That combination had two
 * defects:
 *
 *  1. `Authorization: Bearer abc123` was rewritten to `Authorization=[REDACTED]`,
 *     dropping the `Bearer` keyword and changing the header `:` to `=`.
 *  2. The greedy token class consumed any trailing structural character that
 *     was not whitespace / comma / semicolon, so a log line such as
 *     `type_text({text=Authorization: Bearer abc})` was redacted to
 *     `type_text({text=Authorization=[REDACTED]` — the closing `})` was
 *     eaten along with the secret.
 *
 * Each [RedactionRule] variant owns its own regex (built with the shared
 * [TokenClass]) and its own replacement lambda, so the prefix is preserved
 * exactly as written and the closing structural characters fall outside the
 * match entirely.
 */
internal sealed class RedactionRule {

    /** Apply this rule to [text], returning the redacted output. */
    abstract fun apply(text: String): String

    /** Return true if this rule would match at least one substring of [text]. */
    abstract fun matches(text: String): Boolean

    /**
     * Redacts `key = value` / `key: value` assignments where `key` is one of
     * the supplied sensitive identifiers (`api_key`, `password`, etc.).
     *
     * The match has four groups:
     *
     *  1. The key + delimiter + surrounding whitespace, preserved verbatim.
     *  2. An optional opening quote character (`"`, `'`, or `` ` ``) when the
     *     value is quoted; empty when the value is bare.
     *  3. (Implicit) the secret token itself, matched by [TokenClass] which
     *     excludes whitespace, structural closers, and quote characters so
     *     surrounding scope delimiters are not eaten with the secret.
     *  4. The matching closing quote (a backreference to group 2) when group
     *     2 matched; empty otherwise.
     *
     * The replacement reassembles group 1 + group 2 + [RedactedToken] +
     * group 4, so a value like `password="hunter2"` becomes
     * `password="[REDACTED]"` and a value like `tool({password=hunter2})`
     * becomes `tool({password=[REDACTED]})` with the closing `})` intact.
     */
    class KeyAssignment(keyAlternation: String) : RedactionRule() {
        private val pattern: Regex = Regex(
            "(?i)((?:$keyAlternation)\\s*[:=]\\s*)([\"'`])?$TokenClass(\\2?)"
        )

        override fun apply(text: String): String {
            return pattern.replace(text) { match ->
                val prefix = match.groupValues[1]
                val openQuote = match.groupValues[2]
                val closeQuote = match.groupValues[3]
                prefix + openQuote + RedactedToken + closeQuote
            }
        }

        override fun matches(text: String): Boolean {
            return pattern.containsMatchIn(text)
        }
    }

    /**
     * Redacts `Authorization: Bearer <token>` headers. Group 1 captures the
     * `Authorization: Bearer ` prefix (including any trailing whitespace) so
     * the header keyword is preserved exactly as written; only the bearer
     * token is replaced.
     */
    class BearerHeader : RedactionRule() {
        private val pattern: Regex = Regex(
            "(?i)(authorization:\\s*bearer\\s+)$TokenClass"
        )

        override fun apply(text: String): String {
            return pattern.replace(text) { match ->
                match.groupValues[1] + RedactedToken
            }
        }

        override fun matches(text: String): Boolean {
            return pattern.containsMatchIn(text)
        }
    }

    /**
     * Redacts a self-contained value pattern (long digit runs, six-digit
     * one-time codes, e-mail addresses). The pattern already matches a
     * tight character class, so no separate structural-character protection
     * is needed and the entire match is replaced with [RedactedToken].
     */
    class LiteralValue(private val pattern: Regex) : RedactionRule() {

        override fun apply(text: String): String {
            return pattern.replace(text, RedactedToken)
        }

        override fun matches(text: String): Boolean {
            return pattern.containsMatchIn(text)
        }
    }

    companion object {
        /**
         * Replacement string used for every redacted match. Kept as a single
         * constant so a future change to the redaction sentinel only needs
         * to be made in one place.
         */
        const val RedactedToken: String = "[REDACTED]"

        /**
         * Character class for a redacted secret token.
         *
         * Excludes:
         *  - whitespace, comma, semicolon (preserved from the previous
         *    implementation as legal token boundaries)
         *  - closing structural characters `}`, `)`, `]`, `>` so a secret
         *    that ends a JSON-ish, dict-ish, list-ish, or tag-ish enclosing
         *    scope does not pull the closer into the match
         *  - the three quote characters `'`, `"`, `` ` `` so a quoted secret
         *    keeps its closing quote in the surrounding text
         *
         * The full set is intentionally conservative: any character that a
         * caller might rely on as a structural delimiter goes here.
         */
        const val TokenClass: String = "[^\\s,;\\}\\)\\]\\>\"'`]+"
    }
}
