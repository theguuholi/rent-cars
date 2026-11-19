# Included by the root Makefile; all paths are relative to the repository root.

.PHONY: setup web-dev check-web

setup:
	npm --prefix apps/web ci

web-dev: check-local-environment
	npm --prefix apps/web run dev -- --strictPort

check-web:
	npm --prefix apps/web run format:check
	npm --prefix apps/web run lint
	npm --prefix apps/web run architecture
	npm --prefix apps/web run test:coverage
	npm --prefix apps/web run build
