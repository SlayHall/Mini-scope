package com.example.scope2;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.os.AsyncTask;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private TextView voltageTextView;
    //private TextView frequencyTextView;
    private TextView peakToPeakTextView;
    private TextView dutyCycleTextView;
    private LineChart lineChart;
    private ArrayList<Entry> voltageEntries = new ArrayList<>();
    private Handler handler = new Handler();
    private int xIndex = 0; // Counter for x-values
    private float xAxisScale = 10f; // Initial x-axis scale
    private boolean isRunning = true; // Flag to control start/stop

    private Handler increaseScaleHandler = new Handler();
    private Handler decreaseScaleHandler = new Handler();
    private Runnable increaseScaleRunnable;
    private Runnable decreaseScaleRunnable;
    private long increaseScaleStartTime;
    private long decreaseScaleStartTime;

    private Runnable updateChartRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) {
                return;
            }

            LineDataSet dataSet = new LineDataSet(voltageEntries, "Voltage");
            dataSet.setDrawCircles(false); // Disable drawing circles
            dataSet.setDrawValues(false); // Disable drawing values
            LineData lineData = new LineData(dataSet);
            lineChart.setData(lineData);

            // Calculate the y-axis range
            float maxY = Float.MIN_VALUE;
            float minY = Float.MAX_VALUE;
            for (Entry entry : voltageEntries) {
                if (entry.getY() > maxY) {
                    maxY = entry.getY();
                }
                if (entry.getY() < minY) {
                    minY = entry.getY();
                }
            }
            float range = Math.max(Math.abs(maxY), Math.abs(minY));
            lineChart.getAxisLeft().setAxisMinimum(-range);
            lineChart.getAxisLeft().setAxisMaximum(range);
            lineChart.getAxisRight().setAxisMinimum(-range);
            lineChart.getAxisRight().setAxisMaximum(range);

            // Update peak-to-peak values
            float peakToPeak = maxY - minY;
            peakToPeakTextView.setText(String.format(Locale.US, "Peak-to-Peak: %.3f V", peakToPeak));

            // Calculate and update duty cycle
            float dutyCycle = calculateDutyCycle(voltageEntries);
            dutyCycleTextView.setText(String.format(Locale.US, "Duty Cycle: %.3f %%", dutyCycle));

            // Set x-axis range
            lineChart.getXAxis().setAxisMinimum(xIndex - xAxisScale);
            lineChart.getXAxis().setAxisMaximum(xIndex);

            lineChart.invalidate(); // Refresh the chart
            handler.postDelayed(this, 100); // Update every 100ms
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        voltageTextView = findViewById(R.id.voltageTextView);
        //frequencyTextView = findViewById(R.id.frequencyTextView);
        peakToPeakTextView = findViewById(R.id.peakToPeakTextView);
        dutyCycleTextView = findViewById(R.id.dutyCycleTextView);
        lineChart = findViewById(R.id.lineChart);
        Button increaseScaleButton = findViewById(R.id.increaseScaleButton);
        Button decreaseScaleButton = findViewById(R.id.decreaseScaleButton);
        Button autoScaleButton = findViewById(R.id.autoScaleButton);
        Button startStopButton = findViewById(R.id.startStopButton);

        // Set y-axis properties to center y=0
        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setAxisMinimum(-10f); // Set a default range
        leftAxis.setAxisMaximum(10f);
        leftAxis.setCenterAxisLabels(true);

        YAxis rightAxis = lineChart.getAxisRight();
        rightAxis.setAxisMinimum(-10f); // Set a default range
        rightAxis.setAxisMaximum(10f);
        rightAxis.setCenterAxisLabels(true);

        // Set x-axis properties
        lineChart.getXAxis().setAxisMinimum(0);
        lineChart.getXAxis().setAxisMaximum(xAxisScale);

        // Define the increase scale runnable
        increaseScaleRunnable = new Runnable() {
            @Override
            public void run() {
                long elapsedTime = System.currentTimeMillis() - increaseScaleStartTime;
                long delay = Math.max(10, 200 - (int)(elapsedTime / 10)); // Decrease delay over time
                xAxisScale += 1f; // Increase scale by 1 unit
                increaseScaleHandler.postDelayed(this, delay); // Repeat with adjusted delay
            }
        };

        // Define the decrease scale runnable
        decreaseScaleRunnable = new Runnable() {
            @Override
            public void run() {
                long elapsedTime = System.currentTimeMillis() - decreaseScaleStartTime;
                long delay = Math.max(10, 200 - (int)(elapsedTime / 10)); // Decrease delay over time
                xAxisScale = Math.max(5f, xAxisScale - 1f); // Decrease scale by 1 unit, minimum 5 units
                decreaseScaleHandler.postDelayed(this, delay); // Repeat with adjusted delay
            }
        };

        // Set button touch listeners
        increaseScaleButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        increaseScaleStartTime = System.currentTimeMillis();
                        increaseScaleHandler.post(increaseScaleRunnable);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        increaseScaleHandler.removeCallbacks(increaseScaleRunnable);
                        return true;
                }
                return false;
            }
        });

        decreaseScaleButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        decreaseScaleStartTime = System.currentTimeMillis();
                        decreaseScaleHandler.post(decreaseScaleRunnable);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        decreaseScaleHandler.removeCallbacks(decreaseScaleRunnable);
                        return true;
                }
                return false;
            }
        });

        // Set auto scale button listener
        autoScaleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                autoScale();
            }
        });

        // Set start/stop button listener
        startStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isRunning = !isRunning;
                if (isRunning) {
                    handler.post(updateChartRunnable);
                    startStopButton.setText("Stop");
                } else {
                    startStopButton.setText("Start");
                }
            }
        });

        new TcpClientTask().execute();
        handler.post(updateChartRunnable); // Start updating the chart
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateChartRunnable); // Stop updating the chart
    }

    private class TcpClientTask extends AsyncTask<Void, String, Void> {
        private boolean isConnected = false;

        @Override
        protected Void doInBackground(Void... voids) {
            while (true) {
                if (!isConnected) {
                    try (Socket socket = new Socket("192.168.1.101", 8080);
                         BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                        isConnected = true;
                        String data;
                        while ((data = in.readLine()) != null) {
                            publishProgress(data);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        isConnected = false;
                        try {
                            Thread.sleep(5000); // Wait for 5 seconds before retrying
                        } catch (InterruptedException ie) {
                            ie.printStackTrace();
                        }
                    }
                }
            }
        }

        @Override
        protected void onProgressUpdate(String... values) {
            try {
                String[] data = values[0].split(",");
                for (String voltageStr : data) {
                    float voltage = Float.parseFloat(voltageStr) - 0.06f; // Shift the voltage value by -0.06
                    if (voltageEntries.size() >= 1000) {
                        voltageEntries.remove(0);
                    }
                    voltageEntries.add(new Entry(xIndex++, voltage)); // Use xIndex for x-values
                }
                voltageTextView.setText(String.format(Locale.US, "Voltage: %.3f V", Float.parseFloat(data[data.length - 1])));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }


    private float calculateDutyCycle(ArrayList<Entry> entries) {
        if (entries.size() < 2) {
            return 0;
        }

        // Calculate midY
        float maxY = Float.MIN_VALUE;
        float minY = Float.MAX_VALUE;
        for (Entry entry : entries) {
            if (entry.getY() > maxY) {
                maxY = entry.getY();
            }
            if (entry.getY() < minY) {
                minY = entry.getY();
            }
        }
        float midY = (maxY + minY) / 2;

        float highTime = 0;
        float lowTime = 0;
        boolean isHigh = entries.get(0).getY() > midY;

        for (int i = 1; i < entries.size(); i++) {
            float y1 = entries.get(i - 1).getY();
            float y2 = entries.get(i).getY();
            float duration = 0.1f; // Assuming 100ms update interval

            if ((y1 > midY && y2 <= midY) || (y1 <= midY && y2 > midY)) {
                // Interpolate to find the exact crossing point
                float t = (midY - y1) / (y2 - y1);
                float crossingTime = t * duration;

                if (isHigh) {
                    highTime += crossingTime;
                    lowTime += (duration - crossingTime);
                } else {
                    lowTime += crossingTime;
                    highTime += (duration - crossingTime);
                }

                isHigh = !isHigh;
            } else {
                if (isHigh) {
                    highTime += duration;
                } else {
                    lowTime += duration;
                }
            }
        }

        float totalTime = highTime + lowTime;
        return (highTime / totalTime) * 100;
    }

    private void autoScale() {
        if (voltageEntries.size() < 2) {
            return;
        }

        // Calculate the period of the signal
        float period = 0;
        int peakCount = 0;
        float lastPeakX = 0;
        boolean isRising = voltageEntries.get(0).getY() < voltageEntries.get(1).getY();

        for (int i = 1; i < voltageEntries.size() - 1; i++) {
            float prevY = voltageEntries.get(i - 1).getY();
            float currY = voltageEntries.get(i).getY();
            float nextY = voltageEntries.get(i + 1).getY();

            if (isRising && currY > prevY && currY > nextY) {
                peakCount++;
                if (peakCount > 1) {
                    period += (voltageEntries.get(i).getX() - lastPeakX);
                }
                lastPeakX = voltageEntries.get(i).getX();
                isRising = false;
            } else if (!isRising && currY < prevY && currY < nextY) {
                isRising = true;
            }
        }

        if (peakCount > 1) {
            period /= (peakCount - 1);
            xAxisScale = period * 3; // Display 3 cycles
        }
    }
}
