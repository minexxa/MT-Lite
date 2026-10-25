package com.mtlite.app.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/** Token categories produced by the highlighter. */
enum class TokenType {
    PLAIN, KEYWORD, TYPE, FUNCTION, STRING, NUMBER, COMMENT, ANNOTATION,
    TAG, ATTRIBUTE, PROPERTY, OPERATOR, PUNCT, VARIABLE, CONSTANT,
    PREPROCESSOR, HEADING, LINK, REGEX, ADDED, REMOVED
}

/** Supported highlighting languages. */
enum class CodeLanguage(val displayName: String) {
    YAML("YAML"),
    JSON("JSON"),
    XML("XML"),
    HTML("HTML"),
    MARKDOWN("Markdown"),
    TOML("TOML"),
    PROPERTIES("Properties/INI"),
    DIFF("Diff/Patch"),
    KOTLIN("Kotlin"),
    JAVA("Java"),
    JAVASCRIPT("JavaScript"),
    TYPESCRIPT("TypeScript"),
    C("C"),
    CPP("C/C++"),
    CSHARP("C#"),
    GO("Go"),
    RUST("Rust"),
    SWIFT("Swift"),
    PYTHON("Python"),
    PHP("PHP"),
    RUBY("Ruby"),
    LUA("Lua"),
    SHELL("Shell"),
    SQL("SQL"),
    CSS("CSS"),
    PLAIN("纯文本");

    companion object {
        /** Best-effort language detection from a file name. */
        fun detect(fileName: String): CodeLanguage {
            val lower = fileName.lowercase()
            // extensionless well-known names first
            when (lower) {
                "makefile", "gnumakefile" -> return SHELL
                "dockerfile" -> return SHELL
                "cmakelists.txt" -> return PROPERTIES
                ".gitignore", ".gitattributes", ".editorconfig" -> return PROPERTIES
            }
            return when (lower.substringAfterLast('.', "")) {
                "yml", "yaml" -> YAML
                "json" -> JSON
                "xml" -> XML
                "html", "htm", "xhtml", "vue" -> HTML
                "md", "markdown" -> MARKDOWN
                "toml" -> TOML
                "properties", "conf", "cfg", "rc", "ini", "props", "gitconfig" -> PROPERTIES
                "diff", "patch" -> DIFF
                "kt", "kts" -> KOTLIN
                "java" -> JAVA
                "js", "mjs", "cjs", "jsx" -> JAVASCRIPT
                "ts", "tsx" -> TYPESCRIPT
                "c", "h" -> C
                "cpp", "cc", "cxx", "hpp", "hh", "hxx" -> CPP
                "cs" -> CSHARP
                "go" -> GO
                "rs" -> RUST
                "swift" -> SWIFT
                "py", "pyw", "pyi" -> PYTHON
                "php" -> PHP
                "rb", "rake", "gemspec" -> RUBY
                "lua" -> LUA
                "sh", "bash", "zsh", "bashrc", "profile" -> SHELL
                "sql" -> SQL
                "css", "scss", "sass", "less" -> CSS
                "gradle" -> JAVA
                else -> PLAIN
            }
        }
    }
}

/** Color palette for the editor (light and dark variants). */
data class HighlightPalette(
    val plain: Color,
    val keyword: Color,
    val type: Color,
    val function: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val annotation: Color,
    val tag: Color,
    val attribute: Color,
    val property: Color,
    val operator: Color,
    val punct: Color,
    val variable: Color,
    val constant: Color,
    val preprocessor: Color,
    val heading: Color,
    val link: Color,
    val regex: Color,
    val added: Color,
    val removed: Color,
    val background: Color,
    val gutter: Color,
    val gutterActive: Color,
    val cursor: Color,
    val selection: Color
) {
    fun styleFor(tokenType: TokenType): SpanStyle = when (tokenType) {
        TokenType.PLAIN -> SpanStyle(color = plain)
        TokenType.KEYWORD -> SpanStyle(color = keyword, fontWeight = FontWeight.Medium)
        TokenType.TYPE -> SpanStyle(color = type)
        TokenType.FUNCTION -> SpanStyle(color = function)
        TokenType.STRING -> SpanStyle(color = string)
        TokenType.NUMBER -> SpanStyle(color = number)
        TokenType.COMMENT -> SpanStyle(color = comment, fontStyle = FontStyle.Italic)
        TokenType.ANNOTATION -> SpanStyle(color = annotation)
        TokenType.TAG -> SpanStyle(color = tag, fontWeight = FontWeight.Medium)
        TokenType.ATTRIBUTE -> SpanStyle(color = attribute)
        TokenType.PROPERTY -> SpanStyle(color = property)
        TokenType.OPERATOR -> SpanStyle(color = operator)
        TokenType.PUNCT -> SpanStyle(color = punct)
        TokenType.VARIABLE -> SpanStyle(color = variable)
        TokenType.CONSTANT -> SpanStyle(color = constant)
        TokenType.PREPROCESSOR -> SpanStyle(color = preprocessor)
        TokenType.HEADING -> SpanStyle(color = heading, fontWeight = FontWeight.Bold)
        TokenType.LINK -> SpanStyle(color = link)
        TokenType.REGEX -> SpanStyle(color = regex)
        TokenType.ADDED -> SpanStyle(color = added)
        TokenType.REMOVED -> SpanStyle(color = removed)
    }
}

object EditorThemes {
    /** Light editor palette tuned to resemble MT 管理器的代码页配色. */
    val Light = HighlightPalette(
        plain = Color(0xFF24292F),
        keyword = Color(0xFF4B5CC4),
        type = Color(0xFF7C3AED),
        function = Color(0xFF8250DF),
        string = Color(0xFFB45309),
        number = Color(0xFFB45309),
        comment = Color(0xFF15803D),
        annotation = Color(0xFF7C3AED),
        tag = Color(0xFF0550AE),
        attribute = Color(0xFF0550AE),
        property = Color(0xFF4B5CC4),
        operator = Color(0xFF6E7781),
        punct = Color(0xFF8C959F),
        variable = Color(0xFFB45309),
        constant = Color(0xFFB45309),
        preprocessor = Color(0xFF7C3AED),
        heading = Color(0xFF0550AE),
        link = Color(0xFF0550AE),
        regex = Color(0xFF0A3069),
        added = Color(0xFF116329),
        removed = Color(0xFFCF222E),
        background = Color(0xFFFFFFFF),
        gutter = Color(0xFF9AA4AE),
        gutterActive = Color(0xFF4B5CC4),
        cursor = Color(0xFF4B5CC4),
        selection = Color(0xFFBBD3F9)
    )

