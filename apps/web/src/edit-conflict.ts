export class StaleEdit extends Error {}

export function checkEditConflict(response: Response) {
  if (
    response.status === 409 &&
    response.headers.get('content-type')?.startsWith('application/problem+json')
  ) {
    throw new StaleEdit('The record changed. Reopen the editor to review its current values.')
  }
}
