package h.Hchat.hooks.api.contact;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import org.junit.Test;

public final class DeleteContactServiceMethodPolicyTest {
    private static final class ObfuscatedServiceFixture {
        void a(String username, boolean clearRecord) {}
        void b(String username, int resultCode, boolean clearRecord) {}
        static void c(String username, boolean clearRecord) {}
        int d(String username, boolean clearRecord) { return 0; }
        void e(String username, int resultCode) {}
    }

    @Test
    public void acceptsExactSubmitSignatureOnAnObfuscatedOwner() throws Exception {
        Method method = ObfuscatedServiceFixture.class.getDeclaredMethod(
                "a", String.class, boolean.class);

        assertTrue(DeleteContactServiceMethodPolicy.isSubmitMethod(method));
        assertFalse(DeleteContactServiceMethodPolicy.isSubmitMethod(
                ObfuscatedServiceFixture.class.getDeclaredMethod("c", String.class, boolean.class)));
        assertFalse(DeleteContactServiceMethodPolicy.isSubmitMethod(
                ObfuscatedServiceFixture.class.getDeclaredMethod("d", String.class, boolean.class)));
        assertFalse(DeleteContactServiceMethodPolicy.isSubmitMethod(
                ObfuscatedServiceFixture.class.getDeclaredMethod("e", String.class, int.class)));
    }

    @Test
    public void responseHandlerMustMatchExactOwnerAndSignature() throws Exception {
        Method response = ObfuscatedServiceFixture.class.getDeclaredMethod(
                "b", String.class, int.class, boolean.class);

        assertTrue(DeleteContactServiceMethodPolicy.isResponseMethod(
                response, ObfuscatedServiceFixture.class));
        assertFalse(DeleteContactServiceMethodPolicy.isResponseMethod(
                response, String.class));
        assertFalse(DeleteContactServiceMethodPolicy.isResponseMethod(
                ObfuscatedServiceFixture.class.getDeclaredMethod("c", String.class, boolean.class),
                ObfuscatedServiceFixture.class));
    }
}
