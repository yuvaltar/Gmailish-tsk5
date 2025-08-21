Gmailish (Task 5) – Android Offline‑First Gmail‑Inspired Client
A modern Android client for a Gmail‑like messaging system, featuring offline-first UX, Room local persistence, JWT auth, labels (with server/local mapping), drafts/outbox, and robust sync. It builds on Task 4’s full‑stack foundation (web + Node backend + C++ filter) while focusing here on the native Android implementation and its end‑to‑end flows.

Note on backend for this task: start the Node server with:

cd server

node app.js

Table of Contents
Overview

Key Features

Architecture

Data Model and Mapping

Offline‑First and Sync Strategy

Android App Structure

API Endpoints Used

Building and Running (Android)

Developer Notes and Gotchas

Suggested Screenshots (what to capture, where to put them)

Roadmap and Extensions

Authors

Overview
The Android Gmailish app provides a complete mail experience with local persistence and offline capabilities:

Works online and offline, queuing operations and syncing when connectivity returns

Persists data locally via Room for fast UI and resilience

Mirrors core mailbox flows (Inbox/Primary, Sent, Drafts, Starred, custom labels)

Uses JWT for authenticated operations against the Node backend

Handles label mapping between local canonical names and server names (e.g., primary ↔ inbox)

Provides end‑to‑end flows from login to compose, search, label management, and detailed mail view

Key Features
Authentication

Login via email/password; JWT stored in SharedPreferences

Session token reused across API calls

Mailbox

All inboxes/Primary, Sent, Drafts, Starred, Archive, Spam, Trash, custom labels

Read/unread state and starring supported

Search across mails

Compose, Drafts, Outbox

Save drafts locally to “drafts” label

Send online (immediate) or offline (queued to Outbox with pending operations)

After connectivity resumes, pending sends are processed

Labels

Create labels, assign/remove on messages

Move messages between labels (offline‑first with server sync)

Offline‑first UX

Read cached mail; open details offline

Queue sends, label changes, and moves as pending ops

Automatic sync worker to process pending operations

Theming and UI polish

Light/Dark mode supported by Android theme

Timestamp normalization and pretty formatting

Avatars: image or initial fallback

Architecture
Presentation

Activities: LoginActivity, RegisterActivity, InboxActivity, MailViewActivity, ComposeActivity, CreateLabelActivity

RecyclerView lists for mail items

ViewModels per screen to handle state and network operations

Domain/Data

Repositories: MailRepository, LabelRepository, UserRepository

Local DB: Room (AppDatabase with DAOs: mailDao, labelDao, mailLabelDao, pendingOperationDao)

Pending operations queue to support offline actions

Networking

OkHttp for REST calls

JWT added as Authorization: Bearer header

Endpoints under http://10.0.2.2:3000/

Storage

SharedPreferences for JWT and basic user profile (id, username)

Room for mails, labels, cross‑refs, and pending operations

Sync

Background worker (SyncPendingWorker) enqueues retries and reconciles local ↔ server

Data Model and Mapping
MailEntity

Fields include id, senderId/name, recipientId/name/email, subject, content, timestamp, ownerId, read, starred, draft flag

LabelEntity

id, ownerId, name

MailLabelCrossRef

mailId, labelId

Label normalization:

Local canonical names: primary, starred, drafts, sent, outbox, archive, spam, trash, promotions, social, updates

Server mapping:

primary (local) ↔ inbox (server)

All other labels pass through lower‑cased (e.g., starred ↔ starred)

Timestamp handling:

Robust parsing for ISO‑8601, epoch, and legacy java.util.Date strings

Pretty formatting: HH:mm for today, otherwise MMM d

Offline‑First and Sync Strategy
Reading

All list/detail views read immediately from Room; network refresh updates Room and UI

Drafts

Saved locally under drafts label; edited drafts loaded by id

Sending

Online: POST to /api/mails; save sent copy locally (sent label), remove draft if applicable

Offline/no token: create Outbox mail + Pending MAIL_SEND; worker sends later

Labeling and Moving

Local UI reflects changes instantly; Room updated first

