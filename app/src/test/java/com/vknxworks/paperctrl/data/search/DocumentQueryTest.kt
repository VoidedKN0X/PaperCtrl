package com.vknxworks.paperctrl.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DocumentQueryTest {
    @Test
    fun barcodeBecomesOneSearchTermPerPart() {
        assertEquals("barcodes:WIFI barcodes:S barcodes:Guest", DocumentQuery.barcodeTerms("WIFI:S:Guest;"))
        assertEquals("barcodes:DE89370400440532013000", DocumentQuery.barcodeTerms(" DE89370400440532013000 "))
        assertEquals("", DocumentQuery.barcodeTerms(" :; "))
    }

    @Test
    fun barcodeAndDuplicatesGoIntoTheParameters() {
        val p = DocumentQuery(hasDuplicates = true, barcode = "guest").toParams()
        assertEquals("true", p["has_duplicates"])
        assertEquals("barcodes:guest", p["query"])
        val withText = DocumentQuery(text = "invoice", textMode = TextMode.FULLTEXT, barcode = "guest").toParams()
        assertEquals("invoice barcodes:guest", withText["query"])
        // "More like this" can't be combined with a search query.
        assertFalse(DocumentQuery(moreLike = 4, barcode = "guest").toParams().containsKey("query"))
    }
}
