/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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

import hudson.util.FileVisitor;
import hudson.util.io.Archiver;
import hudson.util.io.ArchiverFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

import de.schlichtherle.truezip.zip.ZipEntry;
import de.schlichtherle.truezip.zip.ZipOutputStream;

/**
 * {@link FileVisitor} that creates a zip archive via TrueZip.
 *
 * Modified version of {@link hudson.util.io.ZipArchiver} that created archives
 * larger than 4G successfully.
 */
final /*pacakge*/ class TrueZipArchiver extends Archiver {
    private final byte[] buf = new byte[8192];
    private final ZipOutputStream zip;

    /*package*/ TrueZipArchiver(OutputStream out) {
        zip = new ZipOutputStream(out, Charset.defaultCharset());
    }

    /*package*/ TrueZipArchiver(OutputStream out, Charset filenamesEncoding) {
        zip = new ZipOutputStream(out, filenamesEncoding);
    }

    @Override
    public void visit(final File f, final String _relativePath) throws IOException {
        // int mode = IOUtils.mode(f); // TODO

        // On Windows, the elements of relativePath are separated by 
        // back-slashes (\), but Zip files need to have their path elements separated
        // by forward-slashes (/)
        String relativePath = _relativePath.replace('\\', '/');
        
        if(f.isDirectory()) {
            ZipEntry dirZipEntry = new ZipEntry(relativePath+'/');
            // Setting this bit explicitly is needed by some unzipping applications (see JENKINS-3294).
            dirZipEntry.setExternalAttributes(BITMASK_IS_DIRECTORY);
            //if (mode!=-1)   dirZipEntry.setUnixMode(mode); // TODO
            dirZipEntry.setTime(f.lastModified());
            zip.putNextEntry(dirZipEntry);
            zip.closeEntry();
        } else {
            ZipEntry fileZipEntry = new ZipEntry(relativePath);
            //if (mode!=-1)   fileZipEntry.setUnixMode(mode); // TODO
            fileZipEntry.setTime(f.lastModified());
            zip.putNextEntry(fileZipEntry);
            FileInputStream in = new FileInputStream(f);
            try {
                int len;
                while((len=in.read(buf))>=0)
                    zip.write(buf,0,len);
            } finally {
                in.close();
            }
            zip.closeEntry();
        }
        entriesWritten++;
    }

    public void close() throws IOException {
        zip.close();
    }

    // Bitmask indicating directories in 'external attributes' of a ZIP archive entry.
    private static final long BITMASK_IS_DIRECTORY = 1<<4;
    
    public static final ArchiverFactory FACTORY = new Factory();

    private static final class Factory extends ArchiverFactory {
        private static final long serialVersionUID = 1L;
        @Override
        public Archiver create(OutputStream out) throws IOException {
            return new TrueZipArchiver(out);
        }
        @Override
        public Archiver create(OutputStream out, Charset filenamesEncoding) throws IOException {
            return new TrueZipArchiver(out, filenamesEncoding);
        }
    }; 
}
