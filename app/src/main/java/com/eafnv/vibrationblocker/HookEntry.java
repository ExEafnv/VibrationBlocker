package com.eafnv.vibrationblocker;

import android.os.Build;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookEntry implements IXposedHookLoadPackage {

    private static final String TAG = "VibBlocker";
    private static final String MODULE_PKG = "com.eafnv.vibrationblocker";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        // 自hook激活检测
        if (MODULE_PKG.equals(lpparam.packageName)) {
            hookModuleActiveCheck(lpparam.classLoader);
            return;
        }

        // 系统框架进程，安装拦截 hook
        if (!"android".equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + ": loaded, SDK=" + Build.VERSION.SDK_INT);
        hookVibratorManagerService(lpparam.classLoader);
        hookVibratorService(lpparam.classLoader);
    }

    private void hookModuleActiveCheck(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.eafnv.vibrationblocker.ModuleActive",
                    cl,
                    "isActive",
                    XC_MethodReplacement.returnConstant(true)
            );
        } catch (Throwable ignored) {}
    }

    private void hookVibratorManagerService(ClassLoader cl) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        try {
            Class<?> clazz = XposedHelpers.findClass(
                    "com.android.server.vibrator.VibratorManagerService", cl);
            XposedBridge.hookAllMethods(clazz, "vibrateInternal", new Blocker());
            XposedBridge.hookAllMethods(clazz, "vibrate", new Blocker());
            XposedBridge.log(TAG + ": VMS hooked");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": VMS hook failed: " + t);
        }
    }

    private void hookVibratorService(ClassLoader cl) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return;
        try {
            Class<?> clazz = XposedHelpers.findClass(
                    "com.android.server.VibratorService", cl);
            XposedBridge.hookAllMethods(clazz, "vibrate", new Blocker());
            XposedBridge.log(TAG + ": VS hooked");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": VS hook failed: " + t);
        }
    }

    public static class Blocker extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            try {
                String opPkg = extractOpPkg(param);
                if (opPkg != null && ConfigReader.shouldBlock(opPkg)) {
                    param.setResult(null);
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": block error " + t);
            }
        }
    }

    private static String extractOpPkg(MethodHookParam param) {
        if (param.args.length > 2 && param.args[2] instanceof String) {
            String s = (String) param.args[2];
            if (isPkgLike(s)) return s;
        }
        for (Object arg : param.args) {
            if (arg instanceof String) {
                String s = (String) arg;
                if (isPkgLike(s)) return s;
            }
        }
        return null;
    }

    private static boolean isPkgLike(String s) {
        return s != null
                && s.length() > 3 && s.length() < 200
                && s.indexOf('.') > 0
                && s.indexOf(' ') < 0;
    }
}