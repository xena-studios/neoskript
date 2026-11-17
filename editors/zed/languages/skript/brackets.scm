; Pairs the grammar has as nodes of their own. Parentheses are part of punctuation tokens and function calls
; (`name(`), so they can't be queried as brackets.
(variable "{" @open "}" @close)
(string "\"" @open "\"" @close)
