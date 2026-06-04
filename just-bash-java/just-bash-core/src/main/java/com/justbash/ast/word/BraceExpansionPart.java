package com.justbash.ast.word;

import com.justbash.ast.word.WordNode;
import java.util.List;

public record BraceExpansionPart(int line, List<BraceItem> items) implements WordPart {
    @Override public String type() { return "BraceExpansion"; }

    public interface BraceItem {}
    public record WordItem(WordNode word) implements BraceItem {}
    public record RangeItem(Object start, Object end, Integer step,
                         String startStr, String endStr) implements BraceItem {}
}
