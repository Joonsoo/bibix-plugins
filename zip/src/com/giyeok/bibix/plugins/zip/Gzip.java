package com.giyeok.bibix.plugins.zip;

import com.giyeok.bibix.base.BuildContext;
import com.giyeok.bibix.base.FileValue;
import com.giyeok.bibix.base.NoneValue;
import com.giyeok.bibix.base.StringValue;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

public class Gzip {
    public FileValue build(BuildContext context) throws IOException {
        Path file = ((FileValue) context.getArguments().get("file")).getFile();
        Object outputFileNameValue = context.getArguments().get("outputFileName");
        String outputFileName;
        if (outputFileNameValue instanceof NoneValue) {
            outputFileName = file.getFileName().toString() + ".gz";
        } else {
            outputFileName = ((StringValue) outputFileNameValue).getValue();
        }
        Path outputFile = context.getDestDirectory().resolve(outputFileName);

        if (context.getHashChanged()) {
            try (GZIPOutputStream zos = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(outputFile)))) {
                Files.copy(file, zos);
            }
        }
        return new FileValue(outputFile);
    }
}
