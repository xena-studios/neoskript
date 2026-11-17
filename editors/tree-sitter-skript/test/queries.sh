#!/bin/sh
# Every query Zed loads must compile against this grammar: Zed's extension registry rejects the extension otherwise.
set -e
sample="$(dirname "$0")/sample.sk"
for query in "$(dirname "$0")"/../../zed/languages/skript/*.scm queries/*.scm; do
  tree-sitter query "$query" "$sample" > /dev/null
  echo "ok $query"
done
