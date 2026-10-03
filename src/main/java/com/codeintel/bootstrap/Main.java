package com.codeintel.bootstrap;

import com.codeintel.adapter.in.cli.CodeIntelCli;

public final class Main {
    private Main() {}
    public static void main(String[] args) {
        System.exit(new CodeIntelCli(EngineFactory.create()).run(args, System.out, System.err));
    }
}
