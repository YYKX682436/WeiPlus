/* Synthetic offscreen Picture model; not production Compose GraphicsLayer or View. */
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Picture;
import android.os.SystemClock;
import java.util.Arrays;

public class BackdropProbe {
    static int draws;
    static final Paint paint = new Paint();
    static void syntheticViewDraw(Canvas canvas) {
        draws++;
        for (int i = 0; i < 100; i++) {
            paint.setColor(0xff000000 | (i * 17011 & 0xffffff));
            canvas.drawRect(i * 3, i * 5, i * 3 + 100, i * 5 + 50, paint);
        }
    }
    public static void main(String[] args) {
        for (int r = 1; r <= 3; r++) {
            long[] times = new long[300];
            draws = 0;
            for (int i = 0; i < times.length; i++) {
                Picture layer = new Picture();
                long start = SystemClock.elapsedRealtimeNanos();
                Canvas canvas = layer.beginRecording(1220, 2712);
                int save = canvas.save();
                canvas.translate(0, 0);
                syntheticViewDraw(canvas);
                canvas.restoreToCount(save);
                layer.endRecording();
                times[i] = SystemClock.elapsedRealtimeNanos() - start;
            }
            Arrays.sort(times);
            System.out.println("round=" + r + " records=" + times.length + " synthetic_draws=" + draws + " median_us=" + times[150]/1000 + " p95_us=" + times[285]/1000);
        }
    }
}
