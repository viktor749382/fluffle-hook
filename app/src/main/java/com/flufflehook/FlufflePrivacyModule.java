package com.flufflehook;

import android.content.Context;
import android.media.AudioManager;

import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledExecutorService;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class FlufflePrivacyModule implements IXposedHookLoadPackage {

    private static FileInputStream pcmStream;
    private static final String PCM_FILE = "/data/data/com.fluffleapp.mobile/fake_audio.pcm";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.fluffleapp.mobile")) return;

        XposedBridge.log("FluffleHook: Loaded into " + lpparam.packageName);

        XposedHelpers.findAndHookMethod(
            "android.media.AudioRecord",
            lpparam.classLoader,
            "read",
            ByteBuffer.class,
            int.class,
            int.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    ByteBuffer buffer = (ByteBuffer) param.args[0];
                    int sizeInBytes = (int) param.args[1];

                    try {
                        if (pcmStream == null) {
                            pcmStream = new FileInputStream(PCM_FILE);
                            XposedBridge.log("FluffleHook: PCM stream opened");
                        }

                        byte[] fake = new byte[sizeInBytes];
                        int read = pcmStream.read(fake);
                        if (read < sizeInBytes) {
                            try { pcmStream.close(); } catch (Exception ignored) {}
                            pcmStream = new FileInputStream(PCM_FILE);
                            pcmStream.read(fake, read, sizeInBytes - read);
                        }

                        buffer.rewind();
                        buffer.put(fake, 0, sizeInBytes);
                        buffer.rewind();
                        param.setResult(sizeInBytes);

                    } catch (Exception e) {
                        XposedBridge.log("FluffleHook: read hook error - " + e.getMessage());
                    }
                }
            }
        );
    }
}
