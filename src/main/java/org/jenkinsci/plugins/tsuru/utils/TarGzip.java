package org.jenkinsci.plugins.tsuru.utils;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/*
    Got from http://sloanseaman.com/wordpress/2012/05/22/tar-and-gzip-compression-in-java/
 */

public class TarGzip {
    /**
     * Compress (tar.gz) the input file (or directory) to the output file
     *
     *
     * In the case of a directory all files within the directory (and all nested
     * directories) will be added to the archive
     *
     * @param file The file(s if a directory) to compress
     * @param output The resulting output file (should end in .tar.gz)
     * @throws IOException
     */
    public static void compressFile(File file, File output)
            throws IOException
    {
        ArrayList<File> list = new ArrayList<File>(1);
        list.add(file);
        compressFiles(list, output);
    }

    /**
     * Compress (tar.gz) the input files to the output file
     *
     * @param files The files to compress
     * @param output The resulting output file (should end in .tar.gz)
     * @throws IOException
     */
    public static void compressFiles(Collection<File> files, File output)
            throws IOException
    {
        // Create the output stream for the output file
        FileOutputStream fos = new FileOutputStream(output);
        // Wrap the output file stream in streams that will tar and gzip everything
        TarArchiveOutputStream taos = new TarArchiveOutputStream(
                new GZIPOutputStream(new BufferedOutputStream(fos)));
        // TAR has an 8 gig file limit by default, this gets around that
        taos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX); // to get past the 8 gig limit
        // TAR originally didn't support long file names, so enable the support for it
        taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);

        // Get to putting all the files in the compressed output file
        for (File f : files) {
            addFilesToCompression(taos, f, ".");
        }

        // Close everything up
        taos.close();
        fos.close();
    }

    /**
     * Does the work of compression and going recursive for nested directories
     * <p/>
     *
     * Borrowed heavily from http://www.thoughtspark.org/node/53
     *
     * @param taos The archive
     * @param file The file to add to the archive
     * @param dir The directory that should serve as the parent directory in the archivew
     * @throws IOException
     */
    private static void addFilesToCompression(TarArchiveOutputStream taos, File file, String dir)
            throws IOException
    {
        // Create an entry for the file
        TarArchiveEntry tarFileEntry = new TarArchiveEntry(file, dir + File.separator + file.getName());

        tarFileEntry.setMode(posixPermissions(file));

        taos.putArchiveEntry(tarFileEntry);
        if (file.isFile()) {
            // Add the file to the archive
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
            IOUtils.copy(bis, taos);
            taos.closeArchiveEntry();
            bis.close();
        }
        else if (file.isDirectory()) {
            // close the archive entry
            taos.closeArchiveEntry();
            // go through all the files in the directory and using recursion, add them to the archive
            for (File childFile : file.listFiles()) {
                addFilesToCompression(taos, childFile, dir + File.separator + file.getName());
            }
        }
    }

    // https://raw.githubusercontent.com/kamranzafar/jtar/master/src/main/java/org/kamranzafar/jtar/PermissionUtils.java

    private static Map<PosixFilePermission, Integer> posixPermissionToInteger = new HashMap<>();

    static {
        posixPermissionToInteger.put(PosixFilePermission.OWNER_EXECUTE, 0100);
        posixPermissionToInteger.put(PosixFilePermission.OWNER_WRITE, 0200);
        posixPermissionToInteger.put(PosixFilePermission.OWNER_READ, 0400);

        posixPermissionToInteger.put(PosixFilePermission.GROUP_EXECUTE, 0010);
        posixPermissionToInteger.put(PosixFilePermission.GROUP_WRITE, 0020);
        posixPermissionToInteger.put(PosixFilePermission.GROUP_READ, 0040);

        posixPermissionToInteger.put(PosixFilePermission.OTHERS_EXECUTE, 0001);
        posixPermissionToInteger.put(PosixFilePermission.OTHERS_WRITE, 0002);
        posixPermissionToInteger.put(PosixFilePermission.OTHERS_READ, 0004);
    }

    private static int posixPermissions(File f) {
        int number = 0;
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(f.toPath());
            for (Map.Entry<PosixFilePermission, Integer> entry : posixPermissionToInteger.entrySet()) {
                if (permissions.contains(entry.getKey())) {
                    number += entry.getValue();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return number;
    }
}
