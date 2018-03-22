package org.jenkinsci.plugins.tsuru;

import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.NameWith;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.util.Secret;

@NameWith(value = TsuruCredentials.NameProvider.class, priority = 1)
public interface TsuruCredentials extends StandardCredentials {
    /** Serial UID from 1.16. */
    long serialVersionUID = -8931505925778535681L;

    String getDisplayName();

    public Secret getSecretKey();

    /**
     * Our name provider.
     */
    public static class NameProvider extends CredentialsNameProvider<TsuruCredentials> {

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getName(@NonNull TsuruCredentials c) {
            String description = Util.fixEmptyAndTrim(c.getDescription());
            return c.getDisplayName() + (description != null ? " (" + description + ")" : "");
        }
    }

}
