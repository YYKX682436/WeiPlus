package h.Hchat.dexkit;

import java.util.List;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

/** Query boundary for DexFinder; keeps locator recovery executable without loading native DexKit. */
interface DexFinderQuery {
    List<MethodData> findMethod(FindMethod query);
    List<ClassData> findClass(FindClass query);
}
