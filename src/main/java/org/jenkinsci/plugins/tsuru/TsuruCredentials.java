package org.jenkinsci.plugins.tsuru;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.Extension;
import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;

public class TsuruCredentials extends UsernamePasswordCredentialsImpl {


    @DataBoundConstructor
    public TsuruCredentials(CredentialsScope scope, String id,
                            String description, String username, String password) {
        super(scope, id, description, username, password);
    }

    @Extension
    public static class DescriptorImpl extends
            BaseStandardCredentialsDescriptor {
        @Override
        public String getDisplayName() {
            return "Tsuru credentials for Tsuru Client Plugin";
        }
    }

}