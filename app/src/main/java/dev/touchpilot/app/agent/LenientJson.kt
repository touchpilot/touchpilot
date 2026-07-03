package dev.touchpilot.app.agent

/**
 * Best-effort repair of the JSON defects language models most often emit around
 * an otherwise valid command object:
 *
 *  - "smart" quotes — curly double (`“ ”`) or curly single (`‘ ’`) — used as
 *    string delimiters where JSON requires straight double quotes,
 *  - a trailing comma directly before a closing `}` or `]`,
 *  - `//` line comments and `/* … */` block comments.
 *
 * [AgentCommandParser] applies this only as a fallback AFTER strict parsing has
 * already failed, so well-formed model output is never rewritten.
 *
 * The repair is a single string-aware pass. It normalizes the *delimiters* of
 * each string to straight double quotes (so single-quoted and smart-quoted
 * payloads become valid JSON) while copying the *contents* verbatim — a comma,
 * brace, `//`, or apostrophe inside a value is preserved. When a string's
 * delimiters are upgraded to double quotes, a straight `"` appearing in its
 * contents is escaped so it cannot terminate the string early.
 */
object LenientJson {

    /** Opening quote characters mapped to the character that closes them. */
    private val OPENERS: Map<Char, Char> = mapOf(
        '"' to '"',
        '\'' to '\'',
        '“' to '”', // curly double
        '‘' to '’', // curly single
    )

    /** Returns [text] with the common recoverable JSON defects repaired. */
    fun repair(text: String): String {
        val out = StringBuilder(text.length)
        val n = text.length
        var i = 0
        var closer: Char? = null // closing char of the current string, or null when outside one
        var escapeInnerQuote = false // true when the current string was opened by a non-double quote

        while (i < n) {
            val c = text[i]

            if (closer != null) {
                // Inside a string literal.
                if (c == '\\' && i + 1 < n) {
                    // Keep escape pairs intact.
                    out.append(c).append(text[i + 1])
                    i += 2
                    continue
                }
                if (c == closer) {
                    out.append('"') // normalize the closing delimiter
                    closer = null
                    i++
                    continue
                }
                // Content. If we upgraded the delimiters to double quotes, a bare
                // double quote in the content must be escaped so it does not end
                // the string. Everything else (commas, braces, apostrophes, other
                // curly quotes) is copied verbatim.
                if (c == '"' && escapeInnerQuote) {
                    out.append("\\\"")
                } else {
                    out.append(c)
                }
                i++
                continue
            }

            // Outside any string.
            val open = OPENERS[c]
            when {
                open != null -> {
                    out.append('"') // normalize the opening delimiter
                    closer = open
                    escapeInnerQuote = c != '"'
                    i++
                }

                c == '/' && i + 1 < n && text[i + 1] == '/' -> {
                    i += 2
                    while (i < n && text[i] != '\n') i++
                }

                c == '/' && i + 1 < n && text[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < n && !(text[i] == '*' && text[i + 1] == '/')) i++
                    i += 2 // skip the closing */
                }

                c == ',' && precedesCloser(text, i + 1) -> {
                    // Trailing comma: drop it, keep the following whitespace/closer.
                    i++
                }

                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }

    /** True if the next non-whitespace character from [start] is `}` or `]`. */
    private fun precedesCloser(text: String, start: Int): Boolean {
        var i = start
        while (i < text.length && text[i].isWhitespace()) i++
        return i < text.length && (text[i] == '}' || text[i] == ']')
    }
}
