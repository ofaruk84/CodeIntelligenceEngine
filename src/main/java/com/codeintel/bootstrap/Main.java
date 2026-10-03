package com.codeintel.bootstrap;

import com.codeintel.adapter.in.cli.CodeIntelCli;

public final class Main {
    private static final java.util.logging.Logger PROJECT_LOG = java.util.logging.Logger.getLogger("com.codeintel");
    private Main() {}
    public static void main(String[] args) {
        // Respect explicit JUL configuration; otherwise suppress ordinary operational INFO only.
        if (System.getProperty("java.util.logging.config.file") == null
                && System.getProperty("java.util.logging.config.class") == null)
            PROJECT_LOG.setLevel(java.util.logging.Level.WARNING);
        System.exit(new CodeIntelCli(EngineFactory.create()).run(args, System.out, System.err));
    }
}
