package org.circadiaware.open_polar_h10_ecg_logger.ui.main;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Locale;

/**
 * High-performance real-time line chart view for ECG, heart rate, and accelerometer waveforms.
 * Uses pre-allocated circular buffers and avoids heap allocations in onDraw() to achieve 60+ FPS.
 */
public class RealtimeLineChartView extends View {

    private static final int DEFAULT_CAPACITY = 300;
    private static final int MAX_SERIES = 3;

    private int seriesCount = 1;
    private int bufferCapacity = DEFAULT_CAPACITY;

    // Circular ring buffers for each series
    private float[][] seriesBuffers;
    private int[] seriesHeads;
    private int[] seriesCounts;
    private int[] seriesColors;
    private String[] seriesLabels;

    // Scaling configuration
    private boolean autoScale = true;
    private float fixedMinY = -1500f;
    private float fixedMaxY = 1500f;
    private float currentMinY = 0f;
    private float currentMaxY = 100f;
    private String unitLabel = "";
    private boolean showGradientFill = false;

    // Reusable drawing objects (pre-allocated to avoid GC churn during animation)
    private Paint backgroundPaint;
    private Paint gridPaint;
    private Paint baselinePaint;
    private Paint textPaint;
    private Paint legendPaint;
    private Paint[] linePaints;
    private Paint fillPaint;
    private Path[] linePaths;
    private Path fillPath;
    private Path clipPath;
    private RectF boundsRect;

    private float density;

    /**
     * Standard constructor for programmatic creation.
     */
    public RealtimeLineChartView(Context context) {
        super(context);
        init();
    }

    /**
     * Standard constructor for XML inflation.
     */
    public RealtimeLineChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * Standard constructor for XML inflation with defStyleAttr.
     */
    public RealtimeLineChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;

        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(Color.parseColor("#111622"));

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(Color.parseColor("#1F2839"));
        gridPaint.setStrokeWidth(1f * density);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setPathEffect(new DashPathEffect(new float[]{4f * density, 4f * density}, 0));

        baselinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        baselinePaint.setColor(Color.parseColor("#2E3A52"));
        baselinePaint.setStrokeWidth(1.2f * density);
        baselinePaint.setStyle(Paint.Style.STROKE);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#7E8B9F"));
        textPaint.setTextSize(10f * density);

        legendPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        legendPaint.setTextSize(11f * density);

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        linePaints = new Paint[MAX_SERIES];
        linePaths = new Path[MAX_SERIES];
        for (int i = 0; i < MAX_SERIES; i++) {
            linePaints[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
            linePaints[i].setStyle(Paint.Style.STROKE);
            linePaints[i].setStrokeWidth(2f * density);
            linePaints[i].setStrokeCap(Paint.Cap.ROUND);
            linePaints[i].setStrokeJoin(Paint.Join.ROUND);
            linePaths[i] = new Path();
        }

        fillPath = new Path();
        clipPath = new Path();
        boundsRect = new RectF();

        setSeriesConfiguration(1, new int[]{Color.parseColor("#00E676")}, new String[]{""});
    }

    /**
     * Configures the number of data series, their line colors, and optional labels.
     *
     * @param count  number of series (1 to 3)
     * @param colors array of ARGB color ints
     * @param labels array of labels for legend
     */
    public synchronized void setSeriesConfiguration(int count, int[] colors, String[] labels) {
        this.seriesCount = Math.max(1, Math.min(count, MAX_SERIES));
        this.seriesColors = Arrays.copyOf(colors, seriesCount);
        this.seriesLabels = labels != null ? Arrays.copyOf(labels, seriesCount) : new String[seriesCount];

        this.seriesBuffers = new float[seriesCount][bufferCapacity];
        this.seriesHeads = new int[seriesCount];
        this.seriesCounts = new int[seriesCount];

        for (int i = 0; i < seriesCount; i++) {
            linePaints[i].setColor(seriesColors[i]);
        }
        postInvalidateOnAnimation();
    }

    /**
     * Sets the maximum number of data points visible on screen at once.
     *
     * @param capacity maximum points in buffer
     */
    public synchronized void setBufferCapacity(int capacity) {
        if (capacity <= 10) {
            capacity = 10;
        }
        this.bufferCapacity = capacity;
        this.seriesBuffers = new float[seriesCount][bufferCapacity];
        this.seriesHeads = new int[seriesCount];
        this.seriesCounts = new int[seriesCount];
        postInvalidateOnAnimation();
    }

