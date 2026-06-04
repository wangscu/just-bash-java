package com.justbash.interpreter;

public class ShoptOptions {
    boolean extglob;
    boolean dotglob;
    boolean nullglob;
    boolean failglob;
    boolean globstar;
    boolean globskipdots;
    boolean nocaseglob;
    boolean nocasematch;
    boolean expand_aliases;
    boolean lastpipe;
    boolean xpg_echo;

    public ShoptOptions() {
        this.extglob = false;
        this.dotglob = false;
        this.nullglob = false;
        this.failglob = false;
        this.globstar = false;
        this.globskipdots = true;
        this.nocaseglob = false;
        this.nocasematch = false;
        this.expand_aliases = false;
        this.lastpipe = false;
        this.xpg_echo = false;
    }

    public static ShoptOptions defaults() {
        return new ShoptOptions();
    }

    public ShoptOptions copy() {
        ShoptOptions copy = new ShoptOptions();
        copy.extglob = this.extglob;
        copy.dotglob = this.dotglob;
        copy.nullglob = this.nullglob;
        copy.failglob = this.failglob;
        copy.globstar = this.globstar;
        copy.globskipdots = this.globskipdots;
        copy.nocaseglob = this.nocaseglob;
        copy.nocasematch = this.nocasematch;
        copy.expand_aliases = this.expand_aliases;
        copy.lastpipe = this.lastpipe;
        copy.xpg_echo = this.xpg_echo;
        return copy;
    }
}
