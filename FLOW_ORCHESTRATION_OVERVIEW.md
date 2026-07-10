# Flow Orchestration Overview

This note explains the current flow orchestration code in this repository and how it relates to a POC for organization onboarding with KYB integration.

## Big Picture

The flow framework is split into two backend bundles:

- `org.wso2.carbon.identity.flow.mgt`
  - Owns flow definition management.
  - Accepts a `FlowDTO` made of ordered/admin-authored `StepDTO` objects.
  - Converts steps into an executable graph.
  - Persists the graph, page content, executor metadata, and edges in database tables.
  - Resolves inherited flow definitions across the organization hierarchy.

- `org.wso2.carbon.identity.flow.execution.engine`
  - Owns runtime execution.
  - Starts or resumes a flow execution context.
  - Loads the configured graph through `FlowMgtService`.
  - Walks graph nodes until the next UI prompt, redirect, WebAuthn challenge, internal prompt, or completion.
  - Invokes pluggable `Executor` services for business logic.

For your KYB project, think of flow management as the admin-side journey designer backend, and flow execution as the self-service portal runtime backend.

## Important Files

- Flow management service:
  - `components/flow-orchestration-framework/org.wso2.carbon.identity.flow.mgt/src/main/java/org/wso2/carbon/identity/flow/mgt/FlowMgtService.java`

- Flow management OSGi component:
  - `components/flow-orchestration-framework/org.wso2.carbon.identity.flow.mgt/src/main/java/org/wso2/carbon/identity/flow/mgt/internal/FlowMgtServiceComponent.java`

- Graph builder:
  - `components/flow-orchestration-framework/org.wso2.carbon.identity.flow.mgt/src/main/java/org/wso2/carbon/identity/flow/mgt/utils/GraphBuilder.java`

- Flow DAO:
  - `components/flow-orchestration-framework/org.wso2.carbon.identity.flow.mgt/src/main/java/org/wso2/carbon/identity/flow/mgt/dao/FlowDAOImpl.java`
  - `components/flow-orchestration-framework/org.wso2.carbon.identity.flow.mgt/src/main/java/org/wso2/carbon/identity/flow/mgt/dao/CacheBackedFlowDAOImpl.java`

- Runtime service:
  - `components/flow-orchestration-framework/org.wso2.carbon.identity.flow.execution.engine/src/main/java/org/wso2/carbon/identity/flow/execution/engine/FlowExecutionService.java`

- Runtime engine:
  - `components/flow-orchestration-framework/org.wso2.carbon.identity.flow.execution.engine/src/main/java/org/wso2/carbon/identity/flow/execution/engine/core/FlowExecutionEngine.java`

- Runtime OSGi component:
  - `components/flow-orchestration-framework/org.wso2.carbon.identity.flow.execution.engine/src/main/java/org/wso2/carbon/identity/flow/execution/engine/internal/FlowExecutionEngineServiceComponent.java`

- Executor extension point:
  - `components/flow-orchestration-framework/org.wso2.carbon.identity.flow.execution.engine/src/main/java/org/wso2/carbon/identity/flow/execution/engine/graph/Executor.java`
  - `components/flow-orchestration-framework/org.wso2.carbon.identity.flow.execution.engine/src/main/java/org/wso2/carbon/identity/flow/execution/engine/graph/TaskExecutionNode.java`

- Endpoint utility used by web/self-service layer:
  - `components/identity-mgt/org.wso2.carbon.identity.mgt.endpoint.util/src/main/java/org/wso2/carbon/identity/mgt/endpoint/util/client/FlowDataRetrievalClient.java`

## FlowMgtService

`FlowMgtService` is a singleton OSGi service. Its main responsibilities are:

- `updateFlow(FlowDTO flowDTO, int tenantID)`
  - Clears organization flow-resolution cache for the tenant.
  - Converts admin-facing steps into a graph using `GraphBuilder`.
  - Persists the graph through `CacheBackedFlowDAOImpl`.
  - Writes an audit log.

- `getFlow(String flowType, int tenantID)`
  - Returns the admin-facing flow model.
  - First resolves which tenant actually owns the flow. A child organization can inherit a flow from an ancestor organization.

- `getGraphConfig(String flowType, int tenantID)`
  - Returns the executable graph used by the runtime engine.
  - Uses the same organization hierarchy resolution logic as `getFlow`.

- `deleteFlow(String flowType, int tenantID)`
  - Deletes only for non-root organizations.
  - Root/primary organization flow deletion is intentionally blocked by returning early.

