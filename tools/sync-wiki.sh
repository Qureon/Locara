#!/usr/bin/env bash
set -e

REPO_URL="https://github.com/Qureon/Locara.wiki.git"
WORK_DIR="/tmp/locara-wiki-sync"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WIKI_SRC="$(dirname "$SCRIPT_DIR")/wiki"

echo "🌸 Synchronizing Locara Wiki to GitHub..."

if ! gh auth status >/dev/null 2>&1; then
    echo "❌ Error: gh CLI is not authenticated. Please run 'gh auth login' first."
    exit 1
fi

TOKEN=$(gh auth token)
AUTH_URL="https://MuchoRio:${TOKEN}@github.com/Qureon/Locara.wiki.git"

rm -rf "$WORK_DIR"
echo "📥 Cloning $REPO_URL..."
if ! git clone "$AUTH_URL" "$WORK_DIR" 2>/dev/null; then
    echo "⚠️ Notice: The wiki repository is not yet initialized on GitHub."
    echo "👉 Please visit https://github.com/Qureon/Locara/wiki once and click 'Create the first page'."
    echo "   After saving the first page, re-run this script to sync all pages automatically!"
    exit 1
fi

echo "📋 Copying wiki pages from $WIKI_SRC..."
cp -r "$WIKI_SRC/"* "$WORK_DIR/"

cd "$WORK_DIR"
git config user.name "MuchoRio"
git config user.email "riogriyaputra@gmail.com"

git add .
if git diff --staged --quiet; then
    echo "✨ Wiki is already up to date! Nothing to commit."
else
    git commit -m "docs(wiki): synchronize comprehensive Locara documentation pages"
    echo "🚀 Pushing to GitHub Wiki..."
    git push origin master 2>/dev/null || git push origin main 2>/dev/null || git push origin HEAD
    echo "🎉 Successfully synchronized Locara Wiki!"
fi

rm -rf "$WORK_DIR"