    /**
     * Sets fixed vertical bounds for the Y axis.
     *
     * @param minY minimum Y value
     * @param maxY maximum Y value
     */
    public synchronized void setFixedRange(float minY, float maxY) {
        this.autoScale = false;
        this.fixedMinY = minY;
        this.fixedMaxY = maxY;
        this.currentMinY = minY;
        this.currentMaxY = maxY;
        postInvalidateOnAnimation();
    }

    /**
     * Enables automatic dynamic Y-axis scaling based on visible data points.
     *
     * @param enabled true to enable autoscale
     */
    public synchronized void setAutoScale(boolean enabled) {
        this.autoScale = enabled;
        postInvalidateOnAnimation();
    }

    /**
     * Sets the unit label displayed next to axis labels (e.g. "µV", "BPM").
     *
     * @param unit string unit label
     */
    public void setUnitLabel(String unit) {
        this.unitLabel = unit != null ? unit : "";
        postInvalidateOnAnimation();
    }

    /**
     * Enables or disables soft gradient area fill under the primary curve.
     *
     * @param show true to show gradient area fill
     */
    public void setShowGradientFill(boolean show) {
        this.showGradientFill = show;
        postInvalidateOnAnimation();
    }

    /**
     * Appends a new data sample to the primary (index 0) series.
     *
     * @param value data value
     */
    public synchronized void addPoint(float value) {
        addPoint(0, value);
    }

    /**
     * Appends a new data sample to the specified series index.
     *
     * @param seriesIndex index of the series (0..seriesCount-1)
     * @param value       data value
     */
    public synchronized void addPoint(int seriesIndex, float value) {
        if (seriesIndex < 0 || seriesIndex >= seriesCount) {
            return;
        }
        int head = seriesHeads[seriesIndex];
        seriesBuffers[seriesIndex][head] = value;
        seriesHeads[seriesIndex] = (head + 1) % bufferCapacity;
        if (seriesCounts[seriesIndex] < bufferCapacity) {
            seriesCounts[seriesIndex]++;
        }
        postInvalidateOnAnimation();
    }

