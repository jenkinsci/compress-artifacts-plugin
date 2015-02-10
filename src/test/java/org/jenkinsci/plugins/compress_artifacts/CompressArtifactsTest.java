/*
 * The MIT License
 *
 * Copyright (c) 2015 Red Hat, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.compress_artifacts;

import static org.junit.Assert.assertEquals;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.tasks.ArtifactArchiver;
import hudson.util.DescribableList;

import java.io.IOException;
import java.util.List;

import jenkins.model.ArtifactManagerFactory;
import jenkins.model.ArtifactManagerFactoryDescriptor;
import jenkins.model.ArtifactManagerConfiguration;
import jenkins.model.Jenkins;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

public class CompressArtifactsTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Before
    public void setUp() {
        ArtifactManagerConfiguration amc = Jenkins.getInstance().getInjector().getInstance(ArtifactManagerConfiguration.class);
        DescribableList<ArtifactManagerFactory, ArtifactManagerFactoryDescriptor> factories = amc.getArtifactManagerFactories();
        factories.clear();
        factories.add(new CompressingArtifactManagerFactory());
    }

    @Test
    public void archiveAndRetrieve() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new WorkspaceWriter("file.txt", "content"));
        p.getPublishersList().add(new ArtifactArchiver("file.txt", null, false));
        FreeStyleBuild build = j.buildAndAssertSuccess(p);

        List<Run<FreeStyleProject, FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();
        assertEquals("number of artifacts archived", 1, artifacts.size());
        Run<FreeStyleProject, FreeStyleBuild>.Artifact artifact = artifacts.get(0);
        assertEquals("file.txt", artifact.getFileName());
        assertEquals(7, artifact.getFileSize());
    }

    @Test @Bug(26858)
    public void useSpecialCharsInPathName() throws Exception {
        final String filename = "x:y[z].txt";

        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new WorkspaceWriter(filename, "content"));
        p.getPublishersList().add(new ArtifactArchiver("*", null, false));
        FreeStyleBuild build = j.buildAndAssertSuccess(p);

        List<Run<FreeStyleProject, FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();
        assertEquals("number of artifacts archived", 1, artifacts.size());
        Run<FreeStyleProject, FreeStyleBuild>.Artifact artifact = artifacts.get(0);
        assertEquals(filename, artifact.getFileName());
        assertEquals(7, artifact.getFileSize());
    }

    // Stolen from https://github.com/jenkinsci/jenkins/commit/cb5845db29bea10afd26c4425a44bc569ee75a7a
    // TODO delete when available in core version plugin depends on
    private final class WorkspaceWriter extends TestBuilder {

        private final String path;
        private final String content;

        public WorkspaceWriter(String path, String content) {
            this.path = path;
            this.content = content;
        }

        @Override
        public boolean perform(
                AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener
        ) throws InterruptedException, IOException {
            build.getWorkspace().child(path).write(content, "UTF-8");
            return true;
        }
    }
}
