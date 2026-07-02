package dev.touchpilot.app.security

/**
 * A named pattern for a credential or high-sensitivity value that must never be
 * surfaced in developer logs, traces, or demonstration recordings.
 *
 * Patterns are deliberately high-precision — each is anchored to a recognizable
 * provider prefix or a structural shape (e.g. the three dot-separated segments
 * of a JWT) — so redaction stays targeted and rarely swallows benign text.
 */
data class SecretPattern(
    val name: String,
    val regex: Regex,
)

/**
 * Catalog of secret patterns shared by [SecretScanner] and
 * [SensitiveTextRedactor]. This is the single source of truth so detection and
 * redaction can never drift apart.
 *
 * The catalog complements — it does not replace — the key/value and header
 * rules already in [SensitiveTextRedactor]. Those catch `password=…` style
 * assignments; these catch bare credential tokens that appear on screen or in
 * tool arguments with no telltale key next to them.
 *
 * Milestone 7: expanded redaction for logs and traces.
 */
object SecretPatterns {

    /** JSON Web Token: three base64url segments separated by dots, `eyJ…` header. */
    val JWT = SecretPattern(
        name = "jwt",
        regex = Regex("eyJ[A-Za-z0-9_-]{6,}\\.[A-Za-z0-9_-]{6,}\\.[A-Za-z0-9_-]{6,}"),
    )

    /** AWS access key id (`AKIA…` long-term, `ASIA…` temporary). */
    val AWS_ACCESS_KEY = SecretPattern(
        name = "aws_access_key_id",
        regex = Regex("\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b"),
    )

    /** GitHub token: personal (`ghp_`), OAuth (`gho_`), user/server (`ghu_`/`ghs_`), refresh (`ghr_`). */
    val GITHUB_TOKEN = SecretPattern(
        name = "github_token",
        regex = Regex("\\bgh[posru]_[A-Za-z0-9]{36,}\\b"),
    )

    /** OpenAI-style secret key: `sk-`, optionally `sk-proj-`/`sk-live-`/`sk-test-`. */
    val OPENAI_KEY = SecretPattern(
        name = "openai_key",
        regex = Regex("\\bsk-(?:proj-|live-|test-)?[A-Za-z0-9_-]{20,}\\b"),
    )

    /** Slack token (`xoxb-`, `xoxp-`, `xoxa-`, `xoxr-`, `xoxs-`). */
    val SLACK_TOKEN = SecretPattern(
        name = "slack_token",
        regex = Regex("\\bxox[baprs]-[A-Za-z0-9-]{10,}\\b"),
    )

    /** Google API key (`AIza…`, 39 chars total). */
    val GOOGLE_API_KEY = SecretPattern(
        name = "google_api_key",
        regex = Regex("\\bAIza[0-9A-Za-z_-]{35}\\b"),
    )

    /** Stripe-style secret/restricted key (`sk_live_…`, `rk_test_…`). */
    val STRIPE_KEY = SecretPattern(
        name = "stripe_key",
        regex = Regex("\\b[sr]k_(?:live|test)_[A-Za-z0-9]{16,}\\b"),
    )

    /** PEM private key block, header through footer, across newlines. */
    val PRIVATE_KEY_BLOCK = SecretPattern(
        name = "private_key",
        regex = Regex(
            "-----BEGIN (?:[A-Z0-9 ]+ )?PRIVATE KEY-----" +
                "[\\s\\S]*?" +
                "-----END (?:[A-Z0-9 ]+ )?PRIVATE KEY-----"
        ),
    )

    /**
     * All patterns. The multi-line PEM block is listed first so it is redacted
     * as a single unit before any narrower pattern can match a fragment inside
     * it.
     */
    val all: List<SecretPattern> = listOf(
        PRIVATE_KEY_BLOCK,
        JWT,
        AWS_ACCESS_KEY,
        GITHUB_TOKEN,
        OPENAI_KEY,
        SLACK_TOKEN,
        GOOGLE_API_KEY,
        STRIPE_KEY,
    )
}
