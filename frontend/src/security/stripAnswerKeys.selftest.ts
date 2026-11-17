import { stripGradedAnswerKeys } from './stripAnswerKeys'

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) {
    throw new Error(message)
  }
}

const leaked = {
  challenge: {
    challengeId: 'chal-1',
    documentation: 'brief',
    expectedMethod: 'GET',
    pathTemplate: '/api/doors/alpha',
    pathParameters: { id: 'x' },
    queryParameters: { zone: 'core' },
  },
  missionProgress: {
    mandatoryDevices: [
      {
        deviceId: 'door-alpha',
        expectedMethod: 'GET',
        pathTemplate: '/api/doors/alpha',
      },
    ],
  },
}

const cleaned = stripGradedAnswerKeys(leaked)

assert(cleaned.challenge.challengeId === 'chal-1', 'keeps public ids')
assert(cleaned.challenge.documentation === 'brief', 'keeps documentation')
assert(!('expectedMethod' in cleaned.challenge), 'strips expectedMethod')
assert(!('pathTemplate' in cleaned.challenge), 'strips pathTemplate')
assert(!('pathParameters' in cleaned.challenge), 'strips pathParameters')
assert(!('queryParameters' in cleaned.challenge), 'strips queryParameters')
assert(
  !('expectedMethod' in cleaned.missionProgress.mandatoryDevices[0]),
  'strips nested device answer keys',
)

console.log('stripAnswerKeys.selftest: ok')
