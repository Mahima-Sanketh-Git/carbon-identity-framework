# OSGi Concepts Used in This Repo

This README explains OSGi from beginner level using examples from the flow orchestration code. Read this together with `FLOW_ORCHESTRATION_OVERVIEW.md`.

## Why OSGi Exists

In a normal Java application, you often start one `main` method and create objects directly:

```java
FlowMgtService service = new FlowMgtService();
```

WSO2 Identity Server is different. It is a large modular server. Many independent modules need to start, stop, provide services, consume services, and be upgraded independently. OSGi provides that runtime module system.

In simple terms:

- A normal Java JAR is just classes.
- An OSGi bundle is a JAR with extra metadata that says what it provides and what it needs.
- The OSGi runtime starts bundles, wires dependencies, and keeps a service registry.

Think of OSGi as a runtime dependency injection and module system for Java server components.

## The Core Terms

### Bundle

A bundle is an OSGi module. In this repo, a Maven module with this packaging becomes an OSGi bundle:

```xml
<packaging>bundle</packaging>
```

Example:

```text
components/flow-orchestration-framework/org.wso2.carbon.identity.flow.mgt
```

This builds the bundle:

```text
org.wso2.carbon.identity.flow.mgt
```

Another example:

```text
components/flow-orchestration-framework/org.wso2.carbon.identity.flow.execution.engine
```

This builds:

```text
org.wso2.carbon.identity.flow.execution.engine
```

### Service

An OSGi service is an object registered in the OSGi service registry. Other bundles can find and use it by type.

In this repo:

```java
bundleContext.registerService(FlowMgtService.class.getName(),
        FlowMgtService.getInstance(), null);
```

This means:

> Register `FlowMgtService.getInstance()` as a service that other bundles can consume as `FlowMgtService`.

### Component

A component is a class managed by OSGi Declarative Services. It usually has:

- `@Component`
- `@Activate`
- `@Deactivate`
- `@Reference`

Example:

```java
@Component(
        name = "flow.mgt.component",
        immediate = true)
public class FlowMgtServiceComponent {
}
```

This tells OSGi:

> This class is a managed component. Start it immediately when its required dependencies are available.

### Declarative Services

Declarative Services, often shortened to DS, is the annotation-based way of declaring OSGi components and dependencies.

These imports are the clue:

```java
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
```

DS is similar in spirit to Spring dependency injection, but it belongs to OSGi.

### Service Registry

The OSGi service registry is a runtime map of service interface to service instance.

For example:

- `FlowMgtServiceComponent` registers `FlowMgtService`.
- `FlowExecutionEngineServiceComponent` declares a reference to `FlowMgtService`.
- OSGi connects them at runtime.

The execution engine does not create `FlowMgtService` directly. It receives it from OSGi.

## Lifecycle: How a Bundle Starts

A simplified startup flow:

1. WSO2 server starts the OSGi runtime.
2. OSGi reads installed bundle metadata.
3. OSGi resolves package imports and exports.
4. OSGi creates Declarative Services components when dependencies are available.
5. OSGi calls `@Activate` methods.
6. Components register services or consume other services.

For flow management:

```java
@Activate
protected void activate(ComponentContext context) {

    BundleContext bundleContext = context.getBundleContext();
    bundleContext.registerService(FlowMgtService.class.getName(),
            FlowMgtService.getInstance(), null);
}
```

Meaning:

> When the flow management component starts, register `FlowMgtService` in the service registry.

For flow execution:

```java
@Reference(
        name = "FlowMgtService",
        service = FlowMgtService.class,
        cardinality = ReferenceCardinality.MANDATORY,
        policy = ReferencePolicy.DYNAMIC,
        unbind = "unsetFlowMgtService")
protected void setFlowMgtService(FlowMgtService flowMgtService) {

    FlowExecutionEngineDataHolder.getInstance().setFlowMgtService(flowMgtService);
}
```

Meaning:

> The flow execution engine cannot work without `FlowMgtService`. When OSGi finds it, call `setFlowMgtService`.

## Bundle Metadata in `pom.xml`

OSGi bundles need metadata in the generated JAR manifest. This repo uses the Apache Felix Maven Bundle Plugin to generate it.

Example from `org.wso2.carbon.identity.flow.mgt/pom.xml`:

