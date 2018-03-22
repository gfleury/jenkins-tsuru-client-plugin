package org.jenkinsci.plugins.tsuru.utils;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class JGit {

    private Git git;

    public JGit(File repository) throws IOException {
        this.git = Git.open(repository);
    }

    public String[] getLog(int size) throws GitAPIException {

        Iterable<RevCommit> logs = this.git.log().setMaxCount(size).call();

        String[] messages = new String[size];
        int i = 0;

        for (RevCommit rev: logs) {
            messages[i++] = rev.getFullMessage();
        }

        return messages;
    }

    public void close() {
        this.git.close();
    }

}
