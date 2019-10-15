package org.jenkinsci.plugins.tsuru

import com.cloudbees.groovy.cps.NonCPS
import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl
import hudson.AbortException
import hudson.EnvVars
import hudson.FilePath
import hudson.Util
import hudson.model.Run
import io.tsuru.client.api.TsuruApi
import io.tsuru.client.model.LoginToken
import org.jenkinsci.plugins.tsuru.pipeline.TsuruAction
import org.jenkinsci.plugins.tsuru.pipeline.TsuruContextInit
import org.jenkinsci.plugins.workflow.cps.EnvActionImpl

import hudson.model.Job;

import java.util.logging.Logger

class TsuruDSL implements Serializable {

    static final Logger LOGGER = Logger.getLogger(TsuruDSL.class.getName());

    private org.jenkinsci.plugins.workflow.cps.CpsScript script;

    private int logLevel = 0; // Modified by calls to openshift.logLevel

    private transient Tsuru.DescriptorImpl config = new Tsuru.DescriptorImpl();

    private transient static HashMap<String, TsuruApi> apiInstance = null;

    private transient Job job;

    Boolean authenticated = false;

    String loginToken;

    public TsuruDSL(org.jenkinsci.plugins.workflow.cps.CpsScript script) {
        this.script = script;

        if (this.apiInstance == null) {
            this.apiInstance = new HashMap<String, TsuruApi>();
        }

        if (this.contexts == null) {
            this.contexts = new HashMap<String, Context>();
        }

        Run<?, ?> build = script.$build();
        if (build == null) {
            throw new IllegalStateException("No associated build");
        }
        this.job = build.getParent();
    }

    public TsuruApi connect() {
        Context localCurrentContext = contexts.get(this.job.getFullName());
        LOGGER.println("Running connect on -> " + localCurrentContext.getServerUrl());
        LOGGER.println("Job Name -> " + this.job.getFullName());
        TsuruApi localApiInstance = this.apiInstance.get(localCurrentContext.getServerUrl());
        if (localApiInstance == null) {
            localApiInstance = new TsuruApi();
            this.apiInstance.put(localCurrentContext.getServerUrl(), localApiInstance);
        }

        String apiUrl = localCurrentContext.getServerUrl();
        if (apiUrl.endsWith("/")) {
            apiUrl = apiUrl.substring(0, apiUrl.length() - 1);
        }
        localApiInstance.getApiClient().setBasePath(apiUrl);
        if (localCurrentContext.getEmail() != null) {
            LoginToken token = localApiInstance.login(localCurrentContext.getEmail(), localCurrentContext.getPassword());
            loginToken = token.getToken();
        } else {
            localApiInstance.getApiClient().addDefaultHeader("Authorization", "bearer " + localCurrentContext.getToken());
        }
        authenticated = true;
        return localApiInstance;
    }

    public Boolean deploy(String appName) {
        return deploy(appName, null);
    }

    public Boolean deploy(String appName, String message) {
        return deploy(appName, message, null);
    }

    public Boolean deploy(String appName, String message, String commit) {
        HashMap<String, String> Param = new HashMap<String, String>();
        appName = appName.toLowerCase();
        Param.put("appName", appName);
        Param.put("message", message);
        Param.put("commit", commit);
        return executeTsuruAction(TsuruAction.Action.DEPLOY, Param);
    }

    public Boolean rollback(String appName, String imageTag) {
        HashMap<String, String> Param = new HashMap<String, String>();
        appName = appName.toLowerCase();
        Param.put("appName", appName);
        Param.put("imageTag", imageTag);
        return executeTsuruAction(TsuruAction.Action.ROLLBACK, Param);
    }

    public Boolean build(String appName, String imageTag) {
        HashMap<String, String> Param = new HashMap<String, String>();
        appName = appName.toLowerCase();
        Param.put("appName", appName);
        Param.put("imageTag", imageTag);
        return executeTsuruAction(TsuruAction.Action.BUILD, Param);
    }

    public Boolean setEnv(String appName, String env, Boolean restartApp, Boolean isPrivate) {
        HashMap<String, String> Param = new HashMap<String, String>();
        appName = appName.toLowerCase();
        Param.put("appName", appName);
        Param.put("env", env);
        Param.put("restartApp", restartApp.toString());
        Param.put("private", isPrivate.toString());
        return executeTsuruAction(TsuruAction.Action.ENV_SET, Param);
    }

    public Boolean getEnv(String appName, String env, String result) {
        HashMap<String, String> Param = new HashMap<String, String>();
        appName = appName.toLowerCase();
        Param.put("appName", appName);
        Param.put("env", env);
        Param.put("result", result);
        return executeTsuruAction(TsuruAction.Action.ENV_GET, Param);
    }

    public Boolean create(String appName, String platform, String appDescription) {
        return create(appName, platform, null, null, null, appDescription, null, null, "");
    }

