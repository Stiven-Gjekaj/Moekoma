package io.github.stivengjekaj.moekoma.core

import com.dylibso.chicory.wasm.types.ValType
import org.jsoup.Jsoup
import org.jsoup.nodes.Comment
import org.jsoup.nodes.DataNode
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Entities
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.Elements
import java.io.ByteArrayInputStream

/**
 * The html module of aidoku-rs, with jsoup, which the sources expect. As in Asabunko, which uses SwiftSoup.
 * The error codes: -1 bad descriptor, -2 bad string, -3 bad HTML, -4 bad query, -5 no result, -6 jsoup failed.
 */
internal object AidokuHtml {
    /** A document of a response, in the charset of its header or of its page. */
    fun parse(response: HttpResponse): Document {
        val charset = response.headers.entries.firstOrNull { it.key.equals("content-type", true) }?.value
            ?.substringAfter("charset=", "")?.trim(' ', '"', ';')?.ifEmpty { null }
            ?.takeIf { runCatching { java.nio.charset.Charset.forName(it) }.isSuccess }
        return Jsoup.parse(ByteArrayInputStream(response.body), charset, response.url)
    }

    /** A list of nodes that are not all elements, such as the child nodes of an element. */
    class Nodes(val list: List<Node>)
}

private fun AidokuHost.element(rid: Int): Element? = item(rid) as? Element

private fun AidokuHost.node(rid: Int): Node? = item(rid) as? Node

private fun AidokuHost.elements(rid: Int): Elements? = item(rid) as? Elements

/** A new item for a node, or -5 when there is none. */
private fun AidokuHost.addNode(node: Node?): Int = if (node == null) -5 else add(node)

/** A string result as a new item, or -5 when there is no value. */
private fun AidokuHost.text(value: String?): Int = if (value == null) -5 else addString(value)

