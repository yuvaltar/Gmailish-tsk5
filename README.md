# Gmailish-tsk4: Full-Stack Gmail-Inspired Messaging Platform

**GitHub Repository**  
[https://github.com/yuvaltar/Gmailish-tsk4.git](https://github.com/yuvaltar/Gmailish-tsk4.git)

## Table of Contents
- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [How It Works](#how-it-works)
- [Supported API Routes](#supported-api-routes)
- [Persistence](#persistence)
- [Building and Running](#building-and-running)
- [Docker Setup](#docker-setup)
- [Screenshots](#screenshots)
- [Jira Link](#jira-link)
- [Authors](#authors)

## Overview

Gmailish-tsk4 is a comprehensive **full-stack messaging platform** that brings together the robust backend from task 3 with a modern React frontend. This Gmail-inspired application delivers a complete user experience with **real-time messaging**, **intelligent spam filtering**, and **responsive design**. The system combines a **RESTful Node.js backend**, a **dynamic React frontend**, and a **high-performance C++ Bloom Filter service** for URL blacklisting - all orchestrated through **Docker** for seamless deployment and scalability.

## Features

**🎨 Modern User Interface**
- **Dark mode and light mode toggle**
- **Responsive sidebar** with collapse functionality (hamburger menu)
- **Gmail-inspired design** and layout
- **Real-time updates** without page refreshes

**📧 Email Management**
- **Complete inbox management** with read/unread status
- **Label-based organization** system
- **Default labels**: Starred, Archived, Deleted, Drafts, and more
- **Pagination system** (50 mails per page with navigation arrows)
- **Email composition** and editing capabilities

**🛡️ Security & Authentication**
- **JWT-based authentication** with 2-hour session duration
- **Strong password requirements** (8+ characters, uppercase, lowercase, number, special character)
- **Username uniqueness validation**
- **Secure user registration** and login

**🚫 Intelligent Spam Protection**
- **Advanced spam detection** using Bloom Filter technology
- **URL-based blacklisting** - emails containing flagged URLs automatically go to spam
- **Real-time spam classification** and filtering

**👤 User Profile Management**
- **Profile picture upload** and display
- **Comprehensive user registration** with validation
- **Persistent user sessions** with JWT tokens

**⚡ Performance & Scalability**
- **Component-based React architecture** for optimal rendering
- **Real-time data fetching** and updates
- **Modular backend** with RESTful API design

## Architecture

The system follows a modern **three-tier architecture**:

**Frontend Layer (React - Port 3001)**
- **Component-based UI** with pages and reusable components
- **State management** using React hooks
- **Real-time communication** with backend APIs
- **Responsive design** with theme switching capabilities

**Backend Layer (Express.js - Port 3000)**
- **RESTful API server** handling all business logic
- **JWT-based authentication** middleware
- **In-memory data storage** for users, emails, and labels
- **Integration with Bloom Filter** service for spam detection

**Filter Service Layer (C++ - Port 4000)**
- **High-performance Bloom Filter** implementation
- **TCP server** for URL blacklist management
- **Persistent blacklist data** storage
- **Real-time spam URL detection** and classification

## How It Works

The Gmailish platform orchestrates **seamless communication** between its three core services. Users interact with the **intuitive React frontend**, which communicates with the Express backend through **secure JWT-authenticated API calls**. The backend processes all **email operations**, **user management**, and **label organization** while consulting the **C++ Bloom Filter service** for **intelligent spam detection**. When emails are sent, **URLs within the content are analyzed** against the blacklist, and **suspicious emails are automatically routed** to the spam folder. The system maintains **session persistence** through JWT tokens, allowing users to stay logged in for up to **2 hours without re-authentication**.

## Supported API Routes

The backend maintains the same comprehensive RESTful API from task 3, including:

- **User Management**: Registration, authentication, and profile operations
- **Email Operations**: Send, receive, update, delete, and search functionality  
- **Label Management**: Create, update, delete, and organize email labels
- **Blacklist Control**: Add/remove URLs from spam detection system
- **Authentication**: JWT token generation and validation

All routes are secured with appropriate authentication middleware and input validation.

## Persistence

Currently, the application uses an in-memory storage system:
- **Application Data**: Users, emails, and labels are stored in memory on the web server
- **Blacklist Data**: Persistently stored by the Bloom Filter service to disk
- **Session Data**: JWT tokens manage user sessions with 2-hour expiration

*Note: Application data is reset upon server restart, while blacklist data persists across restarts.*

## Building and Running

### Local Development

**Prerequisites**: Node.js, npm, and a Linux environment for the C++ Bloom Filter service.

**1. Start the React Frontend:**

*cd react*

*npm start*


*Runs on port 3001

**2. Start the Express Backend:**

*cd server*

*node app.js*

*Runs on port 3000

**3. Start the Bloom Filter Service (Linux only):**

*cd src*

*g++ main.cpp BloomFilter/BloomFilter.cpp BloomFilter/BlackList.cpp BloomFilter/url.cpp server/server.cpp server/SessionHandler.cpp server/CommandManager.cpp -I. -IBloomFilter -Iserver -o cpp_server -pthread -std=gnu++17*

*./cpp_server 4000 1024 3 5*


*Runs on port 4000

### Access the Application
Navigate to `http://localhost:3001` to access the Gmailish platform.

## Docker Setup

### Build and Run All Services
**Build and start all containers:**

*docker-compose up --build*

**Run without rebuilding:**

*docker-compose up*


## Screenshots

### 1. Setup & Docker

![Docker running by terminal](<screenshots/20. docker running by terminal.png>)
*Docker containers running and monitored through terminal interface*

![All three containers working](<screenshots/1. all 3 containers working .png>)
*All three Docker containers running successfully, you can see how the "docker-compose" command successfully creates the 3 containers in the Docker Desktop*

### 2. User Registration & Authentication

![Successful user registration](<screenshots/2. registration success.png>)
*Successful user registration with profile picture upload*

![Username uniqueness validation](<screenshots/3. cant register with the same username.png>)
*Validation preventing duplicate username registrations*

![Strong password validation](<screenshots/4. registration failure because Password isnt safe.png>)
*Enforcing strong password requirements for security*

### 3. Interface & Display Modes

![Light vs Dark mode comparison](<screenshots/5. dark mode vs light mode.png>)
*Interface shown in both light and dark modes*

![Collapsed sidebar in both modes](<screenshots/6. Dark mode vs light mode collapsed.png>)
*Responsive sidebar collapsed in both light and dark themes*

### 4. Core Email Functionality

![Sending and receiving mail](<screenshots/7. sending a mail from one user to another ( one will appear in sent the other in inbox) .png>)
*Email sent from one user appears in the recipient's inbox and the sender's "Sent" folder*

![Read vs Unread email status](<screenshots/8. a difference between a mail that was read to a mail which wasnt marked as read.png>)
*Visual distinction between emails that have been read and those that are unread*

![Starring an email](<screenshots/9. toggle a mail with a star would make it in the star labels.png>)
*Starring an email correctly adds it to the "Starred" label*

![Pagination for large email volumes](<screenshots/21. more than 50 mails can go to another page of mails.png>)
*Pagination system allowing navigation through more than 50 emails across multiple pages*

![Creating a draft email](<screenshots/22. creating a mail. without sendong will be stored at the draft.png>)
*Composing an email without sending automatically saves it as a draft*

![Draft email appearing in drafts folder](<screenshots/23. appearing in the draft.png>)
*Unsent email successfully stored and displayed in the Drafts folder*

### 5. Label Management

![Creating custom labels](<screenshots/10. creating 3 more labels named work project and school.png>)
*Creating new custom labels: "work", "project", and "school"*

![Detailed mail view](<screenshots/11. mail view.png>)
*Interface for viewing the full content of a single email*

![Assigning labels from mail view](<screenshots/12. we can like lables through the mailview (including the mails we just have created).png>)
*Applying labels directly from the detailed mail view*

![Viewing emails by label](<screenshots/13. work is labeled so we can see it in the Work lables .png>)
*Filtering and viewing all emails assigned the "Work" label*

![Bulk email operations](<screenshots/14. we can mark all mails read unread or other label.png>)
*Selecting multiple emails to perform bulk actions like marking as read or assigning labels*

### 6. Advanced Features & Security

![Search functionality](<screenshots/15. search functionality.png>)
*Searching the inbox for specific email content*

![JWT token for logged-in user](<screenshots/16. having a unique token (jwt) for each user logged in before logged out.png>)
*A unique JWT is present for an authenticated user session*

![JWT token after logout](<screenshots/17. no jwt after logging out.png>)
*The JWT token is successfully cleared after the user logs out*

![Spam detection for malicious URL](<screenshots/18. having a bad url in the spam.png>)
*An email containing a blacklisted URL is automatically moved to the spam folder*

![Persistent spam filtering](<screenshots/19. sending the same bad url in a different mail and it goes directly to spam.png>)
*Subsequent emails with the same malicious URL are also automatically filtered to spam*


## Jira Link

Project planning and task tracking are managed in Jira:
*[Jira link to be provided]*

## Authors

- **Yuval Tarnopolsky**
- **Tal Amitay**  
- **Itay Smouha**
