//go:build !linux && !darwin

package httpapi

// diskFreeBytes is unsupported here; the free-space guard is skipped.
func diskFreeBytes(string) (uint64, bool) { return 0, false }
