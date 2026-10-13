package schema

import (
	"bytes"
	"os"
	"path/filepath"
	"testing"
)

// The embedded copies must stay identical to the repository migrations that a fresh
// postgres volume runs from docker-entrypoint-initdb.d.
func TestEmbeddedFilesMatchRepositoryMigrations(t *testing.T) {
	names, err := Files()
	if err != nil {
		t.Fatal(err)
	}
	if len(names) == 0 {
		t.Fatal("no embedded migrations")
	}
	repoDir := filepath.Join("..", "..", "..", "..", "migrations")
	if _, err := os.Stat(repoDir); err != nil {
		t.Skipf("repository migrations dir not available (%v); built outside the monorepo", err)
	}
	for _, name := range names {
		embedded, err := files.ReadFile(name)
		if err != nil {
			t.Fatal(err)
		}
		repo, err := os.ReadFile(filepath.Join(repoDir, filepath.Base(name)))
		if err != nil {
			t.Fatalf("%s has no counterpart in migrations/: %v", name, err)
		}
		if !bytes.Equal(embedded, repo) {
			t.Fatalf("%s differs from migrations/%s; copy it again", name, filepath.Base(name))
		}
	}
}
