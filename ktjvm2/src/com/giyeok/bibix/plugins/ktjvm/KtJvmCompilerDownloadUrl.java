package com.giyeok.bibix.plugins.ktjvm;

import com.giyeok.bibix.base.*;

public class KtJvmCompilerDownloadUrl {
    public BuildRuleReturn build(BuildContext context) {
        String compilerVersion = ((StringValue)context.getArguments().get("compilerVersion")).getValue();
        return BuildRuleReturn.value(new StringValue("https://github.com/JetBrains/kotlin/releases/download/v" + compilerVersion + "/kotlin-compiler-" + compilerVersion + ".zip"));
    }
}
