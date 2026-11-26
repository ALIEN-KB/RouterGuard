package app.routerguard.data

/** A very small JSON reader (maps, lists, strings, numbers, booleans, null). No dependencies. */
object MiniJson {
    fun parse(text: String): Any? {
        val p = Parser(text)
        val v = p.value()
        p.ws()
        if (!p.atEnd()) throw IllegalArgumentException("Unexpected data after JSON at ${p.pos}")
        return v
    }

    @Suppress("UNCHECKED_CAST")
    fun asMap(v: Any?): Map<String, Any?>? = v as? Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun asList(v: Any?): List<Any?>? = v as? List<Any?>

    private class Parser(private val s: String) {
        var pos = 0
        fun atEnd() = pos >= s.length
        fun ws() { while (pos < s.length && s[pos].isWhitespace()) pos++ }

        fun value(): Any? {
            ws()
            if (atEnd()) throw IllegalArgumentException("Unexpected end of JSON")
            return when (val c = s[pos]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw IllegalArgumentException("Unexpected '$c' at $pos")
            }
        }

        private fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, pos)) throw IllegalArgumentException("Bad literal at $pos")
            pos += word.length
            return v
        }

        private fun num(): Any {
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] in "+-.eE")) pos++
            return s.substring(start, pos).toDouble()
        }

        private fun str(): String {
            pos++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw IllegalArgumentException("Unterminated string")
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        val e = s[pos++]
                        when (e) {
                            'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                            'u' -> { sb.append(s.substring(pos, pos + 4).toInt(16).toChar()); pos += 4 }
                            else -> sb.append(e) // \" \\ \/
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun arr(): List<Any?> {
            pos++
            val out = ArrayList<Any?>()
            ws()
            if (s[pos] == ']') { pos++; return out }
            while (true) {
                out.add(value())
                ws()
                when (s[pos++]) { ',' -> continue; ']' -> return out; else -> throw IllegalArgumentException("Expected , or ] at ${pos - 1}") }
            }
        }

        private fun obj(): Map<String, Any?> {
            pos++
            val out = LinkedHashMap<String, Any?>()
            ws()
            if (s[pos] == '}') { pos++; return out }
            while (true) {
                ws()
                if (s[pos] != '"') throw IllegalArgumentException("Expected key at $pos")
                val k = str()
                ws()
                if (s[pos++] != ':') throw IllegalArgumentException("Expected : at ${pos - 1}")
                out[k] = value()
                ws()
                when (s[pos++]) { ',' -> continue; '}' -> return out; else -> throw IllegalArgumentException("Expected , or } at ${pos - 1}") }
            }
        }
    }
}
