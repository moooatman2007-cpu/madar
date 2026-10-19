# Supabase

Production project: `oknpfsvmopdsgsfbbdcs` (TeacherHub).

## IMPORTANT: the migration history is not reproducible yet
The live database was NOT built from `migrations_legacy/` (production's
`schema_migrations` contains a single row). Never run `supabase db push` against
production with those files. They are kept only for reference.

### One-time baseline (needs the Supabase CLI, run once)
```bash
supabase login && supabase link --project-ref oknpfsvmopdsgsfbbdcs
supabase db dump --schema public,storage -f supabase/baseline.sql   # inspect it
# Save it as a migration OLDER than 20261019044122, e.g.:
mv supabase/baseline.sql supabase/migrations/20260918000000_baseline.sql
supabase migration repair --status applied 20260918000000
```
Then `supabase db reset` (local) must succeed from scratch. All later schema changes
go in new files under `supabase/migrations/`.

### Edge Functions
The deployed code is newer than both older repo copies. Download the deployed source
and commit it, so the repo is the source of truth again:
```bash
supabase functions download waha-session
supabase functions download waha-webhook
supabase functions download studentmanager-notification-worker
```
