package com.ma.manager.util

/** An inclusive line range that can be collapsed in the editor. */
data class FoldRange(val startLine: Int, val endLine: Int) {
    val lineCount: Int get() = endLine - startLine + 1
}

/**
 * Computes foldable regions for a text. Two strategies:
 *  - indentation based (YAML / Python / Markdown / TOML / Properties / Shell / HTML / XML)
 *  - brace based, string/comment aware (C family, JSON, CSS, SQL)
 */
object CodeFolding {

    fun compute(text: String, lang: CodeLanguage): List<FoldRange> {
        if (text.isEmpty() || text.length > 400_000) return emptyList()
        val lines = text.split('\n')
        return when (lang) {
            CodeLanguage.YAML, CodeLanguage.PYTHON, CodeLanguage.MARKDOWN,
            CodeLanguage.TOML, CodeLanguage.PROPERTIES, CodeLanguage.SHELL,
            CodeLanguage.HTML, CodeLanguage.XML -> indentFolds(lines)
            CodeLanguage.JSON, CodeLanguage.KOTLIN, CodeLanguage.JAVA,
            CodeLanguage.JAVASCRIPT, CodeLanguage.TYPESCRIPT, CodeLanguage.C,
            CodeLanguage.CPP, CodeLanguage.CSHARP, CodeLanguage.GO, CodeLanguage.RUST,
            CodeLanguage.SWIFT, CodeLanguage.PHP, CodeLanguage.LUA, CodeLanguage.CSS,
            CodeLanguage.SQL -> braceFolds(text, lines, lang)
            else -> emptyList()
        }
    }

    /** Line i folds when the following content lines are indented deeper. */
    private fun indentFolds(lines: List<String>): List<FoldRange> {
        val folds = mutableListOf<FoldRange>()
        val indents = lines.map { line ->
            line.takeWhile { it == ' ' || it == '\t' }.length
        }
        var i = 0
        while (i < lines.size) {
            if (lines[i].isBlank()) { i++; continue }
            val indent = indents[i]
            var j = i + 1
            var lastContent = -1
            while (j < lines.size && (lines[j].isBlank() || indents[j] > indent)) {
                if (lines[j].isNotBlank()) lastContent = j
                j++
            }
            if (lastContent > i) folds += FoldRange(i, lastContent)
            i++
        }
        return folds
    }

    /** Brace based folding; strings and comments are skipped via the highlighter. */
    private fun braceFolds(text: String, lines: List<String>, lang: CodeLanguage): List<FoldRange> {
        val skip = BooleanArray(text.length)
        for (t in SyntaxHighlighter.tokenize(text, lang)) {
            if (t.type == TokenType.STRING || t.type == TokenType.COMMENT) {
                for (k in t.start until t.end) if (k in skip.indices) skip[k] = true
            }
        }

        // Line start offsets for offset -> line mapping.
        val lineStarts = IntArray(lines.size + 1)
        var acc = 0
        for (idx in lines.indices) {
            lineStarts[idx] = acc
            acc += lines[idx].length + 1
        }
        lineStarts[lines.size] = acc

        fun lineOf(offset: Int): Int {
            var lo = 0
            var hi = lines.size - 1
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (lineStarts[mid] <= offset) lo = mid else hi = mid - 1
            }
            return lo
        }

        val folds = mutableListOf<FoldRange>()
        var depth = 0
        val stack = ArrayDeque<Int>()
        var i = 0
        while (i < text.length) {
            if (i < skip.size && skip[i]) { i++; continue }
            when (text[i]) {
                '{' -> {
                    if (depth == 0) stack.addLast(lineOf(i))
                    depth++
                }
                '}' -> {
                    if (depth > 0) {
                        depth--
                        if (depth == 0) {
                            val start = stack.removeLastOrNull()
                            if (start != null) {
                                val end = lineOf(i)
                                if (end > start) folds += FoldRange(start, end)
                            }
                        }
                    }
                }
            }
            i++
        }
        return folds.sortedBy { it.startLine }
    }
}
