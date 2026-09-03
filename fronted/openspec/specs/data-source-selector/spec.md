## ADDED Requirements

### Requirement: Data source selector provides model selection dropdown
The DataSourceSelector SHALL provide an AI model selection dropdown (simple Select) with at least one default option "千寻大模型".

#### Scenario: User selects a model
- **WHEN** user opens the model selector dropdown
- **THEN** a list of available models is displayed with labels and values
- **AND** the selected model value is stored in useKnowledgeStore

#### Scenario: Default model is pre-selected
- **WHEN** the DataSourceSelector component mounts
- **THEN** "千寻大模型" is selected by default as the initial value

### Requirement: Data source selector provides popover-based knowledge base picker
The DataSourceSelector SHALL provide a data source selector that opens a Popover panel when clicked, displaying three categories of knowledge bases with checkbox groups inside.

#### Scenario: User opens data source popover
- **WHEN** user clicks on the data source selector
- **THEN** a Popover panel appears below/above the trigger element
- **AND** the panel contains three collapsible groups: 知识库, 档案库, 资源库
- **AND** each group has multiple checkbox items for individual knowledge entries

#### Scenario: User selects knowledge items
- **WHEN** user checks/unchecks checkboxes within any knowledge category
- **AND** all checked item IDs are recorded in useKnowledgeStore.selectedKnowledgeIds
- **AND** the data source select display value updates to reflect current selections (e.g., "已选 3 项" or default label)

### Requirement: Data source selector supports per-group select-all
The DataSourceSelector SHALL provide a select-all checkbox at each knowledge category header that toggles all items within that group.

#### Scenario: Select all in a group
- **WHEN** user checks the select-all checkbox of a category
- **THEN** all checkboxes within that category become checked
- **AND** all item IDs are added to useKnowledgeStore.selectedKnowledgeIds

#### Scenario: Indeterminate state for partial selection
- **WHEN** some but not all items in a category are checked
- **THEN** the select-all checkbox shows indeterminate state (partially filled)

### Requirement: Data source selector synchronizes state via shared store
The DataSourceSelector SHALL write all selection state to useKnowledgeStore so it is accessible across components.

#### Scenario: State sharing
- **WHEN** user changes model or toggles any knowledge item checkbox
- **THEN** useKnowledgeStore reactive state updates immediately
- **AND** any component reading this store reflects the change

### Requirement: Data source selector uses horizontal flex layout
The DataSourceSelector SHALL render the model selector and data source selector side-by-side in a horizontal flex layout suitable for placement within ChatInput's input area.

#### Scenario: Inline layout rendering
- **WHEN** the DataSourceSelector is rendered inside ChatInput
- **THEN** both selectors display horizontally with appropriate spacing
- **AND** each selector area has appropriate width (~120px for model, ~140px for data source)
