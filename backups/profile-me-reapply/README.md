# Backup: profile `/me` work (commit `16579bd`)

This folder preserves the profile management work that landed on `main` by mistake, so you can **revert `main`** and re-apply everything on a feature branch.

## What is saved

| Artifact | Purpose |
|----------|---------|
| **Git branch** `backup/profile-me-endpoints` | Points at commit `16579bd` (same tree as the profile commit). |
| `git-patches/0001-Add-profile-management-functionality-with-API-endpoi.patch` | `git format-patch` for that commit (use `git am` or review/apply manually). |
| `missing-enum-files/Role.java` and `Province.java` | `Profile.java` in `16579bd` references these types but they were **not** in that commit; copy them into `src/.../model/` after applying or the project will not compile. |
| `category-hardening-wip.patch` | Optional local WIP: `Category` UUID generation + `created_at` DB-managed columns (only if you still want that after the profile branch). |

## Re-apply on a new feature branch

```bash
git checkout main
git pull
git checkout -b feat/profiles-me-endpoints

# Option A — cherry-pick (keeps one commit)
git cherry-pick 16579bd

# Option B — apply the patch file
# git am backups/profile-me-reapply/git-patches/0001-*.patch

# Required: add enum files missing from 16579bd
cp backups/profile-me-reapply/missing-enum-files/Role.java src/main/java/com/gatherly/gatherly_api/model/
cp backups/profile-me-reapply/missing-enum-files/Province.java src/main/java/com/gatherly/gatherly_api/model/

# Optional: Category hardening (fix line endings if Git complains on Windows)
git apply backups/profile-me-reapply/category-hardening-wip.patch
```

Then run tests: `./mvnw test`

## Revert `main` (after this backup is committed or the branch exists)

**If others may have pulled `main`**, prefer revert (adds a new commit):

```bash
git checkout main
git revert 16579bd --no-edit
git push
```

(Use `git revert -m 1 <merge_commit>` only when reverting a **merge** commit.)

**Local backup branch** `backup/profile-me-endpoints` is not pushed by default. To push it as a remote safety net:

```bash
git push -u origin backup/profile-me-endpoints
```

## Commit hash reference

- Profile feature commit: `16579bd` (`Add profile management functionality with API endpoints and data transfer objects`)
- Parent (pre-profile) on current history: `730281d`
