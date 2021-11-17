package jp.co.umesoft.rcescsetuptool;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class FreqGraphView extends View {

    public int [] freq = new int[32];
    
    public FreqGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void updateData(int[] newFreq)
    {
		System.arraycopy(newFreq, 0, freq, 0, freq.length);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(0xffe0e0e0);

        int width = canvas.getWidth();
        int height = canvas.getHeight();

        int block_size_x = width / 32;
        int block_size_y = height / 64;

        int start_x = (width - block_size_x * 32) / 2;
        int start_Y = height - block_size_y * 64;

        Paint paint = new Paint();
        paint.setColor(Color.argb(255, 255, 190, 0));

        for(int i = 0; i < 32; i++) {

            int x = start_x + i * block_size_x;
            int y = start_Y + freq[i] * block_size_y;

            canvas.drawRect(x, y, x + block_size_x, y + block_size_y, paint);
        }
    }
}
