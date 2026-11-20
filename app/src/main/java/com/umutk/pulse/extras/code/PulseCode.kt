package com.umutk.pulse.extras.code

import android.content.Context
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor
import org.eclipse.tm4e.core.registry.IThemeSource
import java.io.File

/*
 * The code editor's engine: Sora Editor (https://github.com/Rosemoe/sora-editor, LGPL-2.1) with the TextMate grammars and themes that Xed-Editor
 * (https://github.com/Xed-Editor/Xed-Editor, GPL-3.0) ships in its assets (they come from VS Code). Pulse adds the language detection, the folding and the look.
 */
object PulseCode {
    class Lang(val scope: String, val label: String)

    private val byExt: Map<String, Lang> = buildMap {
        fun add(l: Lang, vararg exts: String) { exts.forEach { put(it, l) } }
        add(Lang("source.python", "Python"), "py", "pyw", "pyi", "pyx")
        add(Lang("text.html.basic", "HTML"), "html", "htm", "xhtml", "vue", "svelte")
        add(Lang("source.js", "JavaScript"), "js", "mjs", "cjs")
        add(Lang("source.js.jsx", "JSX"), "jsx")
        add(Lang("source.ts", "TypeScript"), "ts", "mts", "cts")
        add(Lang("source.tsx", "TSX"), "tsx")
        add(Lang("source.json", "JSON"), "json", "jsonc", "json5", "geojson", "webmanifest", "ipynb")
        add(Lang("source.css", "CSS"), "css")
        add(Lang("source.css.scss", "SCSS"), "scss", "sass")
        add(Lang("source.css.less", "Less"), "less")
        add(Lang("text.xml", "XML"), "xml", "svg", "xsd", "xsl", "xslt", "plist", "rss", "atom", "kml", "gpx", "csproj", "resx", "axml", "fxml", "iml", "pom")
        add(Lang("text.html.markdown", "Markdown"), "md", "markdown", "mdx")
        add(Lang("source.kotlin", "Kotlin"), "kt", "kts")
        add(Lang("source.java", "Java"), "java")
        add(Lang("source.groovy", "Groovy"), "groovy", "gradle", "gvy")
        add(Lang("source.shell", "Shell"), "sh", "bash", "zsh", "ksh", "fish", "bashrc", "profile", "zshrc")
        add(Lang("source.batchfile", "Batch"), "bat", "cmd")
        add(Lang("source.powershell", "PowerShell"), "ps1", "psm1", "psd1")
        add(Lang("source.yaml", "YAML"), "yml", "yaml")
        add(Lang("source.toml", "TOML"), "toml")
        add(Lang("source.ini", "INI"), "ini", "cfg", "conf", "inf", "reg", "desktop", "editorconfig")
        add(Lang("source.properties", "Properties"), "properties", "prop", "env")
        add(Lang("source.c", "C"), "c", "h")
        add(Lang("source.cpp", "C++"), "cpp", "cc", "cxx", "hpp", "hh", "hxx", "ino", "glsl", "wgsl", "frag", "vert")
        add(Lang("source.cs", "C#"), "cs")
        add(Lang("source.go", "Go"), "go")
        add(Lang("source.rust", "Rust"), "rs")
        add(Lang("text.html.php", "PHP"), "php", "phtml")
        add(Lang("source.ruby", "Ruby"), "rb", "rake", "gemspec")
        add(Lang("source.swift", "Swift"), "swift")
        add(Lang("source.lua", "Lua"), "lua")
        add(Lang("source.sql", "SQL"), "sql")
        add(Lang("source.dart", "Dart"), "dart")
        add(Lang("source.r", "R"), "r")
        add(Lang("source.nim", "Nim"), "nim")
        add(Lang("source.zig", "Zig"), "zig")
        add(Lang("source.smali", "Smali"), "smali")
        add(Lang("source.asm", "Assembly"), "asm", "s")
        add(Lang("source.cmake", "CMake"), "cmake")
        add(Lang("source.diff", "Diff"), "diff", "patch")
        add(Lang("text.tex.latex", "LaTeX"), "tex", "sty", "cls")
        add(Lang("source.ignore", "Ignore"), "gitignore", "dockerignore", "npmignore")
        add(Lang("text.log", "Log"), "log")
        add(Lang("source.pascal", "Pascal"), "pas", "pp")
        add(Lang("source.lisp", "Lisp"), "lisp", "el", "clj", "scm")
        add(Lang("source.nix", "Nix"), "nix")
    }

