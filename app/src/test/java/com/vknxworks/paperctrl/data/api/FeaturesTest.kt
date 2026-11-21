package com.vknxworks.paperctrl.data.api

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturesTest {
    private fun obj(json: String) = ApiJson.parseToJsonElement(json) as JsonObject

    @Test
    fun versionsCompareByNumber() {
        assertTrue(versionAtLeast("3.3.4", 3, 3))
        assertTrue(versionAtLeast("v3.10.0", 3, 2))
        assertTrue(versionAtLeast("3", 3))
        assertFalse(versionAtLeast("3.1.9", 3, 2))
        assertFalse(versionAtLeast("2.20.15", 3))
        assertFalse(versionAtLeast(null, 2))
        assertFalse(versionAtLeast("unknown", 2))
    }

    @Test
    fun featuresFollowTheServerVersion() {
        val old = serverFeatures(9, "2.20.15")
        assertFalse(old.versions || old.bundles || old.removePassword || old.duplicatesFilter || old.barcodeSearch)
        val three = serverFeatures(10, "3.1.0")
        assertTrue(three.versions && three.bundles && three.removePassword)
        assertFalse(three.duplicatesFilter || three.barcodeSearch)
        assertTrue(serverFeatures(10, "3.2.0").duplicatesFilter)
        assertFalse(serverFeatures(10, "3.2.0").barcodeSearch)
        assertTrue(serverFeatures(10, "3.3.4").barcodeSearch)
        // API 10 with an unknown version: Paperless 3 for sure, the later additions not assumed.
        val unknown = serverFeatures(10, null)
        assertTrue(unknown.versions)
        assertFalse(unknown.duplicatesFilter)
    }

    @Test
    fun failedImportIsReadFromBothApiVersions() {
        val v10 = parseFailedImport(
            obj("""{"id":7,"task_id":"abc","status":"failure","date_created":"2026-11-20T10:00:00Z","input_data":{"filename":"bill.pdf"},"result_data":{"error_message":"bad file"}}"""),
        )!!
        assertEquals(7, v10.id)
        assertEquals("bill.pdf", v10.fileName)
        assertEquals("bad file", v10.message)
        val v9 = parseFailedImport(obj("""{"id":8,"task_id":"def","status":"FAILURE","task_file_name":"scan.pdf","result":"Not consuming"}"""))!!
        assertEquals("scan.pdf", v9.fileName)
        assertEquals("Not consuming", v9.message)
        assertNull(parseFailedImport(obj("""{"id":9,"task_id":"ghi","status":"success"}""")))
        assertNull(parseFailedImport(obj("""{"task_id":"no-row-id","status":"failure"}""")))
    }

    @Test
    fun globalSearchBecomesAFlatList() {
        val found = parseGlobalSearch(
            obj("""{"total":3,"documents":[{"id":1,"title":"Invoice"}],"tags":[{"id":2,"name":"Bills"},{"id":3,"name":""}],"users":[{"id":4,"username":"x"}],"saved_views":[{"id":5,"name":"Recent"}]}"""),
        )
        assertEquals(listOf(Found(FoundKind.DOCUMENT, 1, "Invoice"), Found(FoundKind.VIEW, 5, "Recent"), Found(FoundKind.TAG, 2, "Bills")), found)
    }
}
