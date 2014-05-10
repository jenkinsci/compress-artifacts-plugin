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
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
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

    @Test public void simpleList() throws Exception {
        FileUtils.writeStringToFile(new File(content, "top"), "top");
        File dirF = new File(content, "dir");
        assertTrue(dirF.mkdir());
        FileUtils.writeStringToFile(new File(dirF, "sub"), "sub");
        BuildListener l = new StreamBuildListener(System.out, Charset.defaultCharset());
        Map<String,String> artifacts = new HashMap<String,String>();
        artifacts.put("top", "top");
        artifacts.put("dir/sub", "dir/sub");
        ZipStorage.archive(archive, new FilePath(content), new Launcher.LocalLauncher(l), l, artifacts);
        doSimpleList(canonical);
        doSimpleList(zs);
    }
    private void doSimpleList(VirtualFile vf) throws Exception {
        assertTrue(vf.isDirectory());
        VirtualFile[] list = vf.list();
        Arrays.sort(list);
        assertEquals(2, list.length);
        VirtualFile dir = list[0];
        assertEquals("dir", dir.getName());
        assertTrue(dir.isDirectory());
        assertFalse(dir.isFile());
        VirtualFile top = list[1];
        assertEquals("top", top.getName());
        assertFalse(top.isDirectory());
        assertTrue(top.isFile());
        assertEquals("top", IOUtils.toString(top.open()));
        list = dir.list();
        assertEquals(1, list.length);
        VirtualFile sub = list[0];
        assertEquals("sub", sub.getName());
        assertFalse(sub.isDirectory());
        assertTrue(sub.isFile());
        assertEquals("sub", IOUtils.toString(sub.open()));
        assertEquals(sub, vf.child("dir/sub"));
    }
    
    @Test public void testStringList() throws Exception {
        FileUtils.writeStringToFile(new File(content, "top"), "top");
        File dir1 = new File(content, "folder1");
        assertTrue(dir1.mkdir());
        FileUtils.writeStringToFile(new File(dir1, "file1.txt"), "file1Content");
        
        File dir2 = new File(content,"folder2");
        assertTrue(dir2.mkdir());
        FileUtils.writeStringToFile(new File(dir2,"file2.log"), "file2Content");
        
        BuildListener l = new StreamBuildListener(System.out, Charset.defaultCharset());
        Map<String,String> artifacts = new HashMap<String,String>();
        artifacts.put("top", "top");
        artifacts.put("folder1/file1.txt", "folder1/file1.txt");
        artifacts.put("folder2/file2.log", "folder2/file2.log");
        ZipStorage.archive(archive, new FilePath(content), new Launcher.LocalLauncher(l), l, artifacts);

        String [] testVals = zs.list("**");
        assertTrue(testVals.length == 3);
        
        testVals = zs.list("**/*.log");
        assertEquals("expect only file to be file2.log",testVals[0],"folder2/file2.log");
        assertTrue(testVals.length==1);
        
        testVals = zs.list("folder*/*");
        assertTrue(testVals.length==2);
        
        testVals = zs.list("");
        assertTrue(testVals.length==0);
        
        boolean exceptionRaised = false;
        try {
        testVals = zs.list(null);
        } catch (IllegalArgumentException e) {
        	exceptionRaised = true;
        }
        assertTrue("We expect an exception from illegal input",exceptionRaised);
    }

}