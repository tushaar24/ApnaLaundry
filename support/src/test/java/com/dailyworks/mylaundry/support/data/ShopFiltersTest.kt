package com.dailyworks.mylaundry.support.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ShopFiltersTest {
    @Test
    fun defaultQueryOnlyCarriesSortAndPaging() {
        assertEquals(
            mapOf("sort" to "joined_desc", "page" to "1", "pageSize" to "30"),
            ShopFilters().toQuery(1, 30),
        )
    }

    @Test
    fun everyFilterMapsToItsParam() {
        val f = ShopFilters(
            q = "  sharma ", sub = linkedSetOf("trial", "paid"), setup = "done", orders = "has",
            activeWithinDays = 7, called = "never", agent = "Asha", tagIds = linkedSetOf("t1", "t2"),
            sort = "last_call_asc",
        )
        val q = f.toQuery(2, 50)
        assertEquals("sharma", q["q"])
        assertEquals("trial,paid", q["sub"])
        assertEquals("done", q["setup"])
        assertEquals("has", q["orders"])
        assertEquals("7", q["activeWithinDays"])
        assertEquals("never", q["called"])
        assertEquals("Asha", q["agent"])
        assertEquals("t1,t2", q["tag"])
        assertEquals("last_call_asc", q["sort"])
        assertEquals("2", q["page"])
        assertEquals(6, f.sheetCount) // setup, orders, active, called, agent, tags
    }
}
