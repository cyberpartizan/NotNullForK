package com.github.cyberpartizan.notnullfork.processor

/** A primary constructor parameter as written in the source. */
internal class ParameterSource(
    val name: String,
    /** The default value expression exactly as written, or `null` if the parameter has none. */
    val defaultValue: String?,
    /** Names the default value refers to directly (not members after `.`, not named arguments). */
    val referencedNames: Set<String>,
)

/**
 * Reads what KSP doesn't expose from the text of a Kotlin source file: its imports and the default
 * value expressions of a primary constructor. It is a lexer plus bracket matching, not a full parser,
 * so it doesn't need the Kotlin compiler.
 */
internal class KotlinSource(private val text: String) {

    private val tokens: List<Token> = Lexer(text).tokenize()

    /** The file's imports, e.g. `java.util.Locale`, `java.time.*` or `a.b.C as D`. */
    val imports: List<String> by lazy { readImports() }

    /**
     * The primary constructor parameters, by name, of the class named [className] whose declaration
     * starts on or after the 1-based [line]. `null` if no such class is found.
     */
    fun primaryConstructorOf(className: String, line: Int): Map<String, ParameterSource>? {
        val from = lineOffset(line)
        val classIndex = tokens.indices.firstOrNull { i ->
            tokens[i].start >= from && tokens[i].isIdentifier("class") &&
                tokens.getOrNull(i + 1)?.takeIf { it.kind == Kind.IDENTIFIER }?.name == className
        } ?: return null
        val open = constructorStart(classIndex + 2) ?: return emptyMap()
        return readParameters(open + 1)
    }

    // Skips type parameters, annotations, modifiers and `constructor` to the `(` of the primary constructor.
    private fun constructorStart(start: Int): Int? {
        var i = start
        if (tokens.getOrNull(i)?.text == "<") i = matchingAngle(i) + 1
        while (i < tokens.size) {
            val token = tokens[i]
            when {
                token.text == "(" -> return i
                token.text == "@" -> {
                    i += 2
                    while (tokens.getOrNull(i)?.text == "." && tokens.getOrNull(i + 1)?.kind == Kind.IDENTIFIER) i += 2
                    if (tokens.getOrNull(i)?.text == "(") i = matching(i) + 1
                }
                token.kind == Kind.IDENTIFIER && token.text in CONSTRUCTOR_MODIFIERS -> i++
                else -> return null
            }
        }
        return null
    }

    private fun readParameters(start: Int): Map<String, ParameterSource> {
        val parameters = linkedMapOf<String, ParameterSource>()
        var i = start
        while (i < tokens.size && tokens[i].text != ")") {
            var name: String? = null
            var defaultStart = -1
            var angles = 0
            while (i < tokens.size) {
                val token = tokens[i]
                if (token.text in OPENING) {
                    i = matching(i) + 1
                    continue
                }
                if (token.text == ")" || (token.text == "," && angles == 0)) break
                when {
                    name == null -> if (token.text == ":" && tokens[i - 1].kind == Kind.IDENTIFIER &&
                        tokens.getOrNull(i - 2)?.text != "@"
                    ) {
                        name = tokens[i - 1].name
                    }
                    defaultStart < 0 -> when (token.text) {
                        "<" -> angles++
                        ">" -> angles--
                        "=" -> if (angles == 0) defaultStart = i + 1
                    }
                    token.text == "<" && isTypeArgumentStart(i) -> angles++
                    token.text == ">" && angles > 0 -> angles--
                }
                i++
            }
            if (name != null) {
                parameters[name] = if (defaultStart in 0 until i) {
                    ParameterSource(
                        name,
                        text.substring(tokens[defaultStart].start, tokens[i - 1].end),
                        referencedNames(tokens.subList(defaultStart, i)),
                    )
                } else {
                    ParameterSource(name, null, emptySet())
                }
            }
            if (tokens.getOrNull(i)?.text == ",") i++
        }
        return parameters
    }

    // `listOf<Int>()` is a type argument, `a < b` is a comparison.
    private fun isTypeArgumentStart(i: Int): Boolean =
        tokens[i - 1].kind == Kind.IDENTIFIER && tokens[i - 1].end == tokens[i].start