    /**
     * Clears all recorded samples from all series buffers.
     */
    public synchronized void clear() {
        for (int i = 0; i < seriesCount; i++) {
            seriesCounts[i] = 0;
            seriesHeads[i] = 0;
        }
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        // Draw rounded dark background
        float cornerRadius = 6f * density;
        boundsRect.set(0, 0, width, height);
        clipPath.reset();
        clipPath.addRoundRect(boundsRect, cornerRadius, cornerRadius, Path.Direction.CW);
        canvas.drawRoundRect(boundsRect, cornerRadius, cornerRadius, backgroundPaint);

        float paddingLeft = 10f * density;
        float paddingRight = 44f * density; // space for Y-axis labels on right
        float paddingTop = 12f * density;
        float paddingBottom = 12f * density;

        float plotWidth = width - paddingLeft - paddingRight;
        float plotHeight = height - paddingTop - paddingBottom;
        if (plotWidth <= 0 || plotHeight <= 0) {
            return;
        }

        synchronized (this) {
            // Determine vertical bounds
            if (autoScale) {
                float min = Float.MAX_VALUE;
                float max = -Float.MAX_VALUE;
                boolean hasData = false;
                for (int s = 0; s < seriesCount; s++) {
                    int count = seriesCounts[s];
                    if (count > 0) {
                        hasData = true;
                        int head = seriesHeads[s];
                        for (int i = 0; i < count; i++) {
                            int idx = (head - count + i + bufferCapacity) % bufferCapacity;
                            float val = seriesBuffers[s][idx];
                            if (val < min) min = val;
                            if (val > max) max = val;
                        }
                    }
                }
                if (hasData) {
                    float margin = Math.max((max - min) * 0.15f, 10f);
                    currentMinY = min - margin;
                    currentMaxY = max + margin;
                } else {
                    currentMinY = fixedMinY;
                    currentMaxY = fixedMaxY;
                }
            } else {
                currentMinY = fixedMinY;
                currentMaxY = fixedMaxY;
            }

            float yRange = currentMaxY - currentMinY;
            if (yRange <= 0.0001f) {
                yRange = 1f;
            }

            // Draw horizontal grid lines & labels (3 lines: top, middle, bottom)
            for (int i = 0; i <= 2; i++) {
                float fraction = i / 2f;
                float y = paddingTop + fraction * plotHeight;
                canvas.drawLine(paddingLeft, y, paddingLeft + plotWidth, y, gridPaint);

                float labelValue = currentMaxY - fraction * (currentMaxY - currentMinY);
                String labelStr = String.format(Locale.US, "%.0f %s", labelValue, unitLabel).trim();
                canvas.drawText(labelStr, paddingLeft + plotWidth + 4f * density, y + 3.5f * density, textPaint);
            }

            // Draw zero baseline if visible in range
            if (currentMinY < 0 && currentMaxY > 0) {
                float zeroFraction = (currentMaxY - 0f) / yRange;
                float zeroY = paddingTop + zeroFraction * plotHeight;
                canvas.drawLine(paddingLeft, zeroY, paddingLeft + plotWidth, zeroY, baselinePaint);
            }

            // Draw vertical grid lines (4 intervals)
            for (int i = 1; i <= 3; i++) {
                float x = paddingLeft + (i / 4f) * plotWidth;
                canvas.drawLine(x, paddingTop, x, paddingTop + plotHeight, gridPaint);
            }

            // Check if any series has data
            boolean anySeriesHasData = false;
            for (int s = 0; s < seriesCount; s++) {
                if (seriesCounts[s] > 1) {
                    anySeriesHasData = true;
                    break;
                }
            }

            if (!anySeriesHasData) {
                String waitText = "Waiting for signal…";
                float textW = textPaint.measureText(waitText);
                canvas.drawText(waitText, (width - textW) / 2f, height / 2f + 4f * density, textPaint);
                return;
            }

            // Clip plotting area to prevent overflow outside bounds
            canvas.save();
            canvas.clipRect(paddingLeft, paddingTop, paddingLeft + plotWidth, paddingTop + plotHeight);

            // Draw series curves
            for (int s = 0; s < seriesCount; s++) {
                int count = seriesCounts[s];
                if (count <= 1) {
                    continue;
                }
                int head = seriesHeads[s];
                float xStep = plotWidth / (float) (bufferCapacity - 1);

                // Align the newest point to the right edge
                float startX = paddingLeft + (bufferCapacity - count) * xStep;

                Path path = linePaths[s];
                path.reset();

                float firstX = 0f;
                float firstY = 0f;

                for (int i = 0; i < count; i++) {
                    int idx = (head - count + i + bufferCapacity) % bufferCapacity;
                    float val = seriesBuffers[s][idx];
                    float x = startX + i * xStep;
                    float normalizedY = (currentMaxY - val) / yRange;
                    float y = paddingTop + Math.max(0f, Math.min(1f, normalizedY)) * plotHeight;

                    if (i == 0) {
                        path.moveTo(x, y);
                        firstX = x;
                        firstY = y;
                    } else {
                        path.lineTo(x, y);
                    }
                }

                // Optional gradient area fill for single series
                if (showGradientFill && seriesCount == 1) {
                    fillPath.set(path);
                    float lastX = startX + (count - 1) * xStep;
                    fillPath.lineTo(lastX, paddingTop + plotHeight);
                    fillPath.lineTo(firstX, paddingTop + plotHeight);
                    fillPath.close();

                    int baseColor = seriesColors[s];
                    int r = Color.red(baseColor);
                    int g = Color.green(baseColor);
                    int b = Color.blue(baseColor);
                    int startColor = Color.argb(60, r, g, b);
                    int endColor = Color.argb(0, r, g, b);
                    fillPaint.setShader(new LinearGradient(
                            0, paddingTop, 0, paddingTop + plotHeight,
                            startColor, endColor, Shader.TileMode.CLAMP
                    ));
                    canvas.drawPath(fillPath, fillPaint);
                }

                canvas.drawPath(path, linePaints[s]);
            }

            canvas.restore();

            // Draw multi-series legend if labels are provided
            if (seriesCount > 1) {
                float legendX = paddingLeft + 6f * density;
                float legendY = paddingTop + 14f * density;
                for (int s = 0; s < seriesCount; s++) {
                    legendPaint.setColor(seriesColors[s]);
                    String text = (seriesLabels[s] != null && !seriesLabels[s].isEmpty())
                            ? seriesLabels[s]
                            : "Series " + (s + 1);
                    canvas.drawCircle(legendX + 4f * density, legendY - 3.5f * density, 3.5f * density, legendPaint);
                    canvas.drawText(text, legendX + 11f * density, legendY, legendPaint);
                    legendX += legendPaint.measureText(text) + 20f * density;
                }
            }
        }
    }
}
