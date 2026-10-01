package org.circadiaware.open_polar_h10_ecg_logger.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Generates physiologically realistic simulated ECG, heart rate, RR intervals,
 * and 3-axis accelerometer data for demonstration and testing without a connected physical sensor.
 */
public class DemoDataGenerator {

    /**
     * Listener callback interface for generated biomedical data samples.
     */
    public interface DemoListener {
        /**
         * Invoked with simulated ECG voltage in microvolts (µV).
         */
        void onEcgSample(int voltageUv);

        /**
         * Invoked with simulated 3-axis accelerometer readings in milli-G.
         */
        void onAccSample(int x, int y, int z, double magnitude);

        /**
         * Invoked with simulated heart rate in beats per minute.
         */
        void onHrSample(int hrBpm);

        /**
         * Invoked with simulated RR interval duration in milliseconds.
         */
        void onRrSample(int rrMs);
    }

    private final DemoListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ScheduledExecutorService executor;

    // ECG wave parameters
    private long sampleIndex = 0;
    private double respirationPhase = 0.0;
    private long lastHrTickTime = 0;

    /**
     * Constructs a demo generator with the specified target listener.
     *
     * @param listener callback receiver
     */
    public DemoDataGenerator(DemoListener listener) {
        this.listener = listener;
    }

    /**
     * Starts the periodic simulation background thread.
     */
    public synchronized void start() {
        if (executor != null && !executor.isShutdown()) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor();
        sampleIndex = 0;
        lastHrTickTime = System.currentTimeMillis();

        // Tick every 25ms: produces ~3-4 ECG samples per tick to emulate 130 Hz
        executor.scheduleAtFixedRate(this::tick, 0, 25, TimeUnit.MILLISECONDS);
    }

    /**
     * Stops the simulation generator.
     */
    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();

        // Natural sinus arrhythmia: heart rate varies between 70 and 76 BPM
        respirationPhase += 0.02;
        double currentHr = 72.0 + 4.0 * Math.sin(respirationPhase);
        int samplesPerBeat = (int) ((130.0 * 60.0) / currentHr);
        if (samplesPerBeat <= 0) {
            samplesPerBeat = 108;
        }

        // Generate ~3.25 ECG samples per 25ms tick (130 Hz)
        for (int i = 0; i < 3; i++) {
            sampleIndex++;
            double phase = (sampleIndex % samplesPerBeat) / (double) samplesPerBeat;
            int ecgUv = calculateSyntheticEcg(phase);

            final int finalEcg = ecgUv;
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onEcgSample(finalEcg);
                }
            });
        }

        // Accelerometer update (Y-axis points down ~ -980 mG with chest breathing motion)
        double motion = Math.sin(respirationPhase * 0.8);
        int accX = (int) (15 + 25 * Math.sin(respirationPhase * 0.4));
        int accY = (int) (-980 + 40 * motion);
        int accZ = (int) (120 + 20 * Math.cos(respirationPhase * 0.4));
        double mag = Math.sqrt((double) accX * accX + (double) accY * accY + (double) accZ * accZ);

        final int fx = accX;
        final int fy = accY;
        final int fz = accZ;
        final double fmag = mag;
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onAccSample(fx, fy, fz, fmag);
            }
        });

        // 1 Hz Heart rate and RR interval updates
        if (now - lastHrTickTime >= 1000) {
            lastHrTickTime = now;
            final int hrInt = (int) Math.round(currentHr);
            final int rrMs = (int) Math.round(60000.0 / currentHr);
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onHrSample(hrInt);
                    listener.onRrSample(rrMs);
                }
            });
        }
    }

    /**
     * Standard mathematical model of ECG waveform using Gaussian components:
     * P-wave, Q-dip, R-spike, S-dip, and T-wave.
     */
    private int calculateSyntheticEcg(double phase) {
        // Isoelectric baseline with mild 0.1Hz baseline wander
        double voltage = 15.0 * Math.sin(respirationPhase * 0.1);

        // P-wave (at ~16% of cardiac cycle)
        voltage += gaussian(phase, 0.16, 0.035, 160.0);

        // Q-dip (at ~38% of cardiac cycle)
        voltage += gaussian(phase, 0.38, 0.012, -220.0);

        // R-peak (at ~42% of cardiac cycle - sharp ventricular spike)
        voltage += gaussian(phase, 0.42, 0.015, 1450.0);

        // S-dip (at ~46% of cardiac cycle)
        voltage += gaussian(phase, 0.46, 0.016, -380.0);

        // T-wave (at ~65% of cardiac cycle - ventricular repolarization)
        voltage += gaussian(phase, 0.65, 0.065, 320.0);

        // Add subtle physiological sensor micro-noise (±15 µV)
        double noise = (Math.random() - 0.5) * 30.0;
        return (int) Math.round(voltage + noise);
    }

    private double gaussian(double x, double center, double width, double amplitude) {
        double diff = x - center;
        return amplitude * Math.exp(-(diff * diff) / (2.0 * width * width));
    }
}