    /** Dark editor palette (GitHub-dark inspired). */
    val Dark = HighlightPalette(
        plain = Color(0xFFE6EDF3),
        keyword = Color(0xFFFF7B72),
        type = Color(0xFFFFA657),
        function = Color(0xFFD2A8FF),
        string = Color(0xFFA5D6A7),
        number = Color(0xFF79C0FF),
        comment = Color(0xFF8B949E),
        annotation = Color(0xFFD2A8FF),
        tag = Color(0xFF7EE787),
        attribute = Color(0xFF79C0FF),
        property = Color(0xFFFFA657),
        operator = Color(0xFFFF7B72),
        punct = Color(0xFF8B949E),
        variable = Color(0xFFFFA657),
        constant = Color(0xFFD2A8FF),
        preprocessor = Color(0xFFD2A8FF),
        heading = Color(0xFF58A6FF),
        link = Color(0xFF58A6FF),
        regex = Color(0xFF79C0FF),
        added = Color(0xFF7EE787),
        removed = Color(0xFFFFA198),
        background = Color(0xFF0D1117),
        gutter = Color(0xFF6E7681),
        gutterActive = Color(0xFF79C0FF),
        cursor = Color(0xFF58A6FF),
        selection = Color(0xFF264F78)
    )
}

/** A highlighted span within a single line (offsets are line-relative). */
data class HToken(val start: Int, val end: Int, val type: TokenType)

/** Carry-over state between lines (block comments, multiline strings, ...). */
class LineState {
    var blockCommentEnd: String? = null
    var multilineStringEnd: String? = null
    var yamlBlockIndent: Int = -1
    var inXmlComment = false
    var inMarkdownFence = false
    var inRubyBlock = false
    var cssInBlock = false

    fun reset() {
        blockCommentEnd = null
        multilineStringEnd = null
        yamlBlockIndent = -1
        inXmlComment = false
        inMarkdownFence = false
        inRubyBlock = false
        cssInBlock = false
    }
}

private const val MAX_HIGHLIGHT_CHARS = 200_000

private data class CLikeConfig(
    val lineComments: List<String> = listOf("//"),
    val blockComments: List<Pair<String, String>> = listOf("/*" to "*/"),
    val strings: List<Char> = listOf('"', '\''),
    val multilineStrings: List<String> = emptyList(),
    val rawBacktick: Boolean = false,
    val dollarVars: Boolean = false,
    val lifetimes: Boolean = false,
    val preprocessor: Boolean = false,
    val caseInsensitiveKeywords: Boolean = false,
    val keywords: Set<String> = emptySet()
)

/**
 * Line-oriented syntax highlighter. [highlightLines] returns one
 * [AnnotatedString] per source line (line text without the newline), or null
 * when highlighting is disabled (plain text / oversized file).
 */
object SyntaxHighlighter {

    fun highlightLines(text: String, lang: CodeLanguage, palette: HighlightPalette): List<AnnotatedString>? {
        if (lang == CodeLanguage.PLAIN || text.isEmpty()) return null
        if (text.length > MAX_HIGHLIGHT_CHARS) return null
        val state = LineState()
        val lines = text.split('\n')
        return lines.map { line -> highlightLine(line, lang, palette, state) }
    }

    /**
     * Tokenize a whole text, returning tokens with absolute offsets.
     * Used by the folding engine (needs string/comment spans) and by tests.
     */
    fun tokenize(text: String, lang: CodeLanguage): List<HToken> {
        if (text.isEmpty() || lang == CodeLanguage.PLAIN) return emptyList()
        val out = ArrayList<HToken>()
        val state = LineState()
        var start = 0
        for (line in text.split('\n')) {
            tokenizeLine(line, start, lang, state, out)
            start += line.length + 1
        }
        return out
    }

    fun highlightLine(
        line: String,
        lang: CodeLanguage,
        palette: HighlightPalette,
        state: LineState
    ): AnnotatedString {
        val tokens = ArrayList<HToken>()
        tokenizeLine(line, 0, lang, state, tokens)
        if (tokens.isEmpty()) return AnnotatedString(line)
        val builder = AnnotatedString.Builder(line)
        for (t in tokens) {
            val end = t.end.coerceIn(0, line.length)
            val start = t.start.coerceIn(0, end)
            if (start >= end) continue
            builder.addStyle(palette.styleFor(t.type), start, end)
        }
        return builder.toAnnotatedString()
    }

    // ------------------------------------------------------------- dispatch

    private fun tokenizeLine(line: String, base: Int, lang: CodeLanguage, state: LineState, out: MutableList<HToken>) {
        when (lang) {
            CodeLanguage.YAML -> scanYaml(line, base, state, out)
            CodeLanguage.JSON -> scanJson(line, base, out)
            CodeLanguage.XML, CodeLanguage.HTML -> scanXml(line, base, state, out)
            CodeLanguage.MARKDOWN -> scanMarkdown(line, base, state, out)
            CodeLanguage.PROPERTIES -> scanProps(line, base, out)
            CodeLanguage.TOML -> scanToml(line, base, out)
            CodeLanguage.DIFF -> scanDiff(line, base, out)
            CodeLanguage.CSS -> scanCss(line, base, state, out)
            CodeLanguage.SQL -> scanSql(line, base, state, out)
            CodeLanguage.RUBY -> scanRuby(line, base, state, out)
            CodeLanguage.PHP -> scanPhp(line, base, state, out)
            CodeLanguage.SHELL -> scanCLike(line, base, CLikeConfig(
                lineComments = listOf("#"),
                blockComments = emptyList(),
                strings = listOf('"', '\''),
                dollarVars = true,
                keywords = SHELL_KEYWORDS
            ), state, out)
            CodeLanguage.PYTHON -> scanCLike(line, base, CLikeConfig(
                lineComments = listOf("#"),
                blockComments = emptyList(),
                strings = listOf('"', '\''),
                multilineStrings = listOf("\"\"\"", "'''"),
                dollarVars = true,
                keywords = PYTHON_KEYWORDS
            ), state, out)
            CodeLanguage.LUA -> scanCLike(line, base, CLikeConfig(
                lineComments = listOf("--"),
                blockComments = listOf("--[[" to "]]"),
                strings = listOf('"', '\''),
                keywords = LUA_KEYWORDS
            ), state, out)
            else -> scanCLike(line, base, cLikeConfig(lang), state, out)
        }
    }

