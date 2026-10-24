package io.github.stivengjekaj.moekoma.core

import com.dylibso.chicory.wabt.Wat2Wasm
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AidokuHostTest {
    /** A small source in WAT that answers as the register_source! macro of aidoku-rs does. As in Asabunko. */
    private val fakeSource: ByteArray = Wat2Wasm.parse("""
        (module
          (import "std" "buffer_len" (func ${'$'}len (param i32) (result i32)))
          (import "std" "read_buffer" (func ${'$'}read (param i32 i32 i32) (result i32)))
          (import "js" "unknown_function" (func ${'$'}unknown (param i64) (result f64)))
          (memory (export "memory") 1)
          ;; A result: total 26, capacity 0, and the postcard of a page with one series "1" "A" and a next page.
          (data (i32.const 64) "\1a\00\00\00\00\00\00\00\01\01\31\01\41\00\00\00\00\00\00\00\00\00\00\00\00\01")
          ;; An error message: -1, capacity, total 15, and "Bad".
          (data (i32.const 128) "\ff\ff\ff\ff\00\00\00\00\0f\00\00\00Bad")
          (global ${'$'}freed (mut i32) (i32.const 0))
          (func (export "start"))
          (func (export "free_result") (param i32) (global.set ${'$'}freed (i32.add (global.get ${'$'}freed) (i32.const 1))))
          (func (export "freed") (result i32) (global.get ${'$'}freed))
          ;; The stub of an import that the host does not have gives -1.
          (func (export "unknown") (result f64) (call ${'$'}unknown (i64.const 1)))
          ;; The first byte of the query, as the page number, so that the test sees the argument.
          (func (export "get_search_manga_list") (param ${'$'}query i32) (param ${'$'}page i32) (param ${'$'}filters i32) (result i32)
            (if (i32.eq (local.get ${'$'}page) (i32.const 9)) (then (return (i32.const 128))))
            (if (i32.eq (local.get ${'$'}page) (i32.const 8)) (then (return (i32.const -3))))
            (drop (call ${'$'}read (local.get ${'$'}query) (i32.const 512) (call ${'$'}len (local.get ${'$'}query))))
            (i32.const 64))
          (func (export "query_byte") (result i32) (i32.load8_u (i32.const 512))))
    """.trimIndent())

    private fun host() = AidokuHost("fake", fakeSource, AidokuDefaults(null, emptyMap()), fixedFetcher(emptyMap()), null).also { h -> runBlocking { h.run { start() } } }

    @Test fun readsAResultAndFreesItOneTime() = runBlocking {
        val host = host()
        val data = host.run { call("get_search_manga_list", "Z".toByteArray(), 1, byteArrayOf(0)) }
        assertEquals(listOf("A"), Aidoku.MangaPageResult.read(PostcardReader(data)).entries.map { it.title })
        assertEquals(1, host.run { invoke("freed")[0].toInt() })
        assertEquals(90, host.run { invoke("query_byte")[0].toInt() })
        host.close()
    }

    @Test fun givesTheMessageAndTheCodeOfAnError() {
        val host = host()
        val message = assertThrows(AidokuException.Message::class.java) { runBlocking { host.run { call("get_search_manga_list", ByteArray(0), 9, byteArrayOf(0)) } } }
        assertEquals("Bad", message.message)
        val code = assertThrows(AidokuException.Code::class.java) { runBlocking { host.run { call("get_search_manga_list", ByteArray(0), 8, byteArrayOf(0)) } } }
        assertEquals(-3, code.code)
        host.close()
    }

    @Test fun givesAStubToAnImportThatItDoesNotHave() = runBlocking {
        val host = host()
        assertEquals(-1.0, java.lang.Double.longBitsToDouble(host.run { invoke("unknown")[0] }), 0.0)
        host.close()
    }

    @Test fun refusesAMissingExport() {
        val host = host()
        assertThrows(AidokuException.Missing::class.java) { runBlocking { host.run { call("get_manga_list") } } }
        host.close()
    }

    @Test fun keepsTheSettingsInAFile() {
        val file = kotlin.io.path.createTempFile("aidoku", ".json").toFile().apply { delete() }
        val first = AidokuDefaults(file, mapOf("url" to "a".toByteArray()))
        first["url"] = "b".toByteArray()
        val second = AidokuDefaults(file, mapOf("url" to "a".toByteArray(), "other" to "c".toByteArray()))
        assertEquals("b", String(second["url"]!!))
        assertEquals("c", String(second["other"]!!))
        assertTrue(file.delete())
    }
}
