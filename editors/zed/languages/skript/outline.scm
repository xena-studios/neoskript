; Top-level lines (events, commands, functions, options) in the outline: `on join`, `command /hi`.
(line
  .
  (word) @context
  (#match? @context "^(on|every|at|command|function|options|variables|aliases|local)$")
  (_)* @name) @item
