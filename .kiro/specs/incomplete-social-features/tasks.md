# Implementation Plan

- [x] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** - Incomplete Social Features
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bugs exist
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate the bugs exist
  - **Scoped PBT Approach**: For deterministic bugs, scope the property to the concrete failing cases to ensure reproducibility
  - Test implementation details from Bug Condition in design:
    - formatCount(0) returns null instead of "0"
    - Comment button click does nothing (no navigation to post detail)
    - Image clicks in gallery/profile do nothing (no full-screen viewer)
    - Conversation clicks do nothing (no navigation to chat)
    - Post detail screen (R.layout.screen_post_detail) is not wired in MainActivity
    - Chat screen (R.layout.screen_chat) is not wired in MainActivity
    - API endpoints for comments and messages are missing from ApiService
  - The test assertions should match the Expected Behavior Properties from design
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (this is correct - it proves the bugs exist)
  - Document counterexamples found to understand root cause
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10, 1.11_

- [x] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Existing Functionality
  - **IMPORTANT**: Follow observation-first methodology
  - Observe behavior on UNFIXED code for non-buggy inputs:
    - Feed display with posts, likes, and comment counts works correctly
    - Like/unlike functionality with backend persistence works correctly
    - Profile screen displays user info and posts grid correctly
    - Gallery screen displays 2-column grid with sorting correctly
    - Notifications screen displays correctly
    - Conversations list displays correctly
    - Bottom navigation between screens works correctly
    - Existing API calls (feed, likes, user stats, notifications, conversations) work correctly
    - formatCount(1500) returns "1.5k" correctly
  - Write property-based tests capturing observed behavior patterns from Preservation Requirements
  - Property-based testing generates many test cases for stronger guarantees
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10_

