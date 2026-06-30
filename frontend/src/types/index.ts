export type ActivityStatus = '草稿' | '审核中' | '报名中' | '已截止' | '即将开始' | '进行中' | '已结束' | '已下架'
export type ActivityCategory = '城市探索' | '户外运动' | '桌游聚会' | '学习交流' | '运动健身' | '公益活动'
export type UserRole = '个人用户' | '商家用户' | '管理员'
export type ReviewStatus = '待审核' | '已通过' | '已驳回' | '要求修改'

export interface User {
  id: string
  nickname: string
  avatar: string
  role: UserRole
  city: string
  gender?: string
  birthday?: string
  bio: string
  interests: string[]
  following: number
  followers: number
  credit: number
  verified?: boolean
  status?: '正常' | '已封禁'
  banReason?: string
  banUntil?: string
  merchantName?: string
  merchantNickname?: string
  merchantFields?: string[]
}

export interface Activity {
  id: string
  title: string
  summary: string
  description: string
  category: ActivityCategory
  cover: string
  date: string
  time: string
  startAt?: string
  endAt?: string
  deadline?: string
  location: string
  district: string
  distance: number
  longitude: number
  latitude: number
  price: number
  capacity: number
  joined: number
  tags: string[]
  status: ActivityStatus
  organizer: User
  featured?: boolean
  safetyNote?: string
  minAge?: number
  joinFields?: string[]
  offlineReason?: string
  updatedAt?: string
}

export interface Team {
  id: string
  name: string
  description: string
  cover: string
  tags: string[]
  members: number
  capacity: number
  joinMode: '公开加入' | '审核加入'
  activeNow: number
  status?: '正常' | '已停用'
  stopReason?: string
  ownerId?: string
  ownerNickname?: string
  memberRecords?: Record<string, unknown>[]
  activityRecords?: Record<string, unknown>[]
  reportRecords?: Record<string, unknown>[]
}

export interface MerchantApplication {
  id: string
  userId?: string
  merchantName?: string
  licenseName?: string
  licenseUrl?: string
  status: ReviewStatus
  reason?: string
  submittedAt?: string
  reviewedAt?: string
  reviewerId?: string
  email?: string
  nickname?: string
}

export interface Registration {
  id: string
  activityId: string
  userId: string
  status: '已报名' | '候补中' | '已签到' | '已取消'
  createdAt: string
}

export interface ActivityDraft {
  id: string
  title: string
  category: ActivityCategory
  tags: string
  summary: string
  date: string
  startTime: string
  endTime: string
  location: string
  district: string
  capacity: number
  deadline: string
  price: number
  minAge: number
  safetyNote: string
  joinFields: string[]
  updatedAt: string
}

export interface ReviewTask {
  id: string
  type: '活动审核' | '商家认证' | '举报处理'
  title: string
  submitter: string
  risk: '低' | '中' | '高'
  reason: string
  submittedAt: string
  status: ReviewStatus
  targetId: string
}

export interface Message {
  id: string
  senderId: string
  content: string
  type?: 'TEXT' | 'IMAGE' | 'FILE' | 'LOCATION'
  mediaUrl?: string
  latitude?: number
  longitude?: number
  time: string
  mine: boolean
  read: boolean
  recalled?: boolean
}

export interface Conversation {
  id: string
  name: string
  avatar: string
  type: '好友' | '小队'
  unread: number
  lastMessage: string
  lastTime: string
  online?: boolean
  messages: Message[]
}
