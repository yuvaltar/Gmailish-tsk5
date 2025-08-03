#!/usr/bin/env bash
set -euo pipefail

# ── Configuration ────────────────────────────────────────────────────────────
API="http://127.0.0.1:3000/api"
EMAIL_A="yuvaltest@gmailish.com"
PASS_A="Yuv@l123"
EMAIL_B="yuvaltttttttttttest@gmailish.com"
PASS_B="Yuv@l123"

USER_A="48e2b9a8-4540-4404-b71e-caa370976571"
USER_B="9d745844-33aa-4d93-9e67-21f04cbaefa5"

COOKIE_A="cookie-A.txt"
COOKIE_B="cookie-B.txt"

# 1) Login as A
echo "→ Logging in as User A"
curl -i -X POST "$API/tokens" \
  -H "Content-Type: application/json" \
  -c "$COOKIE_A" \
  -d "{\"email\":\"$EMAIL_A\",\"password\":\"$PASS_A\"}"
echo && echo

# 2) Create a draft
echo "→ Creating a draft"
curl -X POST "$API/mails/draft" \
  -b "$COOKIE_A" \
  -H "Content-Type: application/json" \
  -d "{
    \"to\":      \"$USER_B\",
    \"subject\": \"Smoke Test Draft\",
    \"content\": \"This is only a draft.\"
  }"
echo && echo

# 3) List drafts
echo "→ Listing drafts"
curl -X GET "$API/mails?label=drafts" -b "$COOKIE_A"
echo && echo

# 4) Send a real mail A→B
echo "→ 4) SEND A REAL MAIL A→B"
SEND_RESP=$(curl -s -X POST "$API/mails" \
  -b "$COOKIE_A" \
  -H "Content-Type: application/json" \
  -d '{
    "to":      "'"$USER_B"'",
    "subject": "Smoke Test Mail",
    "content": "Hello from A to B!"
  }')

# If you have jq installed:
if command -v jq >/dev/null 2>&1; then
  echo "$SEND_RESP" | jq .
else
  echo "$SEND_RESP"
fi

MAIL_ID=$(echo "$SEND_RESP" | (command -v jq >/dev/null 2>&1 && jq -r .id || awk -F\" '/"id":/ {print $4}'))
echo "  → Sent MAIL_ID=$MAIL_ID"
# 5) List Sent
echo "→ Listing Sent"
curl -X GET "$API/mails?label=sent" -b "$COOKIE_A"
echo && echo

# 6) Search
echo "→ Searching for 'Smoke'"
curl -X GET "$API/mails/search/Smoke" -b "$COOKIE_A"
echo && echo

# 7) Add & remove custom label “work”
echo "→ Adding label 'work'"
curl -X POST "$API/mails/$MAIL_ID/label" -b "$COOKIE_A" \
  -H "Content-Type: application/json" -d '{"label":"work"}'
echo && echo

echo "→ Removing label 'work'"
curl -X DELETE "$API/mails/$MAIL_ID/label/work" -b "$COOKIE_A"
echo && echo

# 8) Star/unstar
echo "→ Toggling star twice"
curl -X POST "$API/mails/$MAIL_ID/star" -b "$COOKIE_A" && echo
curl -X POST "$API/mails/$MAIL_ID/star" -b "$COOKIE_A" && echo
echo && echo

# 9) Mark unread → read
echo "→ Marking unread"
curl -X PATCH "$API/mails/unread" -b "$COOKIE_A" \
  -H "Content-Type: application/json" \
  -d "{\"ids\":[\"$MAIL_ID\"]}"
echo && echo

echo "→ Marking read"
curl -X PATCH "$API/mails/$MAIL_ID/read" -b "$COOKIE_A"
echo && echo

# 10) Update subject & content
echo "→ Updating mail"
curl -X PATCH "$API/mails/$MAIL_ID" -b "$COOKIE_A" \
  -H "Content-Type: application/json" \
  -d "{
    \"subject\":\"Updated Subject\",
    \"content\":\"Updated body text.\"
  }"
echo "  → Fetching updated mail"
curl -X GET "$API/mails/$MAIL_ID" -b "$COOKIE_A"
echo && echo

# 11) Clear A’s trash
echo "→ Clearing trash (A)"
curl -X DELETE "$API/mails/trash" -b "$COOKIE_A"
echo && echo

# 12) Login as B
echo "→ Logging in as User B"
curl -i -X POST "$API/tokens" \
  -H "Content-Type: application/json" \
  -c "$COOKIE_B" \
  -d "{\"email\":\"$EMAIL_B\",\"password\":\"$PASS_B\"}"
echo && echo

# 13) List B’s inbox
echo "→ Listing B’s inbox"
INBOX=$(curl -s -X GET "$API/mails" -b "$COOKIE_B")
echo "$INBOX" | jq .
INBOX_ID=$(echo "$INBOX" | jq -r '.[0].id')
echo "  first INBOX_ID=$INBOX_ID"
echo && echo

# 14) Toggle spam
echo "→ Toggling spam"
curl -X POST "$API/mails/$INBOX_ID/spam" -b "$COOKIE_B"
echo && echo

echo "→ Listing /spam"
curl -X GET "$API/mails/spam" -b "$COOKIE_B"
echo && echo

echo "→ Unmark spam"
curl -X POST "$API/mails/$INBOX_ID/spam" -b "$COOKIE_B"
echo && echo

# 15) Delete mail
echo "→ Deleting mail"
curl -X DELETE "$API/mails/$INBOX_ID" -b "$COOKIE_B"
echo && echo

echo "→ Clearing trash (B)"
curl -X DELETE "$API/mails/trash" -b "$COOKIE_B"
echo && echo

echo "✅ All mail routes exercised successfully!"
