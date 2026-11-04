package com.dailyworks.apnalaundry.data

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A phone contact usable as a customer: name + a 10-digit Indian mobile number. */
data class PhoneContact(val name: String, val phone: String)

/**
 * Reads the phone's contacts (needs READ_CONTACTS) for "Choose from contacts"
 * when adding a customer. Numbers are reduced to their last 10 digits (drops
 * +91 / 0 prefixes, spaces and dashes); entries without a 10-digit number are
 * skipped, and each number appears once.
 */
object PhoneContacts {
    fun tenDigits(raw: String): String? {
        val d = raw.filter { it.isDigit() }
        val ten = when {
            d.length == 10 -> d
            d.length == 11 && d.startsWith("0") -> d.drop(1)
            d.length == 12 && d.startsWith("91") -> d.drop(2)
            else -> return null
        }
        return ten.takeIf { it.first() in '6'..'9' }
    }

    suspend fun load(context: Context): List<PhoneContact> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, PhoneContact>()
        val cols = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, cols, null, null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC",
        )?.use { c ->
            val iName = c.getColumnIndex(cols[0])
            val iNum = c.getColumnIndex(cols[1])
            while (c.moveToNext()) {
                val phone = tenDigits(c.getString(iNum) ?: continue) ?: continue
                val name = (c.getString(iName) ?: "").trim().ifEmpty { phone }
                out.putIfAbsent(phone, PhoneContact(name, phone))
            }
        }
        out.values.toList()
    }

    /**
     * Whether a 10-digit number is saved in the phone's contacts (needs READ_CONTACTS;
     * false without it). WhatsApp opens a chat straight from a share only for
     * numbers it knows — saved contacts — so bills go as a picture to those and
     * as text + link to the rest (BillShare).
     */
    suspend fun isSaved(context: Context, digits: String): Boolean = withContext(Dispatchers.IO) {
        if (digits.length != 10) return@withContext false
        runCatching {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode("+91$digits"))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use { it.count > 0 } ?: false
        }.getOrDefault(false)
    }

    /** The contact chosen in the system picker (no permission needed): name + first valid number. */
    suspend fun fromPickerUri(context: Context, contactUri: Uri): PhoneContact? = withContext(Dispatchers.IO) {
        val id = context.contentResolver.query(contactUri, arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) to (c.getString(1) ?: "") else null } ?: return@withContext null
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?", arrayOf(id.first), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val phone = tenDigits(c.getString(0) ?: continue) ?: continue
                return@withContext PhoneContact(id.second.trim().ifEmpty { phone }, phone)
            }
        }
        null
    }
}