    private fun cLikeConfig(lang: CodeLanguage): CLikeConfig = when (lang) {
        CodeLanguage.C -> CLikeConfig(keywords = C_KEYWORDS, preprocessor = true)
        CodeLanguage.CPP -> CLikeConfig(keywords = CPP_KEYWORDS, preprocessor = true)
        CodeLanguage.CSHARP -> CLikeConfig(keywords = CSHARP_KEYWORDS)
        CodeLanguage.GO -> CLikeConfig(keywords = GO_KEYWORDS, rawBacktick = true)
        CodeLanguage.RUST -> CLikeConfig(keywords = RUST_KEYWORDS, rawBacktick = true, lifetimes = true, strings = listOf('"'))
        CodeLanguage.SWIFT -> CLikeConfig(keywords = SWIFT_KEYWORDS, multilineStrings = listOf("\"\"\""))
        CodeLanguage.JAVA -> CLikeConfig(keywords = JAVA_KEYWORDS)
        CodeLanguage.KOTLIN -> CLikeConfig(keywords = KOTLIN_KEYWORDS, multilineStrings = listOf("\"\"\""), dollarVars = true)
        CodeLanguage.JAVASCRIPT -> CLikeConfig(keywords = JS_KEYWORDS, rawBacktick = true, dollarVars = true)
        CodeLanguage.TYPESCRIPT -> CLikeConfig(keywords = TS_KEYWORDS, rawBacktick = true, dollarVars = true)
        else -> CLikeConfig(keywords = emptySet())
    }

    // ------------------------------------------------------- generic helpers

    /** Scan a quoted run starting at [i]; returns the index just past the closing quote (or line length). */
    private fun scanQuoted(line: String, i: Int, quote: Char, escapes: Boolean): Int {
        var j = i + 1
        val n = line.length
        while (j < n) {
            val c = line[j]
            if (escapes && c == '\\') { j += 2; continue }
            if (c == quote) return j + 1
            j++
        }
        return n
    }

    private fun isIdentStart(c: Char) = c.isLetter() || c == '_'
    private fun isIdentPart(c: Char) = c.isLetterOrDigit() || c == '_'

    // ------------------------------------------------------------ C family

    private fun scanCLike(line: String, base: Int, cfg: CLikeConfig, state: LineState, out: MutableList<HToken>) {
        val n = line.length

        // Continue a block comment from the previous line.
        state.blockCommentEnd?.let { end ->
            val idx = line.indexOf(end)
            if (idx >= 0) {
                out += HToken(base, base + idx + end.length, TokenType.COMMENT)
                state.blockCommentEnd = null
                scanCLikeRest(line, base, idx + end.length, cfg, state, out)
            } else {
                out += HToken(base, base + n, TokenType.COMMENT)
            }
            return
        }
        // Continue a multiline string (Kotlin """...""", Python, Swift).
        state.multilineStringEnd?.let { end ->
            val idx = line.indexOf(end)
            if (idx >= 0) {
                out += HToken(base, base + idx + end.length, TokenType.STRING)
                state.multilineStringEnd = null
                scanCLikeRest(line, base, idx + end.length, cfg, state, out)
            } else {
                out += HToken(base, base + n, TokenType.STRING)
            }
            return
        }
        scanCLikeRest(line, base, 0, cfg, state, out)
    }

    private fun scanCLikeRest(line: String, base: Int, from: Int, cfg: CLikeConfig, state: LineState, out: MutableList<HToken>) {
        var i = from
        val n = line.length
        while (i < n) {
            val c = line[i]
            if (c == ' ' || c == '\t') { i++; continue }

            // Block comments first: "--[[" must win over the "--" line comment.
            var matched = false
            for ((start, end) in cfg.blockComments) {
                if (line.startsWith(start, i)) {
                    val idx = line.indexOf(end, i + start.length)
                    if (idx >= 0) {
                        out += HToken(base + i, base + idx + end.length, TokenType.COMMENT)
                        i = idx + end.length
                    } else {
                        out += HToken(base + i, base + n, TokenType.COMMENT)
                        state.blockCommentEnd = end
                        return
                    }
                    matched = true
                    break
                }
            }
            if (matched) continue

            for (lc in cfg.lineComments) {
                if (line.startsWith(lc, i)) {
                    out += HToken(base + i, base + n, TokenType.COMMENT)
                    return
                }
            }

            for (ms in cfg.multilineStrings) {
                if (line.startsWith(ms, i)) {
                    val idx = line.indexOf(ms, i + ms.length)
                    if (idx >= 0) {
                        out += HToken(base + i, base + idx + ms.length, TokenType.STRING)
                        i = idx + ms.length
                    } else {
                        out += HToken(base + i, base + n, TokenType.STRING)
                        state.multilineStringEnd = ms
                        return
                    }
                    matched = true
                    break
                }
            }
            if (matched) continue

            // C preprocessor directive (only at the beginning of a line).
            if (cfg.preprocessor && c == '#' && line.substring(0, i).isBlank()) {
                out += HToken(base + i, base + n, TokenType.PREPROCESSOR)
                return
            }

            // Rust lifetimes: 'a  (but keep 'x' char literals as strings).
            if (cfg.lifetimes && c == '\'' && i + 1 < n && isIdentStart(line[i + 1])) {
                var j = i + 1
                while (j < n && isIdentPart(line[j])) j++
                out += HToken(base + i, base + j, TokenType.VARIABLE)
                i = j
                continue
            }

            // Strings.
            if (c in cfg.strings || (c == '`' && cfg.rawBacktick)) {
                val end = scanQuoted(line, i, c, escapes = c != '`')
                out += HToken(base + i, base + end, TokenType.STRING)
                if (cfg.dollarVars) emitDollarVars(line, base, i, end, out)
                i = end
                continue
            }

            // Numbers (decimal / hex / float with suffixes).
            if (c.isDigit() || (c == '.' && i + 1 < n && line[i + 1].isDigit())) {
                var j = i
                if (c == '0' && i + 1 < n && (line[i + 1] == 'x' || line[i + 1] == 'X')) {
                    j = i + 2
                    while (j < n && (line[j].isLetterOrDigit() || line[j] == '_')) j++
                } else {
                    while (j < n && (line[j].isDigit() || line[j] == '_')) j++
                    if (j < n && line[j] == '.') { j++; while (j < n && (line[j].isDigit() || line[j] == '_')) j++ }
                    if (j < n && (line[j] == 'e' || line[j] == 'E')) {
                        j++
                        if (j < n && (line[j] == '+' || line[j] == '-')) j++
                        while (j < n && line[j].isDigit()) j++
                    }
                    while (j < n && line[j].isLetter()) j++ // suffixes: f, L, u8...
                }
                out += HToken(base + i, base + j, TokenType.NUMBER)
                i = j
                continue
            }

            // Annotations / decorators.
            if (c == '@' && i + 1 < n && isIdentStart(line[i + 1])) {
                var j = i + 1
                while (j < n && isIdentPart(line[j])) j++
                out += HToken(base + i, base + j, TokenType.ANNOTATION)
                i = j
                continue
            }

            // $var interpolation.
            if (c == '$' && cfg.dollarVars && i + 1 < n && (isIdentStart(line[i + 1]) || line[i + 1] == '{')) {
                var j = i + 1
                if (line[j] == '{') {
                    j++
                    while (j < n && line[j] != '}') j++
                    if (j < n) j++
                } else {
                    while (j < n && isIdentPart(line[j])) j++
                }
                out += HToken(base + i, base + j, TokenType.VARIABLE)
                i = j
                continue
            }

            // Identifiers.
            if (isIdentStart(c)) {
                var j = i
                while (j < n && isIdentPart(line[j])) j++
                val word = line.substring(i, j)
                val probe = if (cfg.caseInsensitiveKeywords) word.lowercase() else word
                val type = when {
                    cfg.keywords.contains(probe) -> TokenType.KEYWORD
                    j < n && line[j] == '(' -> TokenType.FUNCTION
                    word[0].isUpperCase() && word.any { it.isLetter() } -> TokenType.TYPE
                    word.length > 1 && word.all { it.isUpperCase() || it == '_' } && word.any { it.isLetter() } -> TokenType.CONSTANT
                    else -> TokenType.PLAIN
                }
                if (type != TokenType.PLAIN) out += HToken(base + i, base + j, type)
                i = j
                continue
            }

            if (c in "+-*/%=<>!&|^~?:") {
                out += HToken(base + i, base + i + 1, TokenType.OPERATOR)
                i++
                continue
            }
            if (c in "()[]{},;.") {
                out += HToken(base + i, base + i + 1, TokenType.PUNCT)
                i++
                continue
            }
            i++
        }
    }

