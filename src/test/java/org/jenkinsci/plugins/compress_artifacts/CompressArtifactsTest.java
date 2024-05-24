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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.junit.Assert.assertEquals;

import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.remoting.Which;
import hudson.tasks.ArtifactArchiver;
import hudson.util.DescribableList;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import jenkins.model.ArtifactManagerFactory;
import jenkins.model.ArtifactManagerFactoryDescriptor;
import jenkins.model.ArtifactManagerConfiguration;
import jenkins.model.Jenkins;
import jenkins.model.WorkspaceWriter;

import org.apache.commons.io.IOUtils;
import org.hamcrest.Matchers;
import static org.hamcrest.Matchers.is;
import static org.junit.Assume.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.recipes.WithTimeout;

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

    @Test @Issue("JENKINS-26858")
    public void useSpecialCharsInPathName() throws Exception {
        assumeFalse(Functions.isWindows());

        final String filename = "2017-03-21_04:25:03/x:y[z].txt";

        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new WorkspaceWriter(filename, "content"));
        p.getPublishersList().add(new ArtifactArchiver("**/*", null, false));
        FreeStyleBuild build = j.buildAndAssertSuccess(p);

        List<Run<FreeStyleProject, FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();
        assertEquals("number of artifacts archived", 1, artifacts.size());
        Run<FreeStyleProject, FreeStyleBuild>.Artifact artifact = artifacts.get(0);
        assertEquals(filename, artifact.relativePath);
        assertEquals(7, artifact.getFileSize());
    }

    @Test
    public void archiveThousandsFiles() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                FilePath ws = build.getWorkspace();
                FilePath nested = ws.child("there/are/some/subdirs/here");
                nested.mkdirs();

                for (int i = 0; i < 2500; i++) {
                    String name = "file." + Integer.toString(i) + ".txt";
                    ws.child(name).write(name, "UTF-8");
                    nested.child("nested." + name).write(name, "UTF-8");
                }
                return true;
            }
        });
        p.getPublishersList().add(new ArtifactArchiver("**/*", null, false));
        FreeStyleBuild build = j.buildAndAssertSuccess(p);

        List<Run<FreeStyleProject, FreeStyleBuild>.Artifact> artifacts = build.getArtifacts();
        assertEquals("number of artifacts archived", 5000, artifacts.size());

        // read the content
        for (Run<FreeStyleProject, FreeStyleBuild>.Artifact a: artifacts) {
            final InputStream artifactStream = build.getArtifactManager().root().child(a.relativePath).open();
            try {
                assertThat(IOUtils.toString(artifactStream), endsWith("txt"));
            } finally {
                artifactStream.close();
            }
        }
    }

    @Test @Issue("JENKINS-27042") @WithTimeout(0)
    public void archiveLargerThan4GInTotal() throws Exception {
        assumeThat(System.getenv("CRAZY_BIG"), is("OK"));
        FreeStyleProject p = j.createFreeStyleProject();
        final int artifactCount = 700;
        p.getBuildersList().add(new TestBuilder() {
            @Override public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                final FilePath ws = build.getWorkspace();
                final FilePath src = new FilePath(Which.jarFile(Jenkins.class));
                for (int i = 0; i < artifactCount; i++) {
                    final String name = "stuff" + i;
//                    Computer.threadPoolForRemoting.submit(new Callable<Void>() {
//                        public Void call() throws Exception {
                            FilePath artifact = ws.child(name);
                            artifact.copyFrom(src);
//                            return null;
//                        }
//                    });
                }
                return true;
            }
        });
        p.getPublishersList().add(new ArtifactArchiver("**/*", null, false));
        FreeStyleBuild build = j.buildAndAssertSuccess(p);

        final File archiveZip = new File(build.getRootDir(), "archive.zip");
        long length = archiveZip.length();
        assertThat(Functions.humanReadableByteSize(length), length, Matchers.greaterThanOrEqualTo(4L * 1024 * 1024 * 1024));

        assertThat(build.getArtifacts(), Matchers.<Run<FreeStyleProject, FreeStyleBuild>.Artifact>iterableWithSize(artifactCount));
        InputStream open = build.getArtifactManager().root().child("stuff" + (artifactCount - 1)).open();
        try {
            IOUtils.copy(open, new NullOutputStream());
        } finally {
            open.close();
        }


//        CompressingArtifactManager.setup(jenkins);
//
//        FreeStyleJob job = jenkins.jobs.create();
//        job.configure();
//        job.addPublisher(ArtifactArchiver.class).includes("*");
//        if (SystemUtils.IS_OS_UNIX) {
//            job.addBuildStep(ShellBuildStep.class).command( // Generate archive larger than 4G
//                    "#!/bin/bash\nwget $JENKINS_URL/jnlpJars/jenkins-cli.jar -O stuff.jar; for i in {0..7000}; do cp stuff.jar stuff.${i}.jar; done"
//            );
//        }
//        else {
//            // On windows although we could create hard links this would exceed the number of hard links you can create for a given file.
//            // and sparse files compress too well.
//            //job.addBuildStep(BatchCommandBuildStep.class).command("for /l %%x in (0, 1, 7000) do fsutil file createnew stuff.%%x.jar 819200");
//            job.addBuildStep(BatchCommandBuildStep.class).command("powershell -command \"& { " +
//                    "try { " +
//                    "  Invoke-WebRequest %JENKINS_URL%/jnlpJars/jenkins-cli.jar -OutFile stuff.jar " +
//                    "} catch { " +
//                    "  echo 'download of jenkins-cli.jar failed'; " +
//                    "  exit 2 "+
//                    "}}\n " +
//                    "for /l %%x in (0, 1, 7000) do cp stuff.jar stuff.%%x.jar\n");
//
//        }
//        job.save();
//
//        Build build = job.scheduleBuild().waitUntilFinished(15 * 60).shouldSucceed();
//
//        long length = Long.parseLong(jenkins.runScript(
//                "new FilePath(Jenkins.instance.getJob('%s').lastBuild.artifactsDir).parent.child('archive.zip').length()",
//                job.name
//        ));
//        assertThat(length, greaterThanOrEqualTo(4L * 1024 * 1024 * 1024));
    }

    @Test
    public void archiveSingleLargeFile() throws Exception {
        assumeThat(System.getenv("CRAZY_BIG"), is("OK"));
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                FilePath target = build.getWorkspace().child("out");
                long length = 2L * 1024 * 1024 * 1024;
                final FilePath src = new FilePath(Which.jarFile(Jenkins.class));

                final OutputStream out = target.write();
                try {
                    do {
                        IOUtils.copy(src.read(), out);
                    } while (target.length() < length);
                } finally {
                    out.close();
                }
                return true;
            }
        });
        p.getPublishersList().add(new ArtifactArchiver("**/*", null, false));
        FreeStyleBuild build = j.buildAndAssertSuccess(p);
        InputStream out = build.getArtifactManager().root().child("out").open();
        try {
            IOUtils.copy(out, OutputStream.nullOutputStream());
        } finally {
             out.close();
        }
    }

}
