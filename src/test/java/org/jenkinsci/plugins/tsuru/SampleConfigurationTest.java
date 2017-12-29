package org.jenkinsci.plugins.tsuru;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import org.jenkinsci.plugins.tsuru.pipeline.TsuruGlobalVariable;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class SampleConfigurationTest {

    @Rule
    public RestartableJenkinsRule rr = new RestartableJenkinsRule();

    /**
     * Tries to exercise enough code paths to catch common mistakes:
     * <ul>
     * <li>missing {@code load}
     * <li>missing {@code save}
     * <li>misnamed or absent getter/setter
     * <li>misnamed {@code textbox}
     * </ul>
     */
    /*@Test
    public void uiAndStorage() {
        rr.then(r -> {
            assertEquals("global config page let us edit it", "tsuru", TsuruGlobalVariable.getName());
        });
        rr.then(r -> {
            assertEquals("still there after restart of Jenkins", "tsuru", TsuruGlobalVariable.getName());
        });
    }*/

}
