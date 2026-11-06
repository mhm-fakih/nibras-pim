package com.knotes.expenses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentsTest {

    private val p = PendingPayment(
        ref = "r-1", amount = 12.5, currency = "LBP", date = "2026-11-05T09:30:00",
        categoryId = 7, categoryName = "Food", summary = "خبز", notes = "", paid = true, queuedAt = 1,
    )

    @Test
    fun aQueuedPaymentSurvivesBeingSavedAndReadBack() {
        val back = PendingPayment.listFromJson(PendingPayment.listToJson(listOf(p, p.copy(ref = "r-2", categoryId = null, error = "No such category."))))
        assertEquals(listOf(p, p.copy(ref = "r-2", categoryId = null, error = "No such category.")), back)
    }

    @Test
    fun theRequestCarriesTheClientRefAndLeavesOutWhatIsBlank() {
        val o = p.copy(currency = "", notes = "").toRequest()
        assertEquals("r-1", o.getString("clientRef"))
        assertEquals(7L, o.getLong("categoryId"))
        assertTrue(o.getBoolean("paid"))
        assertFalse(o.has("currency"))   // blank = the server's default
        assertFalse(o.has("notes"))
        assertFalse(p.copy(categoryId = null).toRequest().has("categoryId"))
    }

    @Test
    fun aDamagedStoreLosesOnlyTheBadEntries() {
        val text = """[{"ref":"a","amount":3,"date":"2026-11-05T10:00:00"},{"ref":"","amount":1,"date":"x"},{"amount":-2}]"""
        assertEquals(listOf("a"), PendingPayment.listFromJson(text).map { it.ref })
        assertEquals(emptyList<PendingPayment>(), PendingPayment.listFromJson("not json"))
    }

    @Test
    fun amountsAreReadWithEitherDecimalMark() {
        assertEquals(1500.5, parseAmount("1 500,5")!!, 0.0)
        assertEquals(1500.5, parseAmount("1,500.5")!!, 0.0)
        assertEquals(1500.5, parseAmount("1.500,5")!!, 0.0)
        assertEquals(1500000.0, parseAmount("1,500,000")!!, 0.0)
        assertEquals(12.5, parseAmount("12.5")!!, 0.0)
        assertNull(parseAmount(""))
        assertNull(parseAmount("0"))
        assertNull(parseAmount("abc"))
    }

    @Test
    fun theServerListsParse() {
        val cats = parseCategories("""[{"id":3,"name":"Car","code":null},{"id":4,"name":null,"code":"FOOD"},{"id":0,"name":"bad"}]""")
        assertEquals(listOf(PayCategory(3, "Car"), PayCategory(4, "FOOD")), cats)
        val cur = parseCurrencies("""{"defaultCurrency":"USD","currencies":[{"code":"LBP"},{"code":"USD"}]}""")!!
        assertEquals("USD", cur.default)
        assertEquals(listOf("USD", "LBP"), cur.codes)
    }
}
