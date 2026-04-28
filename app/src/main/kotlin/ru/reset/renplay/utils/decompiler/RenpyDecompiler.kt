package ru.reset.renplay.utils.decompiler

import java.io.Writer

/**
 * Decompiles a Ren'Py AST (list of [AstNode] objects) back to .rpy script format.
 */
class RenpyDecompiler(
    private val writer: Writer,
    private val indentation: String = "    "
) {
    private var lineNumber = 1
    private var indentLevel = 0
    private var skipIndentUntilWrite = false
    private var pairedWith: Any? = false
    private var sayInsideMenu: AstNode? = null
    private var labelInsideMenu: AstNode? = null
    private var inInit = false
    private var missingInit = false
    private var initOffset = 0
    private var seenLabel = false

    private val blockStack = mutableListOf<List<Any?>>()
    private val indexStack = mutableListOf<Int>()

    private val block: List<Any?> get() = blockStack.last()
    private val index: Int get() = indexStack.last()
    private val parent: Any?
        get() {
            if (blockStack.size < 2) return null
            return blockStack[blockStack.size - 2][indexStack[indexStack.size - 2]]
        }

    fun decompile(ast: List<Any?>) {
        detectInitOffset(ast)
        skipIndentUntilWrite = true
        printNodes(ast)
    }

    /**
     * Heuristic: scan all Init nodes to find the most common offset,
     * then emit `init offset = X` before the first statement.
     */
    private fun detectInitOffset(ast: List<Any?>) {
        val votes = mutableMapOf<Int, Int>()
        for (node in ast) {
            if (node !is AstNode || !isClass(node, "Init")) continue
            val block = node.getNodeList("block")
            var offset = node.getInt("priority", 0)
            if (block.size == 1) {
                val child = block[0]
                when {
                    isClass(child, "Screen") -> offset -= -500
                    isClass(child, "Testcase") -> offset -= 500
                    isClass(child, "Image") -> offset -= 500
                }
            }
            votes[offset] = (votes[offset] ?: 0) + 1
        }
        if (votes.isNotEmpty()) {
            val winner = votes.maxByOrNull { it.value }!!
            val zeroVotes = votes[0] ?: 0
            if (zeroVotes + 1 < winner.value) {
                initOffset = winner.key
            }
        }
    }

    // --- Core write/indent ---

    private fun write(s: String) {
        lineNumber += s.count { it == '\n' }
        skipIndentUntilWrite = false
        writer.write(s)
    }

    private fun indent() {
        if (!skipIndentUntilWrite) {
            write("\n" + indentation.repeat(indentLevel))
        }
    }

    private fun advanceToLine(target: Int) {
        if (lineNumber < target) {
            write("\n".repeat(target - lineNumber - 1))
        }
    }

    // --- Node printing ---

    private var initOffsetEmitted = false

    private fun printNodes(ast: List<Any?>, extraIndent: Int = 0) {
        indentLevel += extraIndent
        blockStack.add(ast)
        indexStack.add(0)

        for (i in ast.indices) {
            indexStack[indexStack.lastIndex] = i
            val node = ast[i]
            if (node is AstNode) {
                // Emit init offset directive before first real statement at top level
                if (!initOffsetEmitted && initOffset != 0 && blockStack.size == 1 && indentLevel == 0) {
                    initOffsetEmitted = true
                    indent()
                    write("init offset = $initOffset")
                }
                printNode(node)
            }
        }

        blockStack.removeLast()
        indexStack.removeLast()
        indentLevel -= extraIndent
    }

    private fun printNode(ast: AstNode) {
        // Handle say-inside-menu
        if (isClass(ast, "Say")) {
            if (handleSayPossiblyInsideMenu(ast)) return
        }

        // Advance to the correct line for most node types
        val ln = ast.getInt("linenumber", -1)
        if (ln > 0 && !isClass(ast, "TranslateString", "With", "Label", "Pass", "Return")) {
            advanceToLine(ln)
        }

        when {
            isClass(ast, "Say") -> printSay(ast)
            isClass(ast, "TranslateSay") -> printTranslateSay(ast)
            isClass(ast, "Label") -> printLabel(ast)
            isClass(ast, "Jump") -> printJump(ast)
            isClass(ast, "Call") -> printCall(ast)
            isClass(ast, "Return") -> printReturn(ast)
            isClass(ast, "If") -> printIf(ast)
            isClass(ast, "While") -> printWhile(ast)
            isClass(ast, "Pass") -> printPass(ast)
            isClass(ast, "Init") -> printInit(ast)
            isClass(ast, "Menu") -> printMenu(ast)
            isClass(ast, "Python") -> printPython(ast, early = false)
            isClass(ast, "EarlyPython") -> printPython(ast, early = true)
            isClass(ast, "Show") -> printShow(ast)
            isClass(ast, "ShowLayer") -> printShowLayer(ast)
            isClass(ast, "Scene") -> printScene(ast)
            isClass(ast, "Hide") -> printHide(ast)
            isClass(ast, "With") -> printWith(ast)
            isClass(ast, "Camera") -> printCamera(ast)
            isClass(ast, "Image") -> printImage(ast)
            isClass(ast, "Transform") -> printTransform(ast)
            isClass(ast, "Define") -> printDefine(ast)
            isClass(ast, "Default") -> printDefault(ast)
            isClass(ast, "Screen") -> printScreen(ast)
            isClass(ast, "UserStatement") -> printUserStatement(ast)
            isClass(ast, "Style") -> printStyle(ast)
            isClass(ast, "Translate") -> printTranslate(ast)
            isClass(ast, "EndTranslate") -> { /* implicit node, skip */ }
            isClass(ast, "TranslateString") -> printTranslateString(ast)
            isClass(ast, "TranslateBlock") -> printTranslateBlock(ast)
            isClass(ast, "TranslateEarlyBlock") -> printTranslateBlock(ast)
            isClass(ast, "TranslatePython") -> printTranslateBlock(ast)
            isClass(ast, "RPY") -> printRpy(ast)
            isClass(ast, "Testcase") -> printTestcase(ast)
            else -> printUnknown(ast)
        }
    }

    // --- Helpers ---

    private fun isClass(node: AstNode, vararg names: String): Boolean {
        val cls = node.className
        return names.any { cls.endsWith(".$it") }
    }

    private fun requireInit() {
        if (!inInit) missingInit = true
    }

    private fun stringEscape(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
    }

    private fun encodeSayString(s: String): String {
        var r = s.replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\"", "\\\"")
        r = r.replace(Regex("(?<= ) "), "\\\\ ")
        return "\"$r\""
    }

    private fun getPyExprValue(obj: Any?): String? {
        if (obj == null) return null
        if (obj is AstNode && obj.attrs["__is_pyexpr__"] == true) {
            return obj.getString("value") ?: obj.toString()
        }
        if (obj is String) return obj
        return obj.toString()
    }

    private fun getSource(codeNode: Any?): String? {
        if (codeNode is AstNode) {
            val source = codeNode["source"]
            if (source is AstNode && source.attrs["__is_pyexpr__"] == true) {
                return source.getString("value")
            }
            if (source is String) return source
            return source?.toString()
        }
        return codeNode?.toString()
    }

    private fun reconstructParamInfo(paraminfo: Any?): String {
        if (paraminfo == null) return ""
        if (paraminfo is AstNode) {
            // Try the different paraminfo formats
            val params = paraminfo["parameters"]
            // Simplified: just try to output "(params)" if available
            if (params is Map<*, *>) {
                val sb = StringBuilder("(")
                var first = true
                for (entry in params.values) {
                    if (!first) sb.append(", ")
                    first = false
                    if (entry is AstNode) {
                        sb.append(entry.getString("name") ?: "")
                        val default = entry.getString("default")
                        if (default != null) sb.append("=$default")
                    }
                }
                sb.append(")")
                return sb.toString()
            }
            if (params is List<*>) {
                val sb = StringBuilder("(")
                var first = true
                for (entry in params) {
                    if (entry is List<*> && entry.size >= 2) {
                        if (!first) sb.append(", ")
                        first = false
                        sb.append(entry[0] ?: "")
                        if (entry[1] != null) sb.append("=${entry[1]}")
                    }
                }
                // Handle extrapos/extrakw
                val extrapos = paraminfo.getString("extrapos")
                if (extrapos != null) { if (!first) sb.append(", "); first = false; sb.append("*$extrapos") }
                val extrakw = paraminfo.getString("extrakw")
                if (extrakw != null) { if (!first) sb.append(", "); sb.append("**$extrakw") }
                sb.append(")")
                return sb.toString()
            }
        }
        return ""
    }

    private fun reconstructArgInfo(arginfo: Any?): String {
        if (arginfo == null) return ""
        if (arginfo is AstNode) {
            val args = arginfo.getList("arguments")
            if (args.isEmpty()) return "()"
            val sb = StringBuilder("(")
            var first = true
            for (arg in args) {
                if (arg is List<*> && arg.size >= 2) {
                    if (!first) sb.append(", ")
                    first = false
                    val name = arg[0]
                    val value = getPyExprValue(arg[1]) ?: arg[1]?.toString() ?: ""
                    if (name != null) {
                        sb.append("$name=$value")
                    } else {
                        sb.append(value)
                    }
                }
            }
            sb.append(")")
            return sb.toString()
        }
        return ""
    }

    @Suppress("UNCHECKED_CAST")
    private fun getImspec(node: AstNode): List<Any?> {
        val imspec = node["imspec"] ?: return emptyList()
        return imspec as? List<Any?> ?: emptyList()
    }

    private fun printImspec(imspec: List<Any?>): Boolean {
        if (imspec.isEmpty()) return false
        // imspec = (name_tuple, expression, tag, at_list, layer, zorder, behind)
        val nameTuple = imspec.getOrNull(0)
        val expression = imspec.getOrNull(1)
        val tag = imspec.getOrNull(2)
        val atList = imspec.getOrNull(3)
        val layer = imspec.getOrNull(4)
        val zorder = imspec.getOrNull(5)
        val behind = imspec.getOrNull(6)

        val begin = if (expression != null && expression.toString().isNotEmpty()) {
            "expression ${getPyExprValue(expression) ?: expression}"
        } else {
            val names = (nameTuple as? List<*>)?.joinToString(" ") ?: nameTuple?.toString() ?: ""
            names
        }
        write(begin)

        val words = mutableListOf<String>()
        if (tag != null && tag.toString().isNotEmpty()) words.add("as $tag")
        if (behind is List<*> && behind.isNotEmpty()) words.add("behind ${behind.joinToString(", ")}")
        if (layer is String && layer.isNotEmpty()) words.add("onlayer $layer")
        if (zorder != null && zorder.toString() != "null") words.add("zorder $zorder")
        if (atList is List<*> && atList.isNotEmpty()) words.add("at ${atList.joinToString(", ")}")

        if (words.isNotEmpty()) {
            write(" ${words.joinToString(" ")}")
        }
        return true
    }

    // --- Say ---

    private fun sayGetCode(ast: AstNode, inmenu: Boolean = false): String {
        val rv = mutableListOf<String>()
        val who = ast.getString("who")
        if (who != null) rv.add(who)

        val attrs = ast.getList("attributes")
        if (attrs.isNotEmpty()) {
            for (a in attrs) rv.add(a.toString())
        }
        val tempAttrs = ast.getList("temporary_attributes")
        if (tempAttrs.isNotEmpty()) {
            rv.add("@")
            for (a in tempAttrs) rv.add(a.toString())
        }

        val what = ast.getString("what") ?: ""
        rv.add(encodeSayString(what))

        val interact = ast.getBool("interact", true)
        if (!interact && !inmenu) rv.add("nointeract")

        val explicitId = ast.getBool("explicit_identifier", false)
        val identifier = ast.getString("identifier")
        if (explicitId && identifier != null) {
            rv.add("id"); rv.add(identifier)
        } else if (identifier != null) {
            rv.add("id"); rv.add(identifier)
        }

        val arguments = ast["arguments"]
        if (arguments != null) {
            rv.add(reconstructArgInfo(arguments))
        }

        val with = ast.getString("with_")
        if (with != null) { rv.add("with"); rv.add(with) }

        return rv.joinToString(" ")
    }

    private fun printSay(ast: AstNode, inmenu: Boolean = false) {
        indent()
        write(sayGetCode(ast, inmenu))
    }

    private fun printTranslateSay(ast: AstNode) {
        val language = ast.getString("language")
        val identifier = ast.getString("identifier") ?: ""
        if (language != null) {
            indent()
            write("translate $language $identifier:")
            indentLevel++
            printSay(ast, true)
            indentLevel--
        } else {
            printSay(ast)
        }
    }

    // --- Say inside menu ---

    private fun sayBelongsToMenu(say: AstNode, menu: AstNode): Boolean {
        if (say.getBool("interact", true)) return false
        if (say.getString("who") == null) return false
        if (say.getString("with_") != null) return false
        if (say.getList("attributes").isNotEmpty()) return false
        if (!isClass(menu, "Menu")) return false
        val items = menu.getList("items")
        if (items.isEmpty()) return false
        val firstItem = items[0]
        if (firstItem is List<*> && firstItem.size >= 3 && firstItem[2] != null) {
            return true
        }
        return false
    }

    private fun handleSayPossiblyInsideMenu(ast: AstNode): Boolean {
        if (index + 1 < block.size) {
            val nextNode = block[index + 1]
            if (nextNode is AstNode && isClass(nextNode, "Menu") && sayBelongsToMenu(ast, nextNode)) {
                sayInsideMenu = ast
                return true
            }
        }
        return false
    }

    // --- Label ---

    private fun printLabel(ast: AstNode) {
        seenLabel = true

        // If preceded by a Call, it was printed as "from"
        if (index > 0) {
            val prev = block[index - 1]
            if (prev is AstNode && isClass(prev, "Call")) return
        }

        val astBlock = ast.getNodeList("block")
        val params = ast["parameters"]
        val name = ast.getString("name") ?: ast.getString("_name") ?: ""

        // Check if this is a label for a menu
        if (astBlock.isEmpty() && params == null) {
            val remaining = block.size - index
            if (remaining > 1) {
                val next = block[index + 1]
                if (next is AstNode && isClass(next, "Menu")
                    && next.getInt("linenumber") == ast.getInt("linenumber")) {
                    labelInsideMenu = ast
                    return
                }
            }
        }

        val ln = ast.getInt("linenumber", -1)
        if (ln > 0) advanceToLine(ln)
        indent()

        val paramStr = reconstructParamInfo(params)
        val hide = if (ast.getBool("hide")) " hide" else ""
        write("label $name$paramStr$hide:")
        if (astBlock.isNotEmpty()) {
            printNodes(astBlock.toList(), 1)
        }
    }

    // --- Flow control ---

    private fun printJump(ast: AstNode) {
        indent()
        val expr = if (ast.getBool("expression")) "expression " else ""
        write("jump $expr${ast.getString("target") ?: ""}")
    }

    private fun printCall(ast: AstNode) {
        indent()
        val words = mutableListOf("call")
        if (ast.getBool("expression")) words.add("expression")
        words.add(ast.getString("label") ?: "")

        val arguments = ast["arguments"]
        if (arguments != null) {
            if (ast.getBool("expression")) words.add("pass")
            words.add(reconstructArgInfo(arguments))
        }

        // Check for "from" label
        if (index + 1 < block.size) {
            val next = block[index + 1]
            if (next is AstNode && isClass(next, "Label")) {
                val labelName = next.getString("name") ?: next.getString("_name") ?: ""
                if (labelName.isNotEmpty()) words.add("from $labelName")
            }
        }

        write(words.joinToString(" "))
    }

    private fun printReturn(ast: AstNode) {
        val expression = ast["expression"]

        // Strip auto-appended returns at end of file
        if (expression == null && blockStack.size <= 1 && index + 1 == block.size && index > 0) {
            val prev = block[index - 1]
            if (prev is AstNode) {
                if (prev.getInt("linenumber") == ast.getInt("linenumber")
                    || isClass(prev, "Return", "Jump")
                    || !seenLabel
                ) return
            }
        }

        val ln = ast.getInt("linenumber", -1)
        if (ln > 0) advanceToLine(ln)
        indent()
        write("return")
        if (expression != null) {
            val expr = getPyExprValue(expression) ?: expression.toString()
            write(" $expr")
        }
    }

    private fun printIf(ast: AstNode) {
        val entries = ast.getList("entries")
        var isFirst = true
        for (i in entries.indices) {
            val entry = entries[i]
            if (entry !is List<*> || entry.size < 2) continue
            val condition = entry[0]
            val entryBlock = entry[1]

            val isLast = (i + 1 == entries.size)
            val condValue = getPyExprValue(condition)

            // "else" branch: last entry where condition is plain "True" (not PyExpr)
            if (isLast && condition is String && condition == "True") {
                indent()
                write("else:")
            } else if (isLast && condValue == "True" && condition !is AstNode) {
                indent()
                write("else:")
            } else {
                val keyword = if (isFirst) "if" else "elif"
                val condLn = if (condition is AstNode) condition.getInt("linenumber", -1) else -1
                if (condLn > 0) advanceToLine(condLn)
                indent()
                write("$keyword ${condValue ?: condition}:")
            }
            isFirst = false

            if (entryBlock is List<*>) {
                printNodes(entryBlock, 1)
            }
        }
    }

    private fun printWhile(ast: AstNode) {
        indent()
        val cond = getPyExprValue(ast["condition"]) ?: ast.getString("condition") ?: "True"
        write("while $cond:")
        printNodes(ast.getNodeList("block").toList(), 1)
    }

    private fun printPass(ast: AstNode) {
        // Skip if preceded by Call
        if (index > 0) {
            val prev = block[index - 1]
            if (prev is AstNode && isClass(prev, "Call")) return
        }
        // Skip if preceded by Call + Label on same line
        if (index > 1) {
            val prev2 = block[index - 2]
            val prev1 = block[index - 1]
            if (prev2 is AstNode && isClass(prev2, "Call")
                && prev1 is AstNode && isClass(prev1, "Label")
                && prev2.getInt("linenumber") == ast.getInt("linenumber")
            ) return
        }

        val ln = ast.getInt("linenumber", -1)
        if (ln > 0) advanceToLine(ln)
        indent()
        write("pass")
    }

    // --- Init ---

    private fun printInit(ast: AstNode) {
        val prevInInit = inInit
        inInit = true
        try {
            val astBlock = ast.getNodeList("block")
            val priority = ast.getInt("priority", 0)

            // Check for implicit init blocks
            if (astBlock.size == 1) {
                val child = astBlock[0]
                val canBeImplicit = isClass(child, "Define", "Default", "Transform") ||
                        (priority == -500 + initOffset && isClass(child, "Screen")) ||
                        (priority == initOffset && isClass(child, "Style")) ||
                        (priority == 500 + initOffset && isClass(child, "Testcase")) ||
                        (priority == 0 + initOffset && isClass(child, "UserStatement") &&
                                (child.getString("line") ?: "").startsWith("layeredimage ")) ||
                        (priority == 500 + initOffset && isClass(child, "Image"))

                if (canBeImplicit) {
                    printNodes(astBlock.toList())
                    return
                }
            }

            // Check for translatestring blocks
            if (astBlock.isNotEmpty() && priority == initOffset
                && astBlock.all { isClass(it, "TranslateString") }
            ) {
                printNodes(astBlock.toList())
                return
            }

            // Explicit init block
            indent()
            write("init")
            if (priority != initOffset) {
                write(" ${priority - initOffset}")
            }

            if (astBlock.size == 1) {
                write(" ")
                skipIndentUntilWrite = true
                printNodes(astBlock.toList())
            } else {
                write(":")
                printNodes(astBlock.toList(), 1)
            }
        } finally {
            inInit = prevInInit
        }
    }

    // --- Menu ---

    private fun printMenu(ast: AstNode) {
        indent()
        write("menu")

        if (labelInsideMenu != null) {
            val name = labelInsideMenu!!.getString("name") ?: labelInsideMenu!!.getString("_name") ?: ""
            if (name.isNotEmpty()) write(" $name")
            labelInsideMenu = null
        }

        val arguments = ast["arguments"]
        if (arguments != null) {
            write(reconstructArgInfo(arguments))
        }

        write(":")

        indentLevel++

        val withClause = ast.getString("with_")
        if (withClause != null) {
            indent()
            write("with $withClause")
        }

        val setClause = ast.getString("set")
        if (setClause != null) {
            indent()
            write("set $setClause")
        }

        val items = ast.getList("items")
        val itemArguments = ast.getList("item_arguments")

        for (i in items.indices) {
            val item = items[i] as? List<*> ?: continue
            if (item.size < 3) continue

            val label = item[0]?.toString() ?: ""
            val condition = item[1]
            val itemBlock = item[2]
            val args = itemArguments.getOrNull(i)

            // Print say-inside-menu if needed
            if (sayInsideMenu != null) {
                printSay(sayInsideMenu!!, true)
                sayInsideMenu = null
            }

            // Advance to correct line for blank line spacing between menu items
            val itemLineNum = if (condition is AstNode) condition.getInt("linenumber", 0) else 0
            if (itemLineNum > 0) advanceToLine(itemLineNum)

            indent()
            write("\"${stringEscape(label)}\"")

            if (args != null) {
                write(reconstructArgInfo(args))
            }

            if (itemBlock != null) {
                val condValue = getPyExprValue(condition)
                if (condition is AstNode || (condValue != null && condValue != "True")) {
                    write(" if ${condValue ?: condition}")
                }
                write(":")
                if (itemBlock is List<*>) {
                    printNodes(itemBlock, 1)
                }
            }
        }

        if (sayInsideMenu != null) {
            printSay(sayInsideMenu!!, true)
            sayInsideMenu = null
        }

        indentLevel--
    }

    // --- Python ---

    private fun printPython(ast: AstNode, early: Boolean) {
        indent()
        val codeNode = ast.getNode("code")
        val code = codeNode?.getString("source") ?: getSource(ast["code"]) ?: ""

        val indented = code.isNotEmpty() && code[0] == ' '
        val leadingNewline = code.isNotEmpty() && code[0] == '\n'

        if (!(indented || leadingNewline)) {
            write("$ $code")
            return
        }

        val cleanCode = if (leadingNewline) code.substring(1) else code

        write("python")
        if (early) write(" early")
        if (ast.getBool("hide")) write(" hide")
        val store = ast.getString("store") ?: "store"
        if (store != "store") {
            write(" in ${store.removePrefix("store.")}")
        }
        write(":")

        if (indented) {
            write("\n$cleanCode")
        } else {
            indentLevel++
            for (line in splitLogicalLines(cleanCode)) {
                if (line.isEmpty()) {
                    write("\n")
                } else {
                    indent()
                    write(line)
                }
            }
            indentLevel--
        }
    }

    private fun splitLogicalLines(s: String): List<String> {
        val lines = mutableListOf<String>()
        var start = 0
        var contained = 0
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\n' && contained == 0 && (i == 0 || s[i - 1] != '\\')) {
                lines.add(s.substring(start, i))
                i++
                start = i
                continue
            }
            if (c in "([{") { contained++; i++; continue }
            if (c in ")]}" && contained > 0) { contained--; i++; continue }
            i++
        }
        if (i != start) lines.add(s.substring(start))
        return lines
    }

    // --- Show/Scene/Hide/With ---

    private fun printShow(ast: AstNode) {
        indent()
        write("show ")
        val imspec = getImspec(ast)
        printImspec(imspec)
        val atl = ast.getNode("atl")
        if (atl != null) {
            write(":")
            printAtlRaw(atl)
        }
    }

    private fun printShowLayer(ast: AstNode) {
        indent()
        val layer = ast.getString("layer") ?: "master"
        write("show layer $layer")
        val atList = ast.getList("at_list")
        if (atList.isNotEmpty()) {
            write(" at ${atList.joinToString(", ")}")
        }
        val atl = ast.getNode("atl")
        if (atl != null) {
            write(":")
            printAtlRaw(atl)
        }
    }

    private fun printScene(ast: AstNode) {
        indent()
        write("scene")
        val imspec = getImspec(ast)
        if (imspec.isEmpty()) {
            val layer = ast.getString("layer")
            if (layer is String) write(" onlayer $layer")
        } else {
            write(" ")
            printImspec(imspec)
        }
        val atl = ast.getNode("atl")
        if (atl != null) {
            write(":")
            printAtlRaw(atl)
        }
    }

    private fun printHide(ast: AstNode) {
        indent()
        write("hide ")
        printImspec(getImspec(ast))
    }

    private fun printWith(ast: AstNode) {
        val paired = ast["paired"]
        if (paired != null && paired.toString() != "null") {
            pairedWith = paired
        } else if (pairedWith != null && pairedWith != false) {
            if (pairedWith != true) {
                write(" with ${getPyExprValue(ast["expr"]) ?: ast.getString("expr") ?: ""}")
            }
            pairedWith = false
        } else {
            val ln = ast.getInt("linenumber", -1)
            if (ln > 0) advanceToLine(ln)
            indent()
            write("with ${getPyExprValue(ast["expr"]) ?: ast.getString("expr") ?: ""}")
            pairedWith = false
        }
    }

    private fun printCamera(ast: AstNode) {
        indent()
        write("camera")
        val layer = ast.getString("layer") ?: "master"
        if (layer != "master") write(" $layer")
        val atList = ast.getList("at_list")
        if (atList.isNotEmpty()) write(" at ${atList.joinToString(", ")}")
        val atl = ast.getNode("atl")
        if (atl != null) {
            write(":")
            printAtlRaw(atl)
        }
    }

    // --- Image / Transform ---

    private fun printImage(ast: AstNode) {
        requireInit()
        indent()
        val imgname = ast.getList("imgname").joinToString(" ")
        write("image $imgname")
        val code = ast["code"]
        if (code != null) {
            val source = getSource(code)
            if (source != null) write(" = $source")
        } else {
            val atl = ast.getNode("atl")
            if (atl != null) {
                write(":")
                printAtlRaw(atl)
            }
        }
    }

    private fun printTransform(ast: AstNode) {
        requireInit()
        indent()
        val varname = ast.getString("varname") ?: ""
        write("transform $varname")
        val params = ast["parameters"]
        if (params != null) write(reconstructParamInfo(params))
        val atl = ast.getNode("atl")
        if (atl != null) {
            write(":")
            printAtlRaw(atl)
        }
    }

    // --- Define / Default ---

    private fun printDefine(ast: AstNode) {
        requireInit()
        indent()
        val store = ast.getString("store") ?: "store"
        val varname = ast.getString("varname") ?: ""
        val operator = ast.getString("operator") ?: "="
        val code = getSource(ast["code"]) ?: ast.getNode("code")?.getString("source") ?: ""
        val index = ast.getNode("index")
        val indexStr = if (index != null) "[${getSource(index) ?: ""}]" else ""

        if (store == "store") {
            write("define $varname$indexStr $operator $code")
        } else {
            write("define ${store.removePrefix("store.")}.$varname$indexStr $operator $code")
        }
    }

    private fun printDefault(ast: AstNode) {
        requireInit()
        indent()
        val store = ast.getString("store") ?: "store"
        val varname = ast.getString("varname") ?: ""
        val code = getSource(ast["code"]) ?: ast.getNode("code")?.getString("source") ?: ""

        if (store == "store") {
            write("default $varname = $code")
        } else {
            write("default ${store.removePrefix("store.")}.$varname = $code")
        }
    }

    // --- Screen ---

    private fun printScreen(ast: AstNode) {
        requireInit()
        val screen = ast.getNode("screen") ?: ast["screen"]
        if (screen is AstNode) {
            // Try to output SL2 screen
            printSl2Screen(screen)
        } else {
            indent()
            write("pass # Screen (could not decompile)")
        }
    }

    private fun printSl2Screen(screen: AstNode) {
        indent()
        val name = screen.getString("name") ?: ""
        write("screen $name")
        val params = screen["parameters"]
        if (params != null) write(reconstructParamInfo(params))

        // Screen keywords go into the block body, not on the declaration line
        val keyword = screen.getList("keyword")
        write(":")

        val children = screen.getList("children")
        indentLevel++

        // Build a unified list of block items (keywords + children) sorted by line number
        data class BlockItem(val lineNum: Int, val type: String, val data: Any)
        val items = mutableListOf<BlockItem>()

        // tag is a separate attribute on the screen node
        val tag = screen.getString("tag")
        if (tag != null && tag != name) {
            // tag goes right after the screen declaration, before anything else
            items.add(BlockItem(0, "tag", tag))
        }

        // style_prefix is a separate attribute on the screen node
        val stylePrefix = screen.getString("style_prefix")
            ?: screen.getString("style_group")

        // Add screen keywords
        for (kw in keyword) {
            if (kw !is List<*> || kw.size < 2) continue
            val key = kw[0]?.toString() ?: continue
            val rawValue = kw[1] ?: continue
            val value = getPyExprValue(rawValue) ?: rawValue.toString()
            val kwLine = if (rawValue is AstNode) rawValue.getInt("linenumber", 0) else 0
            items.add(BlockItem(kwLine, "keyword", "$key $value"))
        }

        // Add children
        for (child in children) {
            if (child is AstNode) {
                val childLine = getSl2Location(child)
                items.add(BlockItem(childLine, "child", child))
            }
        }

        // Sort by line number (tag at 0 goes first, then by source line)
        items.sortBy { it.lineNum }

        var emittedStylePrefix = false
        if (items.isEmpty() && stylePrefix == null) {
            indent()
            write("pass")
        } else {
            for (item in items) {
                // Emit style_prefix before the first non-tag item
                if (!emittedStylePrefix && stylePrefix != null && item.type != "tag") {
                    emittedStylePrefix = true
                    indent()
                    write("style_prefix \"$stylePrefix\"")
                }
                when (item.type) {
                    "tag" -> { indent(); write("tag ${item.data}") }
                    "keyword" -> {
                        if (item.lineNum > 0) advanceToLine(item.lineNum)
                        indent()
                        write(item.data as String)
                    }
                    "child" -> printSl2Node(item.data as AstNode)
                }
            }
            if (!emittedStylePrefix && stylePrefix != null) {
                indent()
                write("style_prefix \"$stylePrefix\"")
            }
        }
        indentLevel--
    }

    private fun printSl2Node(node: AstNode) {
        // Advance to correct line for blank line spacing
        val ln = getSl2Location(node)
        if (ln > 0) advanceToLine(ln)

        when {
            node.className.contains("SLDisplayable") -> printSl2Displayable(node)
            node.className.contains("SLShowIf") -> printSl2If(node, "showif")
            node.className.contains("SLIf") -> printSl2If(node, "if")
            node.className.contains("SLFor") -> printSl2For(node)
            node.className.contains("SLPython") -> printSl2Python(node)
            node.className.contains("SLUse") -> printSl2Use(node)
            node.className.contains("SLDefault") -> printSl2Default(node)
            node.className.contains("SLPass") -> { indent(); write("pass") }
            node.className.contains("SLContinue") -> { indent(); write("continue") }
            node.className.contains("SLBreak") -> { indent(); write("break") }
            node.className.contains("SLTransclude") -> { indent(); write("transclude") }
            node.className.contains("SLBlock") -> {
                // style_prefix for the block
                val stylePrefix = node.getString("style_prefix")
                    ?: node.getString("style_group")
                if (stylePrefix != null) {
                    indent()
                    write("style_prefix \"$stylePrefix\"")
                }
                // Interleave keywords and children by line number
                val blockKeyword = node.getList("keyword")
                val children = node.getList("children")
                data class SlBlockItem(val lineNum: Int, val isKw: Boolean, val kwText: String?, val child: AstNode?)
                val items = mutableListOf<SlBlockItem>()
                for (kw in blockKeyword) {
                    if (kw is List<*> && kw.size >= 2) {
                        val key = kw[0]?.toString() ?: continue
                        val value = kw[1] ?: continue
                        val valueStr = getPyExprValue(value) ?: value.toString()
                        val kwLine = if (value is AstNode) (value as AstNode).getInt("linenumber", 0) else 0
                        items.add(SlBlockItem(kwLine, true, "$key $valueStr", null))
                    }
                }
                for (child in children) {
                    if (child is AstNode) items.add(SlBlockItem(getSl2Location(child), false, null, child))
                }
                items.sortBy { it.lineNum }
                for (item in items) {
                    if (item.isKw) {
                        if (item.lineNum > 0) advanceToLine(item.lineNum)
                        indent()
                        write(item.kwText!!)
                    } else {
                        printSl2Node(item.child!!)
                    }
                }
            }
            else -> { indent(); write("pass # SL2: ${node.className}") }
        }
    }

    /**
     * Maps (displayable_class_name, style) -> display name for SL2.
     * The displayable field in pickle is stored as a callable reference string.
     */
    companion object {
        private val DISPLAYABLE_NAMES: Map<Pair<String, String?>, Pair<String, Any>> = mapOf(
            // behavior
            Pair("renpy.display.behavior.AreaPicker", "default") to Pair("areapicker", 1),
            Pair("renpy.display.behavior.Button", "button") to Pair("button", 1),
            Pair("renpy.display.behavior.DismissBehavior", "default") to Pair("dismiss", 0),
            Pair("renpy.display.behavior.Input", "input") to Pair("input", 0),
            Pair("renpy.display.behavior.MouseArea", "0") to Pair("mousearea", 0),
            Pair("renpy.display.behavior.MouseArea", null) to Pair("mousearea", 0),
            Pair("renpy.display.behavior.OnEvent", "0") to Pair("on", 0),
            Pair("renpy.display.behavior.OnEvent", null) to Pair("on", 0),
            Pair("renpy.display.behavior.Timer", "default") to Pair("timer", 0),
            // dragdrop
            Pair("renpy.display.dragdrop.Drag", "drag") to Pair("drag", 1),
            Pair("renpy.display.dragdrop.Drag", null) to Pair("drag", 1),
            Pair("renpy.display.dragdrop.DragGroup", null) to Pair("draggroup", "many"),
            // im
            Pair("renpy.display.im.image", "default") to Pair("image", 0),
            // layout
            Pair("renpy.display.layout.Grid", "grid") to Pair("grid", "many"),
            Pair("renpy.display.layout.MultiBox", "fixed") to Pair("fixed", "many"),
            Pair("renpy.display.layout.MultiBox", "hbox") to Pair("hbox", "many"),
            Pair("renpy.display.layout.MultiBox", "vbox") to Pair("vbox", "many"),
            Pair("renpy.display.layout.NearRect", "default") to Pair("nearrect", 1),
            Pair("renpy.display.layout.Null", "default") to Pair("null", 0),
            Pair("renpy.display.layout.Side", "side") to Pair("side", "many"),
            Pair("renpy.display.layout.Window", "frame") to Pair("frame", 1),
            Pair("renpy.display.layout.Window", "window") to Pair("window", 1),
            // motion
            Pair("renpy.display.motion.Transform", "transform") to Pair("transform", 1),
            // sldisplayables
            Pair("renpy.sl2.sldisplayables.sl2add", null) to Pair("add", 0),
            Pair("renpy.sl2.sldisplayables.sl2bar", null) to Pair("bar", 0),
            Pair("renpy.sl2.sldisplayables.sl2vbar", null) to Pair("vbar", 0),
            Pair("renpy.sl2.sldisplayables.sl2viewport", "viewport") to Pair("viewport", 1),
            Pair("renpy.sl2.sldisplayables.sl2vpgrid", "vpgrid") to Pair("vpgrid", "many"),
            // text
            Pair("renpy.text.text.Text", "text") to Pair("text", 0),
            // transform
            Pair("renpy.display.transform.Transform", "transform") to Pair("transform", 1),
            // ui
            Pair("renpy.ui._add", null) to Pair("add", 0),
            Pair("renpy.ui._hotbar", "hotbar") to Pair("hotbar", 0),
            Pair("renpy.ui._hotspot", "hotspot") to Pair("hotspot", 1),
            Pair("renpy.ui._imagebutton", "image_button") to Pair("imagebutton", 0),
            Pair("renpy.ui._imagemap", "imagemap") to Pair("imagemap", "many"),
            Pair("renpy.ui._key", null) to Pair("key", 0),
            Pair("renpy.ui._label", "label") to Pair("label", 0),
            Pair("renpy.ui._textbutton", "button") to Pair("textbutton", 0),
            Pair("renpy.ui._textbutton", "0") to Pair("textbutton", 0),
        )
    }

    /**
     * Resolves the display name for an SLDisplayable node.
     * The displayable field is stored as a callable reference or AstNode.
     */
    private fun resolveDisplayableName(node: AstNode): Pair<String, Any> {
        val displayableRaw = node["displayable"]
        val style = node.getString("style")

        // Extract the class name from the displayable reference
        val className = when (displayableRaw) {
            is String -> displayableRaw.removePrefix("__callable__:")
            is AstNode -> displayableRaw.className
            else -> ""
        }

        // Look up in the displayable names map
        val result = DISPLAYABLE_NAMES[Pair(className, style)]
            ?: DISPLAYABLE_NAMES[Pair(className, null)]

        if (result != null) return result

        // Fallback: use the style name if available, otherwise extract last part of class name
        val fallbackName = if (style != null && style != "0" && style != "null") {
            style
        } else {
            className.substringAfterLast('.')
        }
        return Pair(fallbackName, "many")
    }

    private data class Sl2Kw(val key: String, val value: String, val lineNum: Int)

    private fun printSl2Displayable(node: AstNode, hasBlock: Boolean = false) {
        val (name, childrenSpec) = resolveDisplayableName(node)
        val children = node.getList("children")
        val keyword = node.getList("keyword")
        val positional = node.getList("positional")

        // Separate keywords into first-line and block keywords based on line numbers
        val nodeLineNumber = getSl2Location(node)
        val firstLineKws = mutableListOf<Sl2Kw>()
        val blockKws = mutableListOf<Sl2Kw>()

        for (kw in keyword) {
            if (kw !is List<*> || kw.size < 2) continue
            val key = kw[0]?.toString() ?: continue
            val value = kw[1] ?: continue
            val valueStr = getPyExprValue(value) ?: value.toString()
            val kwLine = if (value is AstNode) value.getInt("linenumber", 0) else 0
            if (kwLine <= nodeLineNumber || nodeLineNumber == 0) {
                firstLineKws.add(Sl2Kw(key, valueStr, kwLine))
            } else {
                blockKws.add(Sl2Kw(key, valueStr, kwLine))
            }
        }

        // Check for "has" statement opportunity:
        // Single child that is also a displayable with its own children,
        // and childrenSpec == 1
        if (!hasBlock && childrenSpec == 1
            && children.size == 1
            && children[0] is AstNode
            && (children[0] as AstNode).className.contains("SLDisplayable")
            && (children[0] as AstNode).getList("children").isNotEmpty()
        ) {
            val hasChild = children[0] as AstNode
            indent()
            write(name)
            printSl2Positional(positional)
            for (kw in firstLineKws) write(" ${kw.key} ${kw.value}")
            write(":")
            indentLevel++
            // style_prefix for the parent displayable
            val stylePrefix = node.getString("style_prefix")
                ?: node.getString("style_group")
            if (stylePrefix != null) {
                indent()
                write("style_prefix \"$stylePrefix\"")
            }
            for (kw in blockKws) {
                if (kw.lineNum > 0) advanceToLine(kw.lineNum)
                indent(); write("${kw.key} ${kw.value}")
            }
            // "has" line: children of the has-displayable print at current indent (not further indented)
            val hasChildLine = getSl2Location(hasChild)
            if (hasChildLine > 0) advanceToLine(hasChildLine)
            indent()
            val (hasName, _) = resolveDisplayableName(hasChild)
            write("has $hasName")
            // Print first-line keywords of the has-displayable on the same line
            val hasKeyword = hasChild.getList("keyword")
            val hasNodeLine = getSl2Location(hasChild)
            for (kw in hasKeyword) {
                if (kw !is List<*> || kw.size < 2) continue
                val key = kw[0]?.toString() ?: continue
                val value = kw[1] ?: continue
                val valueStr = getPyExprValue(value) ?: value.toString()
                val kwLine = if (value is AstNode) value.getInt("linenumber", 0) else 0
                if (kwLine <= hasNodeLine || hasNodeLine == 0) {
                    write(" $key $valueStr")
                }
            }
            // Interleave block keywords and children of has-displayable at same indent level
            val hasBlockKws = mutableListOf<Sl2Kw>()
            for (kw in hasKeyword) {
                if (kw !is List<*> || kw.size < 2) continue
                val key = kw[0]?.toString() ?: continue
                val value = kw[1] ?: continue
                val valueStr = getPyExprValue(value) ?: value.toString()
                val kwLine = if (value is AstNode) value.getInt("linenumber", 0) else 0
                if (kwLine > hasNodeLine && hasNodeLine != 0) {
                    hasBlockKws.add(Sl2Kw(key, valueStr, kwLine))
                }
            }
            val hasChildren = hasChild.getList("children")
            data class HasBlockItem(val lineNum: Int, val isKw: Boolean, val kw: Sl2Kw?, val child: AstNode?)
            val hasItems = mutableListOf<HasBlockItem>()
            for (kw in hasBlockKws) hasItems.add(HasBlockItem(kw.lineNum, true, kw, null))
            for (child in hasChildren) {
                if (child is AstNode) hasItems.add(HasBlockItem(getSl2Location(child), false, null, child))
            }
            hasItems.sortBy { it.lineNum }
            for (item in hasItems) {
                if (item.isKw) {
                    if (item.kw!!.lineNum > 0) advanceToLine(item.kw.lineNum)
                    indent(); write("${item.kw.key} ${item.kw.value}")
                } else {
                    printSl2Node(item.child!!)
                }
            }
            indentLevel--
            return
        }

        if (!hasBlock) indent()
        write(name)
        printSl2Positional(positional)
        for (kw in firstLineKws) write(" ${kw.key} ${kw.value}")

        val hasChildren = children.isNotEmpty()
        val needsBlock = hasChildren || blockKws.isNotEmpty()

        if (needsBlock) {
            write(":")
            indentLevel++
            // style_prefix for the displayable
            val stylePrefix = node.getString("style_prefix")
                ?: node.getString("style_group")
            if (stylePrefix != null) {
                indent()
                write("style_prefix \"$stylePrefix\"")
            }
            // Interleave block keywords and children by line number
            data class DisplayBlockItem(val lineNum: Int, val isKw: Boolean, val kw: Sl2Kw?, val child: AstNode?)
            val blockItems = mutableListOf<DisplayBlockItem>()
            for (kw in blockKws) {
                blockItems.add(DisplayBlockItem(kw.lineNum, true, kw, null))
            }
            for (child in children) {
                if (child is AstNode) {
                    blockItems.add(DisplayBlockItem(getSl2Location(child), false, null, child))
                }
            }
            blockItems.sortBy { it.lineNum }
            for (item in blockItems) {
                if (item.isKw) {
                    if (item.kw!!.lineNum > 0) advanceToLine(item.kw.lineNum)
                    indent(); write("${item.kw.key} ${item.kw.value}")
                } else {
                    printSl2Node(item.child!!)
                }
            }
            indentLevel--
        }
    }

    private fun getSl2Location(node: AstNode): Int {
        val loc = node["location"]
        if (loc is List<*> && loc.size >= 2) {
            return (loc[1] as? Number)?.toInt() ?: 0
        }
        return node.getInt("linenumber", 0)
    }

    private fun printSl2Positional(positional: List<Any?>) {
        for (p in positional) {
            write(" ${getPyExprValue(p) ?: p}")
        }
    }

    private fun printSl2If(node: AstNode, keyword: String = "if") {
        val entries = node.getList("entries")
        var kw = keyword
        for ((idx, entry) in entries.withIndex()) {
            if (entry !is List<*> || entry.size < 2) continue
            val cond = entry[0]
            val block = entry[1]
            // Advance to line for elif/else entries (first entry handled by printSl2Node)
            if (idx > 0) {
                // Try to get line number from the condition or the block
                val entryLine = when {
                    cond is AstNode -> cond.getInt("linenumber", 0)
                    block is AstNode -> getSl2Location(block)
                    else -> 0
                }
                if (entryLine > 0) advanceToLine(entryLine)
            }
            indent()
            val condStr = getPyExprValue(cond) ?: cond?.toString() ?: "True"
            if (cond == null || condStr == "True" && kw != keyword) {
                write("else:")
            } else {
                write("$kw $condStr:")
            }
            kw = "elif"
            if (block is AstNode) {
                indentLevel++
                printSl2Node(block)
                indentLevel--
            } else if (block is List<*>) {
                indentLevel++
                for (child in block) {
                    if (child is AstNode) printSl2Node(child)
                }
                indentLevel--
            }
        }
    }

    private fun printSl2For(node: AstNode) {
        indent()
        var variable = node.getString("variable") ?: "_"
        val children = node.getList("children").toMutableList()

        // Handle tuple unpacking: if variable is "_sl2_i", the first child is a SLPython
        // that does the unpacking
        if (variable == "_sl2_i" && children.isNotEmpty()) {
            val firstChild = children[0]
            if (firstChild is AstNode && firstChild.className.contains("SLPython")) {
                val code = getSource(firstChild["code"]) ?: ""
                if (code.endsWith(" = _sl2_i")) {
                    variable = code.removeSuffix(" = _sl2_i")
                    children.removeAt(0)
                }
            }
        } else {
            variable = variable.trimEnd() + " "
        }

        val expression = getPyExprValue(node["expression"]) ?: node.getString("expression") ?: "[]"
        val indexExpr = node.getString("index_expression")
        if (indexExpr != null) {
            write("for ${variable}index $indexExpr in $expression:")
        } else {
            write("for ${variable}in $expression:")
        }

        if (children.isNotEmpty()) {
            indentLevel++
            for (child in children) {
                if (child is AstNode) printSl2Node(child)
            }
            indentLevel--
        } else {
            indentLevel++; indent(); write("pass"); indentLevel--
        }
    }

    private fun printSl2Python(node: AstNode) {
        indent()
        val codeNode = node["code"]
        val code = getSource(codeNode) ?: ""

        if (code.startsWith("\n")) {
            // Multi-line python block
            val cleanCode = code.substring(1)
            write("python:")
            indentLevel++
            for (line in splitLogicalLines(cleanCode)) {
                if (line.isEmpty()) {
                    write("\n")
                } else {
                    indent()
                    write(line)
                }
            }
            indentLevel--
        } else {
            write("$ $code")
        }
    }

    private fun printSl2Use(node: AstNode) {
        indent()
        val target = node.getString("target")
        val args = node["args"]

        write("use ")
        if (target is String) {
            write(target)
        } else {
            val targetExpr = getPyExprValue(target) ?: target?.toString() ?: ""
            write("expression $targetExpr")
        }
        if (args != null) write(reconstructArgInfo(args))

        val id = node.getString("id")
        if (id != null) write(" id $id")

        // use can have a block
        val block = node.getNode("block")
        if (block != null) {
            // Check if the block has any actual children
            val blockChildren = block.getList("children")
            if (blockChildren.isNotEmpty()) {
                write(":")
                indentLevel++
                printSl2Node(block)
                indentLevel--
            }
        }
    }

    private fun printSl2Default(node: AstNode) {
        indent()
        val variable = node.getString("variable") ?: ""
        val expression = getPyExprValue(node["expression"]) ?: node.getString("expression") ?: "None"
        write("default $variable = $expression")
    }

    // --- ATL (simplified) ---

    private fun printAtlRaw(atl: AstNode) {
        // ATL is complex; output statements if available
        val statements = atl.getList("statements").ifEmpty { atl.getList("block") }
        if (statements.isNotEmpty()) {
            indentLevel++
            for (stmt in statements) {
                if (stmt is AstNode) printAtlStatement(stmt)
            }
            indentLevel--
        } else {
            indentLevel++
            indent()
            write("pass")
            indentLevel--
        }
    }

    private fun getAtlLocation(stmt: AstNode): Int {
        val loc = stmt["loc"]
        return if (loc is List<*> && loc.size >= 2) {
            (loc[1] as? Number)?.toInt() ?: -1
        } else {
            stmt.getInt("linenumber", -1)
        }
    }

    private fun printAtlStatement(stmt: AstNode) {
        // Advance to correct line for ATL blank lines (skip for RawBlock - handled below)
        if (!stmt.className.contains("RawBlock")) {
            val ln = getAtlLocation(stmt)
            if (ln > 0) advanceToLine(ln)
        }

        when {
            stmt.className.contains("RawMultipurpose") -> printAtlMultipurpose(stmt)
            stmt.className.contains("RawBlock") -> {
                val children = stmt.getList("statements").ifEmpty { stmt.getList("block") }
                // RawBlock loc points to the first child; block: keyword is one line before
                val ln = getAtlLocation(stmt)
                if (ln > 1) advanceToLine(ln - 1)
                indent()
                write("block:")
                indentLevel++
                for (child in children) {
                    if (child is AstNode) printAtlStatement(child)
                }
                indentLevel--
            }
            stmt.className.contains("RawRepeat") -> {
                indent()
                val count = stmt["repeats"]
                if (count != null && count.toString() != "null") {
                    write("repeat ${count}")
                } else {
                    write("repeat")
                }
            }
            stmt.className.contains("RawContainsExpr") -> {
                indent()
                write("contains ${getPyExprValue(stmt["expression"]) ?: ""}")
            }
            stmt.className.contains("RawParallel") -> {
                val blocks = stmt.getList("blocks")
                for (i in blocks.indices) {
                    indent()
                    write("parallel:")
                    if (blocks[i] is AstNode) {
                        indentLevel++
                        printAtlBlockBody(blocks[i] as AstNode)
                        indentLevel--
                    }
                }
            }
            stmt.className.contains("RawChoice") -> {
                val choices = stmt.getList("choices")
                for (choice in choices) {
                    if (choice is List<*> && choice.size >= 2) {
                        indent()
                        val weight = choice[0]
                        val block = choice[1]
                        if (weight != null && weight.toString() != "1.0" && weight.toString() != "null") {
                            write("choice $weight:")
                        } else {
                            write("choice:")
                        }
                        if (block is AstNode) {
                            indentLevel++
                            printAtlBlockBody(block)
                            indentLevel--
                        }
                    }
                }
            }
            stmt.className.contains("RawOn") -> {
                val handlers = stmt["handlers"]
                if (handlers is Map<*, *>) {
                    for ((event, handler) in handlers) {
                        indent()
                        write("on $event:")
                        if (handler is AstNode) {
                            indentLevel++
                            printAtlBlockBody(handler)
                            indentLevel--
                        }
                    }
                }
            }
            stmt.className.contains("RawFunction") -> {
                indent()
                write("function ${getPyExprValue(stmt["expr"]) ?: ""}")
            }
            stmt.className.contains("RawEvent") -> {
                indent()
                write("event ${stmt.getString("name") ?: ""}")
            }
            stmt.className.contains("RawTime") -> {
                indent()
                write("${stmt["time"] ?: "0"}")
            }
            else -> {
                indent()
                write("pass # ATL: ${stmt.className}")
            }
        }
    }

    /** Prints the body of a RawBlock without the "block:" wrapper. Used for on/parallel/choice bodies. */
    private fun printAtlBlockBody(block: AstNode) {
        if (block.className.contains("RawBlock")) {
            val children = block.getList("statements").ifEmpty { block.getList("block") }
            for (child in children) {
                if (child is AstNode) printAtlStatement(child)
            }
        } else {
            // Not a RawBlock, just print as statement
            printAtlStatement(block)
        }
    }

    private fun printAtlMultipurpose(stmt: AstNode) {
        indent()
        val parts = mutableListOf<String>()

        val warper = stmt.getString("warp_function") ?: stmt.getString("warper")
        val duration = getPyExprValue(stmt["duration"]) ?: stmt.getString("duration") ?: ""

        if (warper != null && warper.isNotEmpty()) {
            parts.add(warper)
            if (duration.isNotEmpty()) parts.add(duration)
        } else if (duration.isNotEmpty() && duration != "0") {
            parts.add("pause")
            parts.add(duration)
        }

        val properties = stmt.getList("properties")
        for (prop in properties) {
            if (prop is List<*> && prop.size >= 2) {
                parts.add("${prop[0]} ${getPyExprValue(prop[1]) ?: prop[1]}")
            }
        }

        val expressions = stmt.getList("expressions")
        for (expr in expressions) {
            if (expr is List<*> && expr.isNotEmpty()) {
                // expressions entry is (expression, with_clause)
                val exprValue = getPyExprValue(expr[0]) ?: expr[0]?.toString() ?: ""
                parts.add(exprValue)
                if (expr.size > 1 && expr[1] != null) {
                    val withClause = getPyExprValue(expr[1]) ?: expr[1].toString()
                    if (withClause.isNotEmpty() && withClause != "null") {
                        parts.add("with $withClause")
                    }
                }
            }
        }

        val splines = stmt.getList("splines")
        for (spline in splines) {
            if (spline is List<*> && spline.size >= 2) {
                parts.add("${spline[0]} ${spline.drop(1).joinToString(" ")}")
            }
        }

        val revolution = stmt.getString("revolution")
        if (revolution != null) parts.add("${revolution}around")

        val circles = stmt["circles"]
        if (circles != null && circles.toString() != "0" && circles.toString() != "null") {
            parts.add("circles ${circles}")
        }

        if (parts.isEmpty()) {
            write("pass")
        } else {
            write(parts.joinToString(" "))
        }
    }

    // --- UserStatement ---

    private fun printUserStatement(ast: AstNode) {
        indent()
        val line = ast.getString("line") ?: ""
        write(line)

        val userBlock = ast["block"]
        if (userBlock is List<*> && userBlock.isNotEmpty()) {
            indentLevel++
            for (entry in userBlock) {
                printLexEntry(entry)
            }
            indentLevel--
        }
    }

    private fun printLexEntry(entry: Any?) {
        if (entry !is List<*> && entry !is AstNode) return
        if (entry is AstNode) {
            // GroupedLine
            val ln = entry.getInt("number", -1)
            if (ln > 0) advanceToLine(ln)
            indent()
            write(entry.getString("text") ?: "")
            val block = entry["block"]
            if (block is List<*> && block.isNotEmpty()) {
                indentLevel++
                for (child in block) printLexEntry(child)
                indentLevel--
            }
        } else if (entry is List<*>) {
            // Tuple format: (file, linenumber, content, block) or (file, linenumber, indent, content, block)
            val content: String
            val block: Any?
            val ln: Int
            if (entry.size == 4) {
                ln = (entry[1] as? Number)?.toInt() ?: -1
                content = entry[2]?.toString() ?: ""
                block = entry[3]
            } else if (entry.size >= 5) {
                ln = (entry[1] as? Number)?.toInt() ?: -1
                content = entry[3]?.toString() ?: ""
                block = entry[4]
            } else return

            if (ln > 0) advanceToLine(ln)
            indent()
            write(content)
            if (block is List<*> && block.isNotEmpty()) {
                indentLevel++
                for (child in block) printLexEntry(child)
                indentLevel--
            }
        }
    }

    // --- Style ---

    private fun printStyle(ast: AstNode) {
        requireInit()
        indent()
        val styleName = ast.getString("style_name") ?: ""
        write("style $styleName")

        val parentStyle = ast.getString("parent")
        if (parentStyle != null) write(" is $parentStyle")
        if (ast.getBool("clear")) write(" clear")
        val take = ast.getString("take")
        if (take != null) write(" take $take")

        val variantRaw = ast["variant"]
        val variant = if (variantRaw is AstNode) getPyExprValue(variantRaw) else variantRaw?.toString()
        val hasVariant = variant != null && variant != "null"
        val properties = ast["properties"]
        val hasContent = (properties is Map<*, *> && properties.isNotEmpty()) || hasVariant
        if (hasContent) {
            write(":")
            indentLevel++
            if (hasVariant) {
                indent()
                write("variant $variant")
            }
            if (properties is Map<*, *> && properties.isNotEmpty()) {
                for ((key, value) in properties) {
                    // Advance to correct line for blank line spacing
                    val propLine = if (value is AstNode) value.getInt("linenumber", 0) else 0
                    if (propLine > 0) advanceToLine(propLine)
                    indent()
                    write("$key ${getPyExprValue(value) ?: value}")
                }
            }
            indentLevel--
        }
    }

    // --- Translation ---

    private fun printTranslate(ast: AstNode) {
        indent()
        val language = ast.getString("language") ?: "None"
        val identifier = ast.getString("identifier") ?: ""
        write("translate $language $identifier:")
        printNodes(ast.getNodeList("block").toList(), 1)
    }

    private fun printTranslateString(ast: AstNode) {
        requireInit()
        // Check if previous was also TranslateString with same language
        val needsHeader = index == 0 || run {
            val prev = block.getOrNull(index - 1)
            prev !is AstNode || !isClass(prev, "TranslateString")
                    || prev.getString("language") != ast.getString("language")
        }

        if (needsHeader) {
            indent()
            val language = ast.getString("language") ?: "None"
            write("translate $language strings:")
        }

        indentLevel++
        val ln = ast.getInt("linenumber", -1)
        if (ln > 0) advanceToLine(ln)
        indent()
        write("old \"${stringEscape(ast.getString("old") ?: "")}\"")
        indent()
        write("new \"${stringEscape(ast.getString("new") ?: "")}\"")
        indentLevel--
    }

    private fun printTranslateBlock(ast: AstNode) {
        indent()
        val language = ast.getString("language") ?: "None"
        write("translate $language ")
        skipIndentUntilWrite = true

        val prevInInit = inInit
        val block = ast.getNodeList("block")
        if (block.size == 1 && (isClass(block[0], "Python") || isClass(block[0], "Style"))) {
            inInit = true
        }
        try {
            printNodes(block.toList())
        } finally {
            inInit = prevInInit
        }
    }

    // --- RPY directive ---

    private fun printRpy(ast: AstNode) {
        val rest = ast["rest"]
        if (rest is List<*> && rest.size >= 2) {
            indent()
            write("rpy ${rest[0]} ${rest[1]}")
        }
    }

    // --- Testcase ---

    private fun printTestcase(ast: AstNode) {
        requireInit()
        indent()
        val label = ast.getString("label") ?: ""
        write("testcase $label:")
        val test = ast.getNode("test")
        if (test != null) {
            val block = test.getList("block")
            if (block.isNotEmpty()) {
                indentLevel++
                for (item in block) {
                    if (item is AstNode) {
                        indent()
                        write("pass # testcase entry: ${item.className}")
                    }
                }
                indentLevel--
            }
        }
    }

    // --- Unknown ---

    private fun printUnknown(ast: AstNode) {
        indent()
        write("pass # Unknown AST node: ${ast.className}")
    }
}
