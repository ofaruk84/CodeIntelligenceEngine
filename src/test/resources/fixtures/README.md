# Synthetic analysis fixtures

All Java sources here are authored synthetic examples. Maven copies them as test resources; they are analyzed as input and never compiled as engine sources. No fixture build or dependency download is needed.

- `commerce/src/main/java/com/example/commerce`: twelve source files with service collaborators and call sites.
- `commerce/src/test/java/com/example/commerce/OrderScenario.java`: a standard test source root, included in default scanning.
- `multi-module/orders/src/main/java` and `multi-module/payments/src/main/java`: two layout-only module roots, without build descriptors.
- `malformed/Broken.java`: intentionally invalid syntax, isolated from commerce. Batch analysis must report a partial failed unit and keep valid units.

## Expected source relationships

These describe explicit source calls, not established resolved graph edges at this stage.

| Caller | Explicit call | Intended collaborator |
| --- | --- | --- |
| OrderController.submit | orders.place(orderId) | OrderService.place |
| OrderService.place | payments.pay(orderId), twice | PaymentService.pay(String) |
| OrderService.place | inventory.reserve(orderId) | InventoryService.reserve |
| OrderService.place | notifications.confirm(orderId) | NotificationService.confirm |
| InventoryService.reserve | repository.save(orderId) | InventoryRepository.save |
| RetryPaymentJob.retry | payments.pay(orderId, 2) | PaymentService.pay(String, int) |
| NestedTypes.Worker.run(String) | payments.pay(orderId) | PaymentService.pay(String) |
| CycleA.run | next.run() | CycleB.run |
| CycleB.run | next.run() | CycleC.run |
| CycleC.run | next.run() | CycleA.run |
| OrderScenario.exercise | controller.submit("synthetic") | OrderController.submit |

RetryPaymentJob directly invokes PaymentService and is a direct caller when that call is resolved. NotificationService is a collaborator of OrderService and has a same-class formatting call. OrderService validation and PaymentService auditing/overload delegation also exercise same-class calls.

Dependencies use constructor injection and field-scoped calls. PaymentService has overloaded constructors and explicit `this`/`super` invocations. NestedTypes has a static member class, constructor, overloaded run methods, and object creation. Repeated payment call sites have separate locations. UnresolvedClient imports an intentionally absent `missing.vendor.ExternalGateway` and calls `gateway.deliver(orderId)`; no dependency supplies it.

First-pass extraction leaves all calls UNRESOLVED with NOT_ATTEMPTED details and absent targets, including locally plausible calls. Reference-parameter identities remain location-qualified fallbacks. Resolution, unique graph edges, reachability, and runtime behavior are not asserted here.
