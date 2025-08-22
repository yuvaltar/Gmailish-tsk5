# Gmailish: An Android Email App ✨

Gmailish is a modern Android application designed to mimic the functionality of Gmail. It serves as a client for a custom email backend (built on Node.js and MongoDB as part of this project), emphasizing seamless synchronization, local caching, and robust handling of network interruptions. The app allows users to manage emails, labels, and compositions, and even supports the relevant functionality in "offline-mode", where the user is without an internet connection, queuing actions for automatic syncing when online.

Developed as part of a series of assignments, this project showcases advanced Android development techniques, including MVVM architecture, Room persistence, background workers, and REST API integration. It's built to be user-friendly, with a clean interface supporting light/dark modes and intuitive navigation.

## Demo Video 📹

Watch this 5-minute walkthrough to see Gmailish in action: user registration, email composition, labeling, search, theme toggling, and offline-first features like composing/sending while disconnected with auto-sync on reconnect. The demo also shows backend integration with MongoDB (e.g., refreshing to verify new users, labels, and mail relations) and with room.

[![Watch the demo](https://img.youtube.com/vi/HxQslOWTtrQ/hqdefault.jpg)](https://www.youtube.com/watch?v=HxQslOWTtrQ)

### Key Highlights from the Video
- **Registration**: Create a new account with photo, DOB, gender, and strong password validation.
- **Email Actions**: Send, receive, reply, forward, and view in Sent/Drafts.
- **Labels**: Create custom labels, star/move messages, and see relations in a cross-reference table.
- **Search and Themes**: Quick search within labels; toggle light/dark mode.
- **Offline Demo**: Read emails, compose (saved to pending), label changes—all sync automatically when online.
- **Backend Visibility**: Live MongoDB and "room" refreshes confirm data persistence.


## Expanded Features 🚀
Beyond the basics, Gmailish offers a comprehensive set of capabilities tailored for a Gmail-like experience:

- **User Authentication and Profile Management** 🔑: Secure registration with form validation (e.g., password requirements including uppercase, lowercase, numbers, and symbols) and login using JWT tokens. Profiles include optional photo uploads and basic details like name, birthdate, and gender.
  
- **Inbox and Email Handling** 📥: 
  - Displays emails in categorized tabs (e.g., Primary, Social, Promotions) with unread counts.
  - Supports starring, marking as read, deleting, archiving, and moving emails between labels.
  - Full-text search across subjects and content.
  - Detailed email views with reply, forward, and label assignment options.

- **Composition and Sending** ✍️:
  - Rich compose screen for drafting emails, with auto-save to local drafts.
  - Offline composition queues emails in an "Outbox" for later sending.
  - Reply and forward functionalities prefill recipient, subject, and body intelligently (e.g., "Re:" prefix for replies).

- **Labels and Organization** 🏷️:
  - Create custom labels offline, with pending sync.
  - Assign or remove labels via multi-select dialogs.
  - Special handling for system labels like "Starred", "Drafts", "Sent", and "Spam".

- **Offline-First Design** 📴:
  - Uses Room database to cache emails, labels, and relationships locally.
  - Background worker (WorkManager) retries queued operations (e.g., sends, label creations, moves) upon reconnection.
  - Read cached content, compose, and label emails without internet; changes sync automatically.

- **UI/UX Enhancements** 🎨:
  - Theme toggle between light and dark modes.
  - Pretty timestamps (e.g., "HH:mm" for today, "MMM d" otherwise), supporting multiple formats.
  - Profile popup for quick logout.
  - Responsive RecyclerView for email lists with fade effects for read items.

## Technical Stack 🛠️
The app leverages a modern Android toolkit for efficiency and maintainability:

- **Architecture**: MVVM with Hilt for dependency injection.
- **Networking**: OkHttp for API calls to the backend (e.g., `/api/mails`, `/api/users`).
- **Database**: Room with entities for mails, labels, and pending operations; DAOs for CRUD. Integrated with MongoDB on the backend for persistent storage, handling user data, emails, and labels directly in MongoDB collections as shown in the demo video (e.g., reloading MongoDB to verify creations and updates).
- **UI Components**: RecyclerView for lists, Material Design for buttons/dialogs, Glide for image loading.
- **Background Tasks**: WorkManager for syncing pending actions.
- **Other Libraries**: JSON.org for parsing, Executors for async operations.

Key code highlights include robust timestamp parsing (handling ISO, epoch, and Java Date formats) and label normalization (e.g., mapping "inbox" to "primary" locally).

## How It Works ⚙️
1. **Onboarding**: Users register or log in, with data cached in Room and SharedPreferences.
2. **Inbox Sync**: Fetches emails from the backend, caches them locally, and updates UI via LiveData.
3. **Offline Mode**: Actions like sending or labeling create local changes and queue "pending operations" in Room. A worker monitors connectivity and retries.
4. **Sync Flow**: Upon reconnection, the worker processes queues (e.g., POST to `/api/mails` for sends) and resolves local IDs to server ones.

## 📎 Related Wiki Pages


Running the App: https://github.com/yuvaltar/Gmailish-tsk5/wiki/Running-the-App

Visual User Guide: https://github.com/yuvaltar/Gmailish-tsk5/wiki/Visual-User-Guide

