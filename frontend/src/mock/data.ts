import type { Activity, Conversation, Registration, ReviewTask, Team, User } from '@/types'

export const currentUser: User = {
  id: 'u-001', nickname: '小满', avatar: 'https://i.pravatar.cc/160?img=47', role: '个人用户',
  city: '杭州', bio: '在城市缝隙里寻找晚风和好故事。', interests: ['城市漫步', '摄影', '咖啡', '飞盘'],
  following: 86, followers: 236, credit: 98, verified: true,
}

const organizers: User[] = [
  { id: 'u-002', nickname: '野路子俱乐部', avatar: 'https://i.pravatar.cc/160?img=12', role: '商家用户', city: '杭州', bio: '一起去野', interests: ['徒步'], following: 32, followers: 1240, credit: 99, verified: true },
  { id: 'u-003', nickname: '城市切片社', avatar: 'https://i.pravatar.cc/160?img=5', role: '个人用户', city: '杭州', bio: '观察城市', interests: ['摄影', '建筑'], following: 65, followers: 680, credit: 97, verified: true },
  { id: 'u-004', nickname: '阿北', avatar: 'https://i.pravatar.cc/160?img=33', role: '个人用户', city: '杭州', bio: '桌游设计师', interests: ['桌游'], following: 119, followers: 408, credit: 96 },
]

export const activities: Activity[] = [
  {
    id: 'act-001', title: '落日以后，沿运河散步', summary: '从桥西到小河直街，收集夏夜的橘色时刻',
    description: '这不是一场赶路式 citywalk。我们会从桥西历史街区出发，沿运河慢慢走到小河直街，在晚风、老厂房与街边小店之间认识彼此。带上相机，也可以只带一双舒服的鞋。',
    category: '城市探索', cover: 'https://images.unsplash.com/photo-1519501025264-65ba15a82390?auto=format&fit=crop&w=1400&q=85',
    date: '06月30日', time: '18:30 - 21:00', location: '桥西历史文化街区', district: '拱墅区', distance: 2.4,
    longitude: 56, latitude: 44, price: 0, capacity: 18, joined: 13, tags: ['Citywalk', '日落', '摄影友好'], status: '报名中', organizer: organizers[1], featured: true,
  },
  {
    id: 'act-002', title: '九溪轻徒步｜去山里喝杯茶', summary: '低强度，新手友好，在茶山里认识新朋友',
    description: '一条对新手很友好的轻徒步线路，途经九溪烟树与龙井村。我们会在茶园停留，分享各自带来的小点心。',
    category: '户外运动', cover: 'https://images.unsplash.com/photo-1551632811-561732d1e306?auto=format&fit=crop&w=1200&q=85',
    date: '07月02日', time: '09:00 - 14:30', location: '九溪公交站', district: '西湖区', distance: 8.6,
    longitude: 28, latitude: 68, price: 39, capacity: 24, joined: 21, tags: ['轻徒步', '新手友好', '自然'], status: '报名中', organizer: organizers[0],
  },
  {
    id: 'act-003', title: '不熟也能玩的桌游夜', summary: '拒绝硬核规则，专注快乐和有趣的人',
    description: '精选派对桌游和轻策略桌游，无需经验，主持人全程带玩。',
    category: '桌游聚会', cover: 'https://images.unsplash.com/photo-1610890716171-6b1bb98ffd09?auto=format&fit=crop&w=1200&q=85',
    date: '07月04日', time: '19:00 - 22:30', location: '湖滨银泰 IN77', district: '上城区', distance: 4.1,
    longitude: 67, latitude: 63, price: 49, capacity: 12, joined: 12, tags: ['桌游', '破冰', '室内'], status: '即将开始', organizer: organizers[2],
  },
  {
    id: 'act-004', title: '周六清晨湖边飞盘局', summary: '有教练、有装备，第一次玩也完全没问题',
    description: '从传盘技巧开始，一小时后就能加入轻松的小比赛。',
    category: '运动健身', cover: 'https://images.unsplash.com/photo-1552674605-db6ffd4facb5?auto=format&fit=crop&w=1200&q=85',
    date: '07月05日', time: '07:30 - 10:00', location: '太子湾公园大草坪', district: '西湖区', distance: 6.8,
    longitude: 38, latitude: 30, price: 25, capacity: 30, joined: 17, tags: ['飞盘', '晨间', '零基础'], status: '报名中', organizer: currentUser,
  },
  {
    id: 'act-005', title: '独立书店的一小时交换', summary: '带一本读过的书，换走一段陌生人的故事',
    description: '我们交换书，也交换最近读到的一句话。',
    category: '学习交流', cover: 'https://images.unsplash.com/photo-1526243741027-444d633d7365?auto=format&fit=crop&w=1200&q=85',
    date: '07月06日', time: '14:00 - 16:00', location: '晓风书屋', district: '滨江区', distance: 9.2,
    longitude: 76, latitude: 27, price: 0, capacity: 16, joined: 9, tags: ['阅读', '交换', '安静社交'], status: '报名中', organizer: organizers[1],
  },
  {
    id: 'act-006', title: '旧衣新生工作坊', summary: '用两个小时，让闲置衣物重新回到生活里',
    description: '由志愿设计师带领完成旧衣改造，材料由现场提供。',
    category: '公益活动', cover: 'https://images.unsplash.com/photo-1542601906990-b4d3fb778b09?auto=format&fit=crop&w=1200&q=85',
    date: '07月08日', time: '13:30 - 16:30', location: '天目里社区中心', district: '西湖区', distance: 5.3,
    longitude: 45, latitude: 76, price: 10, capacity: 20, joined: 14, tags: ['公益', '手作', '可持续'], status: '报名中', organizer: organizers[0],
  },
]

