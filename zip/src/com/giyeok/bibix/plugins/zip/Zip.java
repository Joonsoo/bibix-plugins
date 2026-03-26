package com.giyeok.bibix.plugins.zip;

import com.giyeok.bibix.base.BuildContext;
import com.giyeok.bibix.base.DirectoryValue;
import com.giyeok.bibix.base.FileValue;
import com.giyeok.bibix.base.NoneValue;
import com.giyeok.bibix.base.SetValue;
import com.giyeok.bibix.base.StringValue;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Zip {
    public FileValue build(BuildContext context) throws IOException {
        List<Path> files = new ArrayList<>();
        for (Object value : ((SetValue) context.getArguments().get("files")).getValues()) {
            files.add(((FileValue) value).getFile().toAbsolutePath());
        }

        Object baseDirArg = context.getArguments().get("baseDir");
        Path baseDir;
        if (baseDirArg instanceof NoneValue) {
            baseDir = commonAncestorDir(files);
        } else {
            baseDir = ((DirectoryValue) baseDirArg).getDirectory();
        }
        for (Path file : files) {
            if (!file.startsWith(baseDir)) {
                throw new IllegalStateException("Invalid baseDir");
            }
        }

        Object outputFileNameValue = context.getArguments().get("outputFileName");
        String outputFileName;
        if (outputFileNameValue instanceof NoneValue) {
            outputFileName = "output.zip";
        } else {
            outputFileName = ((StringValue) outputFileNameValue).getValue();
        }
        Path outputFile = context.getDestDirectory().resolve(outputFileName);

        if (context.getHashChanged()) {
            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(outputFile)))) {
                for (Path file : files) {
                    if (Files.isRegularFile(file)) {
                        Path path = baseDir.relativize(file.toAbsolutePath());
                        zos.putNextEntry(new ZipEntry(path.toString()));
                        Files.copy(file, zos);
                    }
                }
            }
        }
        return new FileValue(outputFile);
    }

    private Path commonAncestorDir(List<Path> files) {
        Path dir = files.get(0).getParent();
        for (int i = 1; i < files.size(); i++) {
            if (!files.get(i).getParent().startsWith(dir)) {
                dir = commonAncestorOf(files.get(i).getParent(), dir);
            }
        }
        return dir;
    }

    Path commonAncestorOf(Path a, Path b) {
        List<String> names = new ArrayList<>();
        int minCount = Math.min(a.getNameCount(), b.getNameCount());
        for (int i = 0; i < minCount; i++) {
            if (a.getName(i).equals(b.getName(i))) {
                names.add(a.getName(i).toString());
            } else {
                break;
            }
        }
        return Paths.get("/" + String.join("/", names));
    }
}
