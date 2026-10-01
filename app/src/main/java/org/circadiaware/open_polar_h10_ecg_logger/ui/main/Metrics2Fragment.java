package org.circadiaware.open_polar_h10_ecg_logger.ui.main;

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
 * Fragment displaying motion and accelerometer real-time charts:
 * 3-Axis Accelerometer (X, Y, Z in mG) and Total Acceleration Magnitude (mG / G).
 */
public class Metrics2Fragment extends Fragment {

    private PolarViewModel viewModel;

    private TextView textModeBadge;
    private Button btnToggleDemo;
    private TextView textAccValue;
    private TextView textMagValue;

    private RealtimeLineChartView chartAcc;
    private RealtimeLineChartView chartMag;

    private final PolarViewModel.AccListener accListener = (x, y, z, magnitude) -> {
        if (chartAcc != null) {
            chartAcc.addPoint(0, x);
            chartAcc.addPoint(1, y);
            chartAcc.addPoint(2, z);
        }
        if (chartMag != null) {
            chartMag.addPoint(0, (float) magnitude);
        }
        if (textAccValue != null) {
            textAccValue.setText(String.format(Locale.US, "X:%d  Y:%d  Z:%d mG", x, y, z));
        }
        if (textMagValue != null) {
            textMagValue.setText(String.format(Locale.US, "%.0f mG (%.2f G)", magnitude, magnitude / 1000.0));
        }
    };

    /**
     * Factory method creating a new Metrics2Fragment instance.
     */
    public static Metrics2Fragment newInstance() {
        return new Metrics2Fragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_metrics2, container, false);
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
        textAccValue = view.findViewById(R.id.textAccValue);
        textMagValue = view.findViewById(R.id.textMagValue);

        chartAcc = view.findViewById(R.id.chartAcc);
        chartMag = view.findViewById(R.id.chartMag);

        btnToggleDemo.setOnClickListener(v -> viewModel.toggleDemoMode());
    }

    private void setupCharts() {
        // 3-Axis ACC Chart: X (Red), Y (Green), Z (Blue) in mG range ±2000 mG (±2G)
        int colorX = ContextCompat.getColor(requireContext(), R.color.chart_acc_x);
        int colorY = ContextCompat.getColor(requireContext(), R.color.chart_acc_y);
        int colorZ = ContextCompat.getColor(requireContext(), R.color.chart_acc_z);

        chartAcc.setSeriesConfiguration(
                3,
                new int[]{colorX, colorY, colorZ},
                new String[]{"X", "Y", "Z"}
        );
        chartAcc.setBufferCapacity(250);
        chartAcc.setFixedRange(-2000f, 2000f);
        chartAcc.setUnitLabel("mG");

        // Magnitude Chart: Amber curve with gradient fill, 0..2500 mG
        int colorMag = ContextCompat.getColor(requireContext(), R.color.chart_mag);
        chartMag.setSeriesConfiguration(1, new int[]{colorMag}, new String[]{"|a|"});
        chartMag.setBufferCapacity(250);
        chartMag.setFixedRange(0f, 2500f);
        chartMag.setShowGradientFill(true);
        chartMag.setUnitLabel("mG");
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
            if (chartAcc != null) chartAcc.clear();
            if (chartMag != null) chartMag.clear();
            if (textAccValue != null) textAccValue.setText(R.string.placeholder_dash);
            if (textMagValue != null) textMagValue.setText(R.string.placeholder_dash);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        viewModel.addAccListener(accListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        viewModel.removeAccListener(accListener);
    }
}
