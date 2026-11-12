/**
 * @file Skript (.sk), the Minecraft server scripting language, for highlighting: lines of words, strings with
 * %expressions% and colour tags, {variables}, {@options}, numbers, event values, function calls and comments. It
 * doesn't parse Skript's syntax (that's NeoSkript's language server); each line is a flat list of tokens, with its
 * indent, so queries can tell a top-level line (an event, command or function) from code.
 * @license GPL-3.0-only
 */

/// <reference types="tree-sitter-cli/dsl" />
// @ts-check

module.exports = grammar({
  name: "skript",

  extras: (_) => [/[ \t]/],

  rules: {
    // Lines end with a newline, except perhaps the last. An indent can only start a line.
    source_file: ($) => seq(repeat(seq(optional($.line), $._newline)), optional($.line)),

    line: ($) => seq(optional($.indent), choice($.comment, seq(repeat1($._item), optional($.comment)))),

    _newline: (_) => /\r?\n/,

    // Only at the start of a line: the space before its first token.
    indent: (_) => token(prec(1, /[ \t]+/)),

    _item: ($) =>
      choice(
        $.string,
        $.variable,
        $.option,
        $.interpolation,
        $.number,
        $.event_value,
        $.function_call,
        $.word,
        $.punctuation,
      ),

    comment: (_) => token(seq("#", /[^\r\n]*/)),

    // A string can't run past its line (string_content stops at a newline), so one left open doesn't colour the
    // lines after it.
    string: ($) => seq('"', repeat(choice($.escape, $.color, $.interpolation, $.string_content)), '"'),
    string_content: (_) => token(/[^"%<&§\r\n]+|[<&§]/),
    escape: (_) => token(choice('""', "%%")),
    color: (_) => token(choice(/<[^<>"\r\n]*>/, /[&§][0-9a-fk-orA-FK-OR]/)),

    interpolation: ($) => seq("%", repeat(choice($.string, $.variable, $.option, $.number, $.event_value, $.function_call, $.word, $.punctuation)), "%"),

    variable: ($) => seq("{", repeat(choice($.variable, $.interpolation, $.variable_name)), "}"),
    variable_name: (_) => /[^{}%\r\n]+/,

    option: (_) => token(seq("{@", /[^}\r\n]*/, "}")),

    number: (_) => token(seq(optional("-"), /\d+(\.\d+)?/)),

    event_value: (_) => token(prec(2, seq(/(event|loop|arg|argument)-/i, /[\w-]+/))),

    // A name straight before '(': a call, or a function's declaration.
    function_call: (_) => token(prec(1, /[\p{L}_][\p{L}\p{N}_]*\(/u)),

    word: (_) => /[\p{L}_][\p{L}\p{N}_'-]*/u,

    punctuation: (_) => token(/[^\s\w"{}%#\p{L}]+/u),
  },
});