    /** Emit $var / ${...} interpolation tokens inside a string range. */
    private fun emitDollarVars(line: String, base: Int, from: Int, to: Int, out: MutableList<HToken>) {
        var i = from + 1
        while (i < to - 1) {
            if (line[i] == '$' && i + 1 < to && (isIdentStart(line[i + 1]) || line[i + 1] == '{')) {
                var j = i + 1
                if (line[j] == '{') {
                    j++
                    while (j < to && line[j] != '}') j++
                    if (j < to) j++
                } else {
                    while (j < to && isIdentPart(line[j])) j++
                }
                out += HToken(base + i, base + j, TokenType.VARIABLE)
                i = j
            } else i++
        }
    }

    // ------------------------------------------------------------------ YAML

    private fun scanYaml(line: String, base: Int, state: LineState, out: MutableList<HToken>) {
        val n = line.length
        var i = 0
        while (i < n && (line[i] == ' ' || line[i] == '\t')) i++
        val indent = i
        if (i >= n) return

        // Inside a block scalar (| or >): everything is literal text.
        if (state.yamlBlockIndent >= 0) {
            if (line.isBlank() || indent > state.yamlBlockIndent) {
                if (line.isNotBlank()) out += HToken(base + i, base + n, TokenType.STRING)
                return
            }
            state.yamlBlockIndent = -1
        }

        if (line[i] == '#') {
            out += HToken(base + i, base + n, TokenType.COMMENT)
            return
        }
        if (line.startsWith("---", i) || line.startsWith("...", i)) {
            out += HToken(base + i, base + n, TokenType.KEYWORD)
            return
        }

        // Leading "- " sequence markers.
        while (i < n && (line.startsWith("- ", i) || (line[i] == '-' && i + 1 >= n))) {
            if (line.startsWith("- ", i)) {
                out += HToken(base + i, base + i + 1, TokenType.PUNCT)
                i += 2
                while (i < n && line[i] == ' ') i++
            } else break
            if (i >= n) return
            if (line[i] == '#') {
                out += HToken(base + i, base + n, TokenType.COMMENT)
                return
            }
        }

        // Locate the key colon (respecting quotes).
        var j = i
        var inQuote: Char? = null
        var colon = -1
        while (j < n) {
            val c = line[j]
            if (inQuote != null) {
                if (c == inQuote) inQuote = null
                j++
                continue
            }
            if (c == '"' || c == '\'') { inQuote = c; j++; continue }
            if (c == '#' && j > i && line[j - 1] == ' ') break
            if (c == ':' && (j + 1 >= n || line[j + 1] == ' ' || line[j + 1] == '\t')) { colon = j; break }
            j++
        }

        if (colon > i) {
            out += HToken(base + i, base + colon, TokenType.PROPERTY)
            var k = colon + 1
            while (k < n && (line[k] == ' ' || line[k] == '\t')) k++
            if (k < n) emitYamlValue(line, base, k, indent, state, out)
        } else {
            // Plain scalar without a key: still surface a trailing comment.
            val h = line.indexOf(" #", i)
            if (h >= 0) {
                out += HToken(base + h + 1, base + n, TokenType.COMMENT)
            }
        }
    }

