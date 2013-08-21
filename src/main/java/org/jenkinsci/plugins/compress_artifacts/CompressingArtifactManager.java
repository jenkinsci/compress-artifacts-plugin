/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.Run;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import jenkins.model.ArtifactManager;

final class CompressingArtifactManager extends ArtifactManager {

    private transient Run<?,?> build;

    CompressingArtifactManager(Run<?,?> build) {
        onLoad(build);
    }

    @Override public void onLoad(Run<?,?> build) {
        this.build = build;
    }

    @Override public void archive(FilePath workspace, Launcher launcher, BuildListener listener, Map<String,String> artifacts) throws IOException, InterruptedException {
        // TODO support updating entries
        OutputStream os = new FileOutputStream(archive());
        try {
            workspace.zip(os, new FilePath.ExplicitlySpecifiedDirScanner(artifacts));
        } finally {
            os.close();
        }
    }

    @Override public boolean deleteArtifacts() throws IOException, InterruptedException {
        return archive().delete();
    }

    @Override public Object browseArtifacts() {
        throw new UnsupportedOperationException(); // TODO
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override public Run.ArtifactList getArtifactsUpTo(int upTo) {
        Map<String,Long> children = new TreeMap<String,Long>(String.CASE_INSENSITIVE_ORDER);
        File archive = archive();
        if (archive.isFile()) {
            try {
                ZipFile zf = new ZipFile(archive);
                try {
                    Enumeration<? extends ZipEntry> entries = zf.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        if (entry.isDirectory()) {
                            continue;
                        }
                        children.put(entry.getName(), entry.getSize());
                    }
                } finally {
                    zf.close();
                }
            } catch (IOException x) {
                Logger.getLogger(CompressingArtifactManager.class.getName()).log(Level.WARNING, null, x);
            }
        }
        Run.ArtifactList r = build.new ArtifactList();
        int n = 0;
        for (Map.Entry<String,Long> e : children.entrySet()) {
            String path = e.getKey();
            // TODO build tree and collapse single items into parent node (cf. StandardArtifactManager; ArtifactList probably needs a helper API taking Enumeration<Map.Entry<String,Long>> files)
            StringBuilder b = new StringBuilder();
            for (String piece : path.split("/")) {
                if (b.length() > 0) {
                    b.append('/');
                }
                b.append(Util.rawEncode(piece));
            }
            r.add(build.new Artifact(path, path, b.toString(), Long.toString(e.getValue()), "n" + n));
            if (++n >= upTo) {
                break;
            }
        }
        r.computeDisplayName();
        return r;
    }

    @Override public InputStream loadArtifact(String artifact) throws IOException {
        final ZipFile zf = new ZipFile(archive());
        ZipEntry entry = zf.getEntry(artifact);
        if (entry == null) {
            zf.close();
            throw new FileNotFoundException(artifact);
        }
        return new FilterInputStream(zf.getInputStream(entry)) {
            @Override public void close() throws IOException {
                zf.close();
            }
        };
    }

    private File archive() {
        return new File(build.getRootDir(), "archive.zip");
    }

}
