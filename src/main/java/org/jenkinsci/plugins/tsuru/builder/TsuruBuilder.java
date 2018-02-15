package org.jenkinsci.plugins.tsuru.builder;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.common.base.Strings;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.tsuru.Tsuru;
import org.jenkinsci.plugins.tsuru.TsuruConfig;
import org.jenkinsci.plugins.tsuru.TsuruCredentials;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


public class TsuruBuilder extends Builder {

    public static final String DEFAULT_LOGLEVEL = "0";

    private String clusterName;

    private String project;

    private String credentialsId;

    private String logLevel = DEFAULT_LOGLEVEL;

    @DataBoundSetter
    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getClusterName() {
        return clusterName;
    }

    public String getClusterName(Map<String, String> overrides) {
        return getOverride(getClusterName(), overrides);
    }

    @DataBoundSetter
    public void setProject(String project) {
        this.project = project;
    }

    public String getProject() {
        return project;
    }

    public String getProject(Map<String, String> overrides) {
        return getOverride(getProject(), overrides);
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getCredentialsId(Map<String, String> overrides) {
        return getOverride(getCredentialsId(), overrides);
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public String getLogLevel(Map<String, String> overrides) {
        return getOverride(getLogLevel(), overrides);
    }

    protected boolean isVerbose() {
        if (Strings.isNullOrEmpty(logLevel)) {
            return false;
        }
        try {
            return (Integer.parseInt(logLevel) > 0);
        } catch (Throwable t) {
            return false;
        }
    }

    @DataBoundSetter
    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    protected TsuruConfig getCluster(Map<String, String> overrides) {
        return (new Tsuru.DescriptorImpl()).getClusterConfig(getClusterName(overrides));
    }

    protected boolean runOcCommand(final AbstractBuild build,
                                   final TaskListener listener, final String verb,
                                   final List verbArgs, final List userArgs, final List options,
                                   final OcProcessRunner runner)
            throws IOException, InterruptedException {
        final Map<String, String> overrides = consolidateEnvVars(listener, build, null);
        TsuruConfig c = getCluster(overrides);
        final String server, project, token, caContent;
        String selectedCAPath = "";
        boolean shouldSkipTLSVerify = false;

        if (c == null) { // if null, we assume the cluster is running the
            // Jenkins node.
            server = "localhost";
        } else {
            server = c.getServerUrl();
        }

        if (Strings.isNullOrEmpty(getProject(overrides))) { // No project was provided
            // for this step
            if (c != null) { // But a cluster definition was provided
                project = c.getDefaultApplication();
                if (Strings.isNullOrEmpty(project)) {
                    throw new IOException(
                            "No project defined in step or in cluster: "
                                    + getClusterName(overrides));
                }
            } else {
                project = "tsuru-dashboard";
            }
        } else {
            project = this.getProject(overrides);
        }

        String actualCredentialsId = getCredentialsId(overrides);
        if (Strings.isNullOrEmpty(actualCredentialsId)) { // No credential
            // information was
            // provided for this
            // step.
            if (c != null) { // But a cluster definition was found
                actualCredentialsId = c.getCredentialsId();
                if (Strings.isNullOrEmpty(actualCredentialsId)) {
                    throw new IOException(
                            "No credentials defined in step or in cluster: "
                                    + getClusterName(overrides));
                }
            }
        }

        if (!Strings.isNullOrEmpty(actualCredentialsId)) {
            TsuruCredentials tokenSecret = CredentialsProvider
                    .findCredentialById(actualCredentialsId,
                            TsuruCredentials.class, build,
                            new ArrayList<DomainRequirement>());
            if (tokenSecret == null) {
                throw new IOException(
                        "Unable to find credential in Jenkins credential store: "
                                + actualCredentialsId);
            }
            token = tokenSecret.getUsername();
        }

        final String finalSelectedCAPath = selectedCAPath;
        final boolean finalShouldSkipTLSVerify = shouldSkipTLSVerify;
        return true;

    }

    protected boolean standardRunOcCommand(final AbstractBuild build,
                                           final TaskListener listener, String verb, List verbArgs,
                                           List userArgs, List options)
            throws IOException, InterruptedException {
        return runOcCommand(build, listener, verb, verbArgs, userArgs, options,
                new OcProcessRunner() {
                    @Override
                    public boolean perform(ProcessBuilder pb)
                            throws IOException, InterruptedException {
                        pb.redirectErrorStream(true); // Merge stdout & stderr
                        Process process = pb.start();
                        // stream for combined stdout & stderr
                        final InputStream output = process.getInputStream();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                byte buffer[] = new byte[1024];
                                int count;
                                try {
                                    while ((count = output.read(buffer)) != -1) {
                                        listener.getLogger().write(buffer, 0,
                                                count);
                                    }
                                } catch (Exception e) {
                                    listener.error("Error streaming process output");
                                    e.printStackTrace(listener.getLogger());
                                }
                            }
                        }).start();

                        int status = process.waitFor();
                        if (status != 0) {
                            listener.getLogger().println(
                                    "Client tool terminated with status: "
                                            + status);
                            return false;
                        }
                        return true;
                    }
                });
    }

    // borrowed from openshift pipeline plugin
    protected Map<String, String> consolidateEnvVars(TaskListener listener,
                                                     AbstractBuild<?, ?> build,
                                                     Launcher launcher) {
        // EnvVars extends TreeMap
        TreeMap<String, String> overrides = new TreeMap<String, String>();
        // merge from all potential sources
        if (build != null) {
            try {
                EnvVars buildEnv = build.getEnvironment(listener);
                if (isVerbose())
                    listener.getLogger()
                            .println("build env vars:  " + buildEnv);
                overrides.putAll(buildEnv);
            } catch (IOException | InterruptedException e) {
                if (isVerbose())
                    e.printStackTrace(listener.getLogger());
            }
        }

        try {
            EnvVars computerEnv = null;
            Computer computer = Computer.currentComputer();
            if (computer != null) {
                computerEnv = computer.getEnvironment();
            } else {
                if (launcher != null) {
                    computer = launcher.getComputer();
                }
                if (computer != null) {
                    computerEnv = computer.getEnvironment();
                }
            }
            if (isVerbose())
                listener.getLogger().println(
                        "computer env vars:  " + computerEnv);
            if (computerEnv != null)
                overrides.putAll(computerEnv);
        } catch (IOException | InterruptedException e2) {
            if (isVerbose())
                e2.printStackTrace(listener.getLogger());
        }

        return overrides;
    }

    // borrowed from openshift pipeline plugin
    public static String pruneKey(String key) {
        if (key == null)
            key = "";
        if (key.startsWith("$"))
            return key.substring(1, key.length()).trim();
        return key.trim();
    }

    // borrowed from openshift pipeline plugin
    public static String getOverride(String key, Map<String, String> overrides) {
        String val = pruneKey(key);
        // try override when the key is the entire parameter ... we don't just
        // use
        // replaceMacro cause we also support PARM with $ or ${}
        if (overrides != null && overrides.containsKey(val)) {
            val = overrides.get(val);
        } else {
            // see if it is a mix used key (i.e. myapp-${VERSION}) or ${val}
            String tmp = hudson.Util.replaceMacro(key, overrides);
            if (tmp != null && tmp.length() > 0)
                val = tmp;
        }
        return val;
    }

    public static abstract class TsuruBuilderDescriptor extends
            BuildStepDescriptor<Builder> {

        TsuruBuilderDescriptor() {
            load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of
            // projectForStep types
            return true;
        }

        public ListBoxModel doFillClusterNameItems() {
            ListBoxModel items = new ListBoxModel();
            List<TsuruConfig> clusters = (new Tsuru.DescriptorImpl())
                    .getClusterConfigs();
            for (TsuruConfig c : clusters) {
                items.add(c.getName(), c.getName());
            }
            items.add("<Cluster Running Jenkins Node>", "");
            return items;
        }

        public ListBoxModel doFillCredentialsIdItems(
                @QueryParameter String credentialsId) {
            return TsuruConfig.doFillCredentialsIdItems(credentialsId);
        }

        public ListBoxModel doFillLogLevelItems() {
            ListBoxModel items = new ListBoxModel();
            items.add("0 - Minimum Logging", "0");
            for (int i = 1; i < 10; i++) {
                items.add("" + i, "" + i);
            }
            items.add("10 - Maximum Logging", "10");
            return items;
        }

    }

    protected static ArrayList<String> toList(String... entries) {
        ArrayList<String> list = new ArrayList<>(entries.length);
        for (String s : entries) {
            list.add(s);
        }
        return list;
    }

    public static boolean withTempInput(String prefix, String content,
                                        WithTempInputRunnable runnable) throws IOException,
            InterruptedException {
        Path tmp = null;
        try {
            if (content != null) {
                tmp = Files.createTempFile(prefix, ".tmp");
                ArrayList<String> list = new ArrayList<String>(1);
                list.add(content);
                Files.write(tmp, list, StandardCharsets.UTF_8,
                        StandardOpenOption.WRITE);
            }
            return runnable.perform((tmp == null) ? null : tmp.toAbsolutePath()
                    .toString());
        } finally {
            if (tmp != null) {
                Files.delete(tmp);
            }
        }
    }

    protected interface WithTempInputRunnable {
        boolean perform(String filename) throws IOException,
                InterruptedException;
    }

    protected interface OcProcessRunner {
        boolean perform(ProcessBuilder pb) throws IOException,
                InterruptedException;
    }

}