```xml
<plugin>
    <groupId>org.apache.felix</groupId>
    <artifactId>maven-bundle-plugin</artifactId>
    <extensions>true</extensions>
    <configuration>
        <instructions>
            <Bundle-SymbolicName>${project.artifactId}</Bundle-SymbolicName>
            <Bundle-Name>${project.artifactId}</Bundle-Name>
            ...
        </instructions>
    </configuration>
</plugin>
```

Important instructions:

- `Bundle-SymbolicName`
  - Unique bundle name in the OSGi runtime.

- `Import-Package`
  - Packages this bundle needs from other bundles.

- `Export-Package`
  - Packages this bundle makes available to other bundles.

- `Private-Package`
  - Packages included inside this bundle but hidden from other bundles.

## Import-Package

`Import-Package` says:

> My bundle uses classes from these packages. OSGi must wire providers for them.

Example from flow execution engine:

```xml
<Import-Package>
    org.wso2.carbon.identity.flow.mgt;
    version="${carbon.identity.package.import.version.range}",
    org.wso2.carbon.identity.flow.mgt.model;
    version="${carbon.identity.package.import.version.range}",
</Import-Package>
```

Meaning:

> The execution engine imports `FlowMgtService` and flow management model classes from another bundle.

If the required package is not exported by any installed bundle, the bundle may fail to resolve.

## Export-Package

`Export-Package` says:

> Other bundles are allowed to use these packages.

Example from flow management:

```xml
<Export-Package>
    !org.wso2.carbon.identity.flow.mgt.internal,
    !org.wso2.carbon.identity.flow.mgt.dao,
    org.wso2.carbon.identity.flow.mgt,
    org.wso2.carbon.identity.flow.mgt.model,
    org.wso2.carbon.identity.flow.mgt.exception;
    version="${carbon.identity.package.export.version}"
</Export-Package>
```

Meaning:

- Public API packages are exported.
- Internal and DAO packages are not exported.

This is why other bundles should use `FlowMgtService`, models, and exceptions, but should not directly use the DAO or internal component classes.

## Private-Package

`Private-Package` says:

> Include these packages in the bundle, but do not expose them to other bundles.

Example:

```xml
<Private-Package>
    org.wso2.carbon.identity.flow.mgt.internal,
    org.wso2.carbon.identity.flow.mgt.dao,
</Private-Package>
```

This is a production-quality boundary. The framework says:

- Service API is public.
- DAO and OSGi wiring internals are private implementation details.

## `@Component`

Example:

```java
@Component(
        name = "flow.execution.engine.component",
        immediate = true)
public class FlowExecutionEngineServiceComponent {
}
```

Important fields:

- `name`
  - Component name visible to OSGi diagnostics.

- `immediate = true`
  - Activate this component as soon as its mandatory references are satisfied.

If `immediate` is false, OSGi may delay activation until some service from that component is requested.

## `@Activate`

`@Activate` marks the method OSGi calls when the component starts.

Example:

```java
@Activate
protected void activate(ComponentContext context) {

    BundleContext bundleContext = context.getBundleContext();
    bundleContext.registerService(FlowExecutionService.class.getName(),
            FlowExecutionService.getInstance(), null);
}
```

Use `@Activate` for startup wiring:

- Register services.
- Initialize caches.
- Register listeners.
- Log successful activation.

Avoid heavy work in activation unless necessary, because it can slow server startup.

## `@Deactivate`

`@Deactivate` marks the method OSGi calls when the component stops.

Example:

```java
@Deactivate
protected void deactivate(ComponentContext context) {

    BundleContext bundleCtx = context.getBundleContext();
    bundleCtx.ungetService(bundleCtx.getServiceReference(FlowExecutionService.class));
}
```

Use `@Deactivate` for cleanup:

- Release services.
- Stop background tasks.
- Clear listeners.
- Close resources if the component owns them.

## `@Reference`

`@Reference` declares a dependency on another OSGi service.

Example:

```java
@Reference(
        name = "configuration.manager",
        service = ConfigurationManager.class,
        cardinality = ReferenceCardinality.MANDATORY,
        policy = ReferencePolicy.DYNAMIC,
        unbind = "unsetConfigurationManager")
protected void setConfigurationManager(ConfigurationManager configurationManager) {

    FlowMgtServiceDataHolder.getInstance().setConfigurationManager(configurationManager);
}
```

