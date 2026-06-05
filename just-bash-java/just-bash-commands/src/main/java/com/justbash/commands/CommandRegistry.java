package com.justbash.commands;

import com.justbash.Bash;
import com.justbash.commands.cat.CatCommand;
import com.justbash.commands.cp.CpCommand;
import com.justbash.commands.mkdir.MkdirCommand;
import com.justbash.commands.mv.MvCommand;
import com.justbash.commands.rm.RmCommand;
import com.justbash.commands.touch.TouchCommand;

public class CommandRegistry {

    public static void registerAll(Bash bash) {
        bash.registerCommand(new CatCommand());
        bash.registerCommand(new CpCommand());
        bash.registerCommand(new MkdirCommand());
        bash.registerCommand(new MvCommand());
        bash.registerCommand(new RmCommand());
        bash.registerCommand(new TouchCommand());
    }
}
