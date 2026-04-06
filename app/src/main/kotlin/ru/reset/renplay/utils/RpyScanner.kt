package ru.reset.renplay.utils

class RpyScanner(private val text: String) {
    var pos = 0

    fun findNext(substring: String): Boolean {
        val idx = text.indexOf(substring, pos)
        if (idx == -1) return false
        pos = idx + substring.length
        return true
    }

    fun skipWhitespace() {
        while (pos < text.length && text[pos].isWhitespace()) {
            pos++
        }
    }

    fun readUntil(stopChar: Char): String {
        val start = pos
        val idx = text.indexOf(stopChar, pos)
        if (idx == -1) {
            pos = text.length
            return text.substring(start)
        }
        pos = idx
        return text.substring(start, idx)
    }

    fun readStringLiteral(): String? {
        skipWhitespace()
        var isLoc = false
        if (pos + 1 < text.length && text[pos] == '_' && text[pos + 1] == '(') {
            isLoc = true
            pos += 2
            skipWhitespace()
        }

        if (pos >= text.length) return null
        val quote = text[pos]
        if (quote != '"' && quote != '\'') return null
        pos++
        
        val sb = StringBuilder()
        while (pos < text.length && text[pos] != quote) {
            if (text[pos] == '\\' && pos + 1 < text.length) {
                pos++
                if (pos < text.length) sb.append(text[pos])
            } else {
                sb.append(text[pos])
            }
            pos++
        }
        if (pos < text.length && text[pos] == quote) pos++
        
        if (isLoc) {
            skipWhitespace()
            if (pos < text.length && text[pos] == ')') pos++
        }
        return sb.toString()
    }

    fun readIdentifier(): String? {
        skipWhitespace()
        if (pos >= text.length || (!text[pos].isLetter() && text[pos] != '_')) return null
        val start = pos
        while (pos < text.length && (text[pos].isLetterOrDigit() || text[pos] == '_')) {
            pos++
        }
        return text.substring(start, pos)
    }
}