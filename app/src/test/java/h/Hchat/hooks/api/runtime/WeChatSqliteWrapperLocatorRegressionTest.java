package h.Hchat.hooks.api.runtime;

import android.content.ContentValues;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class WeChatSqliteWrapperLocatorRegressionTest {
    @Test
    public void acceptsOfficialAndModifiedWrapperMethodShapesWithoutClassNames() {
        assertTrue(WeChatSqliteWrapperLocator.supportsMessageMutations(OfficialShape.class));
        assertTrue(WeChatSqliteWrapperLocator.supportsMessageMutations(ModifiedShape.class));
        assertTrue(WeChatSqliteWrapperLocator.supportsMessageMutations(InheritedShape.class));
    }

    @Test
    public void rejectsAStringMatchThatHasTheWrongMutationSignatures() {
        assertFalse(WeChatSqliteWrapperLocator.supportsMessageMutations(WrongShape.class));
        assertNull(WeChatSqliteWrapperLocator.selectUniqueSupportedCandidate(
                Collections.singletonList(WrongShape.class)));
    }

    @Test
    public void intersectsEveryAnchorAndRefusesAmbiguousStructuralMatches() {
        List<String> markerMatches = Arrays.asList("earlier-wrong-candidate", "real-wrapper", "one-anchor-only");
        List<String> sqlMatches = Arrays.asList("different-error-class", "real-wrapper");
        assertEquals(Collections.singleton("real-wrapper"),
                WeChatSqliteWrapperLocator.intersectCandidateNames(markerMatches, sqlMatches));

        assertSame(OfficialShape.class, WeChatSqliteWrapperLocator.selectUniqueSupportedCandidate(
                Arrays.asList(WrongShape.class, OfficialShape.class)));
        assertNull(WeChatSqliteWrapperLocator.selectUniqueSupportedCandidate(
                Arrays.asList(OfficialShape.class, ModifiedShape.class)));
    }

    public static class OfficialShape {
        public long put(String table, String nullColumnHack, ContentValues values) { return 1L; }
        public int change(String table, ContentValues values, String where, String[] args) { return 1; }
        public int delete(String table, String where, String[] args) { return 1; }
    }

    public static class ModifiedShape {
        public long firstWrite(String table, String nullColumnHack, ContentValues values) { return 1L; }
        public long secondWrite(String table, String nullColumnHack, ContentValues values) { return 1L; }
        public int updateRows(String table, ContentValues values, String where, String[] args) { return 1; }
        public int removeRows(String table, String where, String[] args) { return 1; }
    }

    public static class ParentShape {
        public long insertRows(String table, String nullColumnHack, ContentValues values) { return 1L; }
    }

    public static class InheritedShape extends ParentShape {
        public int updateRows(String table, ContentValues values, String where, String[] args) { return 1; }
        public int deleteRows(String table, String where, String[] args) { return 1; }
    }

    public static class WrongShape {
        public int insertRows(String table, String nullColumnHack, ContentValues values) { return 1; }
        public boolean updateRows(String table, ContentValues values, String where, String[] args) { return true; }
        public long deleteRows(String table, String where, String[] args) { return 1L; }
    }
}
