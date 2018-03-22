package org.jenkinsci.plugins.tsuru;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;

public abstract class BaseTsuruCredentials extends BaseStandardCredentials
        implements TsuruCredentials {
    public BaseTsuruCredentials(
            @CheckForNull CredentialsScope scope,
            @CheckForNull String id, @CheckForNull String description) {
        super(scope, id, description);
    }
}
