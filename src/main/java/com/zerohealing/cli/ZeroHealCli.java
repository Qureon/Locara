package com.zerohealing.cli;

/**
 * Backward compatibility CLI wrapper.
 * Delegates directly to {@link com.qureon.locara.cli.LocaraCli}.
 */
public class ZeroHealCli {

    public static void main(String[] args) {
        com.qureon.locara.cli.LocaraCli.main(args);
    }
}
