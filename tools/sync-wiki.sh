#!/usr/bin/env bash
set -euo pipefail

REPO_URL="https://github.com/Qureon/Locara.wiki.git"
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/locara-wiki-sync.XXXXXX")"
trap 'rm -rf "$WORK_DIR"' EXIT

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WIKI_SRC="$(dirname "$SCRIPT_DIR")/wiki"

echo "🌸 Synchronizing Locara Wiki to GitHub..."

if ! gh auth status >/dev/null 2>&1; then
    echo "❌ Error: gh CLI is not authenticated. Please run 'gh auth login' first."
    exit 1
fi

gh auth setup-git >/dev/null 2>&1 || true

echo "📥 Cloning $REPO_URL..."
if ! git clone "$REPO_URL" "$WORK_DIR/repo" 2>/dev/null; then
    echo "⚠️ Notice: The wiki repository is not yet initialized on GitHub."
    echo "👉 Please visit https://github.com/Qureon/Locara/wiki once and click 'Create the first page'."
    echo "   After saving the first page, re-run this script to sync all pages automatically!"
    exit 1
fi

echo "📋 Copying wiki pages from $WIKI_SRC..."
cp -r "$WIKI_SRC/"* "$WORK_DIR/repo/"

cd "$WORK_DIR/repo"
if [ -n "${GIT_USER_NAME:-}" ]; then
    git config user.name "$GIT_USER_NAME"
elif ! git config user.name >/dev/null 2>&1; then
    git config user.name "Qureon Automation"
fi

if [ -n "${GIT_USER_EMAIL:-}" ]; then
    git config user.email "$GIT_USER_EMAIL"
elif ! git config user.email >/dev/null 2>&1; then
    git config user.email "opensource@qureon.dev"
fi

git add .
if git diff --staged --quiet; then
    echo "✨ Wiki is already up to date! Nothing to commit."
else
    git commit -m "docs(wiki): synchronize comprehensive Locara documentation pages"
    echo "🚀 Pushing to GitHub Wiki..."
    git push origin master 2>/dev/null || git push origin main 2>/dev/null || git push origin HEAD
    echo "🎉 Successfully synchronized Locara Wiki!"
fi
