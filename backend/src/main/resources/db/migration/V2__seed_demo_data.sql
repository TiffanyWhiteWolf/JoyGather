INSERT INTO users (id, email, password_hash, nickname, avatar, role, city, bio, interests, following_count, follower_count, credit, verified, activated, status, merchant_name)
VALUES
('u-001','demo@quju.cn','plain:12345678','小满','https://i.pravatar.cc/160?img=47','个人用户','杭州','在城市缝隙里寻找晚风和好故事。','城市漫步,摄影,咖啡,飞盘',86,236,98,1,1,'正常',NULL),
('u-002','wild@quju.cn','plain:12345678','野路子俱乐部','https://i.pravatar.cc/160?img=12','商家用户','杭州','一起去野','徒步',32,1240,99,1,1,'正常','野路子俱乐部'),
('u-003','slice@quju.cn','plain:12345678','城市切片社','https://i.pravatar.cc/160?img=5','个人用户','杭州','观察城市','摄影,建筑',65,680,97,1,1,'正常',NULL),
('u-004','abei@quju.cn','plain:12345678','阿北','https://i.pravatar.cc/160?img=33','个人用户','杭州','桌游设计师','桌游',119,408,96,0,1,'正常',NULL),
('u-025','momo77@quju.cn','plain:12345678','Momo_77','https://i.pravatar.cc/80?img=25','个人用户','杭州','喜欢热闹，也需要边界。','桌游,咖啡',12,30,72,0,1,'已封禁',NULL);

UPDATE users SET ban_reason='多次发布与实际不符的活动信息', ban_until='2026-07-29' WHERE id='u-025';

INSERT INTO activities (id,title,summary,description,category,cover,date_label,time_label,location,district,distance,longitude,latitude,price,capacity,joined_count,status,organizer_id,featured,safety_note,min_age,join_fields,published_at)
VALUES
('act-001','落日以后，沿运河散步','从桥西到小河直街，收集夏夜的橘色时刻','这不是一场赶路式 citywalk。我们会从桥西历史街区出发，沿运河慢慢走到小河直街，在晚风、老厂房与街边小店之间认识彼此。带上相机，也可以只带一双舒服的鞋。','城市探索','https://images.unsplash.com/photo-1519501025264-65ba15a82390?auto=format&fit=crop&w=1400&q=85','06月30日','18:30 - 21:00','桥西历史文化街区','拱墅区',2.4,56,44,0,18,13,'报名中','u-003',1,'请穿舒适的鞋，夜间活动请保持同行。',16,'真实姓名,手机号码',NOW()),
('act-002','九溪轻徒步｜去山里喝杯茶','低强度，新手友好，在茶山里认识新朋友','一条对新手很友好的轻徒步线路，途经九溪烟树与龙井村。我们会在茶园停留，分享各自带来的小点心。','户外运动','https://images.unsplash.com/photo-1551632811-561732d1e306?auto=format&fit=crop&w=1200&q=85','07月02日','09:00 - 14:30','九溪公交站','西湖区',8.6,28,68,39,24,21,'报名中','u-002',0,'根据天气准备防晒或雨具。',16,'真实姓名,手机号码,紧急联系人',NOW()),
('act-003','不熟也能玩的桌游夜','拒绝硬核规则，专注快乐和有趣的人','精选派对桌游和轻策略桌游，无需经验，主持人全程带玩。','桌游聚会','https://images.unsplash.com/photo-1610890716171-6b1bb98ffd09?auto=format&fit=crop&w=1200&q=85','07月04日','19:00 - 22:30','湖滨银泰 IN77','上城区',4.1,67,63,49,12,12,'即将开始','u-004',0,'请保管好个人物品。',16,'真实姓名,手机号码',NOW()),
('act-004','周六清晨湖边飞盘局','有教练、有装备，第一次玩也完全没问题','从传盘技巧开始，一小时后就能加入轻松的小比赛。','运动健身','https://images.unsplash.com/photo-1552674605-db6ffd4facb5?auto=format&fit=crop&w=1200&q=85','07月05日','07:30 - 10:00','太子湾公园大草坪','西湖区',6.8,38,30,25,30,17,'报名中','u-001',0,'请穿运动鞋，注意热身。',12,'真实姓名,手机号码',NOW()),
('act-005','独立书店的一小时交换','带一本读过的书，换走一段陌生人的故事','我们交换书，也交换最近读到的一句话。','学习交流','https://images.unsplash.com/photo-1526243741027-444d633d7365?auto=format&fit=crop&w=1200&q=85','07月06日','14:00 - 16:00','晓风书屋','滨江区',9.2,76,27,0,16,9,'报名中','u-003',0,'请尊重现场安静阅读环境。',12,'真实姓名,手机号码',NOW()),
('act-006','旧衣新生工作坊','用两个小时，让闲置衣物重新回到生活里','由志愿设计师带领完成旧衣改造，材料由现场提供。','公益活动','https://images.unsplash.com/photo-1542601906990-b4d3fb778b09?auto=format&fit=crop&w=1200&q=85','07月08日','13:30 - 16:30','天目里社区中心','西湖区',5.3,45,76,10,20,14,'报名中','u-002',0,'现场有剪裁工具，请听从志愿者指引。',12,'真实姓名,手机号码',NOW());

