package ru.reset.renplay.utils.decompiler

/**
 * Extended pickle parser that supports Ren'Py AST deserialization.
 * Converts Python pickle stream into a tree of [AstNode] objects.
 *
 * Supports pickle protocols 0-5 with special handling for
 * renpy.ast.*, renpy.sl2.slast.*, renpy.atl.*, renpy.screenlang.* classes.
 */

/**
 * Represents a deserialized Ren'Py AST node.
 * Uses a map of attributes, with `__class__` storing the fully qualified Python class name.
 */
class AstNode(val className: String) {
    val attrs = mutableMapOf<String, Any?>()

    operator fun get(key: String): Any? = attrs[key]
    operator fun set(key: String, value: Any?) { attrs[key] = value }

    fun getInt(key: String, default: Int = 0): Int {
        val v = attrs[key] ?: return default
        return when (v) {
            is Int -> v
            is Long -> v.toInt()
            is Number -> v.toInt()
            else -> default
        }
    }

    fun getString(key: String, default: String? = null): String? {
        val v = attrs[key] ?: return default
        return v as? String ?: default
    }

    fun getBool(key: String, default: Boolean = false): Boolean {
        val v = attrs[key] ?: return default
        return when (v) {
            is Boolean -> v
            is Int -> v != 0
            is Long -> v != 0L
            else -> default
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun getList(key: String): List<Any?> {
        val v = attrs[key] ?: return emptyList()
        return v as? List<Any?> ?: emptyList()
    }

    @Suppress("UNCHECKED_CAST")
    fun getNodeList(key: String): List<AstNode> {
        val v = attrs[key] ?: return emptyList()
        return (v as? List<*>)?.filterIsInstance<AstNode>() ?: emptyList()
    }

    fun getNode(key: String): AstNode? = attrs[key] as? AstNode

    override fun toString(): String = "AstNode($className, ${attrs.keys})"
}

class RenpyPickle(private val data: ByteArray) {
    private var pos = 0
    private val stack = mutableListOf<Any?>()
    private val memo = mutableMapOf<Int, Any?>()
    private val marks = mutableListOf<Int>()

    private fun readByte(): Int {
        if (pos >= data.size) throw IllegalStateException("Unexpected end of pickle data at position $pos")
        return data[pos++].toInt() and 0xFF
    }

    private fun readBytes(n: Int): ByteArray {
        if (pos + n > data.size) throw IllegalStateException("Cannot read $n bytes at position $pos, data size ${data.size}")
        val r = data.copyOfRange(pos, pos + n)
        pos += n
        return r
    }

    private fun readInt32(): Int {
        val b = readBytes(4)
        return (b[0].toInt() and 0xFF) or
                ((b[1].toInt() and 0xFF) shl 8) or
                ((b[2].toInt() and 0xFF) shl 16) or
                ((b[3].toInt() and 0xFF) shl 24)
    }

    private fun readUInt16(): Int {
        val b = readBytes(2)
        return (b[0].toInt() and 0xFF) or ((b[1].toInt() and 0xFF) shl 8)
    }

    private fun readLong8(): Long {
        val b = readBytes(8)
        var res = 0L
        for (i in 0 until 8) {
            res = res or ((b[i].toLong() and 0xFFL) shl (i * 8))
        }
        return res
    }

    private fun readLongLE(n: Int): Long {
        if (n == 0) return 0L
        val bytes = readBytes(n)
        var res = 0L
        for (i in 0 until n) {
            res = res or ((bytes[i].toLong() and 0xFFL) shl (i * 8))
        }
        if (bytes[n - 1].toInt() and 0x80 != 0) {
            for (i in n until 8) {
                res = res or (0xFFL shl (i * 8))
            }
        }
        return res
    }

    private fun readLine(): String {
        val start = pos
        while (pos < data.size && data[pos] != '\n'.code.toByte()) pos++
        val r = String(data, start, pos - start, Charsets.US_ASCII)
        if (pos < data.size) pos++
        return if (r.endsWith("\r")) r.substring(0, r.length - 1) else r
    }

    private fun unquote(s: String): String {
        if ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\""))) {
            return s.substring(1, s.length - 1)
                .replace("\\'", "'").replace("\\\"", "\"").replace("\\\\", "\\")
        }
        return s
    }

    private fun createAstNode(module: String, name: String): AstNode {
        return AstNode("$module.$name").also { node ->
            applyDefaults(node)
        }
    }

    /** Apply default attribute values based on the class, mirroring renpycompat.py */
    private fun applyDefaults(node: AstNode) {
        when (node.className) {
            "renpy.ast.Say" -> {
                node["who"] = null
                node["with_"] = null
                node["interact"] = true
                node["attributes"] = null
                node["arguments"] = null
                node["temporary_attributes"] = null
                node["identifier"] = null
                node["explicit_identifier"] = null
            }
            "renpy.ast.Init" -> { node["priority"] = 0 }
            "renpy.ast.Label" -> {
                node["translation_relevant"] = true
                node["parameters"] = null
                node["hide"] = false
            }
            "renpy.ast.Python", "renpy.ast.EarlyPython" -> {
                node["store"] = "store"
                node["hide"] = false
            }
            "renpy.ast.Image" -> { node["code"] = null; node["atl"] = null }
            "renpy.ast.Transform" -> { node["parameters"] = null; node["store"] = "store" }
            "renpy.ast.Show" -> { node["atl"] = null; node["warp"] = true }
            "renpy.ast.ShowLayer" -> { node["atl"] = null; node["warp"] = true; node["layer"] = "master" }
            "renpy.ast.Camera" -> { node["atl"] = null; node["warp"] = true; node["layer"] = "master" }
            "renpy.ast.Scene" -> { node["imspec"] = null; node["atl"] = null; node["warp"] = true; node["layer"] = "master" }
            "renpy.ast.Hide" -> { node["warp"] = true }
            "renpy.ast.With" -> { node["paired"] = null }
            "renpy.ast.Call" -> { node["arguments"] = null; node["expression"] = false; node["global_label"] = "" }
            "renpy.ast.Return" -> { node["expression"] = null }
            "renpy.ast.Menu" -> {
                node["translation_relevant"] = true
                node["set"] = null
                node["with_"] = null
                node["has_caption"] = false
                node["arguments"] = null
                node["item_arguments"] = null
                node["rollback"] = "force"
            }
            "renpy.ast.Jump" -> { node["expression"] = false; node["global_label"] = "" }
            "renpy.ast.UserStatement" -> {
                node["block"] = emptyList<Any>()
                node["translatable"] = false
                node["code_block"] = null
                node["translation_relevant"] = false
                node["rollback"] = "normal"
                node["subparses"] = emptyList<Any>()
                node["init_priority"] = 0
                node["atl"] = null
            }
            "renpy.ast.Define" -> { node["store"] = "store"; node["operator"] = "="; node["index"] = null }
            "renpy.ast.Default" -> { node["store"] = "store" }
            "renpy.ast.Style" -> {
                node["parent"] = null
                node["clear"] = false
                node["take"] = null
                node["variant"] = null
            }
            "renpy.ast.Translate" -> {
                node["rollback"] = "never"
                node["translation_relevant"] = true
                node["alternate"] = null
                node["language"] = null
                node["after"] = null
            }
            "renpy.ast.TranslateSay" -> {
                node["translatable"] = true
                node["translation_relevant"] = true
                node["alternate"] = null
                node["language"] = null
                node["who"] = null
                node["with_"] = null
                node["interact"] = true
                node["attributes"] = null
                node["arguments"] = null
                node["temporary_attributes"] = null
                node["identifier"] = null
                node["explicit_identifier"] = null
            }
            "renpy.ast.EndTranslate" -> { node["rollback"] = "never" }
            "renpy.ast.TranslateString" -> { node["translation_relevant"] = true; node["language"] = null }
            "renpy.ast.TranslatePython" -> { node["translation_relevant"] = true }
            "renpy.ast.TranslateBlock", "renpy.ast.TranslateEarlyBlock" -> {
                node["translation_relevant"] = true
                node["language"] = null
            }
        }
    }

    /** Apply __setstate__ for specific classes */
    private fun applySetState(node: AstNode, state: Any?) {
        when (node.className) {
            "renpy.ast.PyCode" -> {
                if (state is List<*>) {
                    when (state.size) {
                        4 -> {
                            node["source"] = state[1]
                            node["location"] = state[2]
                            node["mode"] = state[3]
                        }
                        5 -> {
                            node["source"] = state[1]
                            node["location"] = state[2]
                            node["mode"] = state[3]
                            node["py"] = state[4]
                        }
                        6 -> {
                            node["source"] = state[1]
                            node["location"] = state[2]
                            node["mode"] = state[3]
                            node["py"] = state[4]
                            node["hashcode"] = state[5]
                        }
                        7 -> {
                            node["source"] = state[1]
                            node["location"] = state[2]
                            node["mode"] = state[3]
                            node["py"] = state[4]
                            node["hashcode"] = state[5]
                            node["col_offset"] = state[6]
                        }
                    }
                    node["bytecode"] = null
                }
            }
            "renpy.revertable.RevertableSet", "renpy.python.RevertableSet" -> {
                if (state is List<*> && state.size >= 1) {
                    val map = state[0]
                    if (map is Map<*, *>) {
                        node["_items"] = map.keys.toList()
                    }
                } else if (state is Map<*, *>) {
                    node["_items"] = state.keys.toList()
                }
            }
            else -> {
                // Generic __setstate__: handle standard pickle state formats
                if (state is Map<*, *>) {
                    // Direct dict state -> merge into attrs
                    @Suppress("UNCHECKED_CAST")
                    for ((k, v) in state as Map<String, Any?>) {
                        node[k] = v
                    }
                } else if (state is List<*> && state.size == 2 && (state[0] == null || state[0] is Map<*, *>)) {
                    // Standard pickle (slotstate, inststate) tuple
                    // slotstate (state[0]) is usually None for renpy objects
                    // inststate (state[1]) is the __dict__
                    val slotState = state[0]
                    val instState = state[1]
                    if (slotState is Map<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        for ((k, v) in slotState as Map<String, Any?>) {
                            node[k] = v
                        }
                    }
                    if (instState is Map<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        for ((k, v) in instState as Map<String, Any?>) {
                            node[k] = v
                        }
                    }
                }
            }
        }
    }

    /** Resolve GLOBAL opcode — creates an AstNode for known classes, or a placeholder */
    private fun resolveGlobal(module: String, name: String): Any {
        // For collections.OrderedDict, just return a map
        if (module == "collections" && name == "OrderedDict") {
            return "__callable__:collections.OrderedDict"
        }
        // Callable marker — will be used by REDUCE/NEWOBJ
        return "__callable__:$module.$name"
    }

    @Suppress("UNCHECKED_CAST")
    fun load(): Any? {
        while (pos < data.size) {
            val opcode = readByte()
            when (opcode) {
                // Protocol frame (0x80) — skip 1 byte (protocol version)
                0x80 -> pos += 1
                // Frame (0x95) — skip 8 bytes (frame length)
                0x95 -> pos += 8
                // MARK (0x28)
                0x28 -> marks.add(stack.size)
                // STOP (0x2E)
                0x2E -> return stack.removeLastOrNull()
                // NONE (0x4E)
                0x4E -> stack.add(null)
                // NEWTRUE (0x88)
                0x88 -> stack.add(true)
                // NEWFALSE (0x89)
                0x89 -> stack.add(false)
                // INT (0x49)
                0x49 -> {
                    val s = readLine()
                    stack.add(when (s) {
                        "01" -> true
                        "00" -> false
                        else -> s.toLongOrNull() ?: 0L
                    })
                }
                // BININT (0x4A)
                0x4A -> stack.add(readInt32())
                // BININT1 (0x4B)
                0x4B -> stack.add(readByte())
                // BININT2 (0x4D)
                0x4D -> stack.add(readUInt16())
                // LONG1 (0x8A)
                0x8A -> stack.add(readLongLE(readByte()))
                // LONG4 (0x8B)
                0x8B -> stack.add(readLongLE(readInt32()))
                // LONG (0x4C)
                0x4C -> {
                    var s = readLine()
                    if (s.endsWith("L")) s = s.substring(0, s.length - 1)
                    stack.add(s.toLongOrNull() ?: 0L)
                }
                // BINFLOAT (0x47)
                0x47 -> {
                    val b = readBytes(8)
                    val bits = (0 until 8).fold(0L) { acc, i -> acc or ((b[i].toLong() and 0xFF) shl (56 - i * 8)) }
                    stack.add(java.lang.Double.longBitsToDouble(bits))
                }
                // FLOAT (0x46)
                0x46 -> {
                    stack.add(readLine().toDoubleOrNull() ?: 0.0)
                }
                // SHORT_BINUNICODE (0x8C)
                0x8C -> stack.add(String(readBytes(readByte()), Charsets.UTF_8))
                // BINUNICODE (0x58)
                0x58 -> stack.add(String(readBytes(readInt32()), Charsets.UTF_8))
                // BINUNICODE8 (0x8D)
                0x8D -> stack.add(String(readBytes(readLong8().toInt()), Charsets.UTF_8))
                // SHORT_BINSTRING (0x55)
                0x55 -> stack.add(String(readBytes(readByte()), Charsets.UTF_8))
                // BINSTRING (0x54)
                0x54 -> stack.add(String(readBytes(readInt32()), Charsets.UTF_8))
                // SHORT_BINBYTES (0x43)
                0x43 -> stack.add(readBytes(readByte()))
                // BINBYTES (0x42)
                0x42 -> stack.add(readBytes(readInt32()))
                // BINBYTES8 (0x8E)
                0x8E -> stack.add(readBytes(readLong8().toInt()))
                // STRING (0x53), UNICODE (0x56)
                0x53, 0x56 -> stack.add(unquote(readLine()))
                // EMPTY_LIST (0x5D)
                0x5D -> stack.add(mutableListOf<Any?>())
                // LIST (0x6C)
                0x6C -> {
                    val mark = marks.removeLast()
                    val list = stack.subList(mark, stack.size).toMutableList()
                    while (stack.size > mark) stack.removeLast()
                    stack.add(list)
                }
                // EMPTY_DICT (0x7D)
                0x7D -> stack.add(mutableMapOf<Any?, Any?>())
                // DICT (0x64)
                0x64 -> {
                    val mark = marks.removeLast()
                    val dict = mutableMapOf<Any?, Any?>()
                    var i = mark
                    while (i + 1 < stack.size) {
                        dict[stack[i]] = stack[i + 1]
                        i += 2
                    }
                    while (stack.size > mark) stack.removeLast()
                    stack.add(dict)
                }
                // APPEND (0x61)
                0x61 -> {
                    val v = stack.removeLast()
                    (stack.last() as? MutableList<Any?>)?.add(v)
                }
                // APPENDS (0x65)
                0x65 -> {
                    val mark = marks.removeLast()
                    val items = stack.subList(mark, stack.size).toList()
                    while (stack.size > mark) stack.removeLast()
                    (stack.last() as? MutableList<Any?>)?.addAll(items)
                }
                // SETITEM (0x73)
                0x73 -> {
                    val v = stack.removeLast()
                    val k = stack.removeLast()
                    (stack.last() as? MutableMap<Any?, Any?>)?.set(k, v)
                }
                // SETITEMS (0x75)
                0x75 -> {
                    val mark = marks.removeLast()
                    val m = stack[mark - 1] as? MutableMap<Any?, Any?>
                    if (m != null) {
                        var i = mark
                        while (i + 1 < stack.size) {
                            m[stack[i]] = stack[i + 1]
                            i += 2
                        }
                    }
                    while (stack.size > mark) stack.removeLast()
                }
                // TUPLE (0x74)
                0x74 -> {
                    val mark = marks.removeLast()
                    val tuple = stack.subList(mark, stack.size).toList()
                    while (stack.size > mark) stack.removeLast()
                    stack.add(tuple)
                }
                // TUPLE1 (0x85)
                0x85 -> { val v = stack.removeLast(); stack.add(listOf(v)) }
                // TUPLE2 (0x86)
                0x86 -> { val v2 = stack.removeLast(); val v1 = stack.removeLast(); stack.add(listOf(v1, v2)) }
                // TUPLE3 (0x87)
                0x87 -> { val v3 = stack.removeLast(); val v2 = stack.removeLast(); val v1 = stack.removeLast(); stack.add(listOf(v1, v2, v3)) }
                // EMPTY_TUPLE (0x29)
                0x29 -> stack.add(emptyList<Any?>())
                // EMPTY_SET (0x8F)
                0x8F -> stack.add(mutableSetOf<Any?>())
                // ADDITEMS (0x90) — set.update
                0x90 -> {
                    val mark = marks.removeLast()
                    val items = stack.subList(mark, stack.size).toList()
                    while (stack.size > mark) stack.removeLast()
                    (stack.last() as? MutableSet<Any?>)?.addAll(items)
                }
                // FROZENSET (0x91)
                0x91 -> {
                    val mark = marks.removeLast()
                    val items = stack.subList(mark, stack.size).toSet()
                    while (stack.size > mark) stack.removeLast()
                    stack.add(items)
                }
                // BINPUT (0x71)
                0x71 -> memo[readByte()] = stack.last()
                // LONG_BINPUT (0x72)
                0x72 -> memo[readInt32()] = stack.last()
                // BINGET (0x68)
                0x68 -> stack.add(memo[readByte()])
                // LONG_BINGET (0x6A)
                0x6A -> stack.add(memo[readInt32()])
                // MEMOIZE (0x94)
                0x94 -> memo[memo.size] = stack.last()
                // POP (0x30 = '0')
                0x30 -> if (stack.isNotEmpty()) stack.removeLast()
                // DUP (0x32 = '2')
                0x32 -> stack.add(stack.last())
                // POP_MARK (0x31 = '1')
                0x31 -> {
                    val mark = marks.removeLast()
                    while (stack.size > mark) stack.removeLast()
                }
                // GLOBAL (0x63 = 'c')
                0x63 -> {
                    val module = readLine()
                    val name = readLine()
                    stack.add(resolveGlobal(module, name))
                }
                // STACK_GLOBAL (0x93)
                0x93 -> {
                    val name = stack.removeLast() as? String ?: ""
                    val module = stack.removeLast() as? String ?: ""
                    stack.add(resolveGlobal(module, name))
                }
                // INST (0x69 = 'i') — old style
                0x69 -> {
                    val module = readLine()
                    val name = readLine()
                    val mark = marks.removeLast()
                    val args = stack.subList(mark, stack.size).toList()
                    while (stack.size > mark) stack.removeLast()
                    val node = createAstNode(module, name)
                    if (args.size == 1 && args[0] is Map<*, *>) {
                        applySetState(node, args[0])
                    }
                    stack.add(node)
                }
                // REDUCE (0x52 = 'R')
                0x52 -> {
                    val args = stack.removeLast()
                    val callable = stack.removeLast()
                    stack.add(handleReduce(callable, args))
                }
                // NEWOBJ (0x81)
                0x81 -> {
                    val args = stack.removeLast()
                    val callable = stack.removeLast()
                    stack.add(handleReduce(callable, args))
                }
                // NEWOBJ_EX (0x92)
                0x92 -> {
                    val kwargs = stack.removeLast()
                    val args = stack.removeLast()
                    val callable = stack.removeLast()
                    stack.add(handleReduce(callable, args))
                }
                // BUILD (0x62 = 'b') — applies __setstate__ or __dict__.update
                0x62 -> {
                    val state = stack.removeLast()
                    val obj = stack.last()
                    if (obj is AstNode) {
                        applySetState(obj, state)
                    }
                }
                // OBJ (0x6F = 'o')
                0x6F -> {
                    val mark = marks.removeLast()
                    val items = stack.subList(mark, stack.size).toList()
                    while (stack.size > mark) stack.removeLast()
                    if (items.isNotEmpty()) {
                        val callable = items[0]
                        val args = items.drop(1)
                        stack.add(handleReduce(callable, args))
                    } else {
                        stack.add(null)
                    }
                }
                // PUT (0x70 = 'p')
                0x70 -> {
                    val idx = readLine().toIntOrNull() ?: 0
                    memo[idx] = stack.last()
                }
                // GET (0x67 = 'g')
                0x67 -> {
                    val idx = readLine().toIntOrNull() ?: 0
                    stack.add(memo[idx])
                }
                // BYTEARRAY8 (0x96)
                0x96 -> stack.add(readBytes(readLong8().toInt()))

                else -> {
                    // Skip unknown opcodes silently to be more robust
                }
            }
        }
        return stack.lastOrNull()
    }

    private fun handleReduce(callable: Any?, args: Any?): Any? {
        val callableStr = callable as? String ?: return null
        if (!callableStr.startsWith("__callable__:")) return null

        val fullName = callableStr.removePrefix("__callable__:")
        val argList = args as? List<*> ?: emptyList<Any?>()

        return when (fullName) {
            "collections.OrderedDict" -> mutableMapOf<Any?, Any?>()
            "collections.defaultdict" -> mutableMapOf<Any?, Any?>()
            "builtins.list", "__builtin__.list" -> {
                if (argList.isNotEmpty() && argList[0] is List<*>) {
                    (argList[0] as List<*>).toMutableList()
                } else mutableListOf<Any?>()
            }
            "builtins.dict", "__builtin__.dict" -> mutableMapOf<Any?, Any?>()
            "builtins.tuple", "__builtin__.tuple" -> {
                if (argList.isNotEmpty() && argList[0] is List<*>) {
                    (argList[0] as List<*>).toList()
                } else emptyList<Any?>()
            }
            "builtins.set", "__builtin__.set" -> {
                if (argList.isNotEmpty() && argList[0] is List<*>) {
                    (argList[0] as List<*>).toMutableSet()
                } else mutableSetOf<Any?>()
            }
            "builtins.frozenset", "__builtin__.frozenset" -> {
                if (argList.isNotEmpty() && argList[0] is List<*>) {
                    (argList[0] as List<*>).toSet()
                } else emptySet<Any?>()
            }
            else -> {
                // Create an AstNode for any renpy.* class or unknown class
                val parts = fullName.lastIndexOf('.')
                val module = if (parts > 0) fullName.substring(0, parts) else ""
                val name = if (parts > 0) fullName.substring(parts + 1) else fullName

                val node = createAstNode(module, name)
                // Some classes use __getnewargs__ which passes args to __new__
                when (node.className) {
                    "renpy.ast.PyExpr", "renpy.astsupport.PyExpr" -> {
                        // PyExpr(s, filename, linenumber, py=None)
                        if (argList.isNotEmpty()) node["value"] = argList[0]?.toString() ?: ""
                        if (argList.size > 1) node["filename"] = argList[1]
                        if (argList.size > 2) node["linenumber"] = argList[2]
                        if (argList.size > 3) node["py"] = argList[3]
                        node["__is_pyexpr__"] = true
                    }
                    "renpy.object.Sentinel" -> {
                        if (argList.isNotEmpty()) node["name"] = argList[0]
                    }
                    "renpy.lexer.GroupedLine" -> {
                        if (argList.size >= 5) {
                            node["filename"] = argList[0]
                            node["number"] = argList[1]
                            node["indent"] = argList[2]
                            node["text"] = argList[3]
                            node["block"] = argList[4]
                        }
                    }
                    "renpy.revertable.RevertableList", "renpy.python.RevertableList" -> {
                        // Just a list wrapper
                        node["_items"] = if (argList.isNotEmpty() && argList[0] is List<*>) argList[0] else emptyList<Any?>()
                    }
                    "renpy.revertable.RevertableDict", "renpy.python.RevertableDict" -> {
                        node["_items"] = mutableMapOf<Any?, Any?>()
                    }
                    "renpy.revertable.RevertableSet", "renpy.python.RevertableSet" -> {
                        node["_items"] = mutableSetOf<Any?>()
                    }
                }
                node
            }
        }
    }

    companion object {
        /**
         * Parse a pickle byte array and return the second element of the top-level tuple,
         * which is the AST statement list in .rpyc files.
         */
        fun loadAst(data: ByteArray): List<Any?> {
            val result = RenpyPickle(data).load()
            // rpyc files store a tuple (data, stmts) at the top level
            if (result is List<*> && result.size >= 2) {
                val stmts = result[1]
                if (stmts is List<*>) return stmts
            }
            // Sometimes only the stmts list is returned
            if (result is List<*>) return result
            throw IllegalStateException("Unexpected pickle result type: ${result?.javaClass}")
        }
    }
}
