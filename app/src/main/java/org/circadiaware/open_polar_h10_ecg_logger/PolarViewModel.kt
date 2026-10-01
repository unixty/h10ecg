package org.circadiaware.open_polar_h10_ecg_logger

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.polar.androidcommunications.api.ble.model.DisInfo
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.EcgSample
import com.polar.sdk.api.model.PolarAccelerometerData
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarEcgData
import com.polar.sdk.api.model.PolarHealthThermometerData
import com.polar.sdk.api.model.PolarHrData
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.rx3.await
import org.circadiaware.open_polar_h10_ecg_logger.util.DemoDataGenerator
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Shared ViewModel managing Polar H10 BLE communication, live sensor streaming (ECG, ACC, HR, RR),
 * and optional demo simulation data distribution across all UI fragments.
 */
class PolarViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PolarViewModel"
    }

    // ─── Listener Interfaces for Charts ───────────────────────────────────────

    fun interface EcgListener {
        fun onEcgSample(voltageUv: Int)
    }

    fun interface AccListener {
        fun onAccSample(x: Int, y: Int, z: Int, magnitude: Double)
    }

    fun interface HrListener {
        fun onHrSample(hrBpm: Int)
    }

    fun interface RrListener {
        fun onRrSample(rrMs: Int)
    }

    private val ecgListeners = CopyOnWriteArrayList<EcgListener>()
    private val accListeners = CopyOnWriteArrayList<AccListener>()
    private val hrListeners = CopyOnWriteArrayList<HrListener>()
    private val rrListeners = CopyOnWriteArrayList<RrListener>()

    // ─── LiveData exposed to the UI ───────────────────────────────────────────

    private val _deviceId = MutableLiveData("—")
    val deviceId: LiveData<String> = _deviceId

    private val _batteryLevel = MutableLiveData("—")
    val batteryLevel: LiveData<String> = _batteryLevel

    private val _heartRate = MutableLiveData("—")
    val heartRate: LiveData<String> = _heartRate

    private val _ecgValue = MutableLiveData("—")
    val ecgValue: LiveData<String> = _ecgValue

    private val _status = MutableLiveData("Disconnected")
    val status: LiveData<String> = _status

    /** true while BLE scan is running */
    private val _isScanning = MutableLiveData(false)
    val isScanning: LiveData<Boolean> = _isScanning

    /** true when simulated demo mode is active. Default is false (waits for real sensor) */
    private val _isDemoMode = MutableLiveData(false)
    val isDemoMode: LiveData<Boolean> = _isDemoMode

    // ─── Polar BLE API ────────────────────────────────────────────────────────

    val api: PolarBleApi = PolarBleApiDefaultImpl.defaultImplementation(
        application.applicationContext,
        setOf(
            PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
            PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
            PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO
        )
    )

    private var connectedDeviceId: String? = null
    private var ecgJob: Job? = null
    private var accJob: Job? = null
    private var scanJob: Job? = null

    private val demoGenerator: DemoDataGenerator = DemoDataGenerator(object : DemoDataGenerator.DemoListener {
        override fun onEcgSample(voltageUv: Int) {
            if (_isDemoMode.value == true) {
                _ecgValue.value = "$voltageUv µV"
                for (listener in ecgListeners) {
                    listener.onEcgSample(voltageUv)
                }
            }
        }

        override fun onAccSample(x: Int, y: Int, z: Int, magnitude: Double) {
            if (_isDemoMode.value == true) {
                for (listener in accListeners) {
                    listener.onAccSample(x, y, z, magnitude)
                }
            }
        }

        override fun onHrSample(hrBpm: Int) {
            if (_isDemoMode.value == true) {
                _heartRate.value = "$hrBpm BPM"
                for (listener in hrListeners) {
                    listener.onHrSample(hrBpm)
                }
            }
        }

        override fun onRrSample(rrMs: Int) {
            if (_isDemoMode.value == true) {
                for (listener in rrListeners) {
                    listener.onRrSample(rrMs)
                }
            }
        }
    })

    // ─── Initialisation ───────────────────────────────────────────────────────

    init {
        setupApiCallbacks()
        // Demo mode is OFF by default; app waits for real sensor connection
    }

    private fun setupApiCallbacks() {
        api.setApiCallback(object : PolarBleApiCallback() {

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "Connected: ${polarDeviceInfo.deviceId}")
                connectedDeviceId = polarDeviceInfo.deviceId
                _deviceId.postValue(polarDeviceInfo.deviceId)
                _status.postValue("Connected")
                _isScanning.postValue(false)
                // Automatically disable demo mode when real sensor connects
                setDemoMode(false)
            }

            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "Connecting: ${polarDeviceInfo.deviceId}")
                connectedDeviceId = polarDeviceInfo.deviceId
                _deviceId.postValue(polarDeviceInfo.deviceId)
                _status.postValue("Connecting...")
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "Disconnected: ${polarDeviceInfo.deviceId}")
                connectedDeviceId = null
                _status.postValue("Disconnected")
                _heartRate.postValue("—")
                _ecgValue.postValue("—")
                _batteryLevel.postValue("—")
                stopStreaming()
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                Log.d(TAG, "Battery $identifier: $level%")
                _batteryLevel.postValue("$level%")
            }

            override fun hrNotificationReceived(
                identifier: String,
                data: PolarHrData.PolarHrSample
            ) {
                _heartRate.postValue("${data.hr} BPM")
                if (_isDemoMode.value != true) {
                    for (l in hrListeners) {
                        l.onHrSample(data.hr)
                    }
                    for (rr in data.rrsMs) {
                        for (l in rrListeners) {
                            l.onRrSample(rr)
                        }
                    }
                }
            }

            override fun disInformationReceived(
                identifier: String,
                disInfo: DisInfo
            ) {
                Log.d(TAG, "DIS info received: $identifier, $disInfo")
            }

            override fun htsNotificationReceived(
                identifier: String,
                data: PolarHealthThermometerData
            ) {
                Log.d(TAG, "HTS notification received: $identifier, $data")
            }

            override fun bleSdkFeatureReady(
                identifier: String,
                feature: PolarBleApi.PolarBleSdkFeature
            ) {
                Log.d(TAG, "SDK feature ready: $feature for $identifier")
                if (feature == PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING) {
                    startEcgStreaming(identifier)
                    startAccStreaming(identifier)
                }
            }
        })
    }

    // ─── Online Streaming ─────────────────────────────────────────────────────

    private fun startEcgStreaming(deviceId: String) {
        ecgJob?.cancel()
        ecgJob = viewModelScope.launch {
            try {
                val availableSettings = api.requestStreamSettings(
                    deviceId,
                    PolarBleApi.PolarDeviceDataType.ECG
                ).await()
                api.startEcgStreaming(deviceId, availableSettings.maxSettings())
                    .asFlow()
                    .collect { ecgData: PolarEcgData ->
                        if (_isDemoMode.value != true) {
                            for (sample in ecgData.samples) {
                                if (sample is EcgSample) {
                                    val voltage = sample.voltage
                                    _ecgValue.postValue("$voltage µV")
                                    for (listener in ecgListeners) {
                                        listener.onEcgSample(voltage)
                                    }
                                }
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "ECG streaming error: ${e.message}")
            }
        }
    }

    private fun startAccStreaming(deviceId: String) {
        accJob?.cancel()
        accJob = viewModelScope.launch {
            try {
                val availableSettings = api.requestStreamSettings(
                    deviceId,
                    PolarBleApi.PolarDeviceDataType.ACC
                ).await()
                api.startAccStreaming(deviceId, availableSettings.maxSettings())
                    .asFlow()
                    .collect { accData: PolarAccelerometerData ->
                        if (_isDemoMode.value != true) {
                            for (sample in accData.samples) {
                                val x = sample.x
                                val y = sample.y
                                val z = sample.z
                                val mag = Math.sqrt((x * x + y * y + z * z).toDouble())
                                for (listener in accListeners) {
                                    listener.onAccSample(x, y, z, mag)
                                }
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "ACC streaming error: ${e.message}")
            }
        }
    }

    private fun stopStreaming() {
        ecgJob?.cancel()
        ecgJob = null
        accJob?.cancel()
        accJob = null
    }

    // ─── Public commands ──────────────────────────────────────────────────────

    fun setDemoMode(enabled: Boolean) {
        _isDemoMode.postValue(enabled)
        if (enabled) {
            demoGenerator.start()
        } else {
            demoGenerator.stop()
            if (_status.value != "Connected") {
                _heartRate.postValue("—")
                _ecgValue.postValue("—")
            }
        }
    }

    fun toggleDemoMode() {
        val current = _isDemoMode.value == true
        setDemoMode(!current)
    }

    /**
     * Starts a BLE scan and auto-connects to the first Polar H10 found.
     */
    fun searchAndConnect() {
        if (_status.value == "Connected" || _status.value == "Connecting...") return

        _status.postValue("Scanning...")
        _isScanning.postValue(true)
        scanJob?.cancel()

        scanJob = viewModelScope.launch {
            try {
                api.searchForDevice().asFlow().collect { deviceInfo ->
                    val name = deviceInfo.name.uppercase()
                    if (name.contains("H10") || name.contains("POLAR H")) {
                        Log.d(TAG, "Found device: ${deviceInfo.deviceId} (${deviceInfo.name})")
                        scanJob?.cancel()
                        _isScanning.postValue(false)
                        connectToDevice(deviceInfo.deviceId)
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e(TAG, "Scan error: ${e.message}")
                    _status.postValue("Scan failed")
                    _isScanning.postValue(false)
                }
            }
        }
    }

    /**
     * Connects directly to a device with a known ID (e.g. "0A3CBC22").
     */
    fun connectToDevice(deviceId: String) {
        connectedDeviceId = deviceId
        _deviceId.postValue(deviceId)
        _status.postValue("Connecting...")
        try {
            api.connectToDevice(deviceId)
        } catch (e: Exception) {
            Log.e(TAG, "Connect error for '$deviceId': ${e.message}")
            _status.postValue("Invalid ID")
        }
    }

    /** Stops scan / ECG streaming and cleanly disconnects from Polar sensor. */
    fun disconnect() {
        scanJob?.cancel()
        scanJob = null
        _isScanning.postValue(false)
        stopStreaming()

        // Robustly determine ID to disconnect from
        val targetId = connectedDeviceId
            ?: _deviceId.value?.takeIf { it != "—" && it.isNotBlank() }

        if (targetId != null) {
            try {
                api.disconnectFromDevice(targetId)
            } catch (e: Exception) {
                Log.e(TAG, "Disconnect error for '$targetId': ${e.message}")
            }
        }

        connectedDeviceId = null
        _status.postValue("Disconnected")
        _heartRate.postValue("—")
        _ecgValue.postValue("—")
        _batteryLevel.postValue("—")
    }

    // ─── Chart Listener Registration ──────────────────────────────────────────

    fun addEcgListener(listener: EcgListener) {
        ecgListeners.add(listener)
    }

    fun removeEcgListener(listener: EcgListener) {
        ecgListeners.remove(listener)
    }

    fun addAccListener(listener: AccListener) {
        accListeners.add(listener)
    }

    fun removeAccListener(listener: AccListener) {
        accListeners.remove(listener)
    }

    fun addHrListener(listener: HrListener) {
        hrListeners.add(listener)
    }

    fun removeHrListener(listener: HrListener) {
        hrListeners.remove(listener)
    }

    fun addRrListener(listener: RrListener) {
        rrListeners.add(listener)
    }

    fun removeRrListener(listener: RrListener) {
        rrListeners.remove(listener)
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        demoGenerator.stop()
        stopStreaming()
        scanJob?.cancel()
        api.shutDown()
    }
}
