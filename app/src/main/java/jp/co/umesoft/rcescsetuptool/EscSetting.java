package jp.co.umesoft.rcescsetuptool;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;

import jp.team.e_works.inifilelib.IniFileLoader;

public class EscSetting {

    public static final int MIN_BRK_FQ = 0;
    public static final int MAX_BRK_FQ = 0x3f;

    public static final int MIN_RESPONSE = 0;
    public static final int MAX_RESPONSE = 9;

    public static final int MIN_POWER_SAVE = 0;
    public static final int MAX_POWER_SAVE = 7;

    public static final int MIN_CURRENT_LIMITTER = 0;
    public static final int MAX_CURRENT_LIMITTER = 7;

    public static final int MIN_MODE = 0;
    public static final int MAX_MODE = 2;

    public int mode = 0;
    public int brkFq = 0;
    public int cutVt = 0;
    public int response = 0;
    public int curLimit = 0;
    public int [] freq = { 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35 };

    public EscSetting()
    {
    }

    public static EscSetting load(String filepath)
    {
        IniFileLoader loader = new IniFileLoader();
        if (!loader.load(filepath))
        {
            return null;
        }

        EscSetting setting = new EscSetting();

        try {
            setting.mode = Integer.parseInt(loader.getValue("DATA0", "MODE"));
            setting.brkFq= Integer.parseInt(loader.getValue("DATA0", "BRK.FQ"));
            setting.cutVt= Integer.parseInt(loader.getValue("DATA0", "CUT.VT"));
            setting.response= Integer.parseInt(loader.getValue("DATA0", "RESPONSE"));
            setting.curLimit= Integer.parseInt(loader.getValue("DATA0", "CUR.LIM"));

            for(int i = 0; i < setting.freq.length; i++)
            {
                setting.freq[i] = Integer.parseInt(loader.getValue("DATA0", "FREQ" + i));
            }
        }
        catch(Exception e)
        {
            return null;
        }

        return setting;
    }

    public boolean save(File file)
    {
        FileOutputStream fo = null;
        OutputStreamWriter osw = null;
        BufferedWriter bw = null;

        try {
            fo = new FileOutputStream(file);
            osw = new OutputStreamWriter(fo,"SHIFT_JIS");
            bw = new BufferedWriter(osw);

            bw.write("[VFS-1]\n");
            bw.write("DATA=4\n");
            bw.write("[DATA0]\n");
            bw.write("NAME=DATA1\n");
            bw.write("VER=1\n");
            bw.write("MODE=" + mode + "\n");
            bw.write("BRK.FQ=" + brkFq + "\n");
            bw.write("CUT.VT=" + cutVt + "\n");
            bw.write("RESPONSE=" + response + "\n");
            bw.write("CUR.LIM=" + curLimit + "\n");

            for(int i = 0; i < freq.length; i++)
            {
                bw.write("FREQ" + i + "=" + freq[i] + "\n");
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return false;
        }
        finally
        {
            if (bw != null)
            {
                try
                {
                    bw.close();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    return false;
                }
            }
        }
        return true;
    }

    public static String getBrkFqInfo(int value)
    {
        final double[] brkFqKHzTbl = {
            0.46, 0.47, 0.49, 0.51, 0.53, 0.55, 0.57, 0.59, 0.62, 0.65, 0.68, 0.71, 0.75, 0.79, 0.84, 0.87, 0.90, 0.93, 0.96, 0.99, 1.03, 1.07, 1.11, 1.15, 1.20, 1.25, 1.31, 1.37, 1.44, 1.51, 1.60, 1.68,
            1.72, 1.77, 1.83, 1.89, 1.95, 2.03, 2.09, 2.17, 2.26, 2.35, 2.45, 2.56, 2.68, 2.81, 2.92, 3.07, 3.18, 3.27, 3.37, 3.47, 3.53, 3.65, 3.77, 3.90, 4.03, 4.18, 4.34, 4.46, 4.62, 4.81, 5.02, 5.26
        };
        return String.format("%d: %.2fkHz", value + 1, brkFqKHzTbl[value]);
    }

    public static String getModeInfo(int value)
    {
        switch(value)
        {
            case 0: return "BRK / BAK";
            case 1: return "BRAKE";
            case 2: return "BACK";
        }
        return "";
    }

    public static String getResponseInfo(int value)
    {
        if (value == 0)
        {
            return "OFF";
        }
        else
        {
            return String.format("%d", value);
        }
    }

    public static String getPowerSaveInfo(int value)
    {
        final double[] powerSaveTbl = {
                2.5, 3.0, 3.5, 4.0, 4.5, 5.0, 5.5, 6.0
        };
        return String.format("%.1fV", powerSaveTbl[value]);
    }

    public static String getCurrentLimiterInfo(int value)
    {
        if (value == 7)
        {
            return "OFF";
        }
        else
        {
            final int[] currentLimitterTbl = {
                    60, 90, 120, 150,  180, 210, 240
            };
            return String.format("%dA", currentLimitterTbl[value]);
        }
    }

}
