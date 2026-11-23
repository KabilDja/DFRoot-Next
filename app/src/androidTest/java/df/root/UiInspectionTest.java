package df.root;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Environment;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class UiInspectionTest {
    private void shot(UiDevice device, String name) throws Exception {
        device.waitForIdle();
        Thread.sleep(900); // Allow the captured glass layer and popup animation to render.
        File dir = InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }
    private void open(UiDevice device) throws Exception {
        var ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        device.executeShellCommand("am start -W -n df.root/.NextActivity -f 0x10008000");
        if(!device.wait(Until.hasObject(By.text("DFRoot Next")),5000)) {
            var systemClose=device.findObject(By.text("Close app"));
            if(systemClose!=null && device.hasObject(By.text("Quickstep isn't responding"))) systemClose.click();
        }
        if(!device.wait(Until.hasObject(By.text("DFRoot Next")),30000)) {
            shot(device,"launch-failure");
            throw new AssertionError("Home title missing after explicit activity launch");
        }
    }
    @Test public void persistedMarkersResolveState() {
        assertEquals(1,RootUiState.resolve(true,true,false,0));
        assertEquals(-1,RootUiState.resolve(true,false,true,0));
        assertEquals(0,RootUiState.resolve(false,false,false,1));
        assertEquals(0,RootUiState.resolve(true,false,false,0));
        assertEquals(-1,RootUiState.resolve(false,false,false,-1));
    }
    @Test public void inspectBothThemesAndSettings() throws Exception {
        var device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.executeShellCommand("cmd uimode night yes");Thread.sleep(1200); open(device); shot(device,"dark-home");
        // Render result states without invoking the engine.
        var monitor=InstrumentationRegistry.getInstrumentation().addMonitor(NextActivity.class.getName(),null,false);
        open(device);
        var activity=monitor.waitForActivityWithTimeout(5000);
        assertNotNull(activity);
        var setter=NextActivity.class.getDeclaredMethod("setOutcome",int.class);setter.setAccessible(true);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> { try { setter.invoke(activity,1); } catch(Exception e) { throw new RuntimeException(e); } });
        assertTrue(device.wait(Until.hasObject(By.text("Reported by DFRoot.")),5000));
        shot(device,"dark-success");
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> { try { setter.invoke(activity,-1); } catch(Exception e) { throw new RuntimeException(e); } });
        assertTrue(device.wait(Until.hasObject(By.text("Already launched")),5000));
        shot(device,"dark-failure");
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> { try { setter.invoke(activity,0); } catch(Exception e) { throw new RuntimeException(e); } });
        // A fixture uses this app's real installed icon, never invents a manager logo.
        var entries=NextActivity.class.getDeclaredMethod("setEntries",java.util.List.class);entries.setAccessible(true);
        var selected=NextActivity.class.getDeclaredMethod("setSelected",String.class);selected.setAccessible(true);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> { try {
            entries.invoke(activity,java.util.Collections.singletonList(new kotlin.Pair<String,String>("df.root","Icon preview")));
            selected.invoke(activity,"df.root");
        } catch(Exception e) { throw new RuntimeException(e); } });
        shot(device,"manager-icon-preview");
        var managerRow=device.wait(Until.findObject(By.textContains("Icon preview")),5000);
        assertNotNull(managerRow);managerRow.click();
        Thread.sleep(700);
        shot(device,"manager-picker-preview");device.pressBack();
        var output=NextActivity.class.getDeclaredMethod("setOutput",String.class);output.setAccessible(true);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> { try {
            output.invoke(activity,"Blur preview\nRepeated test content behind the floating controls\n".repeat(18));
        } catch(Exception e) { throw new RuntimeException(e); } });
        InstrumentationRegistry.getInstrumentation().removeMonitor(monitor);
        device.findObject(By.desc("Settings")).click();
        assertTrue(device.wait(Until.hasObject(By.text("Start at Boot")),5000));shot(device,"dark-settings");shot(device,"dark-glass-over-content");
        assertFalse(device.hasObject(By.text("Done")));
        device.click(device.getDisplayWidth()/2,device.getDisplayHeight()/3);
        assertTrue(device.wait(Until.gone(By.text("Start at Boot")),5000));
        device.findObject(By.desc("Settings")).click();
        assertTrue(device.wait(Until.hasObject(By.text("Start at Boot")),5000));device.pressBack();
        assertTrue(device.wait(Until.gone(By.text("Start at Boot")),5000));
        device.executeShellCommand("cmd uimode night no");Thread.sleep(1200);open(device);shot(device,"light-home");
        device.findObject(By.desc("Settings")).click();
        assertTrue(device.wait(Until.hasObject(By.text("Auto Soft Reboot")),5000));shot(device,"light-settings");
        device.pressBack();
        assertTrue(device.wait(Until.gone(By.text("Auto Soft Reboot")),5000));
        Thread.sleep(700);
        var scroller=device.findObject(By.scrollable(true));
        if(scroller!=null) scroller.scroll(androidx.test.uiautomator.Direction.DOWN, 1.0f);
        assertTrue(device.wait(Until.hasObject(By.text("Log")),5000));
        shot(device,"light-log");
        device.executeShellCommand("settings put system font_scale 1.3");open(device);shot(device,"large-text");
        device.executeShellCommand("settings put system font_scale 1.0");open(device);
        assertTrue(BuildConfig.UI_INSPECTION);
        device.findObject(By.desc("Launch Root")).click();
        assertFalse(device.hasObject(By.text("Starting root…")));
        assertTrue(device.hasObject(By.text("Not rooted")));
        var pill=device.findObject(By.desc("Launch Root")).getVisibleBounds();
        assertTrue(pill.left > device.getDisplayWidth() * .10);
        assertTrue(pill.right < device.getDisplayWidth() * .90);
        assertTrue(pill.bottom < device.getDisplayHeight() - 60);
    }
}