    private fun emitYamlValue(line: String, base: Int, k0: Int, indent: Int, state: LineState, out: MutableList<HToken>) {
        val n = line.length
        val c = line[k0]
        when {
            c == '#' -> out += HToken(base + k0, base + n, TokenType.COMMENT)
            c == '"' || c == '\'' -> {
                val end = scanQuoted(line, k0, c, escapes = true)
                out += HToken(base + k0, base + end, TokenType.STRING)
                emitYamlTrailingComment(line, base, end, out)
            }
            c == '&' || c == '*' -> {
                var j = k0 + 1
                while (j < n && isIdentPart(line[j])) j++
                out += HToken(base + k0, base + j, TokenType.VARIABLE)
                emitYamlTrailingComment(line, base, j, out)
            }
            c == '|' || c == '>' -> {
                out += HToken(base + k0, base + k0 + 1, TokenType.OPERATOR)
                state.yamlBlockIndent = indent
            }
            c == '!' -> {
                var j = k0
                while (j < n && !line[j].isWhitespace()) j++
                out += HToken(base + k0, base + j, TokenType.VARIABLE)
            }
            c.isDigit() || (c == '-' && k0 + 1 < n && line[k0 + 1].isDigit()) ||
                (c == '+' && k0 + 1 < n && line[k0 + 1].isDigit()) -> {
                var j = k0
                if (line[j] == '-' || line[j] == '+') j++
                while (j < n && (line[j].isDigit() || line[j] == '.' || line[j] == '_')) j++
                if (j < n && (line[j] == 'e' || line[j] == 'E')) {
                    j++
                    if (j < n && (line[j] == '+' || line[j] == '-')) j++
                    while (j < n && line[j].isDigit()) j++
                }
                out += HToken(base + k0, base + j, TokenType.NUMBER)
                emitYamlTrailingComment(line, base, j, out)
            }
            else -> {
                val word = line.substring(k0).takeWhile { !it.isWhitespace() }
                if (word == "true" || word == "false" || word == "null" || word == "~" ||
                    word == "True" || word == "False" || word == "Null" ||
                    word == "TRUE" || word == "FALSE" || word == "NULL"
                ) {
                    out += HToken(base + k0, base + k0 + word.length, TokenType.CONSTANT)
                    emitYamlTrailingComment(line, base, k0 + word.length, out)
                } else {
                    // Plain scalar: surface quoted spans (e.g. `== 'schedule'`)
                    // and the trailing comment.
                    var m = k0
                    while (m < n) {
                        val ch = line[m]
                        if (ch == '"' || ch == '\'') {
                            val e = scanQuoted(line, m, ch, escapes = true)
                            out += HToken(base + m, base + e, TokenType.STRING)
                            m = e
                            continue
                        }
                        if (ch == '#' && m > k0 && line[m - 1] == ' ') {
                            out += HToken(base + m, base + n, TokenType.COMMENT)
                            break
                        }
                        m++
                    }
                }
            }
        }
    }

    private fun emitYamlTrailingComment(line: String, base: Int, from: Int, out: MutableList<HToken>) {
        val h = line.indexOf(" #", from)
        if (h >= 0) out += HToken(base + h + 1, base + line.length, TokenType.COMMENT)
    }

    // ------------------------------------------------------------------ JSON

    private fun scanJson(line: String, base: Int, out: MutableList<HToken>) {
        var i = 0
        val n = line.length
        while (i < n) {
            val c = line[i]
            if (c == ' ' || c == '\t') { i++; continue }
            if (c == '"') {
                val end = scanQuoted(line, i, '"', escapes = true)
                var k = end
                while (k < n && (line[k] == ' ' || line[k] == '\t')) k++
                val type = if (k < n && line[k] == ':') TokenType.PROPERTY else TokenType.STRING
                out += HToken(base + i, base + end, type)
                i = end
                continue
            }
            if (c.isDigit() || (c == '-' && i + 1 < n && line[i + 1].isDigit())) {
                var j = i
                if (line[j] == '-') j++
                while (j < n && (line[j].isDigit() || line[j] == '.' || line[j] == 'e' || line[j] == 'E' ||
                    line[j] == '+' || line[j] == '-')) j++
                out += HToken(base + i, base + j, TokenType.NUMBER)
                i = j
                continue
            }
            if (line.startsWith("true", i)) { out += HToken(base + i, base + i + 4, TokenType.CONSTANT); i += 4; continue }
            if (line.startsWith("false", i)) { out += HToken(base + i, base + i + 5, TokenType.CONSTANT); i += 5; continue }
            if (line.startsWith("null", i)) { out += HToken(base + i, base + i + 4, TokenType.CONSTANT); i += 4; continue }
            if (c in "{}[]:,") { out += HToken(base + i, base + i + 1, TokenType.PUNCT); i++; continue }
            i++
        }
    }

    // -------------------------------------------------------------- XML / HTML

    private fun scanXml(line: String, base: Int, state: LineState, out: MutableList<HToken>) {
        val n = line.length
        var i = 0
        while (i < n) {
            if (state.inXmlComment) {
                val end = line.indexOf("-->", i)
                if (end >= 0) {
                    out += HToken(base + i, base + end + 3, TokenType.COMMENT)
                    state.inXmlComment = false
                    i = end + 3
                } else {
                    out += HToken(base + i, base + n, TokenType.COMMENT)
                    return
                }
                continue
            }
            if (line.startsWith("<!--", i)) {
                val end = line.indexOf("-->", i + 4)
                if (end >= 0) {
                    out += HToken(base + i, base + end + 3, TokenType.COMMENT)
                    i = end + 3
                } else {
                    out += HToken(base + i, base + n, TokenType.COMMENT)
                    state.inXmlComment = true
                    return
                }
                continue
            }
            if (line.startsWith("<!", i) || line.startsWith("<?", i)) {
                val end = line.indexOf('>', i)
                if (end >= 0) {
                    out += HToken(base + i, base + end + 1, TokenType.PREPROCESSOR)
                    i = end + 1
                } else {
                    out += HToken(base + i, base + n, TokenType.PREPROCESSOR)
                    return
                }
                continue
            }
            if (line[i] == '<' && i + 1 < n && (isIdentStart(line[i + 1]) || line[i + 1] == '/')) {
                val nameStart = if (line[i + 1] == '/') i + 2 else i + 1
                var k = nameStart
                while (k < n && (isIdentPart(line[k]) || line[k] in "-_:.")) k++
                if (k > nameStart) out += HToken(base + i, base + k, TokenType.TAG)
                i = k
                while (i < n && line[i] != '>') {
                    val ch = line[i]
                    if (ch == ' ' || ch == '\t') { i++; continue }
                    if (ch == '/') { out += HToken(base + i, base + i + 1, TokenType.PUNCT); i++; continue }
                    if (isIdentStart(ch)) {
                        var a = i
                        while (a < n && (isIdentPart(line[a]) || line[a] in "-_:")) a++
                        out += HToken(base + i, base + a, TokenType.ATTRIBUTE)
                        i = a
                        continue
                    }
                    if (ch == '=') { out += HToken(base + i, base + i + 1, TokenType.OPERATOR); i++; continue }
                    if (ch == '"' || ch == '\'') {
                        val e = scanQuoted(line, i, ch, escapes = false)
                        out += HToken(base + i, base + e, TokenType.STRING)
                        i = e
                        continue
                    }
                    i++
                }
                if (i < n && line[i] == '>') {
                    out += HToken(base + i, base + i + 1, TokenType.PUNCT)
                    i++
                }
                continue
            }
            if (line[i] == '&') {
                val e = line.indexOf(';', i)
                if (e in i + 1 until n) {
                    out += HToken(base + i, base + e + 1, TokenType.VARIABLE)
                    i = e + 1
                    continue
                }
            }
            i++
        }
    }