internal fun AidokuHost.installHtml(define: Define) {
    val i32 = ValType.I32
    fun result(value: Int) = longArrayOf(value.toLong())
    fun one(name: String, body: (Int) -> Int) = define("html", name, listOf(i32), listOf(i32)) { a -> result(body(a[0].toInt())) }
    fun withString(name: String, body: (Int, String) -> Int) =
        define("html", name, listOf(i32, i32, i32), listOf(i32)) { a -> result(body(a[0].toInt(), string(a[1].toInt(), a[2].toInt()))) }
    fun change(name: String, body: (Element, String) -> Unit) = withString(name) { rid, value ->
        val element = element(rid) ?: return@withString -6
        if (runCatching { body(element, value) }.isSuccess) 0 else -6
    }

    define("html", "parse", listOf(i32, i32, i32, i32), listOf(i32)) { a ->
        val document = runCatching { Jsoup.parse(string(a[0].toInt(), a[1].toInt()), string(a[2].toInt(), a[3].toInt())) }.getOrNull()
        result(if (document == null) -3 else add(document))
    }
    define("html", "parse_fragment", listOf(i32, i32, i32, i32), listOf(i32)) { a ->
        val document = runCatching { Jsoup.parseBodyFragment(string(a[0].toInt(), a[1].toInt()), string(a[2].toInt(), a[3].toInt())) }.getOrNull()
        result(if (document == null) -3 else add(document))
    }
    define("html", "escape", listOf(i32, i32), listOf(i32)) { a -> result(addString(Entities.escape(string(a[0].toInt(), a[1].toInt())))) }
    define("html", "unescape", listOf(i32, i32), listOf(i32)) { a ->
        val raw = string(a[0].toInt(), a[1].toInt())
        result(addString(runCatching { Entities.unescape(raw) }.getOrDefault(raw)))
    }

    withString("select") { rid, query ->
        try {
            elements(rid)?.let { return@withString add(it.select(query)) }
            val element = element(rid) ?: return@withString -1
            add(element.select(query))
        } catch (e: Exception) {
            -4
        }
    }
    withString("select_first") { rid, query ->
        try {
            val list = elements(rid)
            val found = if (list != null) list.select(query).first() else (element(rid) ?: return@withString -1).selectFirst(query)
            if (found == null) -5 else add(found)
        } catch (e: Exception) {
            -4
        }
    }
    withString("attr") { rid, key ->
        val name = key.removePrefix("abs:")
        val target = elements(rid)?.firstOrNull { it.hasAttr(name) } ?: element(rid)
        if (target == null || !target.hasAttr(name)) -5 else text(target.attr(key))
    }
    withString("has_attr") { rid, key -> if (element(rid)?.hasAttr(key.removePrefix("abs:")) == true) 1 else 0 }
    withString("has_class") { rid, name -> if (element(rid)?.hasClass(name) == true) 1 else 0 }

    one("text") { rid ->
        elements(rid)?.let { return@one text(it.text()) }
        element(rid)?.let { return@one text(it.text()) }
        (node(rid) as? TextNode)?.let { return@one text(it.text()) }
        -1
    }
    one("untrimmed_text") { rid ->
        element(rid)?.let { return@one text(it.wholeText()) }
        (node(rid) as? TextNode)?.let { return@one text(it.wholeText) }
        -1
    }
    one("own_text") { rid -> text(element(rid)?.ownText()) }
    one("data") { rid ->
        element(rid)?.let { return@one text(it.data()) }
        (node(rid) as? DataNode)?.let { return@one text(it.wholeData) }
        -1
    }
    one("html") { rid -> elements(rid)?.let { text(it.html()) } ?: text(element(rid)?.html()) }
    one("outer_html") { rid -> elements(rid)?.let { text(it.outerHtml()) } ?: text(node(rid)?.outerHtml()) }
    one("id") { rid -> text(element(rid)?.id()) }
    one("tag_name") { rid -> text(element(rid)?.tagName()) }
    one("class_name") { rid -> text(element(rid)?.className()) }
    one("base_uri") { rid -> text(node(rid)?.baseUri()) }

    one("children") { rid -> element(rid)?.let { add(it.children()) } ?: -1 }
    one("siblings") { rid -> element(rid)?.let { add(it.siblingElements()) } ?: -1 }
    one("child_nodes") { rid -> node(rid)?.let { add(AidokuHtml.Nodes(it.childNodes())) } ?: -1 }
    one("parent") { rid -> addNode(node(rid)?.parent()) }
    one("next") { rid -> element(rid)?.let { addNode(it.nextElementSibling()) } ?: addNode(node(rid)?.nextSibling()) }
    one("previous") { rid -> element(rid)?.let { addNode(it.previousElementSibling()) } ?: addNode(node(rid)?.previousSibling()) }
    one("first") { rid -> (item(rid) as? AidokuHtml.Nodes)?.let { addNode(it.list.firstOrNull()) } ?: addNode(elements(rid)?.firstOrNull()) }
    one("last") { rid -> (item(rid) as? AidokuHtml.Nodes)?.let { addNode(it.list.lastOrNull()) } ?: addNode(elements(rid)?.lastOrNull()) }
    one("size") { rid -> (item(rid) as? AidokuHtml.Nodes)?.list?.size ?: elements(rid)?.size ?: -1 }
    define("html", "get", listOf(i32, i32), listOf(i32)) { a ->
        val rid = a[0].toInt()
        val index = a[1].toInt()
        (item(rid) as? AidokuHtml.Nodes)?.let { return@define result(it.list.getOrNull(index)?.let(::add) ?: -5) }
        val list = elements(rid) ?: return@define result(-1)
        result(list.getOrNull(index)?.let(::add) ?: -5)
    }
    one("kind") { rid ->
        when (val item = item(rid)) {
            is Document -> 7
            is Elements, is AidokuHtml.Nodes -> 6
            is Element -> 5
            is TextNode -> 2
            is DataNode -> 3
            is Comment -> 4
            is Node -> 1
            else -> 0
        }
    }

    // The changes of the tree.
    change("set_text") { element, value -> element.text(value) }
    change("set_html") { element, value -> element.html(value) }
    change("append") { element, value -> element.append(value) }
    change("prepend") { element, value -> element.prepend(value) }
    change("add_class") { element, value -> element.addClass(value) }
    change("remove_class") { element, value -> element.removeClass(value) }
    change("remove_attr") { element, value -> element.removeAttr(value) }
    define("html", "set_attr", listOf(i32, i32, i32, i32, i32), listOf(i32)) { a ->
        val element = element(a[0].toInt()) ?: return@define result(-6)
        val done = runCatching { element.attr(string(a[1].toInt(), a[2].toInt()), string(a[3].toInt(), a[4].toInt())) }.isSuccess
        result(if (done) 0 else -6)
    }
    one("remove") { rid ->
        elements(rid)?.let { return@one if (runCatching { it.remove() }.isSuccess) 0 else -6 }
        val node = node(rid) ?: return@one -6
        if (runCatching { node.remove() }.isSuccess) 0 else -6
    }
}
