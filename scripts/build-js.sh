#!/bin/sh
# Bundles the JavaScript libraries of the sources into one file of the core module, as in Asabunko.
# The output is in the repository, so a build of the app does not need Node.
set -eu

cd "$(dirname "$0")/../js"
npm ci --no-audit --no-fund --silent
npx esbuild libraries.js --bundle --minify --format=iife --target=es2020 --legal-comments=eof \
  --outfile=../core/src/main/resources/io/github/stivengjekaj/moekoma/core/js/libraries.js
echo "core/src/main/resources/io/github/stivengjekaj/moekoma/core/js/libraries.js"
