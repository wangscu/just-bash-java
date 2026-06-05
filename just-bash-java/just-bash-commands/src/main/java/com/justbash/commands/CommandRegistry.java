package com.justbash.commands;

import com.justbash.Bash;
import com.justbash.commands.cat.CatCommand;
import com.justbash.commands.cp.CpCommand;
import com.justbash.commands.grep.GrepCommand;
import com.justbash.commands.head.HeadCommand;
import com.justbash.commands.ls.LsCommand;
import com.justbash.commands.mkdir.MkdirCommand;
import com.justbash.commands.mv.MvCommand;
import com.justbash.commands.printf.PrintfCommand;
import com.justbash.commands.rm.RmCommand;
import com.justbash.commands.tail.TailCommand;
import com.justbash.commands.touch.TouchCommand;
import com.justbash.commands.wc.WcCommand;

public class CommandRegistry {

    public static void registerAll(Bash bash) {
        bash.registerCommand(new CatCommand());
        bash.registerCommand(new CpCommand());
        bash.registerCommand(new GrepCommand());
        bash.registerCommand(new HeadCommand());
        bash.registerCommand(new LsCommand());
        bash.registerCommand(new MkdirCommand());
        bash.registerCommand(new MvCommand());
        bash.registerCommand(new PrintfCommand());
        bash.registerCommand(new RmCommand());
        bash.registerCommand(new TailCommand());
        bash.registerCommand(new TouchCommand());
        bash.registerCommand(new WcCommand());
    }
}