    private val byName = mapOf(
        "dockerfile" to Lang("source.shell", "Dockerfile"), "makefile" to Lang("source.shell", "Makefile"), "cmakelists.txt" to Lang("source.cmake", "CMake"),
        "build.gradle" to Lang("source.groovy", "Gradle"), "settings.gradle" to Lang("source.groovy", "Gradle"), "gradlew" to Lang("source.shell", "Shell"),
        ".bashrc" to Lang("source.shell", "Shell"), ".zshrc" to Lang("source.shell", "Shell"), ".profile" to Lang("source.shell", "Shell"),
        ".gitignore" to Lang("source.ignore", "Ignore"), ".gitattributes" to Lang("source.ini", "INI"), "gemfile" to Lang("source.ruby", "Ruby"), "rakefile" to Lang("source.ruby", "Ruby"),
        "proguard-rules.pro" to Lang("source.properties", "ProGuard"), "androidmanifest.xml" to Lang("text.xml", "XML"),
    )

    /** The languages offered when the detected one is wrong (a chip row in the editor). */
    val common: List<Lang> = listOf("py", "html", "js", "ts", "json", "css", "xml", "md", "kt", "java", "cpp", "c", "cs", "go", "rs", "php", "sh", "sql", "yml", "swift", "lua", "rb", "dart", "toml", "ini")
        .mapNotNull { byExt[it] }.distinctBy { it.scope }

    /** The extensions that open in the code editor from Files by a tap (HTML opens as a page there; the menu offers its code). */
    val tapExts: Set<String> = byExt.keys - setOf("html", "htm", "xhtml", "svg", "md", "markdown", "log", "r", "s", "el", "pp", "mdx") + setOf("gradle")

    /** The language of a file: its name, its extension, then its first lines (a #! line, <!doctype, <?xml, a JSON start). Null: no language known. */
    fun detect(name: String, head: String = ""): Lang? {
        val n = name.lowercase()
        byName[n]?.let { return it }
        val ext = n.substringAfterLast('.', "")
        if (ext.isNotEmpty()) byExt[ext]?.let { return it }
        val first = head.trimStart().lineSequence().firstOrNull().orEmpty()
        return when {
            first.startsWith("#!") -> when {
                "python" in first -> byExt["py"]; "node" in first || "deno" in first -> byExt["js"]; "ruby" in first -> byExt["rb"]; "php" in first -> byExt["php"]
                "lua" in first -> byExt["lua"]; "pwsh" in first || "powershell" in first -> byExt["ps1"]; else -> byExt["sh"]
            }
            first.startsWith("<!doctype html", true) || first.startsWith("<html", true) -> byExt["html"]
            first.startsWith("<?xml") || (first.startsWith("<") && first.endsWith(">") && ext.isEmpty()) -> byExt["xml"]
            first.startsWith("<?php") -> byExt["php"]
            (first.startsWith("{") || first.startsWith("[")) && ext.isEmpty() && runCatching { org.json.JSONTokener(head.take(200_000)).nextValue(); true }.getOrDefault(false) -> byExt["json"]
            else -> null
        }
    }

    @Volatile private var ready = false
    private var darkModel: ThemeModel? = null
    private var lightModel: ThemeModel? = null

