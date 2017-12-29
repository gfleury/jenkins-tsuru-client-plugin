package org.jenkinsci.plugins.tsuru

import com.cloudbees.groovy.cps.NonCPS
import com.cloudbees.plugins.credentials.CredentialsProvider
import hudson.AbortException
import hudson.FilePath
import hudson.Util
import io.tsuru.client.api.TsuruApi
import io.tsuru.client.model.LoginToken

import java.util.logging.Logger

class TsuruDSL implements Serializable {

    static final Logger LOGGER = Logger.getLogger(TsuruDSL.class.getName());

    private org.jenkinsci.plugins.workflow.cps.CpsScript script;

    private int logLevel = 0; // Modified by calls to openshift.logLevel

    private transient Tsuru.DescriptorImpl config = new Tsuru.DescriptorImpl();

    public TsuruDSL(org.jenkinsci.plugins.workflow.cps.CpsScript script) {
        this.script = script;
    }

    public TsuruApi connect() {
        TsuruApi apiInstance = new TsuruApi();
        apiInstance.getApiClient().setBasePath(currentContext.getServerUrl());
        LoginToken token = apiInstance.login(currentContext.g, password);
        return apiInstance;
    }

    private Context currentContext = null;

    enum ContextId implements Serializable{
        WITH_API("tsuru.withAPI"), WITH_APPLICATION("tsuru.withApplication")
        private final String name;
        ContextId(String name) {
            this.@name = name;
        }
        public String toString() {
            return name;
        }
    }

    private class Context implements Serializable {

        protected final Context parent;

        private String credentialsId;
        private String email = null;
        private String password = null;
        private String serverUrl;
        private String application;
        private ContextId id;

        private List<FilePath> destroyOnReturn = new ArrayList<FilePath>();

        protected Context(Context parent, ContextId id) {
            this.@parent = parent;
            this.@id = id;
        }

        public <V> V run(Closure<V> body) {
            if (destroyOnReturn == null) {
                throw new IllegalStateException(this.getClass() + " has already been perform once and cannot be used again");
            }
            Context lastContext = currentContext;
            currentContext = this;
            try {
                return body()
            } finally {
                currentContext = lastContext;
                destroyOnReturn.each{ fp -> fp.delete() }
                destroyOnReturn = null;
            }
        }

        public ContextId getContextId() {
            return this.@id;
        }

        public String getApplication() {
            return "";
        }

        public void setApplication(String application) {
            this.@application = Util.fixEmptyAndTrim(application);
        }

        public String getServerUrl() {
            if (this.@serverUrl != null) {
                return this.@serverUrl;
            }
            if (parent != null) {
                return parent.getServerUrl();
            }
            return "";
        }

        public void setServerUrl(String serverUrl) {
            this.@serverUrl = Util.fixEmptyAndTrim(serverUrl);
        }

        public String getEmail() {
            if (this.@credentialsId != null) {
                TsuruCredentials cred = CredentialsProvider.findCredentialById(credentialsId, TsuruCredentials.class, script.$build(), Collections.emptyList());
                if (cred != null) {
                    return cred.getUsername();
                }
            }
            return this.@email;
        }

        void setEmail(String email) {
            this.email = email
        }

        void setPassword(String password) {
            this.password = password
        }

        public String getPassword() {
            if (this.@credentialsId != null) {
                TsuruCredentials cred = CredentialsProvider.findCredentialById(credentialsId, TsuruCredentials.class, script.$build(), Collections.emptyList());
                if (cred != null) {
                    return cred.getPassword();
                }
            }
            return this.@password;
        }

        public void setCredentialsId(String credentialsId) {
            this.@credentialsId = Util.fixEmptyAndTrim(credentialsId);
        }
    }

    /**
     * Returns true if the test context identifier is found within the context
     */
    private boolean contextContains(Context context, ContextId test) {
        while (context != null) {
            if (context.getContextId() == test) {
                return true;
            }
            context = context.parent;
        }
        return false;
    }

    @NonCPS
    private void dieIfWithin(ContextId me, Context context, ContextId... forbidden) throws AbortException {
        for (ContextId forbid : forbidden) {
            if (contextContains(context, forbid)) {
                throw new AbortException(me.toString() + " cannot be used within a " + forbid.toString() + " closure body");
            }
        }
    }

    @NonCPS
    private void dieIfWithout(ContextId me, Context context, ContextId required) throws AbortException {
        if (contextContains(context, required)) {
            throw new AbortException(me.toString() + " can only be used within a " + required.toString() + " closure body");
        }
    }


    public void failUnless(b) {
        b = (new Boolean(b)).booleanValue();
        if (!b) {
            // error is a Jenkins workflow-basic-step
            error("Assertion failed")
        }
    }

    public String application() {
        return currentContext.getApplication();
    }

    public String tsuruApi() {
        return currentContext.getServerUrl();
    }

    public <V> V withAPI(Object oname=null, Object ousername=null, Object opassword=null, Closure<V> body) {
        String name = toSingleString(oname);
        String credentialId = toSingleString(ousername);
        String username = toSingleString(ousername);
        String password = toSingleString(opassword);

        node {

            dieIfWithin(ContextId.WITH_API, currentContext, ContextId.WITH_APPLICATION)

            Context context = new Context(null, ContextId.WITH_API);

            TsuruConfig cc = config.getClusterConfig(name);

            if (name == null) {
                // See if a clusterName named "default" has been defined.
                cc = config.getClusterConfig("default");
            }

            if (cc != null) {
                context.setCredentialsId(cc.credentialsId);
                context.setApplication(cc.defaultApplication);
                context.setServerUrl(cc.getServerUrl());
            }

            if (password != null) {
                context.setCredentialsId(null);
                context.setEmail(username);
                context.setPassword(password);
            }

            context.run {
                body()
            }
        }

    }

    public <V> V withApplication(Object oapplicationName=null, Closure<V> body) {
        String applicationName = toSingleString(oapplicationName);
        dieIfWithout(ContextId.WITH_APPLICATION, currentContext, ContextId.WITH_API)
        Context context = new Context(currentContext, ContextId.WITH_APPLICATION);
        context.setApplication(applicationName);
        return context.run {
            body()
        }
    }

    public void logLevel(int v) {
        this.@logLevel = v;
    }

    /**
     * API calls with String parameters can receive normal Java strings
     * or gstrings. In the DSl/groovy, gstrings are defined by using double quotes and
     * include some interpolation. Methods within the API should
     * accept either. To this end, accept any type of object and turn
     * it into a string.
     */
    @NonCPS
    private static String toSingleString(Object o) {
        if (o == null) {
            return null;
        }
        return o.toString(); // convert from gstring if necessary
    }

    /**
     * See details in toSingleString for rationale.
     */
    @NonCPS
    private static String[] toStringArray(Object[] args) {
        if (args == null) {
            return new String[0];
        }
        // Unpack a Groovy list as if it were an Array
        // Enables openshift.run([ 'x', 'y' ])
        if (args.length == 1 && args[0] instanceof List) {
            args = ((List)args[0]).toArray();
        }
        String[] o = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            o[i] = args[i].toString();
        }
        return o;
    }

    /**
     * See details in toSingleString for rationale.
     */
    @NonCPS
    private static ArrayList<String> toStringList(List<Object> objects) {
        ArrayList l = new ArrayList<String>();
        if (objects != null) {
            for (int i = 0; i < objects.size(); i++) {
                l.add(objects.get(i).toString());
            }
        }
        return l;
    }


}