    // -------------------------------------------------------------- Markdown

    private val MD_LIST = Regex("""^([-*+] |\d+\. )""")

    private fun scanMarkdown(line: String, base: Int, state: LineState, out: MutableList<HToken>) {
        val n = line.length
        val t = line.trimStart()
        val indent = n - t.length
        if (state.inMarkdownFence) {
            if (t.startsWith("```") || t.startsWith("~~~")) {
                state.inMarkdownFence = false
                out += HToken(base + indent, base + n, TokenType.PREPROCESSOR)
            }
            return
        }
        if (t.startsWith("```") || t.startsWith("~~~")) {
            state.inMarkdownFence = true
            out += HToken(base + indent, base + n, TokenType.PREPROCESSOR)
            return
        }
        if (t.startsWith("#")) {
            out += HToken(base + indent, base + n, TokenType.HEADING)
            return
        }
        if (t.startsWith(">")) {
            out += HToken(base + indent, base + n, TokenType.COMMENT)
            return
        }
        val listMatch = MD_LIST.find(t)
        if (listMatch != null) {
            out += HToken(base + indent, base + indent + listMatch.value.length, TokenType.PUNCT)
        }
        // Inline spans: `code`, **bold**, [link](url)
        var i = 0
        while (i < n) {
            val c = line[i]
            if (c == '`') {
                val e = line.indexOf('`', i + 1)
                if (e > i) {
                    out += HToken(base + i, base + e + 1, TokenType.STRING)
                    i = e + 1
                    continue
                }
            }
            if (c == '*' && i + 1 < n && line[i + 1] == '*') {
                val e = line.indexOf("**", i + 2)
                if (e > i) {
                    out += HToken(base + i, base + e + 2, TokenType.CONSTANT)
                    i = e + 2
                    continue
                }
            }
            if (c == '[' || (c == '!' && i + 1 < n && line[i + 1] == '[')) {
                val open = if (c == '!') i + 1 else i
                val close = line.indexOf(']', open)
                if (close > open && close + 1 < n && line[close + 1] == '(') {
                    val paren = line.indexOf(')', close + 2)
                    if (paren > close) {
                        out += HToken(base + i, base + paren + 1, TokenType.LINK)
                        i = paren + 1
                        continue
                    }
                }
            }
            i++
        }
    }

    // ------------------------------------------------------- Properties / INI

    private fun scanProps(line: String, base: Int, out: MutableList<HToken>) {
        val n = line.length
        val t = line.trimStart()
        if (t.isEmpty()) return
        val indent = n - t.length
        if (t[0] == '#' || t[0] == ';' || t[0] == '!') {
            out += HToken(base + indent, base + n, TokenType.COMMENT)
            return
        }
        if (t[0] == '[') {
            val e = t.indexOf(']')
            if (e > 0) {
                out += HToken(base + indent, base + indent + e + 1, TokenType.TAG)
                val rest = t.substring(e + 1)
                val h = rest.indexOfFirst { it == '#' || it == ';' }
                if (h >= 0) out += HToken(base + indent + e + 1 + h, base + n, TokenType.COMMENT)
            }
            return
        }
        val eq = t.indexOf('=')
        val colon = t.indexOf(':')
        val idx = listOf(eq, colon).filter { it > 0 }.minOrNull()
        if (idx != null) {
            out += HToken(base + indent, base + indent + idx, TokenType.PROPERTY)
            val value = t.substring(idx + 1).trimStart()
            val vStart = indent + idx + 1 + (t.substring(idx + 1).length - value.length)
            if (value.isNotEmpty() && (value[0] == '"' || value[0] == '\'')) {
                val end = scanQuoted(line, vStart, value[0], escapes = true)
                out += HToken(base + vStart, base + end, TokenType.STRING)
            }
        }
    }

    // ------------------------------------------------------------------ TOML

    private fun scanToml(line: String, base: Int, out: MutableList<HToken>) {
        val n = line.length
        val t = line.trimStart()
        if (t.isEmpty()) return
        val indent = n - t.length
        if (t[0] == '#') {
            out += HToken(base + indent, base + n, TokenType.COMMENT)
            return
        }
        if (t[0] == '[') {
            val e = t.indexOf(']')
            if (e > 0) {
                out += HToken(base + indent, base + indent + e + 1, TokenType.TAG)
            }
            return
        }
        val eq = t.indexOf('=')
        if (eq > 0) {
            out += HToken(base + indent, base + indent + eq, TokenType.PROPERTY)
            val value = t.substring(eq + 1).trimStart()
            val vStart = indent + eq + 1 + (t.substring(eq + 1).length - value.length)
            when {
                value.isEmpty() -> {}
                value[0] == '"' || value[0] == '\'' -> {
                    val end = scanQuoted(line, vStart, value[0], escapes = true)
                    out += HToken(base + vStart, base + end, TokenType.STRING)
                }
                value[0].isDigit() || value[0] == '-' || value[0] == '+' -> {
                    var j = vStart
                    while (j < n && (line[j].isDigit() || line[j] == '.' || line[j] == '-' || line[j] == '+' ||
                        line[j] == 'e' || line[j] == 'E')) j++
                    out += HToken(base + vStart, base + j, TokenType.NUMBER)
                }
                value.startsWith("true") || value.startsWith("false") ->
                    out += HToken(base + vStart, base + vStart + (if (value.startsWith("true")) 4 else 5), TokenType.CONSTANT)
            }
        }
    }

    // ------------------------------------------------------------------ Diff

    private fun scanDiff(line: String, base: Int, out: MutableList<HToken>) {
        if (line.isEmpty()) return
        val n = line.length
        when (line[0]) {
            '+' -> out += HToken(base, base + n, TokenType.ADDED)
            '-' -> out += HToken(base, base + n, TokenType.REMOVED)
            '@' -> out += HToken(base, base + n, TokenType.HEADING)
            else -> {
                if (line.startsWith("diff ") || line.startsWith("index ") || line.startsWith("new file") ||
                    line.startsWith("deleted file")
                ) {
                    out += HToken(base, base + n, TokenType.KEYWORD)
                }
            }
        }
    }

    // ------------------------------------------------------------------- CSS

    private fun scanCss(line: String, base: Int, state: LineState, out: MutableList<HToken>) {
        val n = line.length
        if (state.blockCommentEnd != null) {
            val end = line.indexOf("*/")
            if (end >= 0) {
                out += HToken(base, base + end + 2, TokenType.COMMENT)
                state.blockCommentEnd = null
                scanCssRest(line, base, end + 2, state, out)
            } else {
                out += HToken(base, base + n, TokenType.COMMENT)
            }
            return
        }
        scanCssRest(line, base, 0, state, out)
    }

