package dev.touchpilot.app.agent

/**
 * Best-effort repair of the JSON defects language models most often emit around
 * an otherwise valid command object:
 *
 *  - curly "smart" quotes (`“ ” ‘ ’`) used where JSON needs straight quotes,
 *  - a trailing comma directly before a closing `}` or `]`,
 *  - `//` line comments and `/* … */` block comments.
 *
 * [AgentCommandParser] applies this only as a fallback AFTER strict parsing has
 * already failed, so well-formed model output is never rewritten. The repair is
 * deliberately conservative: it only rewrites structure that lies OUTSIDE string
 * literals, so the contents of every string are preserved exactly as written —
 * a comma, brace, or `//` inside a `"…"` value is never touched.
 */
object LenientJson {

    /** Returns [text] with the common recoverable JSON defects repaired. */
    fun repair(text: String): String = stripCommentsAndTrailingCommas(normalizeQuotes(text))

    /** Replaces Unicode "smart" quotes with the ASCII quotes JSON requires. */
    private fun normalizeQuotes(text: String): String {
        val out = StringBuilder(text.length)
        for (c in text) {
            out.append(
                when (c) {
                    '“', '”', '„', '‟', '″' -> '"'
                    '‘', '’', '‚', '‛', '′' -> '\''
                    else -> c
                }
            )
        }
        return out.toString()
    }

    /**
     * Single string-aware pass that removes comments and drops a comma that
     * directly precedes a closing `}` or `]`. Text inside a string literal
     * (double- or single-quoted, honouring backslash escapes) is copied verbatim.
     */
    private fun stripCommentsAndTrailingCommas(text: String): String {
        val out = StringBuilder(text.length)
        val n = text.length
        var i = 0
        var stringDelim: Char? = null

        while (i < n) {
            val c = text[i]

            if (stringDelim != null) {
                // Inside a string: copy verbatim, keeping escaped pairs together.
                if (c == '\\' && i + 1 < n) {
                    out.append(c).append(text[i + 1])
                    i += 2
                    continue
                }
                out.append(c)
                if (c == stringDelim) stringDelim = null
                i++
                continue
            }

            when {
                c == '"' || c == '\'' -> {
                    stringDelim = c
                    out.append(c)
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
