package io.github.stivengjekaj.moekoma.core

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * A small tree of an XML document. The names are local names, with no prefix.
 * The keys of the attributes keep their prefix, such as "epub:type", and also have the local name.
 */
class XmlElement(val name: String, val attributes: Map<String, String>) {
    val children = mutableListOf<XmlElement>()

    /** The text of this element only, with no text of its children. */
    var text = ""
        internal set
    var parent: XmlElement? = null
        internal set

    operator fun get(attribute: String): String? = attributes[attribute]

    /** The first child with the name. */
    fun child(name: String): XmlElement? = children.firstOrNull { it.name == name }

    fun children(name: String): List<XmlElement> = children.filter { it.name == name }

    /** Each element below this one with the name, in the order of the document. */
    fun descendants(name: String): List<XmlElement> {
        val found = mutableListOf<XmlElement>()
        for (child in children) {
            if (child.name == name) found += child
            found += child.descendants(name)
        }
        return found
    }

    /** The text of this element and of all elements below it, with the spaces made one. */
    val allText: String
        get() = collapse((listOf(text) + children.map { it.allText }).joinToString(" "))

    /** Makes each run of spaces one space. A no-break space is a space too, as in Swift. */
    private fun collapse(text: String): String = buildString {
        for (ch in text) {
            if (ch.isWhitespace()) {
                if (isNotEmpty() && last() != ' ') append(' ')
            } else {
                append(ch)
            }
        }
    }.trimEnd()

    companion object {
        /**
         * Parses a document. A document of XHTML can use the entities of HTML, such as &nbsp;,
         * which XML does not know. The parser tries again with them changed into characters.
         */
        fun parse(data: ByteArray): XmlElement? {
            build(InputSource(data.inputStream()))?.let { return it }
            var text = TextDecoding.decode(data)
            for ((entity, value) in htmlEntities) text = text.replace(entity, value)
            return build(InputSource(StringReader(text)))
        }

        internal val htmlEntities = mapOf(
            "&nbsp;" to " ", "&mdash;" to "—", "&ndash;" to "–", "&hellip;" to "…",
            "&lsquo;" to "‘", "&rsquo;" to "’", "&ldquo;" to "“", "&rdquo;" to "”",
            "&copy;" to "©", "&reg;" to "®", "&trade;" to "™", "&middot;" to "·",
            "&bull;" to "•", "&laquo;" to "«", "&raquo;" to "»", "&times;" to "×",
        )

        private fun build(source: InputSource): XmlElement? = runCatching {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            factory.isExpandEntityReferences = false
            // A file of a book must not load another file or a URL.
            runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            val builder = factory.newDocumentBuilder()
            builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
            builder.setErrorHandler(null)
            convert(builder.parse(source).documentElement, null)
        }.getOrNull()

        private fun convert(node: Element, parent: XmlElement?): XmlElement {
            val attributes = linkedMapOf<String, String>()
            val list = node.attributes
            for (i in 0 until list.length) {
                val attribute = list.item(i)
                attributes[attribute.nodeName] = attribute.nodeValue
            }
            for (i in 0 until list.length) {
                val attribute = list.item(i)
                val local = attribute.localName ?: attribute.nodeName.substringAfter(':')
                attributes.putIfAbsent(local, attribute.nodeValue)
            }
            val element = XmlElement(node.localName ?: node.nodeName.substringAfter(':'), attributes)
            element.parent = parent
            val text = StringBuilder()
            var child = node.firstChild
            while (child != null) {
                when (child.nodeType) {
                    Node.ELEMENT_NODE -> element.children += convert(child as Element, element)
                    Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> text.append(child.nodeValue)
                }
                child = child.nextSibling
            }
            element.text = text.toString()
            return element
        }
    }
}
