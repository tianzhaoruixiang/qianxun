## ADDED Requirements

### Requirement: Chat bubble renders user messages right-aligned
The ChatBubble SHALL render user messages (`role: 'user'`) with right alignment, primary-color background, and white text.

#### Scenario: User sends a message
- **WHEN** a user message is rendered
- **THEN** the bubble is aligned to the right side of the conversation area
- **AND** the background color uses `--color-primary`
- **AND** the text color is white

### Requirement: Chat bubble renders AI replies left-aligned
The ChatBubble SHALL render AI messages (`role: 'assistant'`) with left alignment, white/light background, and primary text color.

#### Scenario: AI replies to user
- **WHEN** an assistant message is rendered
- **THEN** the bubble is aligned to the left side
- **AND** the background color uses `--card-bg` or `--bg-surface`
- **AND** the text color uses `--text-primary`

### Requirement: Chat bubble displays timestamp
The ChatBubble SHALL display a formatted timestamp below each bubble in muted text color.

#### Scenario: Message shows time
- **WHEN** any message bubble is rendered
- **THEN** a formatted time string (HH:mm) is displayed below the bubble

### Requirement: Chat bubble constrains maximum width
The ChatBubble SHALL not exceed 70% of the conversation area width.

#### Scenario: Long message wraps
- **WHEN** a message with long content is rendered
- **THEN** the bubble wraps text and its width does not exceed 70% of the container
