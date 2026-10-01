package org.circadiaware.open_polar_h10_ecg_logger.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.circadiaware.open_polar_h10_ecg_logger.PolarViewModel
import org.circadiaware.open_polar_h10_ecg_logger.R

/**
 * Fragment for the "Status" tab.
 *
 * Observes [PolarViewModel] (scoped to the Activity so it survives tab switches)
 * and updates the Connected Devices card in real time.
 * "Scan & Connect" triggers an auto-scan for the nearest Polar H10;
 * "Disconnect" stops streaming and closes the BLE link.
 */
class StatusFragment : Fragment() {

    private lateinit var viewModel: PolarViewModel

    // Views – bound in onViewCreated
    private lateinit var tvDeviceId: TextView
    private lateinit var tvBattery: TextView
    private lateinit var tvHr: TextView
    private lateinit var tvEcg: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_status, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Share the ViewModel with MainActivity so the API lifecycle is managed there
        viewModel = ViewModelProvider(requireActivity())[PolarViewModel::class.java]

        bindViews(view)
        observeViewModel()
        setupButtons()
    }

    private fun bindViews(view: View) {
        tvDeviceId = view.findViewById(R.id.device_id_value)
        tvBattery  = view.findViewById(R.id.device_battery_value)
        tvHr       = view.findViewById(R.id.device_hr_value)
        tvEcg      = view.findViewById(R.id.device_ecg_value)
        tvStatus   = view.findViewById(R.id.device_status_value)
        btnStart   = view.findViewById(R.id.btn_start)
        btnStop    = view.findViewById(R.id.btn_stop)
    }

    private fun observeViewModel() {
        viewModel.deviceId.observe(viewLifecycleOwner)     { tvDeviceId.text = it }
        viewModel.batteryLevel.observe(viewLifecycleOwner) { tvBattery.text  = it }
        viewModel.heartRate.observe(viewLifecycleOwner)    { tvHr.text       = it }
        viewModel.ecgValue.observe(viewLifecycleOwner)     { tvEcg.text      = it }
        viewModel.status.observe(viewLifecycleOwner) {
            tvStatus.text = it
            updateButtonStates()
        }
        viewModel.isScanning.observe(viewLifecycleOwner) {
            updateButtonStates()
        }
    }

    private fun updateButtonStates() {
        val status = viewModel.status.value ?: "Disconnected"
        val scanning = viewModel.isScanning.value ?: false

        val connected = status == "Connected"
        val connecting = status.startsWith("Connecting")
        val busy = scanning || status.startsWith("Scanning")

        btnStop.isEnabled  = connected || connecting || busy
        btnStart.isEnabled = !connected && !connecting && !busy
    }

    private fun setupButtons() {
        btnStart.setOnClickListener { viewModel.searchAndConnect() }
        btnStop.setOnClickListener  { viewModel.disconnect() }
    }

    companion object {
        @JvmStatic
        fun newInstance() = StatusFragment()
    }
}
