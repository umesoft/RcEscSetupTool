package jp.co.umesoft.rcescsetuptool;

import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.FtdiSerialDriver;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SerialInputOutputManager.Listener  {

    private String TAG = "MainActivity";

    private UsbSerialPort port = null;
    private SerialInputOutputManager usbIoManager = null;

    private EscSettingParser parser = new EscSettingParser();
    private EscSetting setting = new EscSetting();

    private Button readButton = null;
    private Button writeButton = null;
    private FreqGraphView freqGraphView = null;
    private TextView currBrkFq = null;
    private TextView currThMode = null;
    private TextView currResponse  = null;
    private TextView currPowerSave = null;
    private TextView currCurrentLimitter = null;

    // private final Runnable runnable;
    private Handler mainLooper = null;

    private static final int WRITE_WAIT_MILLIS = 15 * 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        readButton = (Button)findViewById(R.id.readButton);
        writeButton = (Button)findViewById(R.id.writeButton);
        freqGraphView = (FreqGraphView)findViewById(R.id.freqGraphView);
        currBrkFq = (TextView)findViewById(R.id.currBrkFq);
        currThMode = (TextView)findViewById(R.id.currThMode);
        currResponse = (TextView)findViewById(R.id.currResponse);
        currPowerSave = (TextView)findViewById(R.id.currPowerSave);
        currCurrentLimitter = (TextView)findViewById(R.id.currCurrentLimitter);

        UpdateSetting();

        findViewById(R.id.loadButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadSetting();
            }
        });

        findViewById(R.id.saveButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSetting();
            }
        });

        readButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                readEscSetting();
            }
        });

        writeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            	writeEscSetting();
            }
        });

        freqGraphView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEditFreq();
            }
        });

        findViewById(R.id.breakFreq).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showBreakFreq();
            }
        });

        findViewById(R.id.thMode).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showThMode();
            }
        });

        findViewById(R.id.thResponse).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showResponse();
            }
        });

        findViewById(R.id.powerSave).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPowerSave();
            }
        });

        findViewById(R.id.currentLimitter).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCurrentLimitter();
            }
        });

        openDevice();

        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, filter);
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mUsbReceiver);
        closeDevice();
        super.onDestroy();
    }

    // BroadcastReceiver when insert/remove the device USB plug into/from a USB port
    BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                openDevice();
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                closeDevice();
            }
        }
    };

    private void openDevice()
    {
        UsbManager manager = (UsbManager)getSystemService(Context.USB_SERVICE);

        ProbeTable customTable = new ProbeTable();
        customTable.addProduct(0x165c, 6, FtdiSerialDriver.class);

        UsbSerialProber prober = new UsbSerialProber(customTable);
        List<UsbSerialDriver> availableDrivers = prober.findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            disableDevice();
            return;
        }

        UsbSerialDriver driver = availableDrivers.get(0);

        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
        if (connection == null) {
            disableDevice();
            return;
        }

        port = driver.getPorts().get(0);
        if (port == null) {
            disableDevice();
            return;
        }

        try {
            port.open(connection);
            port.setParameters(1200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
        }
        catch(IOException e)
        {
            disableDevice();
            port = null;
            return;
        }

        mainLooper = new Handler(Looper.getMainLooper());

        usbIoManager = new SerialInputOutputManager(port, this);
        usbIoManager.start();

        enableDevice();
    }

    private void closeDevice() {
        if(mainLooper != null) {
            mainLooper = null;
        }

        if(usbIoManager != null) {
            usbIoManager.setListener(null);
            usbIoManager.stop();
            usbIoManager = null;
        }

        if (port != null){
            try {
                port.close();
            }
            catch(IOException e) {
            }
            port = null;
        }
        disableDevice();
    }

    private void enableDevice()
    {
        readButton.setEnabled(true);
        writeButton.setEnabled(true);
    }

    private void disableDevice()
    {
        readButton.setEnabled(false);
        writeButton.setEnabled(false);
    }

    private void loadSetting()
    {
        if (!isExternalStorageReadable()) {
            return;
        }

        String filename = "test.kpd";

        String path = getExternalFilesDir(null).getPath() + "/" + filename;
        EscSetting newSetting = EscSetting.load(path);
        if (newSetting == null) {
            return;
        }

        setting = newSetting;
        UpdateSetting();
    }

    private void saveSetting()
    {
        if (!isExternalStorageWritable()) {
            return;
        }

        String filename = "test.kpd";

        String path = getExternalFilesDir(null).getPath();
        setting.save(new File(path, filename));
    }

    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return (Environment.MEDIA_MOUNTED.equals(state));
    }

    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        return (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state));
    }

    private void readEscSetting()
    {
        parser.parse(0);

        try {
            byte[] cmd = EscSettingParser.MakeReadCmd();
            port.write(cmd, WRITE_WAIT_MILLIS);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

	private void writeEscSetting()
	{
		parser.parse(1);
		
        try {
            byte[] cmd = EscSettingParser.MakeWriteCmd(setting);
            port.write(cmd, WRITE_WAIT_MILLIS);
        } catch (IOException e) {
            e.printStackTrace();
        }
	}

    @Override
    public void onNewData(byte[] data) {
        mainLooper.post(() -> {
            int ret = parser.addData(data);
            if (ret == 1)
            {
                setting = parser.getSetting();
                UpdateSetting();
            }
            else if(ret == -1)
            {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("RcEscSetting");
                builder.setMessage("通信エラー");
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });
    }

    @Override
    public void onRunError(Exception e) {
    }

    private void UpdateSetting()
    {
        freqGraphView.updateData(setting.freq);
        currThMode.setText(EscSetting.getModeInfo(setting.mode));
        currBrkFq.setText(EscSetting.getBrkFqInfo(setting.brkFq));
        currResponse.setText(EscSetting.getResponseInfo(setting.response));
        currPowerSave.setText(EscSetting.getPowerSaveInfo(setting.cutVt));
        currCurrentLimitter.setText(EscSetting.getCurrentLimiterInfo(setting.curLimit));
    }

    private void showEditFreq()
    {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.freq);

        FreqGraphView freqGraph = (FreqGraphView)dialog.findViewById(R.id.freqGraphView);
        freqGraph.updateData(setting.freq);

        TextView textX = (TextView)dialog.findViewById(R.id.textX);
        SeekBar seekX = (SeekBar)dialog.findViewById(R.id.seekX);
        TextView textY = (TextView)dialog.findViewById(R.id.textY);
        SeekBar seekY = (SeekBar)dialog.findViewById(R.id.seekY);

        textX.setText(String.format("THRT%d", 0 + 1));
        seekX.setMin(0);
        seekX.setMax(31);
        seekX.setProgress(0);

        seekX.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int posX = seekX.getProgress();
                textX.setText(String.format("THRT%d", posX + 1));
                seekY.setProgress(freqGraph.freq[posX]);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        dialog.findViewById(R.id.minusX).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int pos = seekX.getProgress();
                if (seekX.getMin() < pos) {
                    seekX.setProgress(pos - 1);
                }
            }
        });
        dialog.findViewById(R.id.plusX).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int pos = seekX.getProgress();
                if (pos < seekX.getMax()) {
                    seekX.setProgress(pos + 1);
                }
            }
        });

        int posY = freqGraph.freq[0];
        textY.setText(String.format("No.%d", posY + 1));
        seekY.setMin(0);
        seekY.setMax(63);
        seekY.setProgress(posY);

        seekY.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int posX = seekX.getProgress();
                int posY = seekY.getProgress();
                textY.setText(String.format("No.%d", posY + 1));
                freqGraph.freq[posX] = posY;
                freqGraph.invalidate();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        dialog.findViewById(R.id.minusY).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int pos = seekY.getProgress();
                if (seekY.getMin() < pos) {
                    seekY.setProgress(pos - 1);
                }
            }
        });
        dialog.findViewById(R.id.plusY).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int pos = seekY.getProgress();
                if (pos < seekY.getMax()) {
                    seekY.setProgress(pos + 1);
                }
            }
        });

        dialog.findViewById(R.id.updateButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                System.arraycopy(freqGraph.freq, 0, setting.freq, 0, setting.freq.length);
                UpdateSetting();
                dialog.dismiss();
            }
        });

        dialog.findViewById(R.id.cancelButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();    }

    private void showBreakFreq() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.breakfreq);

        TextView textValue = (TextView)dialog.findViewById(R.id.textValue);
        SeekBar seekValue = (SeekBar)dialog.findViewById(R.id.seekValue);

        textValue.setText(EscSetting.getBrkFqInfo(setting.brkFq));

        seekValue.setMin(EscSetting.MIN_BRK_FQ);
        seekValue.setMax(EscSetting.MAX_BRK_FQ);
        seekValue.setProgress(setting.brkFq);

        seekValue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textValue.setText(EscSetting.getBrkFqInfo(seekValue.getProgress()));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        dialog.findViewById(R.id.updateButton).setOnClickListener(new View.OnClickListener() {
              @Override
              public void onClick(View v) {
                  setting.brkFq = seekValue.getProgress();
                  UpdateSetting();
                  dialog.dismiss();
              }
        });

        dialog.findViewById(R.id.cancelButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void showThMode() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.thmode);

        TextView textValue = (TextView)dialog.findViewById(R.id.textValue);
        SeekBar seekValue = (SeekBar)dialog.findViewById(R.id.seekValue);

        textValue.setText(EscSetting.getModeInfo(setting.mode));

        seekValue.setMin(EscSetting.MIN_MODE);
        seekValue.setMax(EscSetting.MAX_MODE);
        seekValue.setProgress(setting.mode);

        seekValue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textValue.setText(EscSetting.getModeInfo(seekValue.getProgress()));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        dialog.findViewById(R.id.updateButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setting.mode = seekValue.getProgress();
                UpdateSetting();
                dialog.dismiss();
            }
        });

        dialog.findViewById(R.id.cancelButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void showResponse() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.response);

        TextView textValue = (TextView)dialog.findViewById(R.id.textValue);
        SeekBar seekValue = (SeekBar)dialog.findViewById(R.id.seekValue);

        textValue.setText(EscSetting.getResponseInfo(setting.response));

        seekValue.setMin(EscSetting.MIN_RESPONSE);
        seekValue.setMax(EscSetting.MAX_RESPONSE);
        seekValue.setProgress(setting.response);

        seekValue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textValue.setText(EscSetting.getResponseInfo(seekValue.getProgress()));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        dialog.findViewById(R.id.updateButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setting.response = seekValue.getProgress();
                UpdateSetting();
                dialog.dismiss();
            }
        });

        dialog.findViewById(R.id.cancelButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void showPowerSave() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.powersave);

        TextView textValue = (TextView)dialog.findViewById(R.id.textValue);
        SeekBar seekValue = (SeekBar)dialog.findViewById(R.id.seekValue);

        textValue.setText(EscSetting.getPowerSaveInfo(setting.cutVt));

        seekValue.setMin(EscSetting.MIN_POWER_SAVE);
        seekValue.setMax(EscSetting.MAX_POWER_SAVE);
        seekValue.setProgress(setting.cutVt);

        seekValue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textValue.setText(EscSetting.getPowerSaveInfo(seekValue.getProgress()));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        dialog.findViewById(R.id.updateButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setting.cutVt = seekValue.getProgress();
                UpdateSetting();
                dialog.dismiss();
            }
        });

        dialog.findViewById(R.id.cancelButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void showCurrentLimitter() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.powersave);

        TextView textValue = (TextView)dialog.findViewById(R.id.textValue);
        SeekBar seekValue = (SeekBar)dialog.findViewById(R.id.seekValue);

        textValue.setText(EscSetting.getCurrentLimiterInfo(setting.curLimit));

        seekValue.setMin(EscSetting.MIN_CURRENT_LIMITTER);
        seekValue.setMax(EscSetting.MAX_CURRENT_LIMITTER);
        seekValue.setProgress(setting.curLimit);

        seekValue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textValue.setText(EscSetting.getCurrentLimiterInfo(seekValue.getProgress()));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        dialog.findViewById(R.id.updateButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setting.curLimit = seekValue.getProgress();
                UpdateSetting();
                dialog.dismiss();
            }
        });

        dialog.findViewById(R.id.cancelButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }
}
