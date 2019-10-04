package org.jenkinsci.plugins.tsuru.pipeline;

import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.tsuru.client.ApiClient;
import io.tsuru.client.ApiException;
import io.tsuru.client.api.TsuruApi;
import io.tsuru.client.auth.Authentication;
import io.tsuru.client.model.Application;
import io.tsuru.client.model.Deployments;
import jenkins.security.MasterToSlaveCallable;
import org.jenkinsci.plugins.tsuru.utils.TarGzip;
import org.jenkinsci.plugins.workflow.cps.EnvActionImpl;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Logger;

public class TsuruAction extends Step implements Serializable {

    private static final Logger LOGGER = Logger.getLogger("org.jenkinsci.plugins.tsuru");

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

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

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

        @Override
        public Set<Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, FilePath.class, TaskListener.class, Launcher.class);
        }
    }

    public static class Execution extends
            AbstractStepExecutionImpl {

        private static final long serialVersionUID = 1L;

        @Inject
        private transient TsuruAction step;

        Execution(TsuruAction step, StepContext context) {
            super(context);
            this.step = step;
        }

        public TsuruAction getStep() {
            return step;
        }

        public void setStep(TsuruAction step) {
            this.step = step;
        }

        private transient volatile ScheduledFuture<?> task;

        public Boolean getResult() {
            return result;
        }

        public void setResult(Boolean result) {
            this.result = result;
        }

        private Boolean result = false;

        @Override
        public void stop(Throwable cause) throws Exception {
            if (task != null) {
                task.cancel(false);
            }
            getContext().onFailure(cause);
        }

        public boolean start() {

            new Thread("tsuru") {
                @Override
                public void run() {
                    try {
                        tsuruPerform();
                    } catch (Exception e) {
                        Execution.this.getContext().onFailure(e);
                    }
                }
            }.start();

            return false;
        }

        private void tsuruPerform() throws Exception {
            FilePath workspace = getContext().get(FilePath.class);
            TaskListener listener = getContext().get(TaskListener.class);
            Launcher launcher = getContext().get(Launcher.class);
            String output = "";

            switch (step.action) {
                case DEPLOY:
                    class RunDeployOnNode extends MasterToSlaveCallable<Void, Exception> {
                        private static final long serialVersionUID = 1L;
                        private final TsuruAction step;

                        private String basePath;

                        private String authorization;

                        public RunDeployOnNode(TsuruAction step) {
                            this.step = step;

                            this.basePath = step.apiInstance.getApiClient().getBasePath();
                            this.authorization = step.apiInstance.getApiClient().getDefaultHeaders().get("Authorization");
                        }

                        @Override
                        public Void call() throws Exception {
                            this.step.apiInstance = new TsuruApi();
                            this.step.apiInstance.getApiClient().setBasePath(basePath);
                            this.step.apiInstance.getApiClient().addDefaultHeader("Authorization", this.authorization);

                            // Create temp deployment file
                            File deploymentFile = File.createTempFile("deploymentFile", ".tgz");
                            deploymentFile.deleteOnExit();
                            listener.getLogger().println("[app-deploy] Deploying file " + deploymentFile);
                            listener.getLogger().println("[app-deploy] Directory: " + workspace);

                            File fileDir = new File(workspace + "/");

                            ArrayList<File> fileList;

                            if (fileDir != null) {
                                fileList = new ArrayList<File>(fileDir.listFiles().length);
                            } else {
                                throw new IOException("Failed to enumerate files from: " + workspace + "/");
                            }

                            File tsuruIgnore = new File(fileDir.getAbsolutePath() + File.separator + ".tsuruignore");
                            List<String> ignoredFiles = new ArrayList<>();

                            try {
                                if (tsuruIgnore.exists()) {
                                    ignoredFiles = Files.readAllLines(tsuruIgnore.toPath());
                                    listener.getLogger().println("[app-deploy] Ignoring files on deployment: " + ignoredFiles);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            for (File childFile : fileDir.listFiles()) {
                                Boolean ignoreFile = false;
                                for (String k : ignoredFiles) {
                                    if (k.equals(childFile.getName())) {
                                        ignoreFile = true;
                                        continue;
                                    }
                                }
                                if (!ignoreFile)
                                    fileList.add(childFile);
                            }

                            TarGzip.compressFiles(fileList, deploymentFile);

                            listener.getLogger().println("[app-deploy] Starting Tsuru application deployment ========>");
                            listener.getLogger().flush();

                            int timeout = this.step.apiInstance.getApiClient().getReadTimeout();
                            this.step.apiInstance.getApiClient().setReadTimeout(600000); // Same BuildTimeout than TSURU
                            String output = "";

                            try {
                                output = this.step.apiInstance.appDeploy(this.step.Args.get("appName"), deploymentFile, this.step.Args.get("imageTag"), this.step.Args.get("message"), this.step.Args.get("commit"));
                            } catch (io.tsuru.client.ApiException e) {

                                    if (e.getCause() instanceof java.io.IOException) {
                                        int counter = 0;
                                        String id = "";
                                        listener.getLogger().println("[app-deploy] Logs will be truncated, please check the logs directly on Tsuru!");
                                        do {
                                            List<Deployments> deploys = step.apiInstance.appDeployList(step.Args.get("appName"), 1);
                                            if (deploys.size() > 0) {
                                                Deployments deployment = deploys.get(0);
                                                if (id.length() == 0) {
                                                    id = deployment.getId();
                                                } else if (!id.contains(deployment.getId())) {
                                                    listener.getLogger().println("[app-deploy] Another deployment started in the meanwhile! Aborting this one!");
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
                                                listener.getLogger().println("[app-deploy] No deployment was found!");
                                                break;
                                            }
                                            Thread.sleep(5000 + (counter * 500));
                                            counter++;
                                        } while (counter < 80);
                                    } else {
                                        // TODO: Better handling on unauthorized and conflict (another deployment in course)
                                        throw e;
                                    }

                            } finally {
                                listener.getLogger().println(output);
                                step.apiInstance.getApiClient().setReadTimeout(timeout);
                            }

                            if (!output.endsWith("OK\n")) {
                                throw new ApiException("[app-deploy] Tsuru deployment FAILED ˆˆˆˆˆˆˆˆˆ");
                            }
                            listener.getLogger().println("[app-deploy] Finishing Tsuru application deployment =======>");
                            listener.getLogger().flush();
                            setResult(true);
                            return null;
                        }
                    }

                    launcher.getChannel().call(new RunDeployOnNode(step));
                    break;
                case ROLLBACK:
                    listener.getLogger().println("[app-deploy-rollback] Starting Tsuru application deployment rollback ========>");
                    output = step.apiInstance.appDeployRollback(step.Args.get("appName"), step.Args.get("origin"), step.Args.get("imageTag"));
                    listener.getLogger().println(output);
                    if (!output.endsWith("OK\n")) {
                        throw new ApiException("[app-deploy-rollback] Tsuru deployment rollback FAILED ˆˆˆˆˆˆˆˆˆ");
                    }

                    listener.getLogger().println("[app-deploy-rollback] Finishing Tsuru application deployment rollback =======>");
                    listener.getLogger().flush();
                    setResult(true);
                    break;
                case BUILD:

                    class RunBuildOnNode extends MasterToSlaveCallable<Void, Exception> {
                        private static final long serialVersionUID = 1L;
                        private final TsuruAction step;

                        private String basePath;

                        private String authorization;

                        public RunBuildOnNode(TsuruAction step) {
                            this.step = step;

                            this.basePath = step.apiInstance.getApiClient().getBasePath();
                            this.authorization = step.apiInstance.getApiClient().getDefaultHeaders().get("Authorization");
                        }

                        @Override
                        public Void call() throws Exception {
                            this.step.apiInstance = new TsuruApi();
                            this.step.apiInstance.getApiClient().setBasePath(basePath);
                            this.step.apiInstance.getApiClient().addDefaultHeader("Authorization", this.authorization);

                            // Create temp deployment file for building Image
                            File deploymentFile = File.createTempFile("deploymentFile", ".tgz");
                            deploymentFile.deleteOnExit();
                            System.out.println("Building image from deployment file " + deploymentFile);
                            System.out.println("Directory: " + workspace);

                            TarGzip.compressFile(new File(workspace.getRemote()), deploymentFile);

                            listener.getLogger().println("[app-build] Starting Tsuru application building ========>");
                            String output = step.apiInstance.appBuild(step.Args.get("appName"), step.Args.get("imageTag"), deploymentFile);
                            listener.getLogger().println(output);
                            if (!output.endsWith("OK")) {
                                throw new ApiException("[app-build] Tsuru building FAILED ˆˆˆˆˆˆˆˆˆ");
                            }

                            listener.getLogger().println("[app-build] Finishing Tsuru application build =======>");
                            listener.getLogger().println("[app-build] Image available under TAG: " + step.Args.get("imageTag"));
                            listener.getLogger().flush();
                            setResult(true);
                            return null;
                        }
                    }

                    launcher.getChannel().call(new RunBuildOnNode(step));
                    break;
                case ENV_SET:
                    listener.getLogger().println("[env-set] Setting environment variable ========>");
                    String[] kv = step.Args.get("env").split("=");
                    String[] v = Arrays.copyOfRange(kv, 1, kv.length);
                    String vJoined = String.join("=", v);

                    io.tsuru.client.model.EnvVars envVar = new io.tsuru.client.model.EnvVars(kv[0], vJoined);
                    String NoRestartString = step.Args.get("restartApp");
                    Boolean NoRestart = Boolean.valueOf(NoRestartString);
                    String IsPrivateString = step.Args.get("private");
                    Boolean IsPrivate = Boolean.valueOf(IsPrivateString);
                    output = step.apiInstance.envSet(step.Args.get("appName"), Arrays.asList(envVar), NoRestart, IsPrivate);
                    listener.getLogger().println(output);

                    listener.getLogger().println("[env-set] Environment variable setted =======>");
                    listener.getLogger().flush();
                    setResult(true);
                    break;
                case APP_CREATE:
                    listener.getLogger().println("[app-create] Creating application on Tsuru ========>");
                    String[] routerOpts = step.Args.get("routerOpts").split(",");
                    HashMap<String, String> routerOptsMap = new HashMap<>();
                    for (String routerOpt : routerOpts) {
                        kv = routerOpt.split("=");
                        if (kv.length > 1) {
                            routerOptsMap.put(kv[0], kv[1]);
                        }
                    }
                    output = step.apiInstance.appCreate(step.Args.get("appName"), step.Args.get("platform"), step.Args.get("plan"), step.Args.get("teamOwner"), step.Args.get("pool"), step.Args.get("appDescription"), Arrays.asList(step.Args.get("tags").split(",")), step.Args.get("router"), routerOptsMap);
                    listener.getLogger().println(output);

                    listener.getLogger().println("[app-create] Application created =======>");
                    listener.getLogger().flush();
                    setResult(true);
                    break;
                case APP_CLONE:
                    String newAppName = step.Args.get("newAppName").toLowerCase();
                    try {
                        listener.getLogger().println("[app-clone] Retrieving application on Tsuru ========>");
                        Application originalApp = step.apiInstance.appInfo(step.Args.get("appName"));
                        io.tsuru.client.model.EnvVars[] envVars = step.apiInstance.envGet(originalApp.getName(), null);

                        listener.getLogger().println("[app-clone] Creating the cloned application on Tsuru ========>");
                        String plan = originalApp.getPlan().getName().equals("autogenerated") ? null : originalApp.getPlan().getName();
                        Map routerOptsMaps = originalApp.getRouters().get(0).getOpts();
                        output = step.apiInstance.appCreate(newAppName, originalApp.getPlatform(), plan, originalApp.getTeamOwner(),
                                originalApp.getPool(), originalApp.getDescription(), originalApp.getTag(), originalApp.getRouters().get(0).getName(), routerOptsMaps);
                        listener.getLogger().println(output);

                        for (io.tsuru.client.model.EnvVars e : envVars) {
                            if (!e.getName().startsWith("TSURU_")) { // Do no update or add TSURU* related Environment variables
                                output = step.apiInstance.envSet(newAppName, Arrays.asList(e), false, !e.getIsPublic());
                                listener.getLogger().println(output);
                            }
                        }
                    } catch (ApiException e) {
                        listener.getLogger().println(e.toString() + ": " + e.getResponseBody());
                        try {
                            if (e.getCode() != 409) { // Ignore failing due to already existent application
                                // Rollback the new app creation due to some failure.
                                output = step.apiInstance.appRemove(newAppName);
                                listener.getLogger().println(output);
                            } else {
                                setResult(true);
                                break;
                            }
                        } catch (Exception ie) {
                            listener.getLogger().println(ie.toString());
                        }
                        setResult(false);
                        break;
                    }
                    listener.getLogger().println("[app-clone] Application cloned successfully =======>");
                    listener.getLogger().flush();
                    setResult(true);
                    break;
                default:
                    listener.getLogger().println("[tsuru] Jenkins plugin method not implemented.");
            }

            // Return results
            getContext().onSuccess(this);
        }

    }
}
