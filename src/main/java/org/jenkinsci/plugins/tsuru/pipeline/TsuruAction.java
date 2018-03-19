package org.jenkinsci.plugins.tsuru.pipeline;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.tsuru.client.ApiException;
import io.tsuru.client.api.TsuruApi;
import org.jenkinsci.plugins.tsuru.utils.TarGzip;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.inject.Inject;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class TsuruAction extends AbstractStepImpl {

    public enum Action {
        DEPLOY, ROLLBACK, BUILD,
        APP_CREATE, APP_REMOVE,
        ENV_SET, ENV_GET,
    }

    public static final String FUNCTION_NAME = "_TsuruAction";

    private TsuruApi apiInstance = null;

    private Action action;

    private HashMap<String, String> Args;

    @DataBoundConstructor
    public TsuruAction(TsuruApi apiInstance, Action action, HashMap<String, String> Args) {
        this.apiInstance = apiInstance;
        this.action = action;
        this.Args = Args;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override
        public String getFunctionName() {
            return FUNCTION_NAME;
        }

        @Override
        public String getDisplayName() {
            return "Internal utility function for Tsuru DSL";
        }

        /**
         * This step is not meant to be used directly by DSL scripts. Setting
         * advanced causes this entry to show up at the bottom of the function
         * listing.
         */
        @Override
        public boolean isAdvanced() {
            return true;
        }
    }

    public static class Execution extends
            AbstractSynchronousStepExecution<Execution> {

        private static final long serialVersionUID = 1L;

        @Inject
        private transient TsuruAction step;

        public TsuruAction getStep() {
            return step;
        }

        public void setStep(TsuruAction step) {
            this.step = step;
        }

        public TaskListener getListener() {
            return listener;
        }

        public void setListener(TaskListener listener) {
            this.listener = listener;
        }

        public Launcher getLauncher() {
            return launcher;
        }

        public void setLauncher(Launcher launcher) {
            this.launcher = launcher;
        }

        public EnvVars getEnvVars() {
            return envVars;
        }

        public void setEnvVars(EnvVars envVars) {
            this.envVars = envVars;
        }

        public FilePath getFilePath() {
            return filePath;
        }

        public void setFilePath(FilePath filePath) {
            this.filePath = filePath;
        }

        public Executor getExecutor() {
            return executor;
        }

        public void setExecutor(Executor executor) {
            this.executor = executor;
        }

        public Computer getComputer() {
            return computer;
        }

        public void setComputer(Computer computer) {
            this.computer = computer;
        }

        @StepContextParameter
        private transient TaskListener listener;
        @StepContextParameter
        private transient Launcher launcher;
        @StepContextParameter
        private transient EnvVars envVars;
        @StepContextParameter
        private transient Run<?, ?> runObj;
        @StepContextParameter
        private transient FilePath filePath;
        @StepContextParameter
        private transient Executor executor;
        @StepContextParameter
        private transient Computer computer;

        public Boolean getResult() {
            return result;
        }

        public void setResult(Boolean result) {
            this.result = result;
        }

        private Boolean result = false;

        public FilePath getWorkspaceFilePath() {
            return filePath;
        }

        @Override
        protected Execution run() throws Exception {
            switch (step.action) {
                case DEPLOY:
                    // Create temp deployment file
                    File deploymentFile = File.createTempFile("deploymentFile", ".tgz");
                    deploymentFile.deleteOnExit();
                    System.out.println("Deploying file " + deploymentFile);
                    System.out.println("Directory: " + getWorkspaceFilePath());
                    TarGzip.compressFile(new File(getWorkspaceFilePath().toString()), deploymentFile);
                    try {
                        getListener().getLogger().println("[app-deploy] Starting Tsuru application deployment ========>");
                        String output = step.apiInstance.appDeploy(step.Args.get("appName"), deploymentFile, step.Args.get("imageTag"));
                        getListener().getLogger().print(output);
                        if (!output.endsWith("OK")) {
                            throw new ApiException("[app-deploy] Tsuru deployment FAILED ˆˆˆˆˆˆˆˆˆ");
                        }

                    } catch (ApiException e) {
                        getListener().getLogger().print(e.toString());
                        setResult(false);
                    }
                    getListener().getLogger().println("[app-deploy] Finishing Tsuru application deployment =======>");
                    getListener().getLogger().flush();
                    setResult(true);
                case ROLLBACK:
                    try {
                        getListener().getLogger().println("[app-deploy-rollback] Starting Tsuru application deployment rollback ========>");
                        String output = step.apiInstance.appDeployRollback(step.Args.get("appName"), step.Args.get("origin"), step.Args.get("imageTag"));
                        getListener().getLogger().print(output);
                        if (!output.endsWith("OK")) {
                            throw new ApiException("[app-deploy-rollback] Tsuru deployment rollback FAILED ˆˆˆˆˆˆˆˆˆ");
                        }

                    } catch (ApiException e) {
                        getListener().getLogger().print(e.toString());
                        setResult(false);
                    }
                    getListener().getLogger().println("[app-deploy-rollback] Finishing Tsuru application deployment rollback =======>");
                    getListener().getLogger().flush();
                    setResult(true);
                case BUILD:
                    // Create temp deployment file for building Image
                    deploymentFile = File.createTempFile("deploymentFile", ".tgz");
                    deploymentFile.deleteOnExit();
                    System.out.println("Building image from deployment file " + deploymentFile);
                    System.out.println("Directory: " + getWorkspaceFilePath());
                    TarGzip.compressFile(new File(getWorkspaceFilePath().toString()), deploymentFile);
                    try {
                        getListener().getLogger().println("[app-build] Starting Tsuru application building ========>");
                        String output = step.apiInstance.appBuild(step.Args.get("appName"), step.Args.get("imageTag"), deploymentFile);
                        getListener().getLogger().print(output);
                        if (!output.endsWith("OK")) {
                            throw new ApiException("[app-build] Tsuru building FAILED ˆˆˆˆˆˆˆˆˆ");
                        }
                    } catch (ApiException e) {
                        getListener().getLogger().print(e.toString());
                        setResult(false);
                    }
                    getListener().getLogger().println("[app-build] Finishing Tsuru application build =======>");
                    getListener().getLogger().println("[app-build] Image available under TAG: " + step.Args.get("imageTag"));
                    getListener().getLogger().flush();
                    setResult(true);
                case ENV_SET:
                    try {
                        getListener().getLogger().println("[env-set] Setting environment variable ========>");
                        String output = step.apiInstance.envSet(step.Args.get("appName"), step.Args.get("env"), Boolean.getBoolean(step.Args.get("restartApp")), Boolean.getBoolean(step.Args.get("private")));
                    } catch (ApiException e) {
                        getListener().getLogger().print(e.toString());
                        setResult(false);
                    }
                    getListener().getLogger().println("[env-set] Environment variable setted =======>");
                    getListener().getLogger().flush();
                    setResult(true);
                case APP_CREATE:
                    try {
                        getListener().getLogger().println("[app-create] Creating application on Tsuru ========>");
                        String output = step.apiInstance.appCreate(step.Args.get("appName"), step.Args.get("platform"), step.Args.get("plan"), step.Args.get("teamOwner"), step.Args.get("pool"), step.Args.get("appDescription"), Arrays.asList(step.Args.get("tags").split(",")), step.Args.get("router"), Arrays.asList(step.Args.get("routerOpts").split(",")));
                    } catch (ApiException e) {
                        getListener().getLogger().print(e.toString());
                        setResult(false);
                    }
                    getListener().getLogger().println("[app-create] Application created =======>");
                    getListener().getLogger().flush();
                    setResult(true);
                    default:
                        getListener().getLogger().println("[tsuru] Jenkins plugin method not implemented.");

            }
            return this;
        }

    }
}
