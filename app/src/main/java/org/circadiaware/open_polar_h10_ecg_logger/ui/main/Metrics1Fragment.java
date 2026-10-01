package org.circadiaware.open_polar_h10_ecg_logger.ui.main;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.circadiaware.open_polar_h10_ecg_logger.PolarViewModel;
import org.circadiaware.open_polar_h10_ecg_logger.R;

import java.util.Locale;

/**
 * Fragment displaying cardiovascular real-time charts:
 * ECG waveform (µV), Heart Rate (BPM), and RR Intervals (ms).
 */
public class Metrics1Fragment extends Fragment {

    private PolarViewModel viewModel;

    private TextView textModeBadge;
    private Button btnToggleDemo;
    private TextView textEcgValue;
    private TextView textHrValue;
    private TextView textRrValue;

    private RealtimeLineChartView chartEcg;
    private RealtimeLineChartView chartHr;
    private RealtimeLineChartView chartRr;

    private final PolarViewModel.EcgListener ecgListener = voltageUv -> {
        if (chartEcg != null) {
            chartEcg.addPoint(voltageUv);
        }
        if (textEcgValue != null) {
            textEcgValue.setText(String.format(Locale.US, "%d µV", voltageUv));
        }
    };

    private final PolarViewModel.HrListener hrListener = hrBpm -> {
        if (chartHr != null) {
            chartHr.addPoint(hrBpm);
        }
        if (textHrValue != null) {
            textHrValue.setText(String.format(Locale.US, "%d BPM", hrBpm));
        }
    };

    private final PolarViewModel.RrListener rrListener = rrMs -> {
        if (chartRr != null) {
            chartRr.addPoint(rrMs);
        }
        if (textRrValue != null) {
            textRrValue.setText(String.format(Locale.US, "%d ms", rrMs));
        }
    };

    /**
     * Factory method creating a new Metrics1Fragment instance.
     */
    public static Metrics1Fragment newInstance() {
        return new Metrics1Fragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_metrics1, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(PolarViewModel.class);

        bindViews(view);
        setupCharts();
        observeViewModel();
    }

    private void bindViews(@NonNull View view) {
        textModeBadge = view.findViewById(R.id.textModeBadge);
        btnToggleDemo = view.findViewById(R.id.btnToggleDemo);
        textEcgValue = view.findViewById(R.id.textEcgValue);
        textHrValue = view.findViewById(R.id.textHrValue);
        textRrValue = view.findViewById(R.id.textRrValue);

        chartEcg = view.findViewById(R.id.chartEcg);
        chartHr = view.findViewById(R.id.chartHr);
        chartRr = view.findViewById(R.id.chartRr);

        btnToggleDemo.setOnClickListener(v -> viewModel.toggleDemoMode());
    }

    private void setupCharts() {
        // ECG Chart: High capacity for 130 Hz stream, emerald trace, fixed typical ECG range ±1500 µV
        int colorEcg = ContextCompat.getColor(requireContext(), R.color.chart_ecg);
        chartEcg.setSeriesConfiguration(1, new int[]{colorEcg}, new String[]{"ECG"});
        chartEcg.setBufferCapacity(400);
        chartEcg.setFixedRange(-1500f, 1500f);
        chartEcg.setUnitLabel("µV");

        // HR Chart: Warm coral line with soft area fill, range 40..180 BPM
        int colorHr = ContextCompat.getColor(requireContext(), R.color.chart_hr);
        chartHr.setSeriesConfiguration(1, new int[]{colorHr}, new String[]{"HR"});
        chartHr.setBufferCapacity(90);
        chartHr.setFixedRange(40f, 180f);
        chartHr.setShowGradientFill(true);
        chartHr.setUnitLabel("BPM");

        // RR Intervals Chart: Purple line with soft area fill, range 400..1400 ms
        int colorRr = ContextCompat.getColor(requireContext(), R.color.chart_rr);
        chartRr.setSeriesConfiguration(1, new int[]{colorRr}, new String[]{"RR"});
        chartRr.setBufferCapacity(90);
        chartRr.setFixedRange(400f, 1400f);
        chartRr.setShowGradientFill(true);
        chartRr.setUnitLabel("ms");
    }

    private void observeViewModel() {
        viewModel.isDemoMode().observe(getViewLifecycleOwner(), isDemo -> updateModeBadge());
        viewModel.getStatus().observe(getViewLifecycleOwner(), status -> updateModeBadge());
    }

    private void updateModeBadge() {
        boolean isDemo = Boolean.TRUE.equals(viewModel.isDemoMode().getValue());
        String status = viewModel.getStatus().getValue();

        if (isDemo) {
            textModeBadge.setText(R.string.mode_demo_badge);
            textModeBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.badge_demo_text));
            textModeBadge.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.badge_demo_bg));
        } else if ("Connected".equals(status)) {
            textModeBadge.setText(R.string.mode_live_badge);
            textModeBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.badge_live_text));
            textModeBadge.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.badge_live_bg));
        } else {
            textModeBadge.setText(R.string.mode_disconnected_badge);
            textModeBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.badge_gray_text));
            textModeBadge.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.badge_gray_bg));
        }

        if (!isDemo && !"Connected".equals(status)) {
            if (chartEcg != null) chartEcg.clear();
            if (chartHr != null) chartHr.clear();
            if (chartRr != null) chartRr.clear();
            if (textEcgValue != null) textEcgValue.setText(R.string.placeholder_dash);
            if (textHrValue != null) textHrValue.setText(R.string.placeholder_dash);
            if (textRrValue != null) textRrValue.setText(R.string.placeholder_dash);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        viewModel.addEcgListener(ecgListener);
        viewModel.addHrListener(hrListener);
        viewModel.addRrListener(rrListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        viewModel.removeEcgListener(ecgListener);
        viewModel.removeHrListener(hrListener);
        viewModel.removeRrListener(rrListener);
    }
}
