; Top-level lines (events, commands, functions, options) in the outline.
(line . (word) @context
  (#match? @context "^(on|every|at|command|function|options|variables|aliases|local)$")) @item
