import com.android.uiautomator.core.UiDevice;
import com.android.uiautomator.testrunner.UiAutomatorTestCase;

import java.io.File;
import java.io.FileInputStream;

/**
 * A long-lived UI-tree dumper for qa/run_qa.py. `uiautomator dump` starts a new process every call (~2.5 s on a
 * loaded machine); this one stays up: write a request number to /data/local/tmp/qa_req and it writes the window
 * hierarchy to /data/local/tmp/qa_ui_<n>.xml. Started with `uiautomator runtest qa_dumper.jar -c QaDumper`.
 */
public class QaDumper extends UiAutomatorTestCase {
    public void testServe() throws Exception {
        UiDevice device = getUiDevice();
        File req = new File("/data/local/tmp/qa_req");
        File stop = new File("/data/local/tmp/qa_stop");
        long idleSince = System.currentTimeMillis();
        while (!stop.exists() && System.currentTimeMillis() - idleSince < 30 * 60 * 1000L) {
            if (req.exists()) {
                String n = read(req).trim();
                req.delete();
                File tmp = new File("/data/local/tmp/qa_ui_tmp.xml");
                try {
                    device.waitForIdle(1500);
                } catch (Throwable ignored) {
                }
                try {
                    device.dumpWindowHierarchy("qa_ui_tmp.xml");
                    tmp.renameTo(new File("/data/local/tmp/qa_ui_" + n + ".xml"));
                } catch (Throwable t) {
                    new File("/data/local/tmp/qa_ui_" + n + ".err").createNewFile();
                }
                idleSince = System.currentTimeMillis();
            }
            Thread.sleep(40);
        }
        stop.delete();
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
