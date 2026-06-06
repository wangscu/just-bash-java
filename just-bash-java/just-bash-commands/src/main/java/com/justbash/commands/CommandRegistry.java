package com.justbash.commands;

import com.justbash.Bash;
import com.justbash.commands.cat.CatCommand;
import com.justbash.commands.column.ColumnCommand;
import com.justbash.commands.comm.CommCommand;
import com.justbash.commands.cp.CpCommand;
import com.justbash.commands.cut.CutCommand;
import com.justbash.commands.diff.DiffCommand;
import com.justbash.commands.expr.ExprCommand;
import com.justbash.commands.file.FileCommand;
import com.justbash.commands.fold.FoldCommand;
import com.justbash.commands.grep.GrepCommand;
import com.justbash.commands.head.HeadCommand;
import com.justbash.commands.join.JoinCommand;
import com.justbash.commands.jq.JqCommand;
import com.justbash.commands.ls.LsCommand;
import com.justbash.commands.mkdir.MkdirCommand;
import com.justbash.commands.mv.MvCommand;
import com.justbash.commands.nl.NlCommand;
import com.justbash.commands.paste.PasteCommand;
import com.justbash.commands.printf.PrintfCommand;
import com.justbash.commands.rev.RevCommand;
import com.justbash.commands.rg.RgCommand;
import com.justbash.commands.rm.RmCommand;
import com.justbash.commands.seq.SeqCommand;
import com.justbash.commands.sort.SortCommand;
import com.justbash.commands.split.SplitCommand;
import com.justbash.commands.tail.TailCommand;
import com.justbash.commands.touch.TouchCommand;
import com.justbash.commands.tr.TrCommand;
import com.justbash.commands.tree.TreeCommand;
import com.justbash.commands.uniq.UniqCommand;
import com.justbash.commands.wc.WcCommand;
import com.justbash.commands.xargs.XargsCommand;

public class CommandRegistry {

    public static void registerAll(Bash bash) {
        bash.registerCommand(new CatCommand());
        bash.registerCommand(new ColumnCommand());
        bash.registerCommand(new CommCommand());
        bash.registerCommand(new CpCommand());
        bash.registerCommand(new CutCommand());
        bash.registerCommand(new DiffCommand());
        bash.registerCommand(new ExprCommand());
        bash.registerCommand(new FileCommand());
        bash.registerCommand(new FoldCommand());
        bash.registerCommand(new GrepCommand());
        bash.registerCommand(new HeadCommand());
        bash.registerCommand(new JoinCommand());
        bash.registerCommand(new JqCommand());
        bash.registerCommand(new LsCommand());
        bash.registerCommand(new MkdirCommand());
        bash.registerCommand(new MvCommand());
        bash.registerCommand(new NlCommand());
        bash.registerCommand(new PasteCommand());
        bash.registerCommand(new PrintfCommand());
        bash.registerCommand(new RevCommand());
        bash.registerCommand(new RgCommand());
        bash.registerCommand(new RmCommand());
        bash.registerCommand(new SeqCommand());
        bash.registerCommand(new SortCommand());
        bash.registerCommand(new SplitCommand());
        bash.registerCommand(new TailCommand());
        bash.registerCommand(new TouchCommand());
        bash.registerCommand(new TrCommand());
        bash.registerCommand(new TreeCommand());
        bash.registerCommand(new UniqCommand());
        bash.registerCommand(new WcCommand());
        bash.registerCommand(new XargsCommand());
    }
}
