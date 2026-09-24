# ============================================================
# Xposed 入口类必须保留完整类名
# ============================================================
-keep class com.eafnv.vibrationblocker.HookEntry { *; }
-keep class com.eafnv.vibrationblocker.ConfigReader { *; }
-keep class com.eafnv.vibrationblocker.ConfigWriter { *; }
-keep class com.eafnv.vibrationblocker.MainActivity { *; }
-keep class com.eafnv.vibrationblocker.WhitelistActivity { *; }

# ============================================================
# 保留 Xposed API 全部 + 所有回调子类
# ============================================================
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

-keep class * extends de.robv.android.xposed.XC_MethodHook { *; }
-keepclassmembers class * extends de.robv.android.xposed.XC_MethodHook {
    protected void beforeHookedMethod(de.robv.android.xposed.XC_MethodHook$MethodHookParam);
    protected void afterHookedMethod(de.robv.android.xposed.XC_MethodHook$MethodHookParam);
}

-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage { *; }
-keepclassmembers class * implements de.robv.android.xposed.IXposedHookLoadPackage {
    public void handleLoadPackage(de.robv.android.xposed.callbacks.XC_LoadPackage$LoadPackageParam);
}

# ============================================================
# release 版删除所有日志调用
# ============================================================
-assumenosideeffects class de.robv.android.xposed.XposedBridge {
    public static void log(java.lang.String);
    public static void log(java.lang.Throwable);
    public static void log(java.lang.String, java.lang.Throwable);
}
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
}

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keep class com.eafnv.vibrationblocker.ModuleActive { *; }
-keepclassmembers class com.eafnv.vibrationblocker.ModuleActive {
    public static boolean isActive();
}