export const teams: Team[] = [
  { id: 'team-01', name: '晚风散步社', description: '每周一次，不赶路的城市散步。', cover: 'https://images.unsplash.com/photo-1518005020951-eccb494ad742?auto=format&fit=crop&w=900&q=80', tags: ['Citywalk', '建筑'], members: 186, capacity: 300, joinMode: '公开加入', activeNow: 28 },
  { id: 'team-02', name: '周末逃跑计划', description: '把周末交给山野、溪流和公路。', cover: 'https://images.unsplash.com/photo-1500534314209-a25ddb2bd4297?auto=format&fit=crop&w=900&q=80', tags: ['徒步', '露营'], members: 92, capacity: 120, joinMode: '审核加入', activeNow: 16 },
  { id: 'team-03', name: '胶片未冲洗', description: '欢迎不完美的构图和有温度的瞬间。', cover: 'https://images.unsplash.com/photo-1452780212940-6f5c0d14d848?auto=format&fit=crop&w=900&q=80', tags: ['摄影', '胶片'], members: 64, capacity: 100, joinMode: '公开加入', activeNow: 9 },
]

export const registrations: Registration[] = [
  { id: 'reg-01', activityId: 'act-001', userId: 'u-001', status: '已报名', createdAt: '2026-06-28 10:22' },
  { id: 'reg-02', activityId: 'act-003', userId: 'u-001', status: '候补中', createdAt: '2026-06-27 18:06' },
]

export const reviewTasks: ReviewTask[] = [
  { id: 'rv-001', type: '活动审核', title: '千岛湖夜间桨板体验', submitter: '浪尖水上俱乐部', risk: '高', reason: '水上活动 / 预计 68 人 / AI 标记安全风险', submittedAt: '10分钟前', status: '待审核', targetId: 'act-002' },
  { id: 'rv-002', type: '商家认证', title: '流木咖啡（湖滨店）', submitter: '陈先生', risk: '低', reason: '营业执照与门店认证', submittedAt: '32分钟前', status: '待审核', targetId: '' },
  { id: 'rv-003', type: '举报处理', title: '夏夜酒吧交友派对', submitter: '用户举报 3 次', risk: '中', reason: '疑似宣传内容与实际活动不符', submittedAt: '1小时前', status: '待审核', targetId: '' },
  { id: 'rv-004', type: '活动审核', title: '凌晨天文观测小队', submitter: '星空信号站', risk: '中', reason: '夜间活动 / 偏远地点', submittedAt: '2小时前', status: '待审核', targetId: 'act-006' },
]

export const conversations: Conversation[] = [
  { id: 'cv-01', name: '晚风散步社', avatar: 'https://images.unsplash.com/photo-1518005020951-eccb494ad742?auto=format&fit=crop&w=200&q=80', type: '小队', unread: 6, lastMessage: '安安：周六路线已经发群公告啦', lastTime: '10:42', online: true, messages: [
    { id: 'm1', senderId: 'u2', content: '周六天气很好，我们还是老地方集合～', time: '10:36', mine: false, read: true },
    { id: 'm2', senderId: 'u1', content: '收到！我会带相机，有人想一起拍胶片吗？', time: '10:39', mine: true, read: true },
    { id: 'm3', senderId: 'u2', content: '周六路线已经发群公告啦，记得穿舒服的鞋 👟', time: '10:42', mine: false, read: false },
  ] },
  { id: 'cv-02', name: '林屿', avatar: 'https://i.pravatar.cc/160?img=16', type: '好友', unread: 0, lastMessage: '到时候活动见！', lastTime: '昨天', online: true, messages: [] },
  { id: 'cv-03', name: '周末逃跑计划', avatar: 'https://images.unsplash.com/photo-1500534314209-a25ddb2bd4297?auto=format&fit=crop&w=200&q=80', type: '小队', unread: 2, lastMessage: '领队：这周线路临时调整', lastTime: '昨天', messages: [] },
  { id: 'cv-04', name: '阿北', avatar: 'https://i.pravatar.cc/160?img=33', type: '好友', unread: 0, lastMessage: '这款桌游真的很适合破冰', lastTime: '周六', messages: [] },
]