Meaning:

> This component needs a `ConfigurationManager`. When one is available, call `setConfigurationManager`. If it disappears, call `unsetConfigurationManager`.

## Cardinality

Cardinality tells OSGi how many matching services are expected.

### Mandatory

```java
cardinality = ReferenceCardinality.MANDATORY
```

Means:

> This component cannot activate unless this dependency exists.

Example:

```java
service = FlowMgtService.class,
cardinality = ReferenceCardinality.MANDATORY
```

The flow execution engine needs flow management to load graph configs.

### Multiple

```java
cardinality = ReferenceCardinality.MULTIPLE
```

Means:

> There can be many services of this type.

Example:

```java
@Reference(
        name = "Executor",
        service = Executor.class,
        cardinality = ReferenceCardinality.MULTIPLE,
        policy = ReferencePolicy.DYNAMIC,
        unbind = "unsetExecutors")
protected void setExecutors(Executor executor) {

    FlowExecutionEngineDataHolder.getInstance().getExecutors().put(executor.getName(), executor);
}
```

This is exactly how pluggable flow executors work.

For your KYB POC, if you create a `KYBVerificationExecutor` and register it as an OSGi service implementing `Executor`, this method should receive it.

## Reference Policy

Policy tells OSGi whether dependency changes can happen while the component is running.

### Dynamic

```java
policy = ReferencePolicy.DYNAMIC
```

Means:

> The referenced service can appear or disappear while this component remains active.

That is why there are both set and unset methods:

```java
protected void setFlowMgtService(FlowMgtService flowMgtService) {
    ...
}

protected void unsetFlowMgtService(FlowMgtService flowMgtService) {
    ...
}
```

In production systems, bundles can restart or services can be replaced, so dynamic references make components more resilient.

## Data Holder Pattern

You will see classes like:

```text
FlowMgtServiceDataHolder
FlowExecutionEngineDataHolder
```

This is a common WSO2 pattern.

OSGi injects dependencies into the component class, then the component stores them in a singleton data holder. Other classes in the same bundle read dependencies from the data holder.

Example:

```java
protected void setOrganizationManager(OrganizationManager organizationManager) {

    FlowMgtServiceDataHolder.getInstance().setOrganizationManager(organizationManager);
}
```

Later, `FlowMgtService` can use:

```java
FlowMgtServiceDataHolder.getInstance().getOrganizationManager()
```

Why this pattern exists:

- Many service classes are singletons.
- OSGi injects only into component instances.
- The data holder bridges OSGi wiring to non-component classes.

Be careful with this pattern:

- Always handle nulls carefully in startup/shutdown paths.
- Do not use the data holder as a random global variable store.
- Keep only shared services and runtime registries there.

## Manual Service Registration

This repo often registers services manually in `@Activate`:

```java
bundleContext.registerService(FlowMgtService.class.getName(),
        FlowMgtService.getInstance(), null);
```

This says:

> Put this singleton object into the service registry.

The first parameter is the service type name. The second is the instance.

Another style is to let DS register the component itself as a service through annotation properties, but this repo commonly uses explicit `BundleContext.registerService`.

## Service Consumption Example: Flow Engine Uses Flow Manager

Flow execution needs graph configs. It does not know how to read flow tables directly.

The wiring is:

1. Flow management bundle activates.
2. It registers `FlowMgtService`.
3. Flow execution bundle has a mandatory reference to `FlowMgtService`.
4. OSGi calls `setFlowMgtService`.
5. Flow execution stores it in `FlowExecutionEngineDataHolder`.
6. Runtime code calls:

```java
FlowExecutionEngineDataHolder.getInstance().getFlowMgtService()
        .getGraphConfig(flowType, tenantId);
```

This keeps module boundaries clean:

- Flow execution imports the service API.
- Flow execution does not import flow management DAO internals.

## Extension Example: Flow Executors

The executor extension point is the best OSGi example for your KYB work.

The interface:

```java
public interface Executor {

    String getName();

    ExecutorResponse execute(FlowExecutionContext context) throws FlowEngineException;

    List<String> getInitiationData();

    ExecutorResponse rollback(FlowExecutionContext context) throws FlowEngineException;
}
```

