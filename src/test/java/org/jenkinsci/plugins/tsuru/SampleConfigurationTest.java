package org.jenkinsci.plugins.tsuru;

import hudson.FilePath;
import hudson.Functions;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.slaves.DumbSlave;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import org.jenkinsci.plugins.tsuru.utils.TarGzip;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.io.File;

public class SampleConfigurationTest {

    @Rule
    public RestartableJenkinsRule rr = new RestartableJenkinsRule();

    @Rule public JenkinsRule r = new JenkinsRule();

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule public JenkinsRule j = new JenkinsRule();
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    @Rule public ErrorCollector errors = new ErrorCollector();
    @Rule public LoggerRule logging = new LoggerRule();


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

    @Test
    public void targzFiles() throws Exception {
        File deploymentFile = File.createTempFile("deploymentFile", ".tgz");

        TarGzip.compressFile(new File("./a"), deploymentFile);

        System.out.println(deploymentFile);

    }

    @Test
    public void checkSetEnv() throws Exception {
        DumbSlave slave = j.createSlave("slave", null, null);
        FreeStyleProject f = j.createFreeStyleProject("f"); // the control
        f.setAssignedNode(slave);
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        FilePath ws;

        while ((ws = slave.getWorkspaceFor(p)) == null) {
            Thread.sleep(100);
        }
        p.setDefinition(new CpsFlowDefinition("tsuru.withAPI('localhost') { tsuru.connect(); tsuru.setEnv('salve', 'salve', true, true); }", false));

        j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
    }

}
