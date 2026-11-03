import { describe, expect, test as scenario } from 'vitest'
import { formatBrl, formatBrlInput, parseBrl } from './brl'

describe('BRL amounts', () => {
  scenario.each([
    ['150', 15000],
    ['150,5', 15050],
    ['0,01', 1],
    [' 0 ', 0],
    ['21474836,47', 2147483647],
    ['21474836,48', null],
    ['150,555', null],
    ['', null],
    ['-1', null],
    ['1e3', null],
    ['1.234,00', null],
    ['150,', null],
    ['1.50', null],
  ])(
    'Given the draft "%s", when parsed on submit, then its integer cents are %s',
    (draft, expected) => {
      expect(parseBrl(String(draft))).toBe(expected)
    },
  )

  scenario(
    'Given stored cents, when opening an editor, then the ungrouped value round trips exactly',
    () => {
      expect(formatBrlInput(123450)).toBe('1234,50')
      expect(parseBrl(formatBrlInput(2147483647))).toBe(2147483647)
    },
  )

  scenario(
    'Given stored cents, when displaying a rate, then the amount uses Brazilian currency and grouping',
    () => {
      expect(formatBrl(123450)).toMatch(/^R\$\s1\.234,50$/)
    },
  )
})