Online path PATCHes label add/remove

On failure/offline, enqueue Pending LABEL_MOVE/operations, retried by worker

Star toggle

Immediate local toggle + label cross‑ref, then best‑effort server PATCH

Android App Structure
Authentication
LoginActivity + LoginViewModel

POST /api/tokens with email/password

Extract JWT from Set‑Cookie or response; save to SharedPreferences

On success, navigate to Inbox

RegisterActivity + RegisterViewModel

Multipart form to POST /api/users (firstName, lastName, username, password, gender, birthdate, optional picture)

On success, save minimal user to Room and navigate to Login

Inbox
InboxActivity + InboxViewModel + EmailAdapter

Loads current user: GET /api/users/me; caches id/username/pictureUrl

Fetches mail lists:

All mails: GET /api/mails

By label: GET /api/mails?label={serverLabel}

Search: GET /api/mails/search/{query}

Syncs server results into Room and emits LiveData for UI

Unread counts computed from server payload

EmailAdapter

Displays sender, subject, snippet, time; starred state; draft marker

On star click: update local immediately, then PATCH label add/remove

On item click: open MailViewActivity (drafts open ComposeActivity)

Mail View
MailViewActivity + MailViewModel

Loads detail from Room immediately, then refresh via GET /api/mails/{id}

Normalizes labels for UI; marks as read via PATCH /api/mails/{id}/read

Star toggle: add/remove starred label (offline‑first)

Delete: DELETE /api/mails/{id} (local delete on success)

Move to label: offline‑first; local move then server add/remove with fallback queue

Reply/Forward:

Extract sender email if present; else resolve via GET /api/users/{senderId}; else fallback name@gmailish.com

Opens ComposeActivity prefilled (reply fills To; forward leaves To empty)

Compose
ComposeActivity + ComposeViewModel

New compose or edit draft by id

Auto-save draft on back or onPause (unless fields empty)

Send:

Online: POST /api/mails, save local “sent”, remove draft if used

Offline: save to Outbox + enqueue Pending MAIL_SEND + remove draft

Emits sendSuccess payload for optional downstream usage

Labels
CreateLabelActivity

Local create LabelEntity; enqueue Pending LABEL_CREATE; schedule sync

Caches label names in SharedPreferences for pickers

Header and Profile
HeaderManager

Observes current user; displays avatar image or initial

Popup to logout (clears JWT and returns to Login)

API Endpoints Used (server hints)
Auth

POST /api/tokens → returns token (Set‑Cookie or body)

GET /api/users/me → user id, username, pictureUrl

GET /api/users/{id} → for resolving sender emails

Mails

GET /api/mails → list, optionally filtered by label (?label=inbox, updates, etc.)

GET /api/mails/search/{query}

GET /api/mails/{id}

POST /api/mails → send mail

PATCH /api/mails/{id}/read

PATCH /api/mails/{id}/label → {label[, action:"remove"]}

DELETE /api/mails/{id}

Users

POST /api/users → multipart register (form fields + optional picture)

Labels

POST /api/labels → create new label (used by “New label” flow)

Note: For Task 5 Android, the backend is assumed running locally at 10.0.2.2:3000.

Building and Running (Android)
Requirements

Android Studio (Giraffe+), Gradle, Java 11+

Emulator or device

Steps

Ensure the Node backend is running:

cd server

node app.js

Open the Android project in Android Studio

Build and Run on an emulator/device

Register a user in the app, then login

Load Inbox; try compose/send, star, search, offline actions

Emulator Networking

10.0.2.2 points to the host machine; keep server on port 3000

Developer Notes and Gotchas
Label mapping is critical:

UI and Room use primary; server uses inbox

Timestamps arrive in different formats; robust parsing is implemented

Star toggle in Drafts is disabled

Pending operations must be idempotent and retried; ensure worker is scheduled after enqueuing

Compose “sendOffline” removes related draft immediately to avoid duplicates

Email resolution in reply:

Prefer explicit email fields, then server lookup by senderId, then fallback from name

Registration uses multipart with optional image; if backend returns no id, a UUID is generated locally for Room