- [x] 3. Fix incomplete social features

  - [x] 3.1 Fix formatCount method for zero values
    - Open `app/src/main/java/com/example/chesh/MainActivity.java`
    - Locate formatCount method (around line 783)
    - Add explicit check for n == 0 at the beginning
    - Return "0" immediately when n == 0
    - _Bug_Condition: isBugCondition(input) where input.type == "follower_count_display" AND input.count == 0_
    - _Expected_Behavior: formatCount(0) returns "0" instead of null_
    - _Preservation: formatCount for non-zero values (1, 999, 1000, 1500, etc.) must remain unchanged_
    - _Requirements: 1.1, 2.1_

  - [x] 3.2 Add missing API endpoints to ApiService
    - Open `app/src/main/java/com/example/chesh/network/ApiService.java`
    - Add GET endpoint: `@GET("posts/{id}/comments") Call<CommentsResponse> getPostComments(@Header("Authorization") String authHeader, @Path("id") long postId);`
    - Add POST endpoint: `@POST("posts/{id}/comments") Call<ResponseBody> postComment(@Header("Authorization") String authHeader, @Path("id") long postId, @Body RequestBody requestBody);`
    - Add GET endpoint: `@GET("conversations/{id}/messages") Call<MessagesResponse> getConversationMessages(@Header("Authorization") String authHeader, @Path("id") long conversationId);`
    - Add POST endpoint: `@POST("conversations/{id}/messages") Call<ResponseBody> postMessage(@Header("Authorization") String authHeader, @Path("id") long conversationId, @Body RequestBody requestBody);`
    - _Bug_Condition: isBugCondition(input) where input.type == "api_call" AND input.endpoint IN ["posts/{id}/comments", "conversations/{id}/messages"]_
    - _Expected_Behavior: All four API methods exist, accept correct parameters, return correct types_
    - _Preservation: All existing API endpoints must remain unchanged_
    - _Requirements: 1.6, 1.7, 1.8, 1.9, 2.6, 2.7, 2.8, 2.9_

  - [x] 3.3 Add comment button click handler to FeedAdapter
    - Open `app/src/main/java/com/example/chesh/network/adapters/FeedAdapter.java`
    - Add method to FeedInteractionListener interface: `void onCommentClicked(FeedPost post, int position);`
    - Replace placeholder comment click handler (line 78) with: `listener.onCommentClicked(post, position);`
    - _Bug_Condition: isBugCondition(input) where input.type == "comment_button_click" AND NOT navigationToPostDetail_
    - _Expected_Behavior: Clicking comment button triggers onCommentClicked callback_
    - _Preservation: Like button functionality must remain unchanged_
    - _Requirements: 1.2, 2.2_

  - [x] 3.4 Add image click handler to GalleryAdapter
    - Open `app/src/main/java/com/example/chesh/network/adapters/GalleryAdapter.java`
    - Add GalleryInteractionListener interface with method: `void onImageClicked(FeedPost post, int position);`
    - Add listener parameter to constructor
    - In onBindViewHolder, add click listener to ivPhoto: `h.ivPhoto.setOnClickListener(v -> listener.onImageClicked(post, position));`
    - _Bug_Condition: isBugCondition(input) where input.type == "image_click" AND NOT fullScreenViewer_
    - _Expected_Behavior: Clicking image triggers onImageClicked callback_
    - _Preservation: Gallery display and layout must remain unchanged_
    - _Requirements: 1.3, 2.3_

  - [x] 3.5 Add image click handler to ProfileGridAdapter
    - Open `app/src/main/java/com/example/chesh/network/adapters/ProfileGridAdapter.java`
    - Add ProfileGridInteractionListener interface with method: `void onImageClicked(UserPost post, int position);`
    - Add listener parameter to constructor
    - In onBindViewHolder, add click listener to ivPhoto: `h.ivPhoto.setOnClickListener(v -> listener.onImageClicked(post, position));`
    - _Bug_Condition: isBugCondition(input) where input.type == "image_click" AND NOT fullScreenViewer_
    - _Expected_Behavior: Clicking image triggers onImageClicked callback_
    - _Preservation: Profile grid display and layout must remain unchanged_
    - _Requirements: 1.3, 2.3_

  - [x] 3.6 Add conversation click handler to ConversationsAdapter
    - Open `app/src/main/java/com/example/chesh/network/adapters/ConversationsAdapter.java`
    - Add ConversationInteractionListener interface with method: `void onConversationClicked(ConversationItem item, int position);`
    - Add listener parameter to constructor
    - In onBindViewHolder, add click listener to itemView: `h.itemView.setOnClickListener(v -> listener.onConversationClicked(item, position));`
    - _Bug_Condition: isBugCondition(input) where input.type == "conversation_click" AND NOT navigationToChat_
    - _Expected_Behavior: Clicking conversation triggers onConversationClicked callback_
    - _Preservation: Conversations list display must remain unchanged_
    - _Requirements: 1.5, 2.5_

  - [x] 3.7 Implement full-screen image viewer in MainActivity
    - Open `app/src/main/java/com/example/chesh/MainActivity.java`
    - Add method: `private void showFullScreenImage(String imageUrl)`
    - Create AlertDialog with custom layout containing full-width ImageView
    - Load image using Glide
    - Add click-to-dismiss functionality
    - Handle null/empty URLs gracefully
    - _Bug_Condition: isBugCondition(input) where input.type == "image_click" AND NOT fullScreenViewer_
    - _Expected_Behavior: Full-screen image viewer opens with zoom and dismiss capabilities_
    - _Preservation: No impact on existing screens_
    - _Requirements: 1.3, 2.3_

  - [x] 3.8 Wire post detail screen in MainActivity
    - Open `app/src/main/java/com/example/chesh/MainActivity.java`
    - Add instance variable: `private long currentPostId = -1;`
    - Add case in wireCurrentScreen for R.layout.screen_post_detail
    - Wire back button to navigate to previous screen
    - Wire like button to call likePost/unlikePost API
    - Wire comment send button to call postComment API endpoint
    - Load post details and comments using getPostComments API endpoint
    - Set up RecyclerView with CommentsAdapter
    - _Bug_Condition: isBugCondition(input) where input.type == "post_detail_screen" AND NOT wiredInMainActivity_
    - _Expected_Behavior: Post detail screen loads comments, wires buttons, displays post_
    - _Preservation: Existing screen wiring must remain unchanged_
    - _Requirements: 1.10, 2.10, 2.4_

  - [x] 3.9 Wire chat screen in MainActivity
    - Open `app/src/main/java/com/example/chesh/MainActivity.java`
    - Add instance variable: `private long currentConversationId = -1;`
    - Add case in wireCurrentScreen for R.layout.screen_chat
    - Wire back button to navigate back to messages screen
    - Wire send button to call postMessage API endpoint
    - Load messages using getConversationMessages API endpoint
    - Set up RecyclerView with ChatMessagesAdapter
    - _Bug_Condition: isBugCondition(input) where input.type == "chat_screen" AND NOT wiredInMainActivity_
    - _Expected_Behavior: Chat screen loads messages, wires buttons, displays conversation_
    - _Preservation: Existing screen wiring must remain unchanged_
    - _Requirements: 1.11, 2.11_

  - [x] 3.10 Update loadGalleryScreen to use new GalleryAdapter with listener
    - In loadGalleryScreen method, update GalleryAdapter instantiation to pass listener
    - Implement onImageClicked to call showFullScreenImage with post's first media URL
    - _Bug_Condition: isBugCondition(input) where input.type == "image_click" AND NOT fullScreenViewer_
    - _Expected_Behavior: Clicking gallery image opens full-screen viewer_
    - _Preservation: Gallery loading and display must remain unchanged_
    - _Requirements: 1.3, 2.3_

  - [x] 3.11 Update loadProfileScreen to use new ProfileGridAdapter with listener
    - In loadProfileScreen method, update ProfileGridAdapter instantiation to pass listener
    - Implement onImageClicked to call showFullScreenImage with post's first media URL
    - _Bug_Condition: isBugCondition(input) where input.type == "image_click" AND NOT fullScreenViewer_
    - _Expected_Behavior: Clicking profile grid image opens full-screen viewer_
    - _Preservation: Profile loading and display must remain unchanged_
    - _Requirements: 1.3, 2.3_

  - [x] 3.12 Update loadHomeScreen to use new FeedAdapter with comment listener
    - In loadHomeScreen method, update FeedAdapter instantiation to pass comment listener
    - Implement onCommentClicked to navigate to post detail screen with post ID
    - Set currentPostId before navigation
    - _Bug_Condition: isBugCondition(input) where input.type == "comment_button_click" AND NOT navigationToPostDetail_
    - _Expected_Behavior: Clicking comment button navigates to post detail screen_
    - _Preservation: Feed loading and like functionality must remain unchanged_
    - _Requirements: 1.2, 2.2_

  - [x] 3.13 Update loadMessagesScreen to use new ConversationsAdapter with listener
    - In loadMessagesScreen method, update ConversationsAdapter instantiation to pass listener
    - Implement onConversationClicked to navigate to chat screen with conversation ID
    - Set currentConversationId before navigation
    - _Bug_Condition: isBugCondition(input) where input.type == "conversation_click" AND NOT navigationToChat_
    - _Expected_Behavior: Clicking conversation navigates to chat screen_
    - _Preservation: Messages list loading and display must remain unchanged_
    - _Requirements: 1.5, 2.5_

  - [x] 3.14 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Incomplete Social Features Fixed
    - **IMPORTANT**: Re-run the SAME test from task 1 - do NOT write a new test
    - The test from task 1 encodes the expected behavior
    - When this test passes, it confirms the expected behavior is satisfied
    - Run bug condition exploration test from step 1
    - **EXPECTED OUTCOME**: Test PASSES (confirms bugs are fixed)
    - _Requirements: Expected Behavior Properties from design_

  - [x] 3.15 Verify preservation tests still pass
    - **Property 2: Preservation** - Existing Functionality Unchanged
    - **IMPORTANT**: Re-run the SAME tests from task 2 - do NOT write new tests
    - Run preservation property tests from step 2
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)
    - Confirm all tests still pass after fix (no regressions)

- [x] 4. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.
