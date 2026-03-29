package com.flufflehook;

import android.content.Context;
import android.media.AudioManager;

import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.ScheduledExecutorService;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class FlufflePrivacyModule implements IXposedHookLoadPackage {

    private static FileInputStream pcmStream;
    private static final String PCM_FILE = "/sdcard/fake_audio.pcm";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.fluffleapp.mobile")) return;

        XposedBridge.log("FluffleHook: Loaded into " + lpparam.packageName);

        Class<?> errorCallbackClass = Class.forName(
            "org.webrtc.audio.JavaAudioDeviceModule$AudioRecordErrorCallback",
            true, lpparam.classLoader);
        Class<?> stateCallbackClass = Class.forName(
            "org.webrtc.audio.JavaAudioDeviceModule$AudioRecordStateCallback",
            true, lpparam.classLoader);
        Class<?> samplesCallbackClass = Class.forName(
            "org.webrtc.audio.JavaAudioDeviceModule$SamplesReadyCallback",
            true, lpparam.classLoader);

        XposedHelpers.findAndHookConstructor(
            "org.webrtc.audio.WebRtcAudioRecord",
            lpparam.classLoader,
            Context.class,
            ScheduledExecutorService.class,
            AudioManager.class,
            int.class, int.class,
            errorCallbackClass,
            stateCallbackClass,
            samplesCallbackClass,
            boolean.class, boolean.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        pcmStream = new FileInputStream(PCM_FILE);
                        XposedBridge.log("FluffleHook: PCM file opened OK");
                    } catch (Exception e) {
                        XposedBridge.log("FluffleHook: PCM file error - " + e.getMessage());
                        return;
                    }

                    Object fakeCallback = Proxy.newProxyInstance(
                        lpparam.classLoader,
                        new Class[]{samplesCallbackClass},
                        (proxy, method, args) -> {
                            if (!method.getName().equals("onWebRtcAudioRecordSamplesReady"))
                                return null;

                            try {
                                Object audioSamples = args[0];
                                Field dataField = audioSamples.getClass().getDeclaredField("data");
                                dataField.setAccessible(true);
                                byte[] original = (byte[]) dataField.get(audioSamples);
                                byte[] fake = new byte[original.length];

                                int read = pcmStream.read(fake);
                                if (read < fake.length) {
                                    try { pcmStream.close(); } catch (Exception ignored) {}
                                    pcmStream = new FileInputStream(PCM_FILE);
                                    pcmStream.read(fake, read, fake.length - read);
                                }

                                dataField.set(audioSamples, fake);
                            } catch (Exception e) {
                                XposedBridge.log("FluffleHook: inject error - " + e.getMessage());
                            }
                            return null;
                        }
                    );

                    param.args[7] = fakeCallback;
                }
            }
        );
    }
}
