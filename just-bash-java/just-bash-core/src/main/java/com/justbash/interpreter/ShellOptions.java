package com.justbash.interpreter;

public class ShellOptions {
    boolean errexit;
    boolean pipefail;
    boolean nounset;
    boolean xtrace;
    boolean verbose;
    boolean posix;
    boolean allexport;
    boolean noclobber;
    boolean noglob;
    boolean noexec;
    boolean vi;
    boolean emacs;

    public ShellOptions() {
        this.errexit = false;
        this.pipefail = false;
        this.nounset = false;
        this.xtrace = false;
        this.verbose = false;
        this.posix = false;
        this.allexport = false;
        this.noclobber = false;
        this.noglob = false;
        this.noexec = false;
        this.vi = false;
        this.emacs = false;
    }

    public static ShellOptions defaults() {
        return new ShellOptions();
    }

    public boolean errexit() { return errexit; }
    public boolean pipefail() { return pipefail; }
    public boolean nounset() { return nounset; }
    public boolean xtrace() { return xtrace; }
    public boolean verbose() { return verbose; }
    public boolean posix() { return posix; }
    public boolean allexport() { return allexport; }
    public boolean noclobber() { return noclobber; }
    public boolean noglob() { return noglob; }
    public boolean noexec() { return noexec; }
    public boolean vi() { return vi; }
    public boolean emacs() { return emacs; }

    public ShellOptions copy() {
        ShellOptions copy = new ShellOptions();
        copy.errexit = this.errexit;
        copy.pipefail = this.pipefail;
        copy.nounset = this.nounset;
        copy.xtrace = this.xtrace;
        copy.verbose = this.verbose;
        copy.posix = this.posix;
        copy.allexport = this.allexport;
        copy.noclobber = this.noclobber;
        copy.noglob = this.noglob;
        copy.noexec = this.noexec;
        copy.vi = this.vi;
        copy.emacs = this.emacs;
        return copy;
    }
}