- `getFlowConfig`, `getFlowConfigs`, `updateFlowConfig`
  - Manage flow-level settings using `ConfigurationManager`.
  - Example: auto-login on flow completion.

The important organization inheritance logic is `getFirstTenantWithFlow`. It resolves the current org ID, asks `OrgResourceResolverService` to walk the hierarchy, and returns the first tenant that has a non-empty flow.

## FlowMgtServiceComponent

`FlowMgtServiceComponent` is the OSGi Declarative Services activator for flow management.

On activation it registers:

- `FlowMgtService`
- `FlowAIService`

It also receives and stores required services in `FlowMgtServiceDataHolder`:

- `OrganizationManager`
- `OrgResourceResolverService`
- `ConfigurationManager`

This is standard WSO2/Carbon OSGi wiring. In production, you usually do not instantiate these dependencies manually; OSGi injects them.

## Flow Data Model

The admin or UI builder works mostly with:

- `FlowDTO`
  - `flowType`
  - `List<StepDTO> steps`

- `StepDTO`
  - Step ID.
  - Step type.
  - UI coordinate metadata.
  - `DataDTO` containing components, action, validations, etc.

- `ComponentDTO`
  - UI components such as form, input, captcha, button.

- `ActionDTO`
  - `NEXT`: go to another step.
  - `EXECUTOR`: run backend logic.

- `ExecutorDTO`
  - Executor name.
  - Executor metadata.

The runtime works mostly with:

- `GraphConfig`
  - Graph ID.
  - First node ID.
  - `Map<String, NodeConfig> nodeConfigs`
  - `Map<String, StepDTO> nodePageMappings`

- `NodeConfig`
  - Node ID.
  - Node type.
  - Edges.
  - Executor config.

- `NodeEdge`
  - Source node.
  - Target node.
  - Triggering action/button ID.

## Step Types and Node Types

Defined step types:

- `VIEW`
  - A UI page prompt.
  - Buttons become graph edges.
  - Multiple buttons can become a `DECISION` node.

- `EXECUTION`
  - Runs an executor directly.

- `REDIRECTION`
  - Runs an executor that produces an external redirect.

- `WEBAUTHN`
  - Runs an executor that produces WebAuthn data.

- `USER_ONBOARD`
  - Special task execution step using `UserOnboardingExecutor`.

- `END`
  - Explicit flow end. Usually resolves to a completion redirect.

Runtime node types:

- `PROMPT_ONLY`
  - Shows a view and waits for an action.

- `DECISION`
  - Chooses the next node based on the submitted action ID.

- `TASK_EXECUTION`
  - Resolves and invokes an `Executor`.

## GraphBuilder

`GraphBuilder` converts `FlowDTO.steps` into a `GraphConfig`.

Key rules:

- `VIEW` steps must have components.
- Buttons must have actions.
- Button action type can be `NEXT` or `EXECUTOR`.
- A view step can have only one executor action.
- The graph must have exactly one first node.
- Invalid `nextId` references fail during graph build.
- `USER_ONBOARD` becomes a task execution node with executor name `UserOnboardingExecutor`.

This class is very important for the drag-and-drop UI. The UI eventually needs to emit a valid `FlowDTO` that satisfies these rules.

## Persistence

`FlowDAOImpl.updateFlow` replaces the active flow for a tenant and flow type in one transaction:

1. Deletes the current active flow.
2. Inserts the flow row.
3. Inserts graph nodes.
4. Inserts executor info and executor metadata.
5. Inserts node edges.
6. Inserts page content and UI metadata.

`CacheBackedFlowDAOImpl` adds cache handling for:

- Admin-facing `FlowDTO`
- Runtime `GraphConfig`

When a flow changes, both flow and graph caches are cleared.

## FlowExecutionService

`FlowExecutionService.executeFlow(...)` is the public runtime entry point.

Parameters:

- `tenantDomain`
- `applicationId`
- `flowId`
- `actionId`
- `flowType`
- `inputs`

Lifecycle:

1. If `flowId` is blank, this is a new flow.
   - `FlowExecutionEngineUtils.initiateContext` creates a `FlowExecutionContext`.
   - It loads `GraphConfig` from `FlowMgtService.getGraphConfig`.

2. If `flowId` is present, this is a resumed flow.
   - Context is loaded from `FlowExecCtxCache`.
   - The optimized cached context gets its graph repopulated from `FlowMgtService`.

