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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import jenkins.util.VirtualFile;
import hudson.FilePath;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import de.schlichtherle.truezip.file.TArchiveDetector;
import de.schlichtherle.truezip.file.TFile;
import de.schlichtherle.truezip.file.TVFS;

/**
 * For historical reasons several implementations/configurations was used to create
 * archive. Ensure that later versions can read archives produced by earlier versions.
 *
 * 1.0 - 1.4: org.apache.tools.zip via FilePath#zip (Zip64)
 * 1.5      : de.schlichtherle.truezip ZipDriver (Zip64 with IBM437 entry name encoding)
 * 1.6 - ...: de.schlichtherle.truezip JarDriver (Zip64 with UTF-8 entry name encoding)
 *
 * @author ogondza
 */
public class CompressionInteropTest {

    @Rule public TemporaryFolder src = new TemporaryFolder();
    private final Map<String, String> artifacts = new HashMap<String, String>();

    @Before
    public void setUp() throws Exception {
        FileUtils.writeStringToFile(new File(src.getRoot(), "file"), "content");

        File subdir = new File(src.getRoot(), "dir");
        subdir.mkdirs();

        FileUtils.writeStringToFile(new File(subdir, "nested"), "inner_content");

        artifacts.put("file", "file");
        artifacts.put("dir/nested", "dir/nested");
    }

    private void check(File dst) throws Exception {
        VirtualFile archive = ZipStorage.root(dst);

        assertEquals(2, archive.list().length);
        assertTrue(archive.child("file").isFile());

        VirtualFile dir = archive.child("dir");
        assertTrue(dir.isDirectory());
        assertEquals(1, dir.list().length);
        assertTrue(dir.child("nested").isFile());
    }

    @Test
    public void apacheZip() throws Exception {
        File dst = Files.createTempFile(getClass().getName(), "apacheZip").toFile();
        final FileOutputStream fos = new FileOutputStream(dst);
        try {
            new FilePath(src.getRoot()).zip(fos, new FilePath.ExplicitlySpecifiedDirScanner(artifacts));
        } finally {
            fos.close();
        }

        check(dst);
    }

    @Test
    public void trueZipIbm437() throws Exception {
        File archive = Files.createTempFile(getClass().getName(), "trueZip").toFile();
        File tempArchive = new File(archive.getAbsolutePath() + ".writing.zip");

        TFile zip = new TFile(tempArchive, new TArchiveDetector("zip"));
        zip.mkdir(); // Create new archive file
        for (Entry<String, String> afs: artifacts.entrySet()) {
            File src = new File(this.src.getRoot(), afs.getKey());
            TFile dst = new TFile(zip, afs.getValue(), TArchiveDetector.NULL);
            if (src.isDirectory()) {
                dst.mkdirs();
            } else {
                final FileInputStream in = new FileInputStream(src);
                TFile.cp(in, dst);
            }
        }
        TVFS.umount(zip);
        tempArchive.renameTo(archive);
    }
}
