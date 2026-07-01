import test from 'node:test'
import assert from 'node:assert/strict'
import { buildUrl, buildHeaders, unwrapEnvelope } from '../utils/api.js'

test('buildUrl joins API base and path once', () => {
  assert.equal(
    buildUrl('https://joygather.kyhome.me/api/', '/auth/login'),
    'https://joygather.kyhome.me/api/auth/login',
  )
})

test('buildHeaders attaches bearer token when present', () => {
  assert.deepEqual(buildHeaders('abc123'), {
    'Content-Type': 'application/json',
    Authorization: 'Bearer abc123',
  })
})

test('buildHeaders omits bearer token when absent', () => {
  assert.deepEqual(buildHeaders(''), {
    'Content-Type': 'application/json',
  })
})

test('unwrapEnvelope returns data for successful API envelope', () => {
  assert.deepEqual(unwrapEnvelope({ code: 0, message: 'ok', data: { id: 'a-001' } }), { id: 'a-001' })
})

test('unwrapEnvelope throws backend message for failed envelope', () => {
  assert.throws(() => unwrapEnvelope({ code: 500, message: '请先登录', data: null }), /请先登录/)
})
