// Package schema applies the gateway's idempotent SQL migrations at startup.
//
// The repository's migrations/ directory is mounted into postgres as
// docker-entrypoint-initdb.d, which only runs when the data volume is empty, so a
// table added after the production database was created would never appear there.
// The files in sql/ are byte-for-byte copies of those migrations (a test enforces
// it) and every statement in them is CREATE ... IF NOT EXISTS, so running them on
// every start is safe.
package schema

import (
	"context"
	"embed"
	"fmt"
	"io/fs"
	"sort"

	"github.com/jackc/pgx/v5/pgxpool"
)

//go:embed sql/*.sql
var files embed.FS

// advisoryLockKey serialises concurrent gateway replicas so two CREATE TABLE IF NOT
// EXISTS cannot race each other on the catalog.
const advisoryLockKey = 0x6e6f6f7261696e // "noorain"

// Files returns the embedded migration file names in apply order.
func Files() ([]string, error) {
	names, err := fs.Glob(files, "sql/*.sql")
	if err != nil {
		return nil, err
	}
	sort.Strings(names)
	return names, nil
}

// Apply runs every embedded migration, in name order, inside one transaction.
func Apply(ctx context.Context, db *pgxpool.Pool) error {
	names, err := Files()
	if err != nil {
		return err
	}
	tx, err := db.Begin(ctx)
	if err != nil {
		return fmt.Errorf("begin: %w", err)
	}
	defer tx.Rollback(ctx) //nolint:errcheck // no-op after commit

	if _, err := tx.Exec(ctx, "SELECT pg_advisory_xact_lock($1)", int64(advisoryLockKey)); err != nil {
		return fmt.Errorf("advisory lock: %w", err)
	}
	for _, name := range names {
		body, err := files.ReadFile(name)
		if err != nil {
			return err
		}
		// No arguments: pgx uses the simple protocol, so a file may hold several statements.
		if _, err := tx.Exec(ctx, string(body)); err != nil {
			return fmt.Errorf("%s: %w", name, err)
		}
	}
	return tx.Commit(ctx)
}