    /** Reads the grammar list and the two themes (once; a few milliseconds). */
    @Synchronized fun init(ctx: Context) {
        if (ready) return
        val app = ctx.applicationContext
        FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(app.assets))
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
        fun model(file: String, name: String) = app.assets.open("textmate/$file").use { ThemeModel(IThemeSource.fromInputStream(it, file, null), name) }
        darkModel = model("darcula.json", "darcula"); lightModel = model("quietlight.json", "quietlight")
        ready = true
    }

    fun scheme(dark: Boolean): TextMateColorScheme = TextMateColorScheme.create(if (dark) darkModel!! else lightModel!!)

    fun language(scope: String?): TextMateLanguage? = scope?.let { runCatching { TextMateLanguage.create(it, true) }.getOrNull() }

    // ---- folding (a viewer's: lines are folded away into one marker line, and put back before anything is edited) ------------------------------------------

    /** The folds of one open file: the text each marker line stands for. */
    class Folds { val hidden = HashMap<Int, String>(); var next = 1; val any get() = hidden.isNotEmpty() }

    private val marker = Regex("""\[#(\d+)]\s*$""")
    private fun markerId(line: String): Int? = if (line.trimStart().startsWith("…")) marker.find(line)?.groupValues?.get(1)?.toIntOrNull() else null

    /** Folds the block (of the grammar's folding rules) the cursor is in: its first line stays, the rest becomes one marker line. Returns false when there is none. */
    fun foldAtCursor(ed: CodeEditor, f: Folds): Boolean {
        val text = ed.text
        val line = ed.cursor.leftLine
        val blocks = ed.styles?.blocks ?: return false
        // the innermost block that holds the line and spans more than one line; a block that starts on the line is preferred
        val b = blocks.filter { it.startLine <= line && it.endLine >= line && it.endLine > it.startLine && markerId(text.getLineString(it.startLine)) == null }
            .maxWithOrNull(compareBy({ if (it.startLine == line) 1 else 0 }, { it.startLine }, { -it.endLine })) ?: return false
        return fold(ed, f, b.startLine, b.endLine)
    }

    /** Folds every block that begins at the indentation level of the outermost ones (the first level). */
    fun foldAll(ed: CodeEditor, f: Folds): Int {
        val text = ed.text
        val blocks = ed.styles?.blocks ?: return 0
        val tops = blocks.filter { it.endLine > it.startLine }.sortedBy { it.startLine }
        var n = 0; var coveredTo = -1
        val chosen = ArrayList<Pair<Int, Int>>()
        for (b in tops) if (b.startLine > coveredTo) { chosen += b.startLine to b.endLine; coveredTo = b.endLine }
        // from the bottom, so the line numbers above stay right
        for ((s, e) in chosen.asReversed()) if (markerId(text.getLineString(s)) == null && fold(ed, f, s, e)) n++
        return n
    }

    private fun fold(ed: CodeEditor, f: Folds, start: Int, end: Int): Boolean {
        val text = ed.text
        if (end <= start || end >= text.lineCount) return false
        val cutFrom = text.getColumnCount(start)
        val hidden = text.substring(text.getCharIndex(start, cutFrom), text.getCharIndex(end, text.getColumnCount(end)))
        val first = text.getLineString(start + 1)
        val indent = first.takeWhile { it == ' ' || it == '\t' }
        val id = f.next++
        f.hidden[id] = hidden
        val n = end - start
        text.replace(start, cutFrom, end, text.getColumnCount(end), "\n" + indent + "… $n " + com.umutk.pulse.tr("lines", "satır") + "  [#$id]")
        ed.setSelection(start, 0)
        return true
    }

    /** Puts the folded lines of the marker on [line] back; returns whether there was one. */
    fun unfoldLine(ed: CodeEditor, f: Folds, line: Int): Boolean {
        val text = ed.text
        if (line < 0 || line >= text.lineCount) return false
        val id = markerId(text.getLineString(line)) ?: return false
        val body = f.hidden.remove(id) ?: return false
        text.replace(line, 0, line, text.getColumnCount(line), body.removePrefix("\n"))
        ed.setSelection(maxOf(line - 1, 0), 0)
        return true
    }

    /** Puts every fold back (before editing or saving). */
    fun unfoldAll(ed: CodeEditor, f: Folds) {
        if (!f.any) return
        val text = ed.text
        var guard = 0
        while (f.any && guard++ < 10_000) {
            val l = (text.lineCount - 1 downTo 0).firstOrNull { markerId(text.getLineString(it)) != null } ?: break
            if (!unfoldLine(ed, f, l)) break
        }
        f.hidden.clear()
    }

    fun readText(f: File): String? = runCatching { f.readText() }.getOrNull()
}
