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
import io.tsuru.client.model.Application;
import io.tsuru.client.model.Deployments;
import org.jenkinsci.plugins.tsuru.utils.TarGzip;
import org.jenkinsci.plugins.workflow.cps.EnvActionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class TsuruAction extends AbstractStepImpl {

    public static String createDeployMessage(EnvActionImpl p) throws Exception {
        /*
            env.DEPLOY_MESSAGE =  "CHANGE_ID : ${env.CHANGE_ID}\n"
            env.DEPLOY_MESSAGE += "CHANGE_URL : ${env.CHANGE_URL}\n"
            env.DEPLOY_MESSAGE += "CHANGE_TITLE : ${env.CHANGE_TITLE}\n"
            env.DEPLOY_MESSAGE += "CHANGE_AUTHOR : ${env.CHANGE_AUTHOR}\n"
            env.DEPLOY_MESSAGE += "CHANGE_AUTHOR_DISPLAY_NAME : ${env.CHANGE_AUTHOR_DISPLAY_NAME}\n"
            env.DEPLOY_MESSAGE += "CHANGE_AUTHOR_EMAIL : ${env.CHANGE_AUTHOR_EMAIL}\n"
            env.DEPLOY_MESSAGE += "CHANGE_TARGET : ${env.CHANGE_TARGET}\n"
            env.DEPLOY_MESSAGE += "BRANCH_NAME : ${env.BRANCH_NAME}\n"
            env.DEPLOY_MESSAGE += "GIT_BRANCH : ${env.GIT_BRANCH}\n"
            env.DEPLOY_MESSAGE += "GIT_COMMIT : ${env.GIT_COMMIT}\n"

         */
        return p.getEnvironment().toString();
    }

    public enum Action {
        DEPLOY, ROLLBACK, BUILD,
        APP_CREATE, APP_REMOVE, APP_CLONE,
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
                    getListener().getLogger().println("[app-deploy] Deploying file " + deploymentFile);
                    getListener().getLogger().println("[app-deploy] Directory: " + getWorkspaceFilePath());

                    File fileDir = new File(getWorkspaceFilePath().getRemote() + "/");

                    ArrayList<File> fileList;

                    if (fileDir != null) {
                        fileList = new ArrayList<File>(fileDir.listFiles().length);
                    } else {
                        throw new IOException("Failed to enumerate files from: " + getWorkspaceFilePath().getRemote() + "/");
                    }

                    File tsuruIgnore = new File(fileDir.getAbsolutePath() + File.separator + ".tsuruignore");
                    List<String> ignoredFiles = new ArrayList<>();

                    try {
                        if (tsuruIgnore.exists()) {
                            ignoredFiles = Files.readAllLines(tsuruIgnore.toPath());
                            getListener().getLogger().println("[app-deploy] Ignoring files on deployment: " + ignoredFiles);
                        }
                    } catch (Exception e) {
                    }

                    for (File childFile : fileDir.listFiles()) {
                        Boolean ignoreFile = false;
                        for (String k: ignoredFiles) {
                            if (k.equals(childFile.getName())) {
                                ignoreFile = true;
                                continue;
                            }
                        }
                        if (!ignoreFile)
                            fileList.add(childFile);
                    }

                    TarGzip.compressFiles(fileList, deploymentFile);

                    getListener().getLogger().println("[app-deploy] Starting Tsuru application deployment ========>");
                    int timeout = step.apiInstance.getApiClient().getReadTimeout();
                    step.apiInstance.getApiClient().setReadTimeout(600000); // Same BuildTimeout than TSURU
                    String output = "";
                    try {
                        output = step.apiInstance.appDeploy(step.Args.get("appName"), deploymentFile, step.Args.get("imageTag"), step.Args.get("message"), step.Args.get("commit"));
                        getListener().getLogger().println(output);
                    } catch (io.tsuru.client.ApiException e) {
                        if (e.getCause() instanceof java.io.IOException) {
                            int counter = 0;
                            String id = "";
                            getListener().getLogger().println("[app-deploy] Logs will be truncated, please check the logs directly on Tsuru!");
                            do {
                                List<Deployments> deploys = step.apiInstance.appDeployList(step.Args.get("appName"), 1);
                                if (deploys.size() > 0) {
                                    Deployments deployment = deploys.get(0);
                                    if (id.length() == 0) {
                                        id = deployment.getId();
                                    } else if (!id.contains(deployment.getId())) {
                                        getListener().getLogger().println("[app-deploy] Another deployment started in the meanwhile! Aborting this one!");
                                        break;
                                    }
                                    if (!deployment.getDuration().startsWith("-") && (deployment.getImage().length() != 0 || deployment.getError().length() != 0)) {
                                        output = step.apiInstance.appLog(step.Args.get("appName"), 20);
                                        if (deployment.getImage().length() != 0) {
                                            output += "\nOK\n";
                                        }
                                        break;
                                    }
                                } else {
                                    // No deployments at all
                                    getListener().getLogger().println("[app-deploy] No deployment was found!");
                                    break;
                                }
                                Thread.sleep(5000 + (counter + 500));
                                counter++;
                            } while (counter < 20);
                        } else {
                            // TODO: Better handling on unauthorized and conflict (another deployment in course)
                            throw e;
                        }
                    } finally {
                        step.apiInstance.getApiClient().setReadTimeout(timeout);
                    }
                    if (!output.endsWith("OK\n")) {
                        throw new ApiException("[app-deploy] Tsuru deployment FAILED ˆˆˆˆˆˆˆˆˆ");
                    }

                    getListener().getLogger().println("[app-deploy] Finishing Tsuru application deployment =======>");
                    getListener().getLogger().flush();
                    setResult(true);
                    break;
                case ROLLBACK:
                    getListener().getLogger().println("[app-deploy-rollback] Starting Tsuru application deployment rollback ========>");
                    output = step.apiInstance.appDeployRollback(step.Args.get("appName"), step.Args.get("origin"), step.Args.get("imageTag"));
                    getListener().getLogger().println(output);
                    if (!output.endsWith("OK\n")) {
                        throw new ApiException("[app-deploy-rollback] Tsuru deployment rollback FAILED ˆˆˆˆˆˆˆˆˆ");
                    }

                    getListener().getLogger().println("[app-deploy-rollback] Finishing Tsuru application deployment rollback =======>");
                    getListener().getLogger().flush();
                    setResult(true);
                    break;
                case BUILD:
                    // Create temp deployment file for building Image
                    deploymentFile = File.createTempFile("deploymentFile", ".tgz");
                    deploymentFile.deleteOnExit();
                    System.out.println("Building image from deployment file " + deploymentFile);
                    System.out.println("Directory: " + getWorkspaceFilePath());

                    TarGzip.compressFile(new File(getWorkspaceFilePath().getRemote()), deploymentFile);

                    getListener().getLogger().println("[app-build] Starting Tsuru application building ========>");
                    output = step.apiInstance.appBuild(step.Args.get("appName"), step.Args.get("imageTag"), deploymentFile);
                    getListener().getLogger().println(output);
                    if (!output.endsWith("OK")) {
                        throw new ApiException("[app-build] Tsuru building FAILED ˆˆˆˆˆˆˆˆˆ");
                    }

                    getListener().getLogger().println("[app-build] Finishing Tsuru application build =======>");
                    getListener().getLogger().println("[app-build] Image available under TAG: " + step.Args.get("imageTag"));
                    getListener().getLogger().flush();
                    setResult(true);
                    break;
                case ENV_SET:
                    getListener().getLogger().println("[env-set] Setting environment variable ========>");
                    String[] kv = step.Args.get("env").split("=");
                    io.tsuru.client.model.EnvVars envVar = new io.tsuru.client.model.EnvVars(kv[0], kv[1]);
                    output = step.apiInstance.envSet(step.Args.get("appName"), Arrays.asList(envVar), Boolean.getBoolean(step.Args.get("restartApp")), Boolean.getBoolean(step.Args.get("private")));
                    getListener().getLogger().println(output);

                    getListener().getLogger().println("[env-set] Environment variable setted =======>");
                    getListener().getLogger().flush();
                    setResult(true);
                    break;
                case APP_CREATE:
                    getListener().getLogger().println("[app-create] Creating application on Tsuru ========>");
                    String[] routerOpts = step.Args.get("routerOpts").split(",");
                    HashMap<String,String> routerOptsMap = new HashMap<>();
                    for (String routerOpt: routerOpts) {
                        kv = routerOpt.split("=");
                        if (kv.length > 1) {
                            routerOptsMap.put(kv[0], kv[1]);
                        }
                    }
                    output = step.apiInstance.appCreate(step.Args.get("appName"), step.Args.get("platform"), step.Args.get("plan"), step.Args.get("teamOwner"), step.Args.get("pool"), step.Args.get("appDescription"), Arrays.asList(step.Args.get("tags").split(",")), step.Args.get("router"), routerOptsMap);
                    getListener().getLogger().println(output);

                    getListener().getLogger().println("[app-create] Application created =======>");
                    getListener().getLogger().flush();
                    setResult(true);
                    break;
                case APP_CLONE:
                    String newAppName = step.Args.get("newAppName").toLowerCase();
                    try {
                        getListener().getLogger().println("[app-clone] Retrieving application on Tsuru ========>");
                        Application originalApp = step.apiInstance.appInfo(step.Args.get("appName"));
                        io.tsuru.client.model.EnvVars[] envVars = step.apiInstance.envGet(originalApp.getName(), null);

                        getListener().getLogger().println("[app-clone] Creating the cloned application on Tsuru ========>");
                        String plan = originalApp.getPlan().getName().equals("autogenerated") ? null : originalApp.getPlan().getName();
                        Map routerOptsMaps = originalApp.getRouters().get(0).getOpts();
                        output = step.apiInstance.appCreate(newAppName, originalApp.getPlatform(), plan, originalApp.getTeamOwner(),
                                originalApp.getPool(), originalApp.getDescription(), originalApp.getTag(), originalApp.getRouters().get(0).getName(), routerOptsMaps);
                        getListener().getLogger().println(output);

                        for (io.tsuru.client.model.EnvVars e : envVars) {
                            if (!e.getName().startsWith("TSURU_")) { // Do no update or add TSURU* related Environment variables
                                output = step.apiInstance.envSet(newAppName, Arrays.asList(e), false, !e.getIsPublic());
                                getListener().getLogger().println(output);
                            }
                        }
                    } catch (ApiException e) {
                        getListener().getLogger().println(e.toString() + ": " + e.getResponseBody());
                        try {
                            if (e.getCode() != 409) { // Ignore failing due to already existent application
                                // Rollback the new app creation due to some failure.
                                output = step.apiInstance.appRemove(newAppName);
                                getListener().getLogger().println(output);
                            } else {
                                setResult(true);
                                break;
                            }
                        } catch (Exception ie) {
                            getListener().getLogger().println(ie.toString());
                        }
                        setResult(false);
                        break;
                    }
                    getListener().getLogger().println("[app-clone] Application cloned successfully =======>");
                    getListener().getLogger().flush();
                    setResult(true);
                    break;
                default:
                    getListener().getLogger().println("[tsuru] Jenkins plugin method not implemented.");
            }
            return this;
        }

    }
}
