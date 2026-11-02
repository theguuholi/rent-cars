import type { ProxyOptions } from 'vite'

export function createApiProxy(target?: string): Record<string, ProxyOptions> {
  return {
    '/api': {
      target: target?.trim() || 'http://127.0.0.1:8080',
      rewrite: (path) => path.replace(/^\/api\b/, ''),
    },
  }
}