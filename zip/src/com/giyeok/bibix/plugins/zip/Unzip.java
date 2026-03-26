package com.giyeok.bibix.plugins.zip;

import com.giyeok.bibix.base.BuildContext;
import com.giyeok.bibix.base.DirectoryValue;
import com.giyeok.bibix.base.FileValue;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Unzip {
    public DirectoryValue build(BuildContext context) throws IOException {
        Path zipFile = ((FileValue) context.getArguments().get("zipFile")).getFile();
        Path destDirectory = context.getDestDirectory();

        if (context.getHashChanged()) {
            byte[] buffer = new byte[1000];
            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile)))) {
                ZipEntry entry = zis.getNextEntry();
                while (entry != null) {
                    Path entryPath = destDirectory.resolve(entry.getName());
                    if (entry.isDirectory()) {
                        if (Files.notExists(entryPath)) {
                            Files.createDirectories(entryPath);
                        }
                    } else {
                        Path directory = entryPath.getParent();
                        if (Files.notExists(directory)) {
                            Files.createDirectories(directory);
                        }
                        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(entryPath))) {
                            int count;
                            while ((count = zis.read(buffer, 0, 1000)) >= 0) {
                                output.write(buffer, 0, count);
                            }
                        }
                    }
                    entry = zis.getNextEntry();
                }
                zis.closeEntry();
            }
        }
        return new DirectoryValue(destDirectory);
    }
}
