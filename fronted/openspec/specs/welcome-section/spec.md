## ADDED Requirements

### Requirement: Welcome section displays greeting and capability description
The WelcomeSection SHALL display the greeting "你好，我是千寻问答助手" and a capability description text.

#### Scenario: User sees welcome
- **WHEN** the welcome section is rendered
- **THEN** the greeting text is displayed with primary-color accent and larger font size
- **AND** the capability description is displayed below in secondary text color

### Requirement: Welcome section displays assistant avatar
The WelcomeSection SHALL display the assistant avatar (64x64px circular) centered above the greeting text.

#### Scenario: Avatar visible
- **WHEN** the welcome section is rendered
- **THEN** a 64x64 circular avatar image is displayed centered horizontally

### Requirement: Welcome section renders recommended question cards in a grid
The WelcomeSection SHALL render recommended question cards in a 3-column grid layout.

#### Scenario: Cards displayed
- **WHEN** the welcome section is rendered
- **THEN** recommended question cards are displayed in a 3-column grid
- **AND** each card has rounded corners (12px), white background, and subtle border

### Requirement: Clicking a recommended question sends it
The WelcomeSection SHALL populate the input and send the question when a card is clicked.

#### Scenario: Card click sends question
- **WHEN** user clicks a recommended question card
- **THEN** the question text is sent as a user message
- **AND** the conversation switches to active mode (bubbles visible)

### Requirement: Recommended question cards have hover effects
The WelcomeSection SHALL apply a hover effect to cards: border brightens, slight scale (1.02), and shadow deepens.

#### Scenario: Hover on card
- **WHEN** user hovers over a recommended question card
- **THEN** the card border color brightens
- **AND** the card scales to 1.02
- **AND** the shadow deepens
