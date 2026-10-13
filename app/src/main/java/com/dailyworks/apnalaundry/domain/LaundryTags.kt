package com.dailyworks.apnalaundry.domain

/**
 * A laundry tag: the slip pinned to a cloth so an order's clothes stay together
 * through washing and ironing. Printed on the shop's Bluetooth thermal printer
 * (print/TagPrinter), drawn by print/TagRender in the usual shop layout: shop,
 * CUSTOMER, #order, date, [service code], cloth, piece count, tear line.
 */
data class LaundryTag(
    val shop: String,
    val customer: String,
    val orderNo: String,  // the order's "#…" (owner's serial, else the id)
    val date: String,     // iso order date
    val due: String,      // iso delivery date; "" = not set
    val code: String,     // service code for the box: "DC", "WI", "DC+WF"; "" = no box
    val item: String,     // "Shirt", "Clothes", "Bag · 4 kg"; "12 clothes" on the order's tag
    val count: String,    // "3/12" on a cloth's tag, "12 pcs" on the order's one tag; "" = none
    val express: Boolean,
    val note: String,     // the order's note; "" = none
)

object LaundryTags {
    /** What one cloth's tag is for. [cloth] = false for a bag nobody counted. */
    private data class Piece(val item: String, val service: String, val cloth: Boolean = true)

    /**
     * Every cloth in the order, in bill order: each counted piece; a by-weight
     * bag's listed clothes (else its count, else one tag for the bag); a
     * not-itemised bill's pieces (else one tag for all of it).
     */
    private fun pieces(o: Order): List<Piece> = o.lines.flatMap { l ->
        when {
            l.isQuick ->
                if (l.qty > 0) List(l.qty) { Piece("Clothes", "") } else listOf(Piece("Clothes", "", cloth = false))
            l.kg > 0 && l.clothes.any { it.qty > 0 } ->
                l.clothes.filter { it.qty > 0 }.flatMap { c -> List(c.qty) { Piece(c.name, l.serviceName) } }
            l.kg > 0 && l.qty > 0 -> List(l.qty) { Piece("Clothes", l.serviceName) }
            l.kg > 0 -> listOf(Piece("Bag · ${trimKg(l.kg)} kg", l.serviceName, cloth = false))
            else -> List(l.qty.coerceAtLeast(0)) { Piece(l.itemName, l.serviceName) }
        }
    }

    /** Tags "one for each cloth" prints; 0 = the clothes aren't counted yet. */
    fun pieceCount(o: Order): Int = pieces(o).size

    /** One tag per cloth, numbered "1/12" … "12/12". */
    fun perPiece(shop: Shop, o: Order, c: Customer): List<LaundryTag> {
        val all = pieces(o)
        return all.mapIndexed { i, p -> tag(shop, o, c, code(p.service), p.item, "${i + 1}/${all.size}") }
    }

    /** One tag for the whole order (for its bag): every service's code, and how many clothes. */
    fun forOrder(shop: Shop, o: Order, c: Customer): LaundryTag {
        val clothes = pieces(o).count { it.cloth }
        val codes = o.lines.filter { !it.isQuick }.map { code(it.serviceName) }.filter { it.isNotEmpty() }.distinct()
        return tag(
            shop, o, c, codes.joinToString("+"),
            if (clothes > 0) "$clothes ${if (clothes == 1) "cloth" else "clothes"}" else "Clothes",
            if (clothes > 0) "$clothes pcs" else "",
        )
    }

    /**
     * A service's short code for the tag's box — its initials, or a one-word
     * name itself: "Dry Cleaning" → "DC", "Wash & Iron" → "WI", "Iron" → "IRON".
     */
    fun code(service: String): String {
        val words = service.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() && it.lowercase() != "and" }
        return when {
            words.isEmpty() -> ""
            words.size == 1 -> words[0].let { if (it.length <= 6) it else it.take(4) }.uppercase()
            else -> words.take(3).joinToString("") { it.take(1) }.uppercase()
        }
    }

    private fun tag(shop: Shop, o: Order, c: Customer, code: String, item: String, count: String) = LaundryTag(
        shop = shop.name.trim(), customer = c.name.trim(), orderNo = o.no(), date = o.pickupDate, due = o.deliveryDate,
        code = code, item = item, count = count, express = o.express, note = o.note.trim(),
    )

    private fun trimKg(kg: Double): String = if (kg % 1.0 == 0.0) kg.toInt().toString() else kg.toString()
}
