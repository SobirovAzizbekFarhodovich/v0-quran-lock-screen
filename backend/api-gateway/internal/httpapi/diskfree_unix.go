//go:build linux || darwin

package httpapi

import "syscall"

// diskFreeBytes reports the space available to unprivileged users on dir's filesystem.
func diskFreeBytes(dir string) (uint64, bool) {
	var st syscall.Statfs_t
	if err := syscall.Statfs(dir, &st); err != nil {
		return 0, false
	}
	return uint64(st.Bavail) * uint64(st.Bsize), true
}
