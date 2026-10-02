import android.app.UiAutomation;
import android.os.Environment;

import com.android.uiautomator.core.UiDevice;
import com.android.uiautomator.testrunner.UiAutomatorTestCase;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * A long-lived UI-tree dumper for qa/run_qa.py (fast path; the runner falls back to `uiautomator dump` if this fails).
 * `uiautomator dump` starts a new VM and waits a full second for idle on every call (3-10 s on the loaded dev machine);
 * this stays up for one QA step. Protocol, all in /data/local/tmp:
 *   run_qa.py writes N to qa_req  ->  QaDumper writes the hierarchy to local/tmp/qa_ui_N.xml (or qa_ui_N.err).
 *   qa_stop (or 90 s without a request) ends it, so a crashed runner never leaves UiAutomation held on the shared device.
 * Started with: uiautomator runtest qa_dumper.jar /system/framework/android.test.base.jar -c QaDumper
 * (android.test.base.jar is no longer on the legacy runner's classpath on Android 14+). Built by run_qa.py with javac + d8.
 */
public class QaDumper extends UiAutomatorTestCase {
    public void testServe() throws Exception {
        UiDevice device = getUiDevice();
        File base = new File("/data/local/tmp");
        // UiDevice.dumpWindowHierarchy(name) writes to $ANDROID_DATA/local/tmp/name; the uiautomator script sets
        // ANDROID_DATA=/data/local/tmp for the shell user, so that is /data/local/tmp/local/tmp/.
        File out = new File(Environment.getDataDirectory(), "local/tmp");
        out.mkdirs();
        File req = new File(base, "qa_req");
        File stop = new File(base, "qa_stop");
        File ready = new File(out, "qa_ready");
        UiAutomation automation = uiAutomation(device);
        new File(out, automation != null ? "qa_cache_ok" : "qa_cache_no").createNewFile();
        ready.createNewFile();
        long last = System.currentTimeMillis();
        while (!stop.exists() && System.currentTimeMillis() - last < 90 * 1000L) {
            if (req.exists()) {
                String n = read(req).trim();
                req.delete();
                try {
                    device.waitForIdle(1500);
                } catch (Throwable ignored) {
                }
                // A long-lived UiAutomation answers from its accessibility node cache, which can lag behind the
                // screen (old bounds after a scroll, a snackbar that never shows up). Read the app fresh every time.
                if (automation != null) {
                    try {
                        automation.clearCache();
                    } catch (Throwable ignored) {
                    }
                }
                File tmp = new File(out, "qa_ui_tmp.xml");
                tmp.delete();
                try {
                    device.dumpWindowHierarchy("qa_ui_tmp.xml");
                    if (!tmp.exists() || !tmp.renameTo(new File(out, "qa_ui_" + n + ".xml"))) {
                        new File(out, "qa_ui_" + n + ".err").createNewFile();
                    }
                } catch (Throwable t) {
                    new File(out, "qa_ui_" + n + ".err").createNewFile();
                }
                last = System.currentTimeMillis();
            }
            Thread.sleep(30);
        }
        stop.delete();
        ready.delete();
    }

    /** UiDevice -> (hidden in the 1.0 API) UiAutomatorBridge -> its UiAutomation; null if that ever changes. */
    private static UiAutomation uiAutomation(UiDevice device) {
        try {
            Object b = null;
            for (Method m : UiDevice.class.getDeclaredMethods()) {
                if (m.getParameterTypes().length == 0 && m.getReturnType().getSimpleName().endsWith("UiAutomatorBridge")) {
                    m.setAccessible(true);
                    b = m.invoke(device);
                    break;
                }
            }
            if (b == null) {
                for (Field f : UiDevice.class.getDeclaredFields()) {
                    if (f.getType().getSimpleName().endsWith("UiAutomatorBridge")) {
                        f.setAccessible(true);
                        b = f.get(device);
                        break;
                    }
                }
            }
            for (Class<?> k = b == null ? null : b.getClass(); k != null; k = k.getSuperclass()) {
                for (Field f : k.getDeclaredFields()) {
                    if (UiAutomation.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        return (UiAutomation) f.get(b);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String read(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        try {
            byte[] b = new byte[64];
            int k = in.read(b);
            return k > 0 ? new String(b, 0, k, "UTF-8") : "0";
        } finally {
            in.close();
        }
    }
}