    private fun referencedNames(expression: List<Token>, names: MutableSet<String> = linkedSetOf()): Set<String> {
        for ((i, token) in expression.withIndex()) {
            val previous = expression.getOrNull(i - 1)?.text
            val isMember = previous in MEMBER_ACCESS ||
                (previous == "::" && expression.getOrNull(i - 2)?.let { it.kind == Kind.IDENTIFIER || it.text in CLOSING } == true)
            val isNamedArgument = expression.getOrNull(i + 1)?.text == "="
            if (token.kind == Kind.IDENTIFIER && token.name !in KEYWORDS && !isMember && !isNamedArgument) names += token.name
            if (token.kind == Kind.STRING) referencedNames(token.templates, names)
        }
        return names
    }

    private fun readImports(): List<String> {
        val imports = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            when {
                token.text == "@" -> { // @file:Annotation or @file:[A B]
                    i += 3
                    if (tokens.getOrNull(i)?.text == "[") {
                        i = matching(i) + 1
                    } else {
                        i++
                        while (tokens.getOrNull(i)?.text == ".") i += 2
                        if (tokens.getOrNull(i)?.text == "(") i = matching(i) + 1
                    }
                }
                token.isIdentifier("package") -> {
                    i += 2
                    while (tokens.getOrNull(i)?.text == ".") i += 2
                }
                token.isIdentifier("import") -> {
                    val path = StringBuilder(tokens[i + 1].text)
                    i += 2
                    while (tokens.getOrNull(i)?.text == "." && i + 1 < tokens.size) {
                        path.append('.').append(tokens[i + 1].text)
                        i += 2
                    }
                    if (tokens.getOrNull(i)?.isIdentifier("as") == true) {
                        path.append(" as ").append(tokens[i + 1].text)
                        i += 2
                    }
                    imports += path.toString()
                }
                token.text == ";" -> i++
                else -> break
            }
        }
        return imports
    }

    private fun matching(open: Int): Int {
        var depth = 0
        for (i in open until tokens.size) {
            when (tokens[i].text) {
                in OPENING -> depth++
                in CLOSING -> if (--depth == 0) return i
            }
        }
        return tokens.size
    }

    private fun matchingAngle(open: Int): Int {
        var depth = 0
        for (i in open until tokens.size) {
            when (tokens[i].text) {
                "<" -> depth++
                ">" -> if (--depth == 0) return i
            }
        }
        return tokens.size
    }

    private fun lineOffset(line: Int): Int {
        var offset = 0
        repeat(line - 1) {
            val next = text.indexOf('\n', offset)
            if (next < 0) return text.length
            offset = next + 1
        }
        return offset
    }

    private companion object {
        val OPENING = setOf("(", "[", "{")
        val CLOSING = setOf(")", "]", "}")
        val MEMBER_ACCESS = setOf(".", "?.")
        val CONSTRUCTOR_MODIFIERS = setOf("constructor", "private", "protected", "internal", "public")
        val KEYWORDS = setOf(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is",
            "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof",
            "val", "var", "when", "while", "catch", "finally",
        )
    }
}

private enum class Kind { IDENTIFIER, STRING, CHARACTER, NUMBER, OPERATOR }

private class Token(
    val kind: Kind,
    val text: String,
    val start: Int,
    val end: Int,
    /** For a string literal, the tokens of its template expressions (`$name` and `${...}`), in order. */
    val templates: List<Token> = emptyList(),
) {
    /** The identifier without backticks. */
    val name: String get() = text.removeSurrounding("`")

    fun isIdentifier(name: String) = kind == Kind.IDENTIFIER && text == name
}

/**
 * Splits Kotlin source into tokens, skipping whitespace and comments. Strings and characters are one
 * token each; the tokens of a string's templates are kept on the string token.
 */
