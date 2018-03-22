package org.jenkinsci.plugins.tsuru;

import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsScope;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;

public class TsuruCredentialsImpl extends BaseTsuruCredentials implements TsuruCredentials {

    private static final long serialVersionUID = -3167989896315282034L;

    private final Secret secretKey;

    @DataBoundConstructor
    public TsuruCredentialsImpl(@CheckForNull CredentialsScope scope, @CheckForNull String id,
                                @CheckForNull String description, @CheckForNull String secretKey) {
        super(scope, id, description);
        this.secretKey = Secret.fromString(secretKey);
    }

    public Secret getSecretKey() {
        return secretKey;
    }

    public String getToken() {
        return Secret.toString(secretKey);
    }

    public String getDisplayName() {
            return getToken().substring(5);
    }

    @Extension
    public static class DescriptorImpl extends CredentialsDescriptor {

        @Override
        public String getDisplayName() {
            return "Tsuru API Token key credential";
        }

    }
}
