package org.jenkinsci.plugins.tsuru.pipeline;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.inject.Inject;

public class TsuruContextInit extends AbstractStepImpl {

    public static final String FUNCTION_NAME = "_TsuruContextInit";

    @DataBoundConstructor
    public TsuruContextInit() {
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
        private transient TsuruContextInit step;

        public TsuruContextInit getStep() {
            return step;
        }

        public void setStep(TsuruContextInit step) {
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

        public FilePath getWorkspaceFilePath() {
            return filePath;
        }

        @Override
        protected Execution run() throws Exception {
            return this;
        }

    }
}