private class Lexer(private val text: String) {

    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (true) {
            i = skipWhitespaceAndComments(i)
            if (i >= text.length) return tokens
            val token = next(i)
            tokens += token
            i = token.end
        }
    }

    private fun skipWhitespaceAndComments(start: Int): Int {
        var i = start
        while (i < text.length) {
            i = when {
                text[i].isWhitespace() -> i + 1
                text.startsWith("//", i) -> text.indexOf('\n', i).let { if (it < 0) text.length else it }
                text.startsWith("/*", i) -> skipBlockComment(i)
                else -> return i
            }
        }
        return i
    }

    private fun skipBlockComment(start: Int): Int {
        var depth = 0
        var i = start
        while (i < text.length) {
            when {
                text.startsWith("/*", i) -> { depth++; i += 2 }
                text.startsWith("*/", i) -> { i += 2; if (--depth == 0) return i }
                else -> i++
            }
        }
        return i
    }

    private fun next(start: Int): Token {
        val c = text[start]
        return when {
            text.startsWith("\"\"\"", start) -> string(start, raw = true)
            c == '"' -> string(start, raw = false)
            c == '\'' -> Token(Kind.CHARACTER, start, characterEnd(start + 1))
            c == '`' -> Token(Kind.IDENTIFIER, start, text.indexOf('`', start + 1).let { if (it < 0) text.length else it + 1 })
            Character.isJavaIdentifierStart(c) -> Token(Kind.IDENTIFIER, start, identifierEnd(start))
            c.isDigit() -> Token(Kind.NUMBER, start, numberEnd(start))
            else -> Token(Kind.OPERATOR, start, start + (OPERATORS.firstOrNull { text.startsWith(it, start) }?.length ?: 1))
        }
    }

    private fun Token(kind: Kind, start: Int, end: Int) = Token(kind, text.substring(start, end), start, end)

    private fun identifierEnd(start: Int): Int {
        var i = start + 1
        while (i < text.length && Character.isJavaIdentifierPart(text[i])) i++
        return i
    }

    private fun numberEnd(start: Int): Int {
        var i = start + 1
        while (i < text.length &&
            (text[i].isLetterOrDigit() || text[i] == '_' || (text[i] == '.' && text.getOrNull(i + 1)?.isDigit() == true))
        ) {
            i++
        }
        return i
    }

    // A string literal from its opening quote(s) at [start]. Template expressions are lexed into its [Token.templates].
    private fun string(start: Int, raw: Boolean): Token {
        val templates = mutableListOf<Token>()
        var i = start + if (raw) 3 else 1
        while (i < text.length) {
            when {
                raw && text.startsWith("\"\"\"", i) -> {
                    // Extra quotes before the closing `"""` belong to the string.
                    i += 3
                    while (i < text.length && text[i] == '"') i++
                    break
                }
                !raw && text[i] == '"' -> { i++; break }
                !raw && text[i] == '\\' -> i += 2
                text.startsWith("\${", i) -> i = template(i + 2, templates)
                text[i] == '$' && text.getOrNull(i + 1)?.let(Character::isJavaIdentifierStart) == true -> {
                    val end = identifierEnd(i + 1)
                    templates += Token(Kind.IDENTIFIER, i + 1, end)
                    i = end
                }
                else -> i++
            }
        }
        return Token(Kind.STRING, text.substring(start, i), start, i, templates)
    }

    // Lexes the expression of a `${` template, which starts at [start], into [tokens] (including the
    // tokens of nested templates). Returns the index after its closing `}`.
    private fun template(start: Int, tokens: MutableList<Token>): Int {
        var depth = 1
        var i = start
        while (true) {
            i = skipWhitespaceAndComments(i)
            if (i >= text.length) return i
            val token = next(i)
            i = token.end
            when (token.text) {
                "{" -> depth++
                "}" -> if (--depth == 0) return i
            }
            tokens += token
            tokens += token.templates
        }
    }

    private fun characterEnd(start: Int): Int {
        var i = start
        while (i < text.length) {
            when (text[i]) {
                '\\' -> i += 2
                '\'' -> return i + 1
                else -> i++
            }
        }
        return i
    }

    private companion object {
        // Longest first. `>>` is deliberately missing so nested type arguments close one by one.
        val OPERATORS = listOf(
            "===", "!==", "..<",
            "->", "<=", ">=", "==", "!=", "+=", "-=", "*=", "/=", "%=", "&&", "||", "::", "?.", "?:", "..", "!!", "++", "--",
        )
    }
}
