## ADDED Requirements

### Requirement: Chat view renders the complete AI conversation interface
The ChatView SHALL compose ChatHeader, WelcomeSection, ChatBubble list, and ChatInput into a vertically stacked layout within the main content area.

#### Scenario: User navigates to /chat
- **WHEN** user navigates to `/chat` route
- **THEN** the page displays the header, welcome section (when no messages), and input area

### Requirement: Chat view switches between welcome and conversation modes
The ChatView SHALL display the WelcomeSection when the message list is empty, and display the message bubble list when at least one message exists.

#### Scenario: Empty state shows welcome
- **WHEN** the conversation has zero messages
- **THEN** the WelcomeSection is visible and the bubble list is hidden

#### Scenario: Active conversation hides welcome
- **WHEN** the user sends the first message
- **THEN** the WelcomeSection is hidden and the bubble list becomes visible

### Requirement: Message list auto-scrolls to latest message
The ChatView SHALL auto-scroll the conversation area to the bottom when a new message is added.

#### Scenario: New message arrives
- **WHEN** a new message is added to the store
- **THEN** the conversation container scrolls to the bottom smoothly
