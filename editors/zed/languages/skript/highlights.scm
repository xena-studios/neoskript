; Skript highlights, as neoskript.dev's parser colours scripts.

(comment) @comment

; A top-level line's first word: the structure it opens.
(line . (word) @keyword
  (#match? @keyword "^(on|every|at|command|function|options|variables|aliases|import|using|local)$"))

; Control words and Skript's common words, anywhere.
((word) @keyword.control
  (#match? @keyword.control "^(if|else|loop|while|do|return|stop|exit|continue|wait|trigger|parse|any|all)$"))
((word) @keyword
  (#match? @keyword "^(set|add|remove|delete|clear|reset|send|broadcast|give|teleport|cancel|make|execute|kill|to|from|of|is|are|not|isn't|aren't|has|have|and|or|with|in|where|by|times|event)$"))
((word) @boolean
  (#match? @boolean "^(true|false|yes|no)$"))

; A command's entries: `permission:`, `trigger:` and so on.
(line (indent) . (word) @property
  (#match? @property "^(description|usage|permission|aliases|executable|cooldown|prefix|trigger)$"))

(string) @string
(string_content) @string
(escape) @string.escape
(color) @string.special

(interpolation "%" @punctuation.special)
(variable "{" @punctuation.bracket "}" @punctuation.bracket)
(variable_name) @variable
(option) @constant

(number) @number
(event_value) @variable.special
(function_call) @function

(punctuation) @punctuation
