package jp.co.umesoft.rcescsetuptool;

public class EscSettingParser {

    private byte[] recvBuffer = new byte[80];
    private int recvSize = 0;

    private int recvType = 0;
    private int recvLen = -1;

    EscSetting setting = null;

    public EscSettingParser()
    {
    }

    public void parse(int type)
    {
        recvType = type;
        recvSize = 0;
        setting = null;

        switch(recvType)
        {
            case 0:     recvLen = 44;   break;
            case 1:		recvLen = 45;	break;
            default:    recvLen = -1;   break;
        }
    }

    public int addData(byte[] data)
    {
        if (recvLen == -1)
        {
            return 0;
        }

        if (recvSize + data.length > recvBuffer.length)
        {
            recvLen = -1;
            return -1;
        }

        System.arraycopy(data, 0, recvBuffer, recvSize, data.length);
        recvSize += data.length;
        if (recvSize == recvLen)
        {
            recvLen = -1;

            byte sum = calcChecksum(recvBuffer, 1, recvSize - 1);
            if (sum != recvBuffer[recvSize - 1])
            {
                return -1;
            }

            if (recvType == 0)
            {
                if (!parseEscSetting())
                {
                    return -1;
                }
            }
            else if(recvType == 1)
            {
                if (recvBuffer[recvLen - 1] != 0x06)
                {
                    return -1;
                }
            }

            return 1;
        }

        return 0;
    }

    static final int[] modeTbl = {
            0,      // BRK/BAK
            2,      // BRAKE
            4       // BAK
    };

    static final int[] cutVtTable = {
            0x46,   // 2.5V
            0x50,   // 3.0V
            0x5A,   // 3.5V
            0x64,   // 4.0V
            0x6E,   // 4.5V
            0x7D,   // 5.0V
            0x8C,   // 5.5V
            0x96    // 6.0V
    };

    static final int[] responseTable = {
            0x1e,   // OFF
            0x1B,   // 1
            0x18,   // 2
            0x15,   // 3
            0x12,   // 4
            0x0F,   // 5
            0x0C,   // 6
            0x09,   // 7
            0x06,   // 8
            0x03    // 9
    };

    static final int[] cutLimitTable = {
            0x01,   // 60A
	        0x02,   // 90A
            0x03,   // 120A
            0x04,   // 150A
            0x05,   // 180A
            0x06,   // 210A
            0x07,   // 240A
            0x64,   // OFF
    };

    private boolean parseEscSetting()
    {
        setting = new EscSetting();

        int mode = DataToIndex(recvBuffer[2], modeTbl);
        if (mode == -1)
        {
            return false;
        }
        setting.mode = mode;

        int brkFq = recvBuffer[3];
        if (brkFq < EscSetting.MIN_BRK_FQ || EscSetting.MAX_BRK_FQ < brkFq)
        {
            return false;
        }
        setting.brkFq = brkFq;

        int cutVt = DataToIndex(recvBuffer[8], cutVtTable);
        if (cutVt == -1)
        {
            return false;
        }
        setting.cutVt = cutVt;

        int response = DataToIndex(recvBuffer[9], responseTable);
        if (response == -1)
        {
            return false;
        }
        setting.response = response;

        int curLimit = DataToIndex(recvBuffer[10], cutLimitTable);
        if (curLimit == -1)
        {
            return false;
        }
        setting.curLimit = curLimit;

        for(int i = 0; i < 32; i++)
        {
            int freq = recvBuffer[11 + i];
            if (freq < 0 || 0x40 < freq)
            {
                return false;
            }
            setting.freq[i] = freq;
        }

        return true;
    }

    private static int DataToIndex(byte data, int [] table)
    {
        int index = -1;
        for(int i = 0; i < table.length; i++)
        {
            if (table[i] == data)
            {
                index = i;
                break;
            }
        }
        return index;
    }

    public EscSetting getSetting()
    {
        return setting;
    }

    public static byte[] MakeReadCmd()
    {
        byte[] cmd = { (byte)0xc5 };
        return cmd;
    }

    public static byte[] MakeWriteCmd(EscSetting setting)
    {
        byte[] cmd = new byte[44];

        cmd[0] = (byte)0xd5;
        cmd[1] = (byte)0x5a;
        cmd[2] = (byte)modeTbl[setting.mode];
        cmd[3] = (byte)setting.brkFq;
        cmd[4] = (byte)0xa2;
        cmd[5] = (byte)0x67;
        cmd[6] = (byte)0x4c;
        cmd[7] = (byte)0x04;
        cmd[8] = (byte)cutVtTable[setting.cutVt];
        cmd[9] = (byte)responseTable[setting.response];
        cmd[10] = (byte)cutLimitTable[setting.curLimit];
        for(int i = 0; i < 32; i++)
        {
            cmd[11 + i] = (byte)setting.freq[i];
        }
        cmd[43] = calcChecksum(cmd, 1, 42);

        return null;
    }

    private static byte calcChecksum(byte[] data, int offset, int length)
    {
        byte sum = 0;
        for(int i = offset; i < length; i++)
        {
            sum += data[i];
        }
        return sum;
    }
}
