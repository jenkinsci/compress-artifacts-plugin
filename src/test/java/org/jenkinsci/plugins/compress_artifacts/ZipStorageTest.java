/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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
import hudson.model.BuildListener;
import hudson.model.StreamBuildListener;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import jenkins.util.VirtualFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ZipStorageTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private File archive;
    private File content;
    private VirtualFile zs;
    private VirtualFile canonical;

    @Before public void samples() throws Exception {
        archive = new File(tmp.getRoot(), "archive.zip");
        zs = ZipStorage.root(archive);
        content = tmp.newFolder();
        canonical = VirtualFile.forFile(content);
    }

    @Test public void basics() throws Exception {
        FileUtils.writeStringToFile(new File(content, "top"), "top");
        File dirF = new File(content, "dir");
        assertTrue(dirF.mkdir());
        FileUtils.writeStringToFile(new File(dirF, "sub"), "sub");
        new File(content, "dir/nested").mkdir();

        Map<String,String> artifacts = new HashMap<String,String>();
        artifacts.put("top", "top");
        artifacts.put("dir/sub", "dir/sub");
        artifacts.put("dir/nested", "dir/nested");

        archive(artifacts);

        doBasics(canonical);
        doBasics(zs);

        // This is broken in VirtualFile#FileVF
        assertEquals(null, zs.getParent());
    }

    private void doBasics(VirtualFile vf) throws Exception {
        assertTrue(vf.isDirectory());
        assertTrue(vf.exists());
        vf.toURI(); // No Exception

        VirtualFile[] list = vf.list();
        Arrays.sort(list);
        assertEquals(2, list.length);
        VirtualFile dir = list[0];
        assertEquals("dir", dir.getName());
        assertTrue(dir.isDirectory());
        assertFalse(dir.isFile());
        vf.toURI(); // No Exception
        assertFalse(vf.child("dir").isFile());
        assertTrue(dir.exists());
        assertEquals(vf, dir.getParent());

        VirtualFile top = list[1];
        assertEquals("top", top.getName());
        assertFalse(top.isDirectory());
        assertTrue(top.isFile());
        assertTrue(top.exists());
        assertEquals(vf, top.getParent());
        assertEquals("top", read(top));

        list = dir.list();
        Arrays.sort(list);
        assertEquals(2, list.length);
        VirtualFile sub = list[1];
        assertEquals("sub", sub.getName());
        assertFalse(sub.isDirectory());
        assertTrue(sub.isFile());
        assertTrue(sub.exists());
        assertEquals(dir, sub.getParent());
        assertEquals("sub", read(sub));
        assertEquals(sub, vf.child("dir/sub"));

        VirtualFile nested = list[0];
        assertTrue(nested.isDirectory());
        assertFalse(nested.isFile());
        assertTrue(nested.exists());
        assertEquals(dir, nested.getParent());

        final VirtualFile doesNotExist = vf.child("there_is_no_such_child");
        assertFalse(doesNotExist.isFile());
        assertFalse(doesNotExist.exists());
    }

    private String read(VirtualFile vf) throws IOException {
        InputStream open = vf.open();
        try {
            return IOUtils.toString(open);
        } finally {
            open.close();
        }
    }
    
    @Deprecated
    @Test public void globList() throws Exception {
        FileUtils.writeStringToFile(new File(content, "top"), "top");
        File dir1 = new File(content, "folder1");
        assertTrue(dir1.mkdir());
        FileUtils.writeStringToFile(new File(dir1, "file1.txt"), "file1Content");
        
        File dir2 = new File(content,"folder2");
        assertTrue(dir2.mkdir());
        FileUtils.writeStringToFile(new File(dir2,"file2.log"), "file2Content");
        
        Map<String,String> artifacts = new HashMap<String,String>();
        artifacts.put("top", "top");
        artifacts.put("folder1/file1.txt", "folder1/file1.txt");
        artifacts.put("folder2/file2.log", "folder2/file2.log");
        archive(artifacts);
        
        doGlobList(canonical);
        doGlobList(zs);
    }
    @Deprecated
	private void doGlobList(VirtualFile vf) throws IOException {
        assertGlobList(vf, "**", "top", "folder1/file1.txt", "folder2/file2.log");
        assertGlobList(vf, "**/*.log", "folder2/file2.log");
        assertGlobList(vf, "folder*/*", "folder1/file1.txt", "folder2/file2.log");
        VirtualFile child = vf.child("folder1");
        assertGlobList(child, "**", "file1.txt");
        assertGlobList(child, "*log");
        assertGlobList(child, "*.txt", "file1.txt");
	}
    @Deprecated
    private static void assertGlobList(VirtualFile vf, String glob, String... expected) throws IOException {
        Arrays.sort(expected);
        String[] actual = vf.list(glob);
        Arrays.sort(actual);
        assertEquals(Arrays.asList(expected), Arrays.asList(actual));
    }

    @Test public void readError() throws Exception {
        new File(content, "dir").mkdir();
        Map<String,String> artifacts = new HashMap<String,String>();
        artifacts.put("dir", "dir");
        archive(artifacts);

        doReadDir(canonical);
        doReadDir(zs);

        doReadNonexistingDir(canonical);
        doReadNonexistingDir(zs);
    }
    private void doReadNonexistingDir(VirtualFile vf) {
        try {
            VirtualFile child = vf.child("there_is_none");
            fail("expected " + child + " to not exist but got: " + IOUtils.toString(child.open()));
        } catch (IOException ex) {
            // good
        }
    }
    private void doReadDir(VirtualFile vf) throws IOException {
        try {
            VirtualFile child = vf.list()[0];
            fail("expected " + child + " to be a directory but got: " + IOUtils.toString(child.open()));
        } catch (IOException ex) {
            // good
        }
    }

    @Test public void operateOnNonexistingFiles() throws Exception {
        archive(new HashMap<String,String>());

        doRead(canonical);
        doRead(zs);
    }
    private void doRead(VirtualFile vf) throws Exception {
        assertEquals(0, vf.child("thre_is_none").lastModified());
        assertEquals(0, vf.child("thre_is_none").length());
    }

    @Test
    public void avoidZipExceptionWhileWriting() throws Exception {
        FileUtils.writeStringToFile(new File(content, "file"), "content");

        final Entry<String, String> validArtifact = Collections.singletonMap("file", "file").entrySet().iterator().next();

        // Simulate archiving that takes forever serving valid artifact and then block forever on the next.
        final Map<String,String> artifacts = new HashMap<String,String>() {
            @Override
            public Set<Map.Entry<String, String>> entrySet() {
                return new HashSet<Map.Entry<String, String>>() {
                    @Override
                    public Iterator<Map.Entry<String, String>> iterator() {
                        return new Iterator<Map.Entry<String, String>>() {
                            private boolean block = false;

                            public boolean hasNext() {
                                return true;
                            }

                            public Map.Entry<String, String> next() {
                                if (!block) {
                                    block = true;
                                    return validArtifact;
                                }

                                synchronized (this) {
                                    try {
                                        this.wait(); // Block forever
                                        throw new AssertionError();
                                    } catch (InterruptedException ex) {
                                        // Expected at cleanup time
                                    }
                                }

                                return validArtifact;
                            }

                            public void remove() {
                                throw new UnsupportedOperationException();
                            }
                        };
                    }
                };
            }
        };

        // start archiving
        Thread compressor = new Thread("compressing-thread") {
            @Override
            public void run() {
                try {
                    archive(artifacts);
                } catch (Exception ex) {
                    throw new Error(ex);
                }
            }
        };

        compressor.start();
        try {
            Thread.sleep(1000);

            assertTrue(compressor.isAlive());

            assertArrayEquals(new String[0], zs.list());
            assertArrayEquals(new VirtualFile[0], zs.list("*"));
        } finally {
            compressor.stop();
        }
    }

    @Test // This can happen when it was not yet (fully) written or it was deleted
    public void supporMissingArchiveFile() throws Exception {
        assertArrayEquals(new String[0], zs.list());
        assertArrayEquals(new VirtualFile[0], zs.list("*"));
        assertFalse(zs.exists());
        assertFalse(zs.isFile());
        assertFalse(zs.isDirectory());

        VirtualFile child = zs.child("child");
        assertFalse(child.exists());
        assertFalse(child.isFile());
        assertFalse(child.isDirectory());
        assertEquals(0, child.length());
        assertEquals(0, child.lastModified());

        try {
            child.open();
            fail();
        } catch (IOException ex) {
            assertTrue(ex instanceof FileNotFoundException);
        }
    }

    @Test
    public void specialCaracters() throws Exception {
        String dirname = "Příliš_žluťoučký_kůň";
        String filename = "úpěl_ďábelské_ódy";

        File subdir = new File(content, dirname);
        subdir.mkdirs();

        FileUtils.writeStringToFile(new File(subdir, filename), "content");

        String fullname = dirname + "/" + filename;
        archive(Collections.singletonMap(fullname, fullname));

        testSpecialCaracters(canonical);
        testSpecialCaracters(zs);
    }

    private void testSpecialCaracters(VirtualFile vf) throws Exception {
        assertEquals(1, vf.list().length);
        VirtualFile subdir = vf.child("Příliš_žluťoučký_kůň");
        assertTrue(subdir.exists());
        assertEquals(1, subdir.list().length);
        assertTrue(subdir.child("úpěl_ďábelské_ódy").exists());
    }

    private void archive(Map<String, String> artifacts) throws Exception {
        BuildListener l = new StreamBuildListener(System.out, Charset.defaultCharset());
        ZipStorage.archive(archive, new FilePath(content), new Launcher.LocalLauncher(l), l, artifacts);
    }
}