INSERT INTO activity_tags (activity_id, tag, rank_order)
VALUES
('act-001','Citywalk',1),('act-001','日落',2),('act-001','摄影友好',3),
('act-002','轻徒步',1),('act-002','新手友好',2),('act-002','自然',3),
('act-003','桌游',1),('act-003','破冰',2),('act-003','室内',3),
('act-004','飞盘',1),('act-004','晨间',2),('act-004','零基础',3),
('act-005','阅读',1),('act-005','交换',2),('act-005','安静社交',3),
('act-006','公益',1),('act-006','手作',2),('act-006','可持续',3);

INSERT INTO registrations (id, activity_id, user_id, status, queue_position)
VALUES
('reg-01','act-001','u-001','已报名',0),
('reg-02','act-003','u-001','候补中',1);

INSERT INTO review_tasks (id,type,target_id,title,submitter,risk,reason,submitted_at,status)
VALUES
('rv-001','活动审核','act-pending-001','千岛湖夜间桨板体验','浪尖水上俱乐部','高','水上活动 / 预计 68 人 / AI 标记安全风险',DATE_SUB(NOW(), INTERVAL 10 MINUTE),'待审核'),
('rv-002','商家认证','merchant-001','流木咖啡（湖滨店）','陈先生','低','营业执照与门店认证',DATE_SUB(NOW(), INTERVAL 32 MINUTE),'待审核'),
('rv-003','举报处理','report-001','夏夜酒吧交友派对','用户举报 3 次','中','疑似宣传内容与实际活动不符',DATE_SUB(NOW(), INTERVAL 1 HOUR),'待审核'),
('rv-004','活动审核','act-pending-002','凌晨天文观测小队','星空信号站','中','夜间活动 / 偏远地点',DATE_SUB(NOW(), INTERVAL 2 HOUR),'待审核');

INSERT INTO teams (id,name,description,cover,tags,members_count,capacity,join_mode,active_now,owner_id)
VALUES
('team-01','晚风散步社','每周一次，不赶路的城市散步。','https://images.unsplash.com/photo-1518005020951-eccb494ad742?auto=format&fit=crop&w=900&q=80','Citywalk,建筑',186,300,'公开加入',28,'u-003'),
('team-02','周末逃跑计划','把周末交给山野、溪流和公路。','https://images.unsplash.com/photo-1500534314209-a25ddb2bd4297?auto=format&fit=crop&w=900&q=80','徒步,露营',92,120,'审核加入',16,'u-002'),
('team-03','胶片未冲洗','欢迎不完美的构图和有温度的瞬间。','https://images.unsplash.com/photo-1452780212940-6f5c0d14d848?auto=format&fit=crop&w=900&q=80','摄影,胶片',64,100,'公开加入',9,'u-003');

INSERT INTO team_members (team_id,user_id,role)
VALUES ('team-01','u-001','成员'),('team-01','u-003','队长'),('team-02','u-002','队长'),('team-03','u-003','队长');

INSERT INTO conversations (id,name,avatar,type,team_id,friend_user_id,unread,last_message,last_time,online)
VALUES
('cv-01','晚风散步社','https://images.unsplash.com/photo-1518005020951-eccb494ad742?auto=format&fit=crop&w=200&q=80','小队','team-01',NULL,6,'安安：周六路线已经发群公告啦','10:42',1),
('cv-02','林屿','https://i.pravatar.cc/160?img=16','好友',NULL,NULL,0,'到时候活动见！','昨天',1),
('cv-03','周末逃跑计划','https://images.unsplash.com/photo-1500534314209-a25ddb2bd4297?auto=format&fit=crop&w=200&q=80','小队','team-02',NULL,2,'领队：这周线路临时调整','昨天',0),
('cv-04','阿北','https://i.pravatar.cc/160?img=33','好友',NULL,'u-004',0,'这款桌游真的很适合破冰','周六',0);

INSERT INTO messages (id,conversation_id,sender_id,content,sent_at,mine,read_flag)
VALUES
('m1','cv-01','u-003','周六天气很好，我们还是老地方集合～',DATE_SUB(NOW(), INTERVAL 8 MINUTE),0,1),
('m2','cv-01','u-001','收到！我会带相机，有人想一起拍胶片吗？',DATE_SUB(NOW(), INTERVAL 5 MINUTE),1,1),
('m3','cv-01','u-003','周六路线已经发群公告啦，记得穿舒服的鞋。',DATE_SUB(NOW(), INTERVAL 2 MINUTE),0,0);