    private fun scanCssRest(line: String, base: Int, from: Int, state: LineState, out: MutableList<HToken>) {
        var i = from
        val n = line.length
        while (i < n) {
            val c = line[i]
            if (c == ' ' || c == '\t') { i++; continue }
            if (c == '/' && i + 1 < n && line[i + 1] == '*') {
                val end = line.indexOf("*/", i + 2)
                if (end >= 0) {
                    out += HToken(base + i, base + end + 2, TokenType.COMMENT)
                    i = end + 2
                } else {
                    out += HToken(base + i, base + n, TokenType.COMMENT)
                    state.blockCommentEnd = "*/"
                    return
                }
                continue
            }
            if (c == '"' || c == '\'') {
                val end = scanQuoted(line, i, c, escapes = true)
                out += HToken(base + i, base + end, TokenType.STRING)
                i = end
                continue
            }
            if (c == '#' && i + 1 < n && (line[i + 1].isDigit() || line[i + 1].isLetter() || line[i + 1] in "abcdefABCDEF")) {
                var j = i + 1
                while (j < n && (line[j].isLetterOrDigit())) j++
                if (j - i in 4..9) {
                    out += HToken(base + i, base + j, TokenType.CONSTANT)
                    i = j
                    continue
                }
            }
            if (c == '{') { state.cssInBlock = true; out += HToken(base + i, base + i + 1, TokenType.PUNCT); i++; continue }
            if (c == '}') { state.cssInBlock = false; out += HToken(base + i, base + i + 1, TokenType.PUNCT); i++; continue }
            if (c == '@' && i + 1 < n && isIdentStart(line[i + 1])) {
                var j = i + 1
                while (j < n && isIdentPart(line[j])) j++
                out += HToken(base + i, base + j, TokenType.KEYWORD)
                i = j
                continue
            }
            if (isIdentStart(c) || c == '-' || c == '.' || c == '#') {
                var j = i
                while (j < n && (isIdentPart(line[j]) || line[j] in "-.#")) j++
                val word = line.substring(i, j)
                var k = j
                while (k < n && (line[k] == ' ' || line[k] == '\t')) k++
                when {
                    state.cssInBlock && k < n && line[k] == ':' ->
                        out += HToken(base + i, base + j, TokenType.PROPERTY)
                    k < n && line[k] == '(' ->
                        out += HToken(base + i, base + j, TokenType.FUNCTION)
                    !state.cssInBlock ->
                        out += HToken(base + i, base + j, TokenType.TAG)
                }
                i = j
                continue
            }
            if (c.isDigit()) {
                var j = i
                while (j < n && (line[j].isDigit() || line[j] == '.' || line[j] == '%')) j++
                out += HToken(base + i, base + j, TokenType.NUMBER)
                i = j
                continue
            }
            if (c in ":;,>+*~()") {
                out += HToken(base + i, base + i + 1, TokenType.PUNCT)
                i++
                continue
            }
            i++
        }
    }

    // ------------------------------------------------------------------- SQL

    private fun scanSql(line: String, base: Int, state: LineState, out: MutableList<HToken>) {
        val cfg = CLikeConfig(
            lineComments = listOf("--"),
            blockComments = listOf("/*" to "*/"),
            strings = listOf('\''),
            caseInsensitiveKeywords = true,
            keywords = SQL_KEYWORDS
        )
        scanCLike(line, base, cfg, state, out)
    }

    // ------------------------------------------------------------------ Ruby

    private fun scanRuby(line: String, base: Int, state: LineState, out: MutableList<HToken>) {
        val n = line.length
        val t = line.trimStart()
        val indent = n - t.length
        if (state.inRubyBlock) {
            if (t == "=end") state.inRubyBlock = false
            out += HToken(base + indent, base + n, TokenType.COMMENT)
            return
        }
        if (t == "=begin") {
            state.inRubyBlock = true
            out += HToken(base + indent, base + n, TokenType.COMMENT)
            return
        }
        scanCLike(line, base, CLikeConfig(
            lineComments = listOf("#"),
            strings = listOf('"', '\''),
            dollarVars = true,
            keywords = RUBY_KEYWORDS
        ), state, out)
        // Symbols: :name (not part of :: or a string)
        var i = 0
        while (i < n - 1) {
            if (line[i] == ':' && isIdentStart(line[i + 1]) && (i == 0 || line[i - 1] != ':')) {
                var j = i + 1
                while (j < n && isIdentPart(line[j])) j++
                out += HToken(base + i, base + j, TokenType.VARIABLE)
                i = j
            } else i++
        }
    }

    // ------------------------------------------------------------------- PHP

    private fun scanPhp(line: String, base: Int, state: LineState, out: MutableList<HToken>) {
        val n = line.length
        if (line.startsWith("<?php") || line.startsWith("<?=") || line.trimStart().startsWith("?>")) {
            out += HToken(base, base + n, TokenType.PREPROCESSOR)
            return
        }
        scanCLike(line, base, CLikeConfig(
            lineComments = listOf("//", "#"),
            strings = listOf('"', '\''),
            dollarVars = true,
            keywords = PHP_KEYWORDS
        ), state, out)
    }
}

// ------------------------------------------------------------------- keyword sets

private val C_KEYWORDS = setOf(
    "auto", "break", "case", "char", "const", "continue", "default", "do", "double", "else",
    "enum", "extern", "float", "for", "goto", "if", "inline", "int", "long", "register",
    "restrict", "return", "short", "signed", "sizeof", "static", "struct", "switch", "typedef",
    "union", "unsigned", "void", "volatile", "while", "_Bool", "_Atomic", "_Thread_local"
)

private val CPP_KEYWORDS = C_KEYWORDS + setOf(
    "alignas", "alignof", "and", "asm", "bool", "catch", "class", "concept", "constexpr",
    "const_cast", "decltype", "delete", "dynamic_cast", "explicit", "export", "false",
    "friend", "mutable", "namespace", "new", "noexcept", "not", "nullptr", "operator", "or",
    "private", "protected", "public", "reinterpret_cast", "requires", "static_assert",
    "static_cast", "template", "this", "thread_local", "throw", "true", "try", "typeid",
    "typename", "using", "virtual", "wchar_t"
)

private val JAVA_KEYWORDS = setOf(
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
    "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally",
    "float", "for", "goto", "if", "implements", "import", "instanceof", "int", "interface",
    "long", "native", "new", "package", "private", "protected", "public", "return", "short",
    "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
    "transient", "try", "void", "volatile", "while", "true", "false", "null", "var", "record",
    "sealed", "yield"
)

