package com.justbash.interpreter;

import java.util.ArrayList;
import java.util.List;

public class CompletionSpec {
    public String wordlist;
    public String function;
    public String command;
    public List<String> options = new ArrayList<>();
    public List<String> actions = new ArrayList<>();
    public boolean isDefault;

    public CompletionSpec copy() {
        CompletionSpec copy = new CompletionSpec();
        copy.wordlist = this.wordlist;
        copy.function = this.function;
        copy.command = this.command;
        copy.options = new ArrayList<>(this.options);
        copy.actions = new ArrayList<>(this.actions);
        copy.isDefault = this.isDefault;
        return copy;
    }
}