The flow execution component listens for all services implementing `Executor`:

```java
@Reference(
        name = "Executor",
        service = Executor.class,
        cardinality = ReferenceCardinality.MULTIPLE,
        policy = ReferencePolicy.DYNAMIC,
        unbind = "unsetExecutors")
protected void setExecutors(Executor executor) {

    FlowExecutionEngineDataHolder.getInstance().getExecutors().put(executor.getName(), executor);
}
```

At runtime, `TaskExecutionNode` resolves the executor:

```java
Executor mappedFlowExecutor =
        FlowExecutionEngineDataHolder.getInstance().getExecutors().get(executorName);
```

Then it runs:

```java
ExecutorResponse response = mappedFlowExecutor.execute(context);
```

For a KYB POC:

```java
public class KYBVerificationExecutor implements Executor {

    @Override
    public String getName() {

        return "KYBVerificationExecutor";
    }

    @Override
    public ExecutorResponse execute(FlowExecutionContext context) throws FlowEngineException {

        ExecutorResponse response = new ExecutorResponse();
        response.setResult("COMPLETE");
        return response;
    }

    @Override
    public List<String> getInitiationData() {

        return Collections.emptyList();
    }

    @Override
    public ExecutorResponse rollback(FlowExecutionContext context) throws FlowEngineException {

        return new ExecutorResponse("COMPLETE");
    }
}
```

To make this production-usable, you must also register it as an OSGi service from a component:

```java
@Component(
        name = "kyb.verification.executor.component",
        immediate = true)
public class KYBVerificationExecutorComponent {

    @Activate
    protected void activate(ComponentContext context) {

        context.getBundleContext().registerService(Executor.class.getName(),
                new KYBVerificationExecutor(), null);
    }
}
```

When this bundle starts, the existing flow engine component should receive the executor through `setExecutors`.

## Listener Example

The flow engine also supports multiple listeners:

```java
@Reference(
        name = "FlowExecutionListener",
        service = FlowExecutionListener.class,
        cardinality = ReferenceCardinality.MULTIPLE,
        policy = ReferencePolicy.DYNAMIC,
        unbind = "unsetFlowExecutionListener")
protected void setFlowExecutionListener(FlowExecutionListener flowExecutionListener) {

    FlowExecutionEngineDataHolder.getInstance().getFlowListeners()
            .add(flowExecutionListener);
    FlowExecutionEngineDataHolder.getInstance().getFlowListeners().sort(listenerComparator);
}
```

This lets separate bundles plug behavior into flow execution without modifying `FlowExecutionService`.

Example use cases:

- Input processing.
- Auditing.
- Metrics.
- Pre/post validation.

This is one of the main benefits of OSGi in a large platform.

## Why Some Classes Are Singleton

You will see:

```java
private static final FlowMgtService INSTANCE = new FlowMgtService();

public static FlowMgtService getInstance() {
    return INSTANCE;
}
```

This service instance is then registered with OSGi.

The pattern is:

- Use a singleton for stateless service implementation.
- Register that singleton as an OSGi service.
- Other bundles consume it through references.

Do not assume every class should be singleton. It is common for central services, but not for per-request objects like DTOs and contexts.

## OSGi vs Normal Dependency Injection

Normal Java:

```java
FlowMgtService service = new FlowMgtService();
```

Spring-like DI:

```java
@Autowired
private FlowMgtService service;
```

OSGi DS style:

```java
@Reference(
        service = FlowMgtService.class,
        cardinality = ReferenceCardinality.MANDATORY,
        policy = ReferencePolicy.DYNAMIC,
        unbind = "unsetFlowMgtService")
protected void setFlowMgtService(FlowMgtService flowMgtService) {

    FlowExecutionEngineDataHolder.getInstance().setFlowMgtService(flowMgtService);
}
```

The OSGi style is more explicit because services can appear and disappear at runtime.

## Common Failure Modes

### Component Does Not Activate

Likely reasons:

- A mandatory `@Reference` is missing.
- Imported package cannot be resolved.
- Bundle is not installed.
- Bundle started before a required feature was available.

What to check:

- Activation logs.
- OSGi console bundle state.
- `Import-Package` and `Export-Package`.
- Whether the dependency bundle is included in the product feature.

