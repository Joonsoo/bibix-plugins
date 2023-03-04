package com.giyeok.bibix.plugins.ktjvm;

import com.giyeok.bibix.base.*;
import org.jetbrains.kotlin.cli.common.CLITool;
import org.jetbrains.kotlin.cli.common.ExitCode;
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Library {
    Set<Path> allFilesOf(Path directory) {
        try {
            return Files.list(directory).flatMap(sub -> {
                if (Files.isDirectory(sub)) {
                    return allFilesOf(sub).stream();
                } else {
                    return Stream.of(sub);
                }
            }).collect(Collectors.toSet());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Set<Path> findResourceDirectoriesOf(Collection<Path> paths) {
        List<Path> mutPaths = new ArrayList<>(paths);
        Set<Path> resDirs = new HashSet<>();

        while (!mutPaths.isEmpty()) {
            Path path = mutPaths.remove(0);
            Path directory = path.getParent();
            Set<Path> dirFiles = allFilesOf(directory);
            if (paths.containsAll(dirFiles)) {
                resDirs.removeIf(it -> it.startsWith(directory));
                resDirs.add(directory);
                mutPaths.removeAll(dirFiles);
            } else {
                resDirs.add(path);
            }
        }
        return resDirs;
    }

    private BibixValue runCompiler(SetValue classPaths, SetValue deps, BuildContext context, ListValue optIns) throws IOException {
        Path destDirectory = context.getDestDirectory();

        SetValue srcs = (SetValue) context.getArguments().get("srcs");
        if (srcs.getValues().isEmpty()) {
            throw new IllegalArgumentException("srcs must not be empty");
        }

        SetValue resourcesValue = (SetValue) context.getArguments().get("resources");
        Set<Path> resources = new HashSet<>();
        for (BibixValue value : resourcesValue.getValues()) {
            resources.add(((FileValue) value).getFile().toAbsolutePath());
        }

        if (context.getHashChanged()) {
            ArrayList<String> args = new ArrayList<>();
            if (!classPaths.getValues().isEmpty()) {
                ArrayList<Path> cps = new ArrayList<>();
                args.add("-cp");
                // System.out.println(deps);
                classPaths.getValues().forEach(v -> {
                    cps.add(((PathValue) v).getPath());
                });
                args.add(cps.stream().map(Path::toAbsolutePath).map(Path::toString).collect(Collectors.joining(":")));
            }

            for (BibixValue value : srcs.getValues()) {
                args.add(((FileValue) value).getFile().toAbsolutePath().toString());
            }

            args.add("-d");
            args.add(destDirectory.toAbsolutePath().toString());

            if (optIns != null && !optIns.getValues().isEmpty()) {
                optIns.getValues().forEach(optIn ->
                        args.add("-opt-in=" + ((StringValue) optIn).getValue()));
            }

            args.add("-no-stdlib");
            // args.add("-no-reflect");

            // System.out.println("** ktjvm args: " + args);
            ExitCode exitCode = CLITool.doMainNoExit(new K2JVMCompiler(), args.toArray(new String[0]));
            if (exitCode != ExitCode.OK) {
                throw new IllegalStateException("Failed to compile kotlin sources");
            }
        }

        Set<Path> resDirs = findResourceDirectoriesOf(resources);

        // ClassPkg = (origin: ClassOrigin, cpinfo: CpInfo, deps: set<ClassPkg>)
        // CpInfo = {JarInfo, ClassesInfo}
        // ClassesInfo = (classDirs: set<directory>, resDirs: set<directory>, srcs: {set<file>, none})
        return new NClassInstanceValue("jvm.ClassPkg", Map.of(
                "origin", new NClassInstanceValue("jvm.LocalBuilt", Map.of(
                        "objHash", new StringValue(context.getObjectIdHash()),
                        "builderName", new StringValue("ktjvm.library")
                )),
                "cpinfo", new NClassInstanceValue("jvm.ClassesInfo", Map.of(
                        "classDirs", new SetValue(new DirectoryValue(destDirectory)),
                        "resDirs", new SetValue(resDirs.stream().map(PathValue::new).collect(Collectors.toList())),
                        "srcs", srcs
                )),
                "deps", deps
        ));
    }

    public BuildRuleReturn build(BuildContext context) throws IOException {
        SetValue deps = (SetValue) context.getArguments().get("deps");
        BibixValue optInsValue = (BibixValue) context.getArguments().get("optIns");
        ListValue optIns = (optInsValue instanceof NoneValue) ? null : (ListValue) optInsValue;
        StringValue sdkVersion = (StringValue) context.getArguments().get("sdkVersion");

        return BuildRuleReturn.evalAndThen(
                "maven.artifact",
                Map.of(
                        "group", new StringValue("org.jetbrains.kotlin"),
                        "artifact", new StringValue("kotlin-stdlib"),
                        "version", sdkVersion
                ),
                (sdkClassPkg) -> {
                    List<BibixValue> newDeps = new ArrayList<>(deps.getValues());
                    newDeps.add(sdkClassPkg);
                    SetValue newDepsValue = new SetValue(newDeps);
                    return BuildRuleReturn.evalAndThen(
                            "jvm.resolveClassPkgs",
                            Map.of("classPkgs", newDepsValue),
                            (classPaths) -> {
                                SetValue cps = (SetValue) ((ClassInstanceValue) classPaths).get("cps");
                                try {
                                    return BuildRuleReturn.value(runCompiler(cps, newDepsValue, context, optIns));
                                } catch (Exception e) {
                                    return BuildRuleReturn.failed(e);
                                }
                            });
                }
        );
    }

    public void run(ActionContext context) {
        System.out.println("ktjvm.Library.run " + context);
    }
}
