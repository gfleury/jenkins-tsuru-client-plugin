package org.jenkinsci.plugins.tsuru;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.io.Serializable;


public class TsuruConfig extends AbstractDescribableImpl<TsuruConfig> implements Serializable {

    // Human readable name for cluster. Used in drop down lists.
    private String name;

    // API server URL for the cluster.
    private String serverUrl;

    // If this cluster is reference, what project to assume, if any.
    private String defaultApplication;

    private String credentialsId;

    @DataBoundConstructor
    public TsuruConfig(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    @DataBoundSetter
    public void setServerUrl(String serverUrl) {
        this.serverUrl = Util.fixEmptyAndTrim(serverUrl);
    }

    public String getDefaultApplication() {
        return defaultApplication;
    }

    @DataBoundSetter
    public void setDefaultApplication(String defaultApplication) {
        this.defaultApplication = Util.fixEmptyAndTrim(defaultApplication);
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = Util.fixEmptyAndTrim(credentialsId);
    }

    @Override
    public String toString() {
        return String.format("Tsuru cluster [name:%s] [serverUrl:%s]",
                name, serverUrl);
    }

    // https://wiki.jenkins-ci.org/display/JENKINS/Credentials+Plugin
    // http://javadoc.jenkins-ci.org/credentials/com/cloudbees/plugins/credentials/common/AbstractIdCredentialsListBoxModel.html
    // https://github.com/jenkinsci/kubernetes-plugin/blob/master/src/main/java/org/csanchez/jenkins/plugins/kubernetes/KubernetesCloud.java
    public static ListBoxModel doFillCredentialsIdItems(String credentialsId) {
        if (credentialsId == null) {
            credentialsId = "";
        }

        if (!Jenkins.getInstance().hasPermission(Jenkins.ADMINISTER)) {
            // Important! Otherwise you expose credentials metadata to random
            // web requests.
            return new StandardListBoxModel()
                    .includeCurrentValue(credentialsId);
        }

        return new StandardListBoxModel()
                .includeEmptyValue()
                .includeAs(ACL.SYSTEM, Jenkins.getInstance(),
                        TsuruCredentials.class)
                // .includeAs(ACL.SYSTEM, Jenkins.getInstance(),
                // StandardUsernamePasswordCredentials.class)
                // .includeAs(ACL.SYSTEM, Jenkins.getInstance(),
                // StandardCertificateCredentials.class)
                // TODO: Make own type for token or use the existing token
                // generator auth type used by sync plugin? or kubernetes?
                .includeCurrentValue(credentialsId);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<TsuruConfig> {

        @Override
        public String getDisplayName() {
            return "Tsuru Endpoint";
        }

        public FormValidation doCheckName(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckServerUrl(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public ListBoxModel doFillCredentialsIdItems(
                @QueryParameter String credentialsId) {
            // It is valid to choose no default credential, so enable
            // 'includeEmpty'
            return TsuruConfig.doFillCredentialsIdItems(credentialsId);
        }

    }
}
