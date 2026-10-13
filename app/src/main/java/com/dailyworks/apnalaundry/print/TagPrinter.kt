package com.dailyworks.apnalaundry.print

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.dailyworks.apnalaundry.domain.LaundryTag
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Laundry tags on a Bluetooth thermal printer — the 58 / 80 mm kind shops use
 * for receipts — over classic Bluetooth serial (SPP) with ESC/POS raster
 * images. The printer is paired once in the phone's Bluetooth settings; the
 * app only lists paired devices and never scans, so it needs no location.
 */
object TagPrinter {
    private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    /** A paired Bluetooth device; [isPrinter] = it says it's a printer (or is named like one). */
    data class Device(val address: String, val name: String, val isPrinter: Boolean)

    /** Why printing failed: [reason] for analytics, the message for the owner. */
    class PrintError(val reason: String, message: String) : Exception(message)

    fun adapter(context: Context): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    /** Android 12+ asks for "Nearby devices" before an app may see paired devices. */
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun isOn(context: Context): Boolean = runCatching { adapter(context)?.isEnabled == true }.getOrDefault(false)

    /** The phone's paired devices, printers first; empty without permission or with Bluetooth off. */
    @SuppressLint("MissingPermission")
    fun paired(context: Context): List<Device> {
        if (!hasPermission(context) || !isOn(context)) return emptyList()
        val bonded = runCatching { adapter(context)?.bondedDevices.orEmpty() }.getOrDefault(emptySet())
        return bonded.map { d ->
            val name = runCatching { d.name }.getOrNull().orEmpty().ifBlank { d.address }
            Device(d.address, name, looksLikePrinter(d, name))
        }.sortedWith(compareByDescending<Device> { it.isPrinter }.thenBy { it.name.lowercase() })
    }

    @SuppressLint("MissingPermission")
    private fun looksLikePrinter(d: BluetoothDevice, name: String): Boolean {
        if (runCatching { d.bluetoothClass?.majorDeviceClass }.getOrNull() == BluetoothClass.Device.Major.IMAGING) return true
        val n = name.lowercase()
        return listOf("print", "pos", "rpp", "mtp", "thermal", "receipt", "xp-", "pt-").any { it in n }
    }

    /**
     * Prints [tags], [dots] wide, on the printer at [address]. [onProgress]
     * gets 0 once connected, then the count of tags sent. Throws [PrintError].
     */
    @SuppressLint("MissingPermission")
    suspend fun print(
        context: Context, address: String, name: String, tags: List<LaundryTag>, dots: Int,
        onProgress: (Int) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val bt = adapter(context) ?: throw PrintError("no_bluetooth", "This phone has no Bluetooth.")
        if (!hasPermission(context)) throw PrintError("permission", "Bluetooth is not allowed for this app.")
        if (!isOn(context)) throw PrintError("bluetooth_off", "Bluetooth is off. Turn it on and try again.")
        // A running device search slows the connection down (stopping it needs a permission we don't ask for).
        runCatching { bt.cancelDiscovery() }
        val device = runCatching { bt.getRemoteDevice(address) }.getOrNull()
            ?: throw PrintError("connect", "Printer not found. Tap ‘Change’ and choose it again.")
        val socket = connect(device)
            ?: throw PrintError("connect", "Could not connect to $name. Switch the printer on, keep it near the phone, and try again.")
        try {
            val out = socket.outputStream
            out.write(EscPos.INIT)
            withContext(Dispatchers.Main) { onProgress(0) }
            tags.forEachIndexed { i, t ->
                ensureActive()
                val bytes = EscPos.raster(TagRender.render(t, dots))
                try {
                    out.write(bytes); out.flush()
                } catch (e: IOException) {
                    throw PrintError("write", "Printing stopped at tag ${i + 1} of ${tags.size}. Check the printer is on and has paper, then try again.")
                }
                // Cheap printers drop data sent faster than they print (~16 KB a second).
                delay(bytes.size / 16L)
                withContext(Dispatchers.Main) { onProgress(i + 1) }
            }
            out.write(EscPos.feed(4)); out.flush()
            delay(600) // the last bytes reach the printer before the link drops
        } catch (e: CancellationException) {
            throw e
        } catch (e: PrintError) {
            throw e
        } catch (e: Exception) {
            throw PrintError("write", "Printing stopped. Check the printer is on and has paper, then try again.")
        } finally {
            runCatching { socket.close() }
        }
    }

    /** Serial port first; some printers only answer insecurely, or on channel 1 (hidden API — may be blocked). */
    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice): BluetoothSocket? {
        val tries = listOf<() -> BluetoothSocket>(
            { device.createRfcommSocketToServiceRecord(SPP) },
            { device.createInsecureRfcommSocketToServiceRecord(SPP) },
            { device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType).invoke(device, 1) as BluetoothSocket },
        )
        for (make in tries) {
            val socket = runCatching { make() }.getOrNull() ?: continue
            try {
                socket.connect()
                return socket
            } catch (e: Exception) {
                runCatching { socket.close() }
            }
        }
        return null
    }
}

/** The tag printer and choices, kept on this phone (not synced, kept on logout — it's the shop's hardware). */
class TagPrefs(context: Context) {
    private val sp = context.getSharedPreferences("tag_printer", Context.MODE_PRIVATE)

    var address: String
        get() = sp.getString("address", "").orEmpty()
        set(v) = sp.edit().putString("address", v).apply()

    var name: String
        get() = sp.getString("name", "").orEmpty()
        set(v) = sp.edit().putString("name", v).apply()

    /** 3-inch (80 mm) paper; false = 2-inch (58 mm), the common one. */
    var wide: Boolean
        get() = sp.getBoolean("wide", false)
        set(v) = sp.edit().putBoolean("wide", v).apply()

    /** One tag per cloth; false = one tag for the whole order. */
    var perPiece: Boolean
        get() = sp.getBoolean("per_piece", true)
        set(v) = sp.edit().putBoolean("per_piece", v).apply()
}