3. Inputs are merged into `context.userInputData`.

4. `context.currentActionId` is set.

5. Pre-execution listeners run.

6. `FlowExecutionEngine.execute(context)` walks the graph.

7. Post-execution listeners run.

8. If complete:
   - Context is removed from cache.
   - Optional authentication assertion is generated for redirect completion.

9. If incomplete:
   - Context is cached for the next browser/API call.

On failures in registration flow, it publishes registration failure events and rolls back completed task execution nodes.

## FlowExecutionEngine

The engine walks the graph in a loop.

For each node:

1. It runs temporary input validation.
2. It triggers the node:
   - `DECISION` -> `UserChoiceDecisionNode`
   - `TASK_EXECUTION` -> `TaskExecutionNode`
   - `PROMPT_ONLY` -> `PagePromptNode`

3. If node status is `COMPLETE`, it moves to the next node.

4. If node status is `INCOMPLETE`, it returns a `FlowExecutionStep` to the API/UI.

Returned step types:

- `VIEW`: UI should render components.
- `REDIRECTION`: UI/client should redirect to returned URL.
- `WEBAUTHN`: UI/client should handle WebAuthn data.
- `INTERNAL_PROMPT`: client/backend prompt for required data.
- `COMPLETE`: flow is done.

## Executor Extension Point

Executors implement:

```java
public interface Executor {
    String getName();
    ExecutorResponse execute(FlowExecutionContext context) throws FlowEngineException;
    List<String> getInitiationData();
    ExecutorResponse rollback(FlowExecutionContext context) throws FlowEngineException;
}
```

`FlowExecutionEngineServiceComponent` dynamically registers all OSGi services implementing `Executor` into:

```java
FlowExecutionEngineDataHolder.getInstance().getExecutors()
```

`TaskExecutionNode` resolves an executor by name from `NodeConfig.executorConfig.name`.

Important executor response statuses:

- `COMPLETE`
  - Node is complete.
  - Updated user claims and credentials are merged into `FlowUser`.

- `USER_INPUT_REQUIRED`
  - Return a `VIEW` step with required/optional inputs.

- `CLIENT_INPUT_REQUIRED`
  - Return an `INTERNAL_PROMPT` step.

- `EXTERNAL_REDIRECTION`
  - Return a `REDIRECTION` step. `additionalInfo` must contain `redirectUrl`.

- `WEBAUTHN`
  - Return a `WEBAUTHN` step. `additionalInfo` must contain `webAuthnData`.

- `RETRY`
  - Return the same UI view with an error.

- `USER_ERROR`
  - Throw client error.

- `ERROR`
  - Throw server error.

For KYB, a sample executor could be named something like `KYBVerificationExecutor`. The flow definition would include an `EXECUTION` step or button `EXECUTOR` action with this executor name.

## Current Gap for KYB

This repository contains the framework and executor interface. In this checkout, concrete production executors such as `UserOnboardingExecutor` are not visible in the flow framework module. For a POC you have two practical paths:

1. Add a simple sample executor bundle/module in this repo that implements `Executor`.
2. Locate the product/repo module that already contributes registration executors and add the KYB sample there.

For a 5-day POC, the fastest path is usually a sample `KYBVerificationExecutor` that:

- Reads organization fields from `FlowExecutionContext.userInputData`.
- Calls a fake/stub KYB service first.
- Stores KYB status in `contextProperties`.
- Returns `COMPLETE`, `RETRY`, `USER_INPUT_REQUIRED`, or `EXTERNAL_REDIRECTION` depending on the scenario.

After that works, replace the fake call with GLEIF/KYB REST integration.

## Suggested POC Architecture

Minimum viable POC:

1. Reuse `REGISTRATION` or current self-registration flow type.
2. Add organization fields to a `VIEW` step:
   - Legal name.
   - Registration number.
   - Country.
   - LEI if available.

3. Add an `EXECUTION` step:
   - Executor name: `KYBVerificationExecutor`.
   - Metadata: endpoint URL, timeout, provider name, mode.

4. Executor behavior:
   - Validate required organization fields.
   - Call KYB provider or mock service.
   - If verified, add status to context and return `COMPLETE`.
   - If not verified, return `RETRY` or `USER_INPUT_REQUIRED`.
   - If async/manual review is needed, return `EXTERNAL_REDIRECTION` or introduce a persisted pending state in the org onboarding layer.

5. Continue to `USER_ONBOARD` or future organization onboarding executor.

