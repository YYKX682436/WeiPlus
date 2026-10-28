package h.Hchat.hooks.api.sns;

import org.junit.Test;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

public class SnsVideoRequestArgumentsTest {
    public static final class Helpers {
        public boolean four(String path, String thumb, String desc, String md5) { return true; }
        public boolean five(String path, String thumb, String desc, String md5, String rawFingerprint) { return true; }
        public void wrongReturn(String a, String b, String c, String d, String e) {}
        public boolean wrongType(String a, String b, String c, String d, Object e) { return true; }
    }

    @Test public void supportsBothConfirmedShapesAndOnlyDefaultsTheVerifiedFingerprint() throws Exception {
        Method four = Helpers.class.getMethod("four", String.class, String.class, String.class, String.class);
        Method five = Helpers.class.getMethod("five", String.class, String.class, String.class,
                String.class, String.class);
        assertArrayEquals(new Object[]{"video", "thumb", "caption", "md5"},
                SnsVideoRequestArguments.create(four, "video", "thumb", "caption", "md5"));
        assertArrayEquals(new Object[]{"video", "thumb", "caption", "md5", ""},
                SnsVideoRequestArguments.create(five, "video", "thumb", "caption", "md5"));
        assertNull(SnsVideoRequestArguments.create(
                Helpers.class.getMethod("wrongReturn", String.class, String.class, String.class,
                        String.class, String.class), "v", "t", "d", "m"));
        assertNull(SnsVideoRequestArguments.create(
                Helpers.class.getMethod("wrongType", String.class, String.class, String.class,
                        String.class, Object.class), "v", "t", "d", "m"));
    }
}