    public Boolean create(String appName, String platform, String plan, String teamOwner,
                          String pool, String appDescription, String tags, String router,
                          String routerOpts) {
        HashMap<String, String> Param = new HashMap<String, String>();
        appName = appName.toLowerCase();
        Param.put("appName", appName);
        Param.put("platform", platform);
        Param.put("plan", plan);
        Param.put("teamOwner", teamOwner);
        Param.put("pool", pool);
        Param.put("appDescription", appDescription);
        Param.put("tags", tags);
        Param.put("router", router);
        Param.put("routerOpts", routerOpts);
        return executeTsuruAction(TsuruAction.Action.APP_CREATE, Param);
    }

    public String createPRApp(String appName, String prID) {
        HashMap<String, String> Param = new HashMap<String, String>();
        appName = appName.toLowerCase();
        prID = prID.toLowerCase();
        String newAppName = appName + "-" + prID;
        Param.put("appName", appName);
        Param.put("newAppName", newAppName);

        if(executeTsuruAction(TsuruAction.Action.APP_CLONE, Param))
            return newAppName;

        return null;

    }

    public Boolean clone(String appName, String newAppName) {
        HashMap<String, String> Param = new HashMap<String, String>();
        appName = appName.toLowerCase();
        Param.put("appName", appName);
        Param.put("newAppName", newAppName);

        return executeTsuruAction(TsuruAction.Action.APP_CLONE, Param);
    }

    public String GetAuthToken() {
        return loginToken;
    }

    private Boolean executeTsuruAction (TsuruAction.Action action, HashMap<String, String> Param) {
        Context localCurrentContext = contexts.get(this.job.getFullName());
        TsuruApi localApiInstance = this.apiInstance.get(localCurrentContext.getServerUrl());
        def Args = HashMap.newInstance();
        Args.putAll([
                apiInstance: localApiInstance,
                action: action,
                Args: Param
        ]);

        TsuruAction.Execution result = script._TsuruAction(Args);
        return result.result;
    }

    private Context currentContext = null;
    private HashMap<String, Context> contexts = null;

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
        private final TsuruContextInit.Execution exec;

        private String credentialsId;
        private String email = null;
        private String password = null;
        private String token = null;
        private String serverUrl;
        private String application;
        private ContextId id;

        private List<FilePath> destroyOnReturn = new ArrayList<FilePath>();

        protected Context(Context parent, ContextId id) {
            this.@parent = parent;
            this.@id = id;
            this.@exec = script._TsuruContextInit();
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
                UsernamePasswordCredentialsImpl cred = CredentialsProvider.findCredentialById(credentialsId, UsernamePasswordCredentialsImpl.class, script.$build(), Collections.emptyList());
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
                UsernamePasswordCredentialsImpl cred = CredentialsProvider.findCredentialById(credentialsId, UsernamePasswordCredentialsImpl.class, script.$build(), Collections.emptyList());
                if (cred != null) {
                    return cred.getPassword().plainText;
                }
            }
            return this.@password;
        }

        void setToken(String token) {
            this.token = token
        }

        public String getToken() {
            if (this.@credentialsId != null) {
                TsuruCredentials cred = CredentialsProvider.findCredentialById(credentialsId, TsuruCredentialsImpl.class, script.$build(), Collections.emptyList());
                if (cred != null) {
                    return cred.getToken();
                }
            }
            return this.@token;
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
        Context localCurrentContext = contexts.get(this.job.getFullName());
        return localCurrentContext.getApplication();
    }

    public String tsuruApi() {
        Context localCurrentContext = contexts.get(this.job.getFullName());
        return localCurrentContext.getServerUrl();
    }

    public <V> V withAPI(Object tname =null, Object tusername=null, Object tpassword=null, Closure<V> body) {
        String name = toSingleString(tname);
        String credentialId = toSingleString(tusername);
        String username = toSingleString(tusername);
        String password = toSingleString(tpassword);

        node {
            // TODO: Check why it's failing hard.
            //dieIfWithin(ContextId.WITH_API, currentContext, ContextId.WITH_APPLICATION)

            Context context = new Context(null, ContextId.WITH_API);

            TsuruConfig cc = config.getClusterConfig(name);

            if (name == null) {
                // See if a clusterName named "default" has been defined.
                cc = config.getClusterConfig("default");
            }

            if (cc != null) {
                context.setCredentialsId(cc.credentialsId);
                context.setApplication("tsuru-dashboard");
                context.setServerUrl(cc.getServerUrl());
            }

            if (password != null) {
                context.setCredentialsId(null);
                context.setEmail(username);
                context.setPassword(password);
            }

            contexts.put(this.job.getFullName(), context);

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

    private <V> V node(Closure<V> body) {
        if (script.env.NODE_NAME != null) {
            // Already inside a node block.
            body()
        } else {
            script.node {
                body()
            }
        }
    }
}
