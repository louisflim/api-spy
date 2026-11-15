export interface Module0Option {
  id: string
  label: string
}

export interface Module0Interaction {
  type: 'multi_choice' | 'request_response' | 'terminal_training' | string
  prompt: string
  options?: Module0Option[]
  correctOptionId?: string
  successFeedback: string
  failureFeedback: string
  demoMethod?: string
  demoPath?: string
  demoStatus?: number
  demoBody?: Record<string, unknown>
  challengeId?: string
}

export interface Module0Topic {
  id: string
  order: number
  title: string
  objective: string
  controlIntro: string
  explanation: string
  workedExample: {
    title: string
    notes: string
    body: string
  }
  interaction: Module0Interaction
  completionMessage: string
}

export interface Module0Academy {
  moduleId: string
  title: string
  outsideCatalog: boolean
  graded: boolean
  includedInKpis: boolean
  topics: Module0Topic[]
}

export interface Module0Progress {
  participantCode: string
  lastTopicId: string
  completedTopicIds: string[]
  completedSteps: Record<string, string[]>
  orientationCompleted: boolean
  updatedAt: string
}

export type TopicStatus = 'completed' | 'in_progress' | 'not_started'
