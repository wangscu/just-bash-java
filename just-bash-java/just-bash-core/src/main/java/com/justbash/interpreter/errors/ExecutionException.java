package com.justbash.interpreter.errors;

public sealed class ExecutionException extends BashException
    permits ExitException, ReturnException, BreakException, ContinueException,
            ExecutionLimitException, ExecutionAbortedException,
            BadSubstitutionException, ErrexitException {

    public ExecutionException(String message) { super(message); }
    public ExecutionException(String message, String stdout, String stderr) {
        super(message);
        this.stdout = stdout;
        this.stderr = stderr;
    }
    private String stdout = "";
    private String stderr = "";
    public String stdout() { return stdout; }
    public String stderr() { return stderr; }
    public void setStdout(String s) { this.stdout = s; }
    public void setStderr(String s) { this.stderr = s; }
}
