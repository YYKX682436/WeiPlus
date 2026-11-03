package com.tencent.tinker.loader;

import android.content.Intent;
import com.tencent.tinker.loader.app.TinkerApplication;

public class TinkerLoader {
    public int tryLoadPatchFilesInternal(TinkerApplication application, Intent intent) { return 1; }
    public int tryLoadPatchFilesInternal(TinkerApplication application, Intent intent, int flags) { return 2; }
}
