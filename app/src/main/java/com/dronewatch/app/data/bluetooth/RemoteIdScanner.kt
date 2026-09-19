package com.dronewatch.app.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.dronewatch.app.data.remoteid.OpenDroneIdDecoder
import com.dronewatch.app.data.remoteid.PartialRemoteIdMessage
import com.dronewatch.app.data.remoteid.RemoteIdDecoder
import com.dronewatch.app.domain.model.TransportType
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID

/**
 * Continuous BLE scanner specialised for OpenDroneID / ASTM F3411 Remote ID advertisements.
 *
 * - Uses passive / low-latency scan settings optimised for Remote ID
 * - Filters on the OpenDroneID service UUID when possible
 * - Emits decoded partial messages via a SharedFlow
 * - Never crashes on malformed packets
 */
class RemoteIdScanner(
    private val context: Context,
    private val decoder: RemoteIdDecoder = OpenDroneIdDecoder()
) {
    companion object {
        private const val TAG = "RemoteIdScanner"
        // OpenDroneID 16-bit service UUID
        private val ODID_SERVICE_UUID: UUID = UUID.fromString("0000FFFA-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter
    private var scanner: BluetoothLeScanner? = null
    private var isScanning = false

    private val _messages = MutableSharedFlow<PartialRemoteIdMessage>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages: SharedFlow<PartialRemoteIdMessage> = _messages.asSharedFlow()

    private val _rawAdvertisementCount = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val rawAdvertisementEvents: SharedFlow<Unit> = _rawAdvertisementCount.asSharedFlow()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "BLE scan failed with code $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        try {
            _rawAdvertisementCount.tryEmit(Unit)

            val scanRecord = result.scanRecord ?: return
            val bytes = scanRecord.bytes ?: return
            val rssi = result.rssi
            val timestamp = System.currentTimeMillis()

            // Prefer service data for OpenDroneID
            val serviceData = scanRecord.getServiceData(ParcelUuid(ODID_SERVICE_UUID))
            val payload = when {
                serviceData != null && serviceData.isNotEmpty() -> serviceData
                else -> bytes
            }

            val transport = TransportType.BLUETOOTH_LEGACY // Could refine with PHY info on newer APIs

            val decoded = decoder.decode(payload, rssi, transport, timestamp)
            if (decoded != null) {
                _messages.tryEmit(decoded)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling scan result", e)
        }
    }

    /**
     * Start continuous scanning. Safe to call multiple times.
     * Requires BLUETOOTH_SCAN (and location on older Android) permission.
     */
    @SuppressLint("MissingPermission")
    fun start() {
        if (isScanning) return
        val bt = adapter
        if (bt == null || !bt.isEnabled) {
            Log.w(TAG, "Bluetooth not available or disabled")
            return
        }
        scanner = bt.bluetoothLeScanner ?: run {
            Log.w(TAG, "BluetoothLeScanner null")
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    ScanSettings.SCAN_MODE_LOW_LATENCY
                } else {
                    ScanSettings.SCAN_MODE_BALANCED
                }
            )
            .setReportDelay(0)
            .build()

        // Prefer filtered scan for power efficiency; fall back to unfiltered
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(ODID_SERVICE_UUID))
                .build()
        )

        try {
            scanner?.startScan(filters, settings, scanCallback)
            isScanning = true
            Log.i(TAG, "Remote ID BLE scan started (filtered)")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission for BLE scan", e)
        } catch (e: Exception) {
            // Some devices reject the filter – try without
            try {
                scanner?.startScan(null, settings, scanCallback)
                isScanning = true
                Log.i(TAG, "Remote ID BLE scan started (unfiltered fallback)")
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to start BLE scan", e2)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!isScanning) return
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping scan", e)
        }
        isScanning = false
        Log.i(TAG, "Remote ID BLE scan stopped")
    }

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    fun isBluetoothAvailable(): Boolean = adapter != null

    fun isCurrentlyScanning(): Boolean = isScanning
}
