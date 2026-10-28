package h.Hchat.hooks.api.message;

import org.junit.Test;
import java.lang.reflect.Constructor;
import static org.junit.Assert.*;

public class SendTextRequestArgumentsTest {
    public static final class FiveArgs {
        public FiveArgs(String talker, String text, int type, int flag, long source) {}
        public FiveArgs(String talker, String text, int type, int flag, Object source) {}
    }

    public static final class SixArgs {
        public SixArgs(String talker, String text, int type, int flag, long source, String msgSource) {}
        public SixArgs(String talker, String text, int type, int flag, Object source, String msgSource) {}
    }

    @Test public void buildsVerifiedFiveAndSixArgumentShapes() throws Exception {
        Object source = new Object();
        Object[] fiveLong = create(FiveArgs.class.getConstructor(String.class, String.class,
                int.class, int.class, long.class), null);
        assertArrayEquals(new Object[]{"talker", "body", 1, 2, 0L}, fiveLong);

        Object[] fiveObject = create(FiveArgs.class.getConstructor(String.class, String.class,
                int.class, int.class, Object.class), source);
        assertArrayEquals(new Object[]{"talker", "body", 1, 2, source}, fiveObject);

        Object[] sixLong = create(SixArgs.class.getConstructor(String.class, String.class,
                int.class, int.class, long.class, String.class), null);
        assertArrayEquals(new Object[]{"talker", "body", 1, 2, 0L, null}, sixLong);

        Object[] sixObject = create(SixArgs.class.getConstructor(String.class, String.class,
                int.class, int.class, Object.class, String.class), source);
        assertArrayEquals(new Object[]{"talker", "body", 1, 2, source, null}, sixObject);
        assertNull(SendTextRequestArguments.create(
                SixArgs.class.getConstructor(String.class, String.class, int.class, int.class,
                        long.class, String.class), "talker", "body", 1, 2, source));
    }

    private static Object[] create(Constructor<?> constructor, Object sourceArgs) {
        return SendTextRequestArguments.create(
                constructor, "talker", "body", 1, 2, sourceArgs);
    }
}
