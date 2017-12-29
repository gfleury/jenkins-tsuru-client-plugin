package org.jenkinsci.plugins.tsuru.pipeline;


import groovy.lang.Binding;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jenkinsci.plugins.workflow.cps.GlobalVariable;

import javax.annotation.Nonnull;

/**
 * Defines the "tsuru" global variable in pipeline DSL scripts.
 */
@Extension
public class TsuruGlobalVariable extends GlobalVariable {

    @Nonnull
    @Override
    public String getName() {
        return "tsuru";
    }

    @Nonnull
    @Override
    public Object getValue(@Nonnull CpsScript script) throws Exception {
        Binding binding = script.getBinding();
        script.println();
        Object tsuru;
        if (binding.hasVariable(getName())) {
            tsuru = binding.getVariable(getName());
        } else {
            // Note that if this were a method rather than a constructor, we
            // would need to mark it @NonCPS lest it throw
            // CpsCallableInvocation.
            tsuru = script.getClass().getClassLoader()
                    .loadClass("org.jenkinsci.plugins.tsuru.TsuruDSL")
                    .getConstructor(CpsScript.class).newInstance(script);
            binding.setVariable(getName(), tsuru);
        }
        return tsuru;

    }
}
