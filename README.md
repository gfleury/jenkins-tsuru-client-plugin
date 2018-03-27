# jenkins-tsuru-client-plugin

This project provides access to Tsuru API from Jenkins through pipeline DSL.

NOTE: This plugin does NOT require the tsuru-client binary be present.

Based on https://github.com/openshift/jenkins-plugin

You will need https://github.com/gfleury/tsuru-client-java

Example of Jenkinsfile creating one Application per pull request and later after merging the PR deploying on integration:

```
pipeline {

  agent any

  stages {

    stage('Create and Deploy PR integration App') {

      when {
        allOf {
            not {
                branch 'master'
            }
            expression {
                return env.BRANCH_NAME.startsWith("PR-")
            }
            expression {
                return env.CHANGE_TARGET.equals("integration")
            }
        }
      }
      steps {
        script {
            tsuru.withAPI('tsuru-integration') {
                tsuru.connect()
                appName = tsuru.createPRApp(env.JOB_NAME.tokenize('/')[1], env.BRANCH_NAME)
                tsuru.deploy(appName, createDeployMessage(env))
            }
        }
      }

    }

    stage('Deploying Integration') {
      when {
        branch 'integration'
      }
      steps {
        script {
            tsuru.withAPI('tsuru-integration') {
                appName = env.JOB_NAME.tokenize('/')[1]
                tsuru.connect()
                tsuru.deploy(appName, createDeployMessage(env))
            }
        }
      }

    }


    }

  }

  post {
    success  {

    }
  }
}
```