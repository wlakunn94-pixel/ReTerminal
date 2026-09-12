package com.rk.terminal.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

enum class UsbProtocol {
    ADB,
    FASTBOOT,
    UNKNOWN
}

data class UsbTarget(
    val device: UsbDevice,
    val protocol: UsbProtocol,
    val description: String
)

/**
 * Minimal USB-host transport for Android devices. This intentionally uses
 * UsbManager directly so no root, Shizuku, or desktop bridge is required.
 */
class UsbAdbFastbootManager(private val context: Context) {
    companion object {
        const val ACTION_USB_PERMISSION = "com.rk.terminal.USB_PERMISSION"
        private const val USB_TIMEOUT_MS = 2_000
    }

    private val usbManager = context.getSystemService(UsbManager::class.java)
    private var connection: UsbDeviceConnection? = null
    private var claimedInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null

    fun targets(): List<UsbTarget> = usbManager.deviceList.values.mapNotNull { device ->
        val protocol = detectProtocol(device) ?: return@mapNotNull null
        UsbTarget(
            device = device,
            protocol = protocol,
            description = "${device.productName ?: "Android device"} (${device.deviceName})"
        )
    }

    fun permissionIntent(): PendingIntent {
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun requestPermission(target: UsbTarget) {
        usbManager.requestPermission(target.device, permissionIntent())
    }

    fun hasPermission(target: UsbTarget): Boolean = usbManager.hasPermission(target.device)

    fun registerReceiver(receiver: BroadcastReceiver) {
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
    }

    fun unregisterReceiver(receiver: BroadcastReceiver) {
        context.unregisterReceiver(receiver)
    }

    suspend fun fastbootGetvar(target: UsbTarget, variable: String): String =
        withContext(Dispatchers.IO) {
            require(target.protocol == UsbProtocol.FASTBOOT) { "The selected device is not in fastboot mode" }
            open(target.device, UsbProtocol.FASTBOOT)
            try {
                transfer("getvar:$variable")
            } finally {
                close()
            }
        }

    suspend fun fastbootReboot(target: UsbTarget): String =
        withContext(Dispatchers.IO) {
            require(target.protocol == UsbProtocol.FASTBOOT) { "The selected device is not in fastboot mode" }
            open(target.device, UsbProtocol.FASTBOOT)
            try {
                transfer("reboot")
            } finally {
                close()
            }
        }

    private fun detectProtocol(device: UsbDevice): UsbProtocol? {
        for (index in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(index)
            if (usbInterface.interfaceClass != UsbConstants.USB_CLASS_VENDOR_SPEC) continue
            when {
                usbInterface.interfaceSubclass == 0x42 && usbInterface.interfaceProtocol == 0x01 ->
                    return UsbProtocol.ADB
                usbInterface.interfaceSubclass == 0x03 && usbInterface.interfaceProtocol == 0x01 ->
                    return UsbProtocol.FASTBOOT
            }
        }
        return null
    }

    private fun open(device: UsbDevice, protocol: UsbProtocol) {
        check(usbManager.hasPermission(device)) { "USB permission was not granted" }
        close()
        val selectedInterface = (0 until device.interfaceCount)
            .map(device::getInterface)
            .firstOrNull {
                it.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC &&
                    when (protocol) {
                        UsbProtocol.ADB -> it.interfaceSubclass == 0x42 && it.interfaceProtocol == 0x01
                        UsbProtocol.FASTBOOT -> it.interfaceSubclass == 0x03 && it.interfaceProtocol == 0x01
                        UsbProtocol.UNKNOWN -> false
                    }
            }
            ?: error("No $protocol USB interface found")
        val usbConnection = usbManager.openDevice(device) ?: error("Unable to open USB device")
        check(usbConnection.claimInterface(selectedInterface, true)) { "Unable to claim USB interface" }
        val endpoints = (0 until selectedInterface.endpointCount).map(selectedInterface::getEndpoint)
        inEndpoint = endpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_IN }
        outEndpoint = endpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_OUT }
        check(inEndpoint != null && outEndpoint != null) { "USB interface has no bulk endpoints" }
        connection = usbConnection
        claimedInterface = selectedInterface
    }

    private fun transfer(command: String): String {
        val usbConnection = connection ?: error("USB device is not connected")
        val input = inEndpoint ?: error("USB input endpoint is unavailable")
        val output = outEndpoint ?: error("USB output endpoint is unavailable")
        // Fastboot commands are fixed-size ASCII packets, padded with NUL bytes.
        val bytes = ByteArray(64)
        command.toByteArray(StandardCharsets.UTF_8).also {
            check(it.size <= bytes.size) { "Fastboot command is too long" }
            it.copyInto(bytes)
        }
        check(usbConnection.bulkTransfer(output, bytes, bytes.size, USB_TIMEOUT_MS) >= 0) {
            "USB command failed"
        }
        val response = ByteArray(input.maxPacketSize.coerceAtLeast(64))
        val result = StringBuilder()
        do {
            val length = usbConnection.bulkTransfer(input, response, response.size, USB_TIMEOUT_MS)
            check(length >= 0) { "USB device did not respond" }
            val packet = response.copyOf(length).toString(StandardCharsets.UTF_8).trimEnd('\u0000')
            result.append(packet)
        } while (packet.length < 4 || (packet.substring(0, 4) != "OKAY" && packet.substring(0, 4) != "FAIL"))
        return result.toString()
    }

    fun close() {
        val usbConnection = connection ?: return
        claimedInterface?.let { usbConnection.releaseInterface(it) }
        usbConnection.close()
        connection = null
        claimedInterface = null
        inEndpoint = null
        outEndpoint = null
    }
}