private val KOTLIN_KEYWORDS = setOf(
    "abstract", "actual", "annotation", "as", "break", "by", "catch", "class", "companion",
    "const", "constructor", "continue", "crossinline", "data", "delegate", "do", "dynamic",
    "else", "enum", "expect", "external", "false", "field", "final", "finally", "for", "fun",
    "get", "if", "import", "in", "infix", "init", "inline", "inner", "interface", "internal",
    "is", "lateinit", "noinline", "null", "object", "open", "operator", "out", "override",
    "package", "private", "protected", "public", "reified", "return", "sealed", "set",
    "super", "suspend", "tailrec", "this", "throw", "true", "try", "typealias", "typeof",
    "val", "var", "vararg", "when", "where", "while"
)

private val JS_KEYWORDS = setOf(
    "async", "await", "break", "case", "catch", "class", "const", "continue", "debugger",
    "default", "delete", "do", "else", "enum", "export", "extends", "false", "finally",
    "for", "function", "if", "import", "in", "instanceof", "let", "new", "null", "of",
    "return", "static", "super", "switch", "this", "throw", "true", "try", "typeof",
    "undefined", "var", "void", "while", "with", "yield"
)

private val TS_KEYWORDS = JS_KEYWORDS + setOf(
    "abstract", "any", "as", "asserts", "bigint", "boolean", "declare", "implements",
    "infer", "interface", "is", "keyof", "module", "namespace", "never", "number",
    "object", "override", "private", "protected", "public", "readonly", "require",
    "string", "symbol", "type", "unique", "unknown"
)

private val GO_KEYWORDS = setOf(
    "break", "case", "chan", "const", "continue", "default", "defer", "else", "fallthrough",
    "for", "func", "go", "goto", "if", "import", "interface", "map", "package", "range",
    "return", "select", "struct", "switch", "type", "var", "nil", "true", "false", "iota"
)

private val RUST_KEYWORDS = setOf(
    "as", "async", "await", "break", "const", "continue", "crate", "dyn", "else", "enum",
    "extern", "false", "fn", "for", "if", "impl", "in", "let", "loop", "match", "mod",
    "move", "mut", "pub", "ref", "return", "self", "Self", "static", "struct", "super",
    "trait", "true", "type", "union", "unsafe", "use", "where", "while"
)

private val SWIFT_KEYWORDS = setOf(
    "actor", "as", "async", "await", "break", "case", "catch", "class", "continue", "default",
    "defer", "deinit", "do", "else", "enum", "extension", "fallthrough", "false", "fileprivate",
    "for", "func", "guard", "if", "import", "in", "init", "inout", "internal", "is", "let",
    "nil", "open", "operator", "private", "protocol", "public", "repeat", "return", "rethrows",
    "self", "Self", "static", "struct", "subscript", "super", "switch", "throw", "throws",
    "true", "try", "typealias", "var", "where", "while"
)

private val CSHARP_KEYWORDS = setOf(
    "abstract", "as", "base", "bool", "break", "byte", "case", "catch", "char", "checked",
    "class", "const", "continue", "decimal", "default", "delegate", "do", "double", "else",
    "enum", "event", "explicit", "extern", "false", "finally", "fixed", "float", "for",
    "foreach", "get", "goto", "if", "implicit", "in", "int", "interface", "internal", "is",
    "lock", "long", "namespace", "new", "null", "object", "operator", "out", "override",
    "params", "private", "protected", "public", "readonly", "ref", "return", "sbyte",
    "sealed", "set", "short", "sizeof", "stackalloc", "static", "string", "struct",
    "switch", "this", "throw", "true", "try", "typeof", "uint", "ulong", "unchecked",
    "unsafe", "ushort", "using", "var", "virtual", "void", "volatile", "while", "yield"
)

private val PYTHON_KEYWORDS = setOf(
    "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del",
    "elif", "else", "except", "False", "finally", "for", "from", "global", "if", "import",
    "in", "is", "lambda", "None", "nonlocal", "not", "or", "pass", "raise", "return",
    "True", "try", "while", "with", "yield", "match", "case"
)

private val PHP_KEYWORDS = setOf(
    "abstract", "and", "array", "as", "break", "callable", "case", "catch", "class", "clone",
    "const", "continue", "declare", "default", "do", "echo", "else", "elseif", "empty",
    "enddeclare", "endfor", "endforeach", "endif", "endswitch", "endwhile", "enum", "extends",
    "final", "finally", "fn", "for", "foreach", "function", "global", "goto", "if",
    "implements", "include", "instanceof", "insteadof", "interface", "isset", "list",
    "match", "namespace", "new", "null", "or", "print", "private", "protected", "public",
    "require", "return", "static", "switch", "throw", "trait", "try", "unset", "use",
    "var", "while", "xor", "yield", "true", "false"
)

private val RUBY_KEYWORDS = setOf(
    "alias", "and", "begin", "break", "case", "class", "def", "defined?", "do", "else",
    "elsif", "end", "ensure", "false", "for", "if", "in", "module", "next", "nil", "not",
    "or", "redo", "rescue", "retry", "return", "self", "super", "then", "true", "undef",
    "unless", "until", "when", "while", "yield", "require", "attr_accessor", "attr_reader",
    "attr_writer", "puts", "raise", "lambda", "proc"
)

private val LUA_KEYWORDS = setOf(
    "and", "break", "do", "else", "elseif", "end", "false", "for", "function", "goto", "if",
    "in", "local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while"
)

private val SHELL_KEYWORDS = setOf(
    "if", "then", "elif", "else", "fi", "for", "do", "done", "while", "until", "case", "esac",
    "in", "function", "select", "time", "return", "exit", "break", "continue", "export",
    "local", "readonly", "declare", "unset", "shift", "source", "alias", "eval", "exec",
    "set", "trap", "true", "false"
)

private val SQL_KEYWORDS = setOf(
    "add", "all", "alter", "and", "as", "asc", "between", "by", "case", "check", "column",
    "constraint", "create", "cross", "database", "default", "delete", "desc", "distinct",
    "drop", "else", "end", "exists", "foreign", "from", "full", "group", "having", "in",
    "index", "inner", "insert", "into", "is", "join", "key", "left", "like", "limit", "not",
    "null", "on", "or", "order", "outer", "primary", "references", "right", "select", "set",
    "table", "then", "union", "unique", "update", "values", "view", "when", "where", "with"
)
