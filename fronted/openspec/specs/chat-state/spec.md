## ADDED Requirements

### Requirement: Chat store manages message list
The useChatStore SHALL maintain a reactive array of messages with id, role, content, and timestamp fields.

#### Scenario: Add user message
- **WHEN** the `sendMessage` action is called with user text
- **THEN** a new message with `role: 'user'` is appended to the list
- **AND** the message has a unique id and current timestamp

#### Scenario: Add assistant message
- **WHEN** the AI response is received
- **THEN** a new message with `role: 'assistant'` is appended to the list

### Requirement: Chat store manages input text state
The useChatStore SHALL maintain the current input text state and provide an action to clear it after sending.

#### Scenario: Input state tracked
- **WHEN** user types in the input field
- **THEN** the store's input text state is updated
- **AND** when message is sent, the input state is reset to empty string

### Requirement: Chat store manages loading state
The useChatStore SHALL track whether the AI is currently generating a response.

#### Scenario: Loading during response
- **WHEN** a user message is sent
- **THEN** `isLoading` becomes `true`
- **AND** when the response completes, `isLoading` becomes `false`

### Requirement: Chat store supports creating a new conversation
The useChatStore SHALL provide an action to clear all messages and reset to the initial empty state.

#### Scenario: New conversation
- **WHEN** the `newConversation` action is called
- **THEN** the message list is cleared
- **AND** the input text is reset
- **AND** a new conversation id is generated

### Requirement: Chat store simulates AI streaming response
The useChatStore SHALL simulate a streaming AI response by gradually appending characters to the assistant message.

#### Scenario: Streaming response
- **WHEN** a user message is sent
- **THEN** an empty assistant message is immediately added
- **AND** characters are appended incrementally over time
- **AND** `isLoading` is `true` until the full response is displayed
