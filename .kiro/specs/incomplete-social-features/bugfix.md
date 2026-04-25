# Bugfix Requirements Document

## Introduction

The Android app has multiple incomplete social features that were partially implemented but stopped midway. These include: null display for followers/following counts, inaccessible post detail and chat screens, non-functional comment viewing, missing full-screen image viewer, and missing backend API endpoints for comments and messages. This document defines the requirements to complete these features and integrate them with the existing backend API.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN a user has zero followers or following THEN the profile screen displays "null" instead of "0"

1.2 WHEN a user clicks the comment icon (💬) on a feed post THEN nothing happens (placeholder comment)

1.3 WHEN a user clicks on a post image in the gallery or profile grid THEN the image cannot be viewed in full screen

1.4 WHEN a user wants to view comments on a post THEN there is no way to access the post detail screen

1.5 WHEN a user wants to send a message from the conversations list THEN there is no way to access the chat screen

1.6 WHEN the app tries to fetch comments for a post THEN the API endpoint does not exist in ApiService

1.7 WHEN the app tries to post a comment THEN the API endpoint does not exist in ApiService

1.8 WHEN the app tries to fetch messages for a conversation THEN the API endpoint does not exist in ApiService

1.9 WHEN the app tries to send a message THEN the API endpoint does not exist in ApiService

1.10 WHEN the post detail screen layout (screen_post_detail.xml) exists THEN it is not wired in MainActivity's wireCurrentScreen method

1.11 WHEN the chat screen layout (screen_chat.xml) exists THEN it is not wired in MainActivity's wireCurrentScreen method

### Expected Behavior (Correct)

2.1 WHEN a user has zero followers or following THEN the profile screen SHALL display "0" instead of "null"

2.2 WHEN a user clicks the comment icon (💬) on a feed post THEN the app SHALL navigate to the post detail screen showing the post and its comments

2.3 WHEN a user clicks on a post image in the gallery or profile grid THEN the app SHALL open a full-screen image viewer

2.4 WHEN a user is on the post detail screen THEN the app SHALL display all comments for that post fetched from the backend

2.5 WHEN a user clicks on a conversation in the messages list THEN the app SHALL navigate to the chat screen for that conversation

2.6 WHEN the app needs to fetch comments for a post THEN ApiService SHALL have a GET endpoint: `posts/{id}/comments`

2.7 WHEN the app needs to post a comment THEN ApiService SHALL have a POST endpoint: `posts/{id}/comments`

2.8 WHEN the app needs to fetch messages for a conversation THEN ApiService SHALL have a GET endpoint: `conversations/{id}/messages`

2.9 WHEN the app needs to send a message THEN ApiService SHALL have a POST endpoint: `conversations/{id}/messages`

2.10 WHEN the post detail screen is rendered THEN MainActivity SHALL wire up the back button, like button, comment input, and load comments from the backend

2.11 WHEN the chat screen is rendered THEN MainActivity SHALL wire up the back button, message input, send button, and load messages from the backend

### Unchanged Behavior (Regression Prevention)

3.1 WHEN a user views the feed on the home screen THEN the app SHALL CONTINUE TO display posts with like and comment counts

3.2 WHEN a user likes or unlikes a post THEN the app SHALL CONTINUE TO update the like count and persist to the backend

3.3 WHEN a user views their profile THEN the app SHALL CONTINUE TO display their posts in a grid layout

3.4 WHEN a user views the gallery THEN the app SHALL CONTINUE TO display posts in a 2-column grid with sorting options

3.5 WHEN a user views notifications THEN the app SHALL CONTINUE TO display activity notifications from the backend

3.6 WHEN a user views the messages tab THEN the app SHALL CONTINUE TO display the list of conversations

3.7 WHEN a user navigates using the bottom navigation bar THEN the app SHALL CONTINUE TO switch between gallery, leaderboard, upload, social, and profile screens

3.8 WHEN the app loads data from the backend THEN it SHALL CONTINUE TO use the existing ApiService and bearer token authentication

3.9 WHEN the app displays empty states THEN it SHALL CONTINUE TO show friendly messages when no data is available

3.10 WHEN a user submits a post THEN the app SHALL CONTINUE TO upload the post with caption and media URL to the backend