### Unsupported Executor

If flow execution throws unsupported executor, likely reasons:

- Executor bundle is not installed.
- Executor component did not activate.
- Executor was not registered as an OSGi service.
- `getName()` does not match the name in flow config.
- The package containing `Executor` is not imported/exported correctly.

Debug here:

```java
FlowExecutionEngineServiceComponent.setExecutors
TaskExecutionNode.resolveExecutor
```

### NoClassDefFoundError or ClassNotFoundException

Likely reasons:

- Missing `Import-Package`.
- Package not exported by provider bundle.
- Dependency exists in Maven but not in OSGi runtime.

Maven dependency means compile-time availability. OSGi import/export means runtime availability. You need both.

### Component Activates but Dependency Is Null Later

Likely reasons:

- Dynamic `unset...` method was called.
- Data holder was cleared.
- Another component accessed the data holder too early.

Add breakpoints to both setter and unsetter.

## Debugging OSGi in This Repo

Useful breakpoints:

- `FlowMgtServiceComponent.activate`
- `FlowMgtServiceComponent.setOrganizationManager`
- `FlowMgtServiceComponent.setOrgResourceResolverService`
- `FlowMgtServiceComponent.setConfigurationManager`
- `FlowExecutionEngineServiceComponent.activate`
- `FlowExecutionEngineServiceComponent.setFlowMgtService`
- `FlowExecutionEngineServiceComponent.setExecutors`
- `FlowExecutionEngineServiceComponent.unsetExecutors`
- `TaskExecutionNode.resolveExecutor`

When debugging startup, verify:

- Did the component activate?
- Were mandatory references injected?
- Did service registration run?
- Did consuming components receive the service?
- Did data holder get populated?

## How to Read an OSGi Component Class

Use this checklist:

1. Find `@Component`.
   - What is the component name?
   - Is it immediate?

2. Find `@Activate`.
   - What service does it register?
   - What startup work does it do?

3. Find `@Deactivate`.
   - What cleanup happens?

4. Find `@Reference`.
   - What dependencies does it need?
   - Are they mandatory or multiple?
   - Are they dynamic?
   - Where are they stored?

5. Find data holder usage.
   - Which other classes read those dependencies?

## How to Add a New OSGi Extension for KYB

High-level steps:

1. Create or choose a bundle module.
2. Add dependency on `org.wso2.carbon.identity.flow.execution.engine`.
3. Implement `Executor`.
4. Create a DS component with `@Component`.
5. In `@Activate`, register the executor as an `Executor` service.
6. Ensure `pom.xml` uses `packaging=bundle`.
7. Export only API packages if needed.
8. Keep implementation/internal packages private.
9. Include the bundle in the relevant feature/product build.
10. Debug `setExecutors` to confirm registration.

Minimum skeleton:

```java
@Component(
        name = "kyb.verification.executor.component",
        immediate = true)
public class KYBVerificationExecutorComponent {

    @Activate
    protected void activate(ComponentContext context) {

        context.getBundleContext().registerService(Executor.class.getName(),
                new KYBVerificationExecutor(), null);
    }
}
```

Then your flow step should refer to:

```text
KYBVerificationExecutor
```

That value must match:

```java
public String getName() {
    return "KYBVerificationExecutor";
}
```

## Beginner Mental Model

For this repo, you can think like this:

- Bundle = Maven module deployed into the WSO2 server.
- Component = OSGi-managed startup class.
- Service = Java object published for other bundles.
- Reference = dependency requested from other bundles.
- Data holder = WSO2 pattern for sharing injected services inside a bundle.
- Import package = runtime package dependency.
- Export package = public package offered to other bundles.
- Private package = implementation hidden inside the bundle.

If you understand those, most OSGi code in this repo becomes readable.

## Connection to Flow Orchestration

The flow system uses OSGi because it needs loose coupling:

- Flow management provides `FlowMgtService`.
- Flow execution consumes `FlowMgtService`.
- Flow execution provides `FlowExecutionService`.
- Executors plug into flow execution as separate OSGi services.
- Listeners plug into flow execution as separate OSGi services.

That design is exactly what your KYB project needs: external verification logic should be pluggable, not hardcoded into the core flow engine.

