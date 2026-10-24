package io.github.stivengjekaj.moekoma.ui

import android.content.Context
import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * The calls of the novel reader to the page of a chapter: the sentences for the speech, the search, and the marks, as
 * in Asabunko. A sentence and a mark are ranges of the text of the page, and the CSS Custom Highlight API shows them
 * with no change of the page.
 */
class ChapterControl {
    var view: WebView? = null

    /** The result of a script as JSON, or null with no page. */
    private suspend fun call(script: String): String? {
        val web = view ?: return null
        return suspendCancellableCoroutine { done -> web.evaluateJavascript("(function () { $script })()") { done.resume(it) } }
    }

    suspend fun sentences(): List<String> {
        val json = call("return window.moekoma ? window.moekoma.sentences() : [];") ?: return emptyList()
        return runCatching { JSONArray(json).let { array -> List(array.length()) { array.getString(it) } } }.getOrDefault(emptyList())
    }

    suspend fun markSentence(index: Int) { call("window.moekoma && window.moekoma.markSentence($index); return true;") }

    suspend fun clearSentence() { call("window.moekoma && window.moekoma.clear('moekoma-speech'); return true;") }

    /** Shows the first place of the words in the chapter. It gives false when the chapter does not have them. */
    suspend fun find(words: String): Boolean = call("return !!(window.moekoma && window.moekoma.find(${JSONObject.quote(words)}));") == "true"

    suspend fun showMarks(texts: List<String>) { call("window.moekoma && window.moekoma.marks(${JSONArray(texts)}); return true;") }

    /** The words that the user selected, or null. */
    suspend fun selection(): String? = text(call("return window.moekoma ? window.moekoma.selection() : '';"))?.takeIf { it.isNotBlank() }

    /** The first words at the top of the screen, for the title of a bookmark. */
    suspend fun visibleText(): String = text(call("return window.moekoma ? window.moekoma.visible() : '';")).orEmpty()

    /** A JSON string as text. */
    private fun text(json: String?): String? = json?.let { runCatching { JSONArray("[$it]").getString(0) }.getOrNull() }

    companion object {
        /** The scripts of the page. */
        val SCRIPT = """
            (function () {
              if (window.moekoma) return;
              function textNodes() {
                const walker = document.createTreeWalker(document.body || document.documentElement, NodeFilter.SHOW_TEXT, {
                  acceptNode: (node) => node.nodeValue.trim() && !node.parentElement.closest('script, style') ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT });
                const list = [];
                while (walker.nextNode()) list.push(walker.currentNode);
                return list;
              }
              // All the text of the page in one string, with the start of each text node in it.
              function flat() {
                const nodes = textNodes(); const starts = []; let text = '';
                for (const node of nodes) { starts.push(text.length); text += node.nodeValue; }
                return { nodes, starts, text };
              }
              function locate(f, offset) {
                let low = 0, high = f.nodes.length - 1;
                while (low < high) { const mid = (low + high + 1) >> 1; if (f.starts[mid] <= offset) low = mid; else high = mid - 1; }
                return { node: f.nodes[low], offset: Math.min(offset - f.starts[low], f.nodes[low].nodeValue.length) };
              }
              function range(f, start, end) {
                const a = locate(f, start), b = locate(f, Math.max(end, start));
                const r = document.createRange(); r.setStart(a.node, a.offset); r.setEnd(b.node, b.offset); return r;
              }
              function show(r) {
                const rect = r.getBoundingClientRect();
                if (rect.top < 60 || rect.bottom > window.innerHeight - 90) window.scrollBy(0, rect.top - window.innerHeight / 3);
              }
              let sentences = null;
              window.moekoma = {
                sentences() {
                  const f = flat(); const list = [];
                  const pattern = /[^.!?。！？…]+[.!?。！？…]*["'”’」』)\]]*\s*/g;
                  let m;
                  while ((m = pattern.exec(f.text))) {
                    const words = m[0].trim();
                    if (words) list.push({ words, start: m.index, end: m.index + m[0].trimEnd().length });
                  }
                  sentences = { f, list };
                  return list.map((s) => s.words);
                },
                markSentence(index) {
                  if (!sentences || !sentences.list[index]) return;
                  const s = sentences.list[index]; const r = range(sentences.f, s.start, s.end);
                  CSS.highlights.set('moekoma-speech', new Highlight(r)); show(r);
                },
                clear(name) { CSS.highlights.delete(name); },
                find(words) {
                  const f = flat(); const at = f.text.toLowerCase().indexOf(String(words).toLowerCase());
                  if (at < 0) return false;
                  const r = range(f, at, at + words.length); CSS.highlights.set('moekoma-find', new Highlight(r)); show(r); return true;
                },
                marks(texts) {
                  const f = flat(); const h = new Highlight();
                  for (const t of texts) { const at = f.text.indexOf(t); if (at >= 0) h.add(range(f, at, at + t.length)); }
                  CSS.highlights.set('moekoma-marks', h);
                },
                selection() { return String(window.getSelection() || '').trim(); },
                visible() {
                  for (const node of textNodes()) {
                    const r = document.createRange(); r.selectNodeContents(node); const rect = r.getBoundingClientRect();
                    if (rect.bottom > 60) return node.nodeValue.trim().slice(0, 80);
                  }
                  return '';
                },
              };
            })();
        """.trimIndent()

        /** The colors of the sentence of the speech, of the marks, and of a result of a search. */
        const val CSS = """
            ::highlight(moekoma-speech) { background-color: rgba(230, 170, 40, 0.35); }
            ::highlight(moekoma-marks) { background-color: rgba(255, 214, 10, 0.45); }
            ::highlight(moekoma-find) { background-color: rgba(60, 150, 255, 0.35); }
        """
    }
}

/** A web view of a chapter with "Highlight" in the menu of a selection. */
class ChapterWebView(context: Context) : WebView(context) {
    /** Gets the words of a highlight that the user made from a selection. */
    var onHighlight: ((String) -> Unit)? = null

    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? = super.startActionMode(withHighlight(callback), type)

    private fun withHighlight(callback: ActionMode.Callback) = object : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val shown = callback.onCreateActionMode(mode, menu)
            menu.add(Menu.NONE, HIGHLIGHT, 0, "Highlight")
            return shown
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = callback.onPrepareActionMode(mode, menu)

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (item.itemId != HIGHLIGHT) return callback.onActionItemClicked(mode, item)
            // The page reads the selection before the menu closes, because the close clears the selection.
            evaluateJavascript("String(window.getSelection() || '').trim()") { json ->
                val words = runCatching { JSONArray("[$json]").getString(0) }.getOrNull().orEmpty()
                mode.finish()
                if (words.isNotBlank()) onHighlight?.invoke(words)
            }
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) = callback.onDestroyActionMode(mode)

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
            if (callback is ActionMode.Callback2) callback.onGetContentRect(mode, view, outRect) else super.onGetContentRect(mode, view, outRect)
        }
    }

    private companion object {
        const val HIGHLIGHT = 0x6d6b
    }
}
