# Angular Routing Guide

Establishes the routing conventions for the EPM frontend (`frontend/epm-frontend`).

---

## The Rule

| Context | Use | Example |
|---------|-----|---------|
| Feature component navigation (page-level) | **Absolute route** | `this.router.navigate(['/projects', id, 'tasks', 'new'])` |
| Within a component's own named child outlet | **Relative route** with `{ relativeTo: this.route }` | `this.router.navigate(['..'], { relativeTo: this.route })` |

---

## Why Absolute Routes for Feature Navigation

Angular's `router.navigate(['relative-path'])` resolves relative to the **current activated route**. In lazy-loaded feature modules, the activated route already includes the feature path prefix. Using a relative path like `['tasks', 'new']` from a component at `/projects/1` produces `/projects/1/tasks/tasks/new` — a **duplicated segment**.

### The Phase 4 Bug (motivation)

During Phase 4, the Kanban board component navigated to `['tasks', 'new']` (relative) from a route at `/projects/:projectId/tasks/kanban`. This resolved to `/projects/1/tasks/kanban/tasks/new` instead of `/projects/1/tasks/new`.

Fixed in commit `875485d` by switching all feature navigations to absolute paths.

---

## When Relative Routes ARE Correct

Using `['..']` or `['../sibling']` with `{ relativeTo: this.route }` is the **right approach** when navigating within a component's own child outlet — for example, navigating "back" to the parent after form submission:

```typescript
// TaskFormComponent — after save, go back to the task list
this.router.navigate(['..'], { relativeTo: this.route });
// At /projects/1/tasks/new  →  resolves to /projects/1/tasks  ✅
```

This is correct because we are navigating UP the current route tree, not constructing a new absolute path.

---

## Checklist for New Routes

When adding a new navigation call:

- [ ] Does it navigate to a **different feature page**? → Use an **absolute path**: `['/feature', ...params]`
- [ ] Does it navigate **within the same component's child outlet** (e.g., "cancel", "back")? → Use a **relative path** with `{ relativeTo: this.route }`
- [ ] Does it result in a duplicated segment? → Switch to absolute path immediately

---

## Current Route Map

```
/                           → redirects to /projects
/projects                   → ProjectListComponent
/projects/new               → ProjectCreateComponent
/projects/:projectId/tasks          → TaskListComponent
/projects/:projectId/tasks/new      → TaskFormComponent
/projects/:projectId/tasks/kanban   → KanbanBoardComponent
```

---

## References

- [Angular Router — relativeTo](https://angular.dev/api/router/NavigationExtras#relativeTo)
- Phase 4 fix commit: `875485d` — "fix(frontend): use absolute routes in Kanban and TaskList"