For the real project, organization onboarding should not remain modeled as user registration forever. The POC can prove orchestration and connector behavior on self-registration, but the product design should eventually introduce an organization onboarding flow type, organization-specific context, and pending organization state.

## Async KYB Considerations

The current execution engine caches incomplete flow contexts. That works for browser-session style waits, but serious KYB async workflows need durable state:

- Organization created in `PENDING_APPROVAL` or equivalent state.
- KYB request ID stored against organization/onboarding request.
- Webhook endpoint receives provider result.
- Webhook correlates result to onboarding request.
- Admin/manual approval can move pending organization to active/rejected.

Do not rely only on `FlowExecCtxCache` for long-running KYB waits. Cache can expire or be evicted.

## Debugging Breakpoints

Start with these breakpoints:

- `FlowMgtService.updateFlow`
  - See how a UI/admin `FlowDTO` becomes a graph.

- `GraphBuilder.withSteps`
  - Watch each step become node(s).

- `GraphBuilder.resolveGraphEdgesAndFirstNode`
  - Debug broken graph references and first-node errors.

- `FlowDAOImpl.updateFlow`
  - See exact DB persistence flow.

- `FlowExecutionService.executeFlow`
  - Main runtime entry point.

- `FlowExecutionEngineUtils.initiateContext`
  - Confirm tenant, flow type, graph loading, and flow config.

- `FlowExecutionEngine.execute`
  - Step through graph traversal.

- `TaskExecutionNode.triggerExecutor`
  - Confirm executor lookup and response handling.

- `PagePromptNode.execute`
  - Understand how button/action submission moves the flow.

- `UserChoiceDecisionNode.execute`
  - Understand multi-button branching.

When debugging OSGi services, also watch:

- `FlowMgtServiceComponent.activate`
- `FlowExecutionEngineServiceComponent.activate`
- `FlowExecutionEngineServiceComponent.setExecutors`

If `TaskExecutionNode` says unsupported executor, check whether your executor bundle is activated and registered as an OSGi service.

## Useful Unit Tests

Read these tests as executable documentation:

- `FlowMgtServiceTest`
  - Valid flow update/get.
  - Graph generation.
  - Invalid step validation.
  - Multiple first node cases.

- `FlowServiceTest`
  - New flow execution.
  - Resume flow execution.
  - Complete vs incomplete behavior.

- `FlowEngineTest`
  - Runtime graph walking behavior.

- `TaskExecutionNodeTest`
  - Executor response handling.

- `PagePromptNodeTest`
  - Prompt/action behavior.

Run targeted tests while changing this area instead of building the full repository every time.

## Mental Model for Java Production Code Here

Patterns used heavily in this codebase:

- OSGi Declarative Services for dependency injection.
- Singleton services and data holders.
- DAO layer with `JdbcTemplate`.
- DTO/model classes for API and persistence payloads.
- Carbon caches for tenant-aware caching.
- Checked framework exceptions split into client/server error types.
- TestNG + Mockito/PowerMockito style static mocking.
- Tenant and organization resolution through Carbon utilities and organization management services.

When adding code:

- Keep tenant ID/domain handling explicit.
- Use existing exception helpers and error-code patterns.
- Avoid storing secrets in flow metadata.
- Avoid putting long-running KYB state only in cache.
- Add tests for graph validation, executor status handling, and failure paths.
- Keep executor names stable because flow definitions reference them by string.

## 5-Day POC Plan

Day 1:

- Run and debug existing flow management and execution tests.
- Create a small flow JSON with one org-details view and one execution step.
- Confirm `GraphBuilder` produces the graph you expect.

Day 2:

- Implement or locate a sample executor registration point.
- Add `KYBVerificationExecutor` with stubbed responses.
- Make it return `COMPLETE`, `RETRY`, and `USER_INPUT_REQUIRED`.

Day 3:

- Wire the flow definition to call the executor.
- Execute through `/api/server/v1/flow/execute` or the local service path.
- Confirm `FlowExecutionStep` responses are understandable by the frontend.

Day 4:

- Replace the stub with a simple REST call or mock HTTP server.
- Store provider result and request ID in context properties.
- Add tests for success, retry, and provider failure.

Day 5:

- Demo flow:
  - Fill organization details.
  - Run KYB verification.
  - Show verified or pending/manual-review path.
  - Continue to onboarding/completion.

Keep the POC small. Prove that an admin-configured step can invoke a pluggable connector and influence the journey.

