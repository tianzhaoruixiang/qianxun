## ADDED Requirements

### Requirement: Chat input provides a multi-line text area
The ChatInput SHALL provide a multi-line text input with placeholder "请输入你想要了解的问题" and auto-resize height.

#### Scenario: User focuses input
- **WHEN** user clicks the input area
- **THEN** the input is focused and a primary-color border glow is applied

### Requirement: Chat input includes data source selector via DataSourceSelector component
The ChatInput SHALL include the model selector and data source selector via the DataSourceSelector component.

#### Scenario: Model and data source selectors render
- **WHEN** the ChatInput renders
- **THEN** a DataSourceSelector component is shown above the input area
- **AND** it provides both model selection (Select dropdown) and data source selection (Popover with checkbox groups)

### Requirement: Chat input no longer has inline hardcoded selectors
The ChatInput SHALL NOT contain any inline model options array, data source options array, or local refs for selectedModel/selectedDataSource.

#### Scenario: Clean component interface
- **WHEN** examining ChatInput's script section
- **THEN** there are no `modelOptions`, `dataSourceOptions`, `selectedModel`, or `selectedDataSource` variables defined locally
- **AND** all selection state is managed by useKnowledgeStore through DataSourceSelector

### Requirement: Chat input provides quick action buttons
The ChatInput SHALL display quick action icon buttons for attachment, image, table, mail, and document.

#### Scenario: User clicks quick action
- **WHEN** user clicks any quick action button
- **THEN** a placeholder notification "功能开发中" is shown

### Requirement: Chat input provides session actions (new chat & history)
The ChatInput SHALL display "新建对话" (New Chat) and "历史会话" (History) buttons on the right side of the header row.

#### Scenario: User clicks new chat
- **WHEN** user clicks the "新建对话" button
- **THEN** a new conversation session is created with cleared messages

#### Scenario: User clicks history
- **WHEN** user clicks the "历史会话" button
- **THEN** a placeholder notification about upcoming feature is shown

### Requirement: Chat input sends message on click or Enter
The ChatInput SHALL send the message when user clicks the send button or presses Enter (without Shift).

#### Scenario: Send via button
- **WHEN** user clicks the circular send button
- **THEN** the input text is sent as a user message
- **AND** the input is cleared

#### Scenario: Send via Enter key
- **WHEN** user presses Enter without Shift
- **THEN** the input text is sent as a user message
- **AND** the input is cleared

#### Scenario: Newline via Shift+Enter
- **WHEN** user presses Shift+Enter
- **THEN** a newline is inserted in the input without sending

### Requirement: Send button is disabled when input is empty
The ChatInput SHALL disable the send button when the input text is empty or whitespace-only.

#### Scenario: Empty input
- **WHEN** the input contains only whitespace
- **THEN** the send button is visually disabled and non-interactive